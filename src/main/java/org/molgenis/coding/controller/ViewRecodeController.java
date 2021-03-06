package org.molgenis.coding.controller;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.molgenis.coding.backup.BackupCodesInState;
import org.molgenis.coding.elasticsearch.CodingState;
import org.molgenis.coding.elasticsearch.ElasticSearchImp;
import org.molgenis.coding.elasticsearch.Hit;
import org.molgenis.coding.elasticsearch.SearchService;
import org.molgenis.coding.ngram.NGramService;
import org.molgenis.coding.util.ProcessVariableUtil;
import org.molgenis.coding.util.RecodeResponse;
import org.molgenis.data.csv.CsvWriter;
import org.molgenis.data.support.MapEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/recode")
public class ViewRecodeController
{
	private final static String VIEW_NAME = "view-recode-report";
	private final static String UNKNOWN_CODE = "99999";
	private final static String UNKNOWN_CODE_NAME = "Unknown";

	private final SearchService elasticSearchImp;
	private final NGramService nGramService;
	private final CodingState codingState;
	private final BackupCodesInState backupCodesInState;
	private final ProcessVariableUtil processVariableUtil;

	private final static List<String> ALLOWED_COLUMNS = Arrays.asList("identifier", "name");

	@Autowired
	public ViewRecodeController(SearchService elasticSearchImp, NGramService nGramService, CodingState codingState,
			BackupCodesInState backupCodesInState, ProcessVariableUtil processVariableUtil)
	{
		if (elasticSearchImp == null) throw new IllegalArgumentException("ElasticSearch is null");
		if (nGramService == null) throw new IllegalArgumentException("NGramService is null");
		if (codingState == null) throw new IllegalArgumentException("CodingState is null");
		if (backupCodesInState == null) throw new IllegalArgumentException("BackupCodesInState is null");
		if (processVariableUtil == null) throw new IllegalArgumentException("ProcessVariableUtil is null");
		this.elasticSearchImp = elasticSearchImp;
		this.nGramService = nGramService;
		this.codingState = codingState;
		this.backupCodesInState = backupCodesInState;
		this.processVariableUtil = processVariableUtil;
	}

	@RequestMapping(method = RequestMethod.GET)
	public String defaultView(Model model)
	{
		model.addAttribute("selectedCodeSystem", codingState.getSelectedCodeSystem());
		model.addAttribute("threshold", codingState.getThreshold());
		model.addAttribute("hidForm", codingState.isCoding());
		model.addAttribute("viewId", VIEW_NAME);
		model.addAttribute("isBackup", backupCodesInState.isBackupRunning());
		model.addAttribute("isUploading", processVariableUtil.isUploading());
		model.addAttribute("percentage", processVariableUtil.percentage());
		model.addAttribute("totalNumber", codingState.getTotalIndividuals().size());
		model.addAttribute("codingJobName", codingState.getCodingJobName());
		if (codingState.getErrorMessage() != null)
		{
			model.addAttribute("message", codingState.getErrorMessage());
			codingState.setErrorMessage(null);
		}
		return VIEW_NAME;
	}

	@RequestMapping(value = "/finish", method = RequestMethod.GET)
	public String finishedRecoding(Model model) throws IOException
	{
		backupCodesInState.index(true);
		return "redirect:/recode";
	}

	@RequestMapping(value = "/backup", method = RequestMethod.GET)
	public String backup(Model model) throws IOException
	{
		backupCodesInState.index(false);
		return "redirect:/recode";
	}

	@RequestMapping(value = "/recovery", method = RequestMethod.POST, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> recovery(@RequestBody Map<String, Object> request)
	{
		if (request.containsKey("codingJobName") && !StringUtils.isEmpty(request.get("codingJobName").toString()))
		{
			String codeJobName = request.get("codingJobName").toString();
			return backupCodesInState.recovery(codeJobName);
		}
		return Collections.emptyMap();
	}

	@RequestMapping(value = "/check", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> checkBackUp(Model model)
	{
		Map<String, Object> results = new HashMap<String, Object>();
		results.put("backupExists", backupCodesInState.getBackups().size() > 0);
		results.put("backup", backupCodesInState.getBackups());
		return results;
	}

	@RequestMapping(value = "/retrieve/{mapped}", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> retrieveReport(@PathVariable String mapped,
			@RequestParam(required = false, value = "maxNumber") String maxNumberOption)
	{
		Map<String, Object> results = new HashMap<String, Object>();
		try
		{
			boolean isMapped = Boolean.parseBoolean(mapped);
			int maxNumber = isMapped ? 100 : 10;
			List<RecodeResponse> values = new ArrayList<RecodeResponse>(isMapped ? codingState.getMappedActivities()
					.values() : codingState.getRawActivities().values());
			Collections.sort(values);
			if (!StringUtils.isEmpty(maxNumberOption))
			{
				if (maxNumberOption.equalsIgnoreCase("all")) maxNumber = values.size();
				else if (maxNumberOption.matches("[0-9]+")) maxNumber = Integer.parseInt(maxNumberOption);
			}
			else
			{
				maxNumber = isMapped ? 100 : 10;
				maxNumberOption = maxNumber + "";
			}

			results.put("results", values.subList(0, maxNumber <= values.size() ? maxNumber : values.size()));
			results.put("maxNumber", maxNumberOption);
		}
		catch (Exception e)
		{
			System.out.println(e.getMessage());
			throw new RuntimeException(e.getMessage());
		}

		return results;
	}

	@RequestMapping(value = "/totalnumber", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> retrieveTotalNumber()
	{
		Map<String, Object> results = new HashMap<String, Object>();

		int matchedTotal = 0;
		for (RecodeResponse recodeResponse : codingState.getMappedActivities().values())
		{
			matchedTotal += recodeResponse.getIdentifiers().size();
		}

		int unmatchedTotal = 0;
		for (RecodeResponse recodeResponse : codingState.getRawActivities().values())
		{
			unmatchedTotal += recodeResponse.getIdentifiers().size();
		}

		results.put("matchedTotal", matchedTotal);
		results.put("unmatchedTotal", unmatchedTotal);

		return results;
	}

	@RequestMapping(value = "/add", method = RequestMethod.POST, consumes = APPLICATION_JSON_VALUE)
	@ResponseStatus(OK)
	public void addDoc(@RequestBody Map<String, Object> request)
	{
		if (request.containsKey("data") && request.get("data") != null && request.containsKey("score")
				&& request.get("score") != null && request.containsKey("query") && request.get("query") != null
				&& request.containsKey("documentId") && request.get("documentId") != null && request.containsKey("add")
				&& request.get("add") != null)
		{
			Map<String, Object> data = convertObjectToMap(request.get("data"));
			if (data.containsKey(ElasticSearchImp.DEFAULT_NAME_FIELD)
					&& data.get(ElasticSearchImp.DEFAULT_NAME_FIELD) != null
					&& data.containsKey(ElasticSearchImp.DEFAULT_CODE_FIELD)
					&& data.get(ElasticSearchImp.DEFAULT_CODE_FIELD) != null
					&& data.containsKey(ElasticSearchImp.DEFAULT_NAME_FIELD)
					&& data.get(ElasticSearchImp.DEFAULT_NAME_FIELD) != null
					&& data.containsKey(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD)
					&& data.get(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD) != null)
			{
				String queryString = request.get("query").toString();
				String documentType = data.containsKey(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD)
						&& data.get(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD) != null ? data.get(
						ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD).toString() : null;
				String documentId = request.get("documentId").toString();
				Float score = Float.parseFloat(request.get("score").toString());

				// Check if the a new code entry needs to be added to the index
				if (Boolean.parseBoolean(request.get("add").toString()))
				{
					// Add a new code entry or update the existing code entry
					// depending on the similarity score
					if (score.intValue() == 100)
					{
						Hit hit = elasticSearchImp.getDocumentById(documentId, documentType);
						StringBuilder updateScriptStringBuilder = new StringBuilder();
						updateScriptStringBuilder.append("frequency").append('=').append((hit.getFrequency() + 1));
						elasticSearchImp.updateIndex(hit.getDocumentId(), updateScriptStringBuilder.toString(),
								documentType);
					}
					else
					{
						elasticSearchImp.indexDocument(
								ElasticSearchImp.addDefaultFields(translateDataToMap(data, queryString)), documentType);

						while (true)
						{
							List<Hit> searchHits = elasticSearchImp.search(documentType,
									data.get(ElasticSearchImp.DEFAULT_NAME_FIELD).toString(),
									ElasticSearchImp.DEFAULT_NAME_FIELD);
							if (searchHits.size() > 0)
							{
								if (searchHits.get(0).getColumnValueMap().get(ElasticSearchImp.DEFAULT_CODE_FIELD)
										.toString()
										.equalsIgnoreCase(data.get(ElasticSearchImp.DEFAULT_CODE_FIELD).toString()))
								{
									break;
								}
							}
							try
							{
								Thread.sleep(3000);
							}
							catch (Exception e)
							{
								throw new RuntimeException(e.getMessage());
							}
						}
					}

					for (String activityName : new HashSet<String>(codingState.getRawActivities().keySet()))
					{
						List<Hit> searchHits = elasticSearchImp.search(documentType, activityName, null);
						nGramService.calculateNGramSimilarity(activityName, "name", searchHits);
						for (Hit hit : searchHits)
						{
							if (hit.getScore().intValue() >= codingState.getThreshold())
							{
								// Add/Remove the items to the matched
								// /unmatched
								// cateogires for which the
								// matching scores have improved due to the
								// manual
								// curation
								if (!codingState.getMappedActivities().containsKey(activityName))
								{
									codingState.getMappedActivities().put(activityName,
											codingState.getRawActivities().get(activityName));
									codingState.getMappedActivities().get(activityName)
											.setHit(new Hit(hit.getDocumentId(), null, data));
									codingState.getMappedActivities().get(activityName).getHit().setScore(score);
									codingState.getMappedActivities().get(activityName).getIdentifiers()
											.putAll(codingState.getRawActivities().get(activityName).getIdentifiers());
									codingState.getRawActivities().remove(activityName);
									break;
								}
							}
						}
					}
				}
				else
				{
					// Only code the this query
					if (!codingState.getMappedActivities().containsKey(queryString)
							&& codingState.getRawActivities().containsKey(queryString))
					{
						codingState.getMappedActivities().put(queryString,
								codingState.getRawActivities().get(queryString));
						codingState.getMappedActivities().get(queryString).setHit(new Hit(documentId, null, data));
						codingState.getMappedActivities().get(queryString).getHit().setScore(score);
						codingState.getMappedActivities().get(queryString).getIdentifiers()
								.putAll(codingState.getRawActivities().get(queryString).getIdentifiers());
						codingState.getRawActivities().remove(queryString);
					}
				}
				codingState.getMappedActivities().get(queryString).setFinalSelection(true);
				codingState.getMappedActivities().get(queryString).setAddedDate(new Date());
			}
		}
	}

	@RequestMapping(value = "/unknown", method = RequestMethod.POST, consumes = APPLICATION_JSON_VALUE)
	@ResponseStatus(OK)
	public void unkonwnCode(@RequestBody Map<String, Object> request)
	{
		if (request.containsKey("codesystem") && request.get("codesystem") != null && request.containsKey("query")
				&& request.get("query") != null)
		{
			String documentType = request.get(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD).toString();
			String queryString = request.get("query").toString();
			List<Hit> hits = elasticSearchImp.search(documentType, UNKNOWN_CODE, ElasticSearchImp.DEFAULT_CODE_FIELD);
			if (hits.size() == 0)
			{
				Map<String, Object> doc = new HashMap<String, Object>();
				doc.put(ElasticSearchImp.DEFAULT_CODE_FIELD, UNKNOWN_CODE);
				doc.put(ElasticSearchImp.DEFAULT_NAME_FIELD, UNKNOWN_CODE_NAME);
				doc.put(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD, documentType);
				elasticSearchImp.indexDocument(ElasticSearchImp.addDefaultFields(doc), documentType);
				hits = elasticSearchImp.search(documentType, UNKNOWN_CODE, ElasticSearchImp.DEFAULT_CODE_FIELD);
			}

			// Only code this query
			if (!codingState.getMappedActivities().containsKey(queryString)
					&& codingState.getRawActivities().containsKey(queryString))
			{
				codingState.getMappedActivities().put(queryString, codingState.getRawActivities().get(queryString));
				codingState.getMappedActivities().get(queryString)
						.setHit(new Hit(hits.get(0).getDocumentId(), null, hits.get(0).getColumnValueMap()));
				codingState.getMappedActivities().get(queryString).getHit().setScore((float) 0);
				codingState.getMappedActivities().get(queryString).getIdentifiers()
						.putAll(codingState.getRawActivities().get(queryString).getIdentifiers());
				codingState.getMappedActivities().get(queryString).setFinalSelection(true);
				codingState.getMappedActivities().get(queryString).setAddedDate(new Date());
				codingState.getRawActivities().remove(queryString);
			}
		}
	}

	@RequestMapping(value = "/remove", method = RequestMethod.POST, consumes = APPLICATION_JSON_VALUE)
	@ResponseStatus(OK)
	public void removeCodedResult(@RequestBody Map<String, Object> request)
	{
		if (request.containsKey("query") && request.get("query") != null)
		{
			String queryString = request.get("query").toString();
			// Only code the this query
			if (codingState.getMappedActivities().containsKey(queryString))
			{
				codingState.getRawActivities().put(queryString, codingState.getMappedActivities().get(queryString));
				codingState.getRawActivities().get(queryString).setFinalSelection(false);
				List<Hit> searchHits = elasticSearchImp.search(codingState.getSelectedCodeSystem(), queryString, null);
				nGramService.calculateNGramSimilarity(queryString, "name", searchHits);
				if (searchHits.size() > 0) codingState.getMappedActivities().get(queryString).setHit(searchHits.get(0));
				else codingState.getMappedActivities().get(queryString).setHit(null);

				codingState.getMappedActivities().remove(queryString);
			}
		}
	}

	@RequestMapping(value = "/threshold", method = RequestMethod.POST)
	public String threshold(@RequestParam("threshold") String threshold, Model model)
	{
		Integer previousThreshold = codingState.getThreshold();
		try
		{
			codingState.setThreshold(Integer.parseInt(threshold));
		}
		catch (Exception e)
		{
			codingState.setThreshold(previousThreshold);
		}

		// New threshold is smaller than the previous one, some of the unmatched
		// items should be moved to the matched category
		if (codingState.getThreshold() < previousThreshold)
		{
			Set<String> keys = new HashSet<String>(codingState.getRawActivities().keySet());
			for (String activityName : keys)
			{
				RecodeResponse recodeResponse = codingState.getRawActivities().get(activityName);
				if (recodeResponse.getHit().getScore() >= codingState.getThreshold())
				{
					if (!codingState.getMappedActivities().containsKey(activityName))
					{
						codingState.getMappedActivities().put(activityName, recodeResponse);
						codingState.getRawActivities().remove(activityName);
					}
				}
			}
		}
		else if (codingState.getThreshold() >= previousThreshold)
		{
			Set<String> keys = new HashSet<String>(codingState.getMappedActivities().keySet());
			for (String activityName : keys)
			{
				RecodeResponse recodeResponse = codingState.getMappedActivities().get(activityName);
				if (recodeResponse.getHit().getScore() < codingState.getThreshold())
				{
					if (!codingState.getRawActivities().containsKey(activityName)
							&& !codingState.getMappedActivities().get(activityName).isFinalSelection())
					{
						codingState.getRawActivities().put(activityName, recodeResponse);
						codingState.getMappedActivities().remove(activityName);
					}
				}
			}
		}

		for (String activityName : new HashSet<String>(codingState.getRawActivities().keySet()))
		{
			List<Hit> searchHits = elasticSearchImp.search(codingState.getSelectedCodeSystem(), activityName, null);
			nGramService.calculateNGramSimilarity(activityName, "name", searchHits);
			for (Hit hit : searchHits)
			{
				if (hit.getScore().intValue() >= codingState.getThreshold())
				{
					// Add/Remove the items to the matched
					// /unmatched
					// cateogires for which the
					// matching scores have improved due to the
					// manual
					// curation
					if (!codingState.getMappedActivities().containsKey(activityName))
					{
						codingState.getMappedActivities().put(activityName,
								codingState.getRawActivities().get(activityName));
						codingState.getMappedActivities().get(activityName).setHit(hit);
						codingState.getMappedActivities().get(activityName).getIdentifiers()
								.putAll(codingState.getRawActivities().get(activityName).getIdentifiers());
						codingState.getRawActivities().remove(activityName);
						break;
					}
				}
			}
		}

		return "redirect:/recode";
	}

	@RequestMapping(value = "/upload/missing", method = RequestMethod.POST, headers = "Content-Type=multipart/form-data")
	public String uploadMissingCaseFilehandler(@RequestParam("file") MultipartFile multipartFile, Model model)
			throws InvalidFormatException, IOException
	{
		if (!multipartFile.isEmpty() && !StringUtils.isEmpty(codingState.getSelectedCodeSystem())
				&& !StringUtils.isEmpty(codingState.getCodingJobName()))
		{
			processVariableUtil.processUploadedVariableData(multipartFile.getInputStream(),
					codingState.getSelectedCodeSystem(), codingState.getCodingJobName());
		}

		return "redirect:/recode";
	}

	@RequestMapping(value = "/upload", method = RequestMethod.POST, headers = "Content-Type=multipart/form-data")
	public String uploadFileHandler(@RequestParam("file") MultipartFile multipartFile,
			@RequestParam(value = "selectedCodeSystem", required = false) String codeSystem,
			@RequestParam(value = "codingJobName", required = false) String codingJobName, Model model)
			throws InvalidFormatException, IOException
	{
		if (!multipartFile.isEmpty() && !StringUtils.isEmpty(codeSystem) && !StringUtils.isEmpty(codingJobName))
		{
			if (backupCodesInState.checkBackupByName(codingJobName))
			{
				codingState.setErrorMessage("The job name has existed in backup, please use another name!");
			}
			else
			{
				processVariableUtil.processUploadedVariableData(multipartFile.getInputStream(), codeSystem,
						codingJobName);
			}
		}
		else
		{
			codingState.setErrorMessage("Please fill out all the required fields in the form!");
		}
		return "redirect:/recode";
	}

	@RequestMapping(value = "/download", method = RequestMethod.GET)
	public void download(HttpServletResponse response) throws IOException
	{
		response.setContentType("application/zip");
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		response.addHeader("Content-Disposition",
				"attachment; filename=recoding-download-" + dateFormat.format(new Date()) + ".zip");

		String property = System.getProperty("java.io.tmpdir");
		List<String> filePaths = new ArrayList<String>();
		ZipOutputStream zipOutputStream = null;

		try
		{
			List<String> columnHeaders = new ArrayList<String>();
			columnHeaders.addAll(ALLOWED_COLUMNS);
			columnHeaders.addAll(Arrays.asList("code", "codename", "codesystem", "similarity"));
			File fileForSummaryTable = new File(property + "recoding-download-" + dateFormat.format(new Date())
					+ ".0.csv");
			CsvWriter summaryCsvWritier = new CsvWriter(fileForSummaryTable);
			filePaths.add(fileForSummaryTable.getAbsolutePath());

			summaryCsvWritier.writeAttributeNames(codingState.getMaxNumColumns());

			Map<Integer, CsvWriter> sheetMap = new HashMap<Integer, CsvWriter>();

			for (int index = 1; index < codingState.getMaxNumColumns().size(); index++)
			{
				File file = new File(property + "recoding-download-" + dateFormat.format(new Date()) + "." + index
						+ ".csv");
				filePaths.add(file.getAbsolutePath());
				sheetMap.put(index, new CsvWriter(file));
				sheetMap.get(index).writeAttributeNames(columnHeaders);
			}
			Map<String, MapEntity> entityMap = new HashMap<String, MapEntity>();

			for (Entry<String, RecodeResponse> entry : codingState.getMappedActivities().entrySet())
			{
				String activityName = entry.getKey();
				RecodeResponse recodeResponse = entry.getValue();
				Map<String, Object> columnValueMap = recodeResponse.getHit().getColumnValueMap();

				for (Entry<String, Set<Integer>> identifiers : recodeResponse.getIdentifiers().entrySet())
				{
					String identifier = identifiers.getKey();

					if (!entityMap.containsKey(identifier))
					{
						entityMap.put(identifier, new MapEntity());
						entityMap.get(identifier).set("identifier", identifier);
					}

					for (Integer columnIndex : identifiers.getValue())
					{
						MapEntity entity = new MapEntity();
						entity.set("name", activityName);
						entity.set("identifier", identifier);
						entity.set("code", columnValueMap.get(ElasticSearchImp.DEFAULT_CODE_FIELD));
						entity.set("codename", columnValueMap.get(ElasticSearchImp.DEFAULT_NAME_FIELD));
						entity.set("codesystem", columnValueMap.get(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD));
						entity.set("similarity", recodeResponse.getHit().getScore());
						sheetMap.get(columnIndex).add(entity);

						entityMap.get(identifier).set(codingState.getMaxNumColumns().get(columnIndex),
								columnValueMap.get(ElasticSearchImp.DEFAULT_CODE_FIELD));
					}
				}
			}

			summaryCsvWritier.add(entityMap.values());
			for (String identifier : codingState.getInvalidIndividuals())
			{
				MapEntity entity = new MapEntity();
				entity.set("identifier", identifier);
				summaryCsvWritier.add(entity);
			}
			summaryCsvWritier.close();

			for (CsvWriter sheet : sheetMap.values())
				sheet.close();

			zipOutputStream = new ZipOutputStream(response.getOutputStream());
			zipFile(zipOutputStream, filePaths);

		}
		finally
		{
			IOUtils.closeQuietly(zipOutputStream);
		}
	}

	// ######################## static helper method
	// ############################
	public static Map<String, Object> convertObjectToMap(Object data)
	{
		Map<String, Object> map = new HashMap<String, Object>();

		if (data instanceof Map<?, ?>)
		{
			for (Entry<?, ?> entry : ((Map<?, ?>) data).entrySet())
			{
				map.put(entry.getKey().toString(), entry.getValue());
			}
		}
		return map;
	}

	public static Map<String, Object> translateDataToMap(Map<String, Object> data, String query)
	{
		Map<String, Object> doc = new HashMap<String, Object>();
		doc.put(ElasticSearchImp.DEFAULT_NAME_FIELD, query);
		doc.put(ElasticSearchImp.DEFAULT_CODE_FIELD, data.get(ElasticSearchImp.DEFAULT_CODE_FIELD));
		doc.put(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD, data.get(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD));
		return doc;
	}

	public static void zipFile(ZipOutputStream zipOutputStream, List<String> filePaths) throws IOException
	{

		byte[] buffer = new byte[128];
		for (String filePath : filePaths)
		{
			File file = new File(filePath);
			if (file.exists())
			{
				ZipEntry zipEntry = new ZipEntry(file.getName());
				FileInputStream fis = new FileInputStream(file);
				zipOutputStream.putNextEntry(zipEntry);

				int read = 0;
				while ((read = fis.read(buffer)) != -1)
				{
					zipOutputStream.write(buffer, 0, read);
				}

				zipOutputStream.closeEntry();
				fis.close();
			}
		}
	}
}