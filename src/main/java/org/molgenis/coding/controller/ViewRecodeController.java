package org.molgenis.coding.controller;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.molgenis.coding.backup.BackupCodesInState;
import org.molgenis.coding.elasticsearch.CodingState;
import org.molgenis.coding.elasticsearch.ElasticSearchImp;
import org.molgenis.coding.elasticsearch.Hit;
import org.molgenis.coding.elasticsearch.SearchService;
import org.molgenis.coding.ngram.NGramService;
import org.molgenis.coding.util.RecodeResponse;
import org.molgenis.data.AttributeMetaData;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityMetaData;
import org.molgenis.data.csv.CsvRepository;
import org.molgenis.data.csv.CsvWriter;
import org.molgenis.data.processor.CellProcessor;
import org.molgenis.data.processor.LowerCaseProcessor;
import org.molgenis.data.processor.TrimProcessor;
import org.molgenis.data.support.MapEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
	private boolean isRecoding = false;

	private final static String VIEW_NAME = "view-recode-report";
	private final static String UNKNOWN_CODE = "99999";
	private final static String UNKNOWN_CODE_NAME = "Unknown";

	private final SearchService elasticSearchImp;
	private final NGramService nGramService;
	private final CodingState codingState;
	private final BackupCodesInState backupCodesInState;

	private final static List<String> ALLOWED_COLUMNS = Arrays.asList("identifier", "name");
	private final static Logger logger = Logger.getLogger(ViewRecodeController.class);

	@Autowired
	public ViewRecodeController(SearchService elasticSearchImp, NGramService nGramService, CodingState codingState,
			BackupCodesInState backupCodesInState)
	{
		if (elasticSearchImp == null) throw new IllegalArgumentException("ElasticSearch is null");
		if (nGramService == null) throw new IllegalArgumentException("NGramService is null");
		if (codingState == null) throw new IllegalArgumentException("CodingState is null");
		if (backupCodesInState == null) throw new IllegalArgumentException("BackupCodesInState is null");
		this.elasticSearchImp = elasticSearchImp;
		this.nGramService = nGramService;
		this.codingState = codingState;
		this.backupCodesInState = backupCodesInState;

	}

	@RequestMapping(method = RequestMethod.GET)
	public String defaultView(Model model)
	{
		model.addAttribute("selectedCodeSystem", codingState.getSelectedCodeSystem());
		model.addAttribute("threshold", codingState.getThreshold());
		model.addAttribute("hidForm", isRecoding);
		model.addAttribute("viewId", VIEW_NAME);
		return VIEW_NAME;
	}

	@RequestMapping(value = "/finish", method = RequestMethod.GET)
	public String finishedRecoding(Model model)
	{
		if (codingState.getRawActivities().size() == 0)
		{
			isRecoding = false;
			codingState.clearState();
		}
		return "redirect:/recode";
	}

	@RequestMapping(value = "/recovery", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> recovery()
	{
		isRecoding = true;
		return backupCodesInState.recovery();
	}

	@RequestMapping(value = "/check", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> checkBackUp(Model model)
	{
		Map<String, Object> results = new HashMap<String, Object>();
		results.put("backup", backupCodesInState.backupExisits());
		return results;
	}

	@RequestMapping(value = "/retrieve", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> retrieveReport(@RequestParam(required = false, value = "displayNumberMatched")
	Integer maxNumberMatched, @RequestParam(required = false, value = "displayNumberMatched")
	Integer maxNumberUnMatched)
	{
		Map<String, Object> results = new HashMap<String, Object>();
		List<RecodeResponse> listOfMapped = new ArrayList<RecodeResponse>(codingState.getMappedActivities().values());
		List<RecodeResponse> listOfUnMapped = new ArrayList<RecodeResponse>(codingState.getRawActivities().values());

		Collections.sort(listOfMapped);
		Collections.sort(listOfUnMapped);

		int displaySizeMatched = 100;
		if (maxNumberMatched != null)
		{
			displaySizeMatched = maxNumberMatched > displaySizeMatched ? maxNumberMatched : displaySizeMatched;
		}

		results.put(
				"matched",
				listOfMapped.subList(0,
						displaySizeMatched <= listOfMapped.size() ? displaySizeMatched : listOfMapped.size()));

		int displaySizeUmatched = 10;
		if (maxNumberUnMatched != null)
		{
			displaySizeUmatched = maxNumberUnMatched > displaySizeUmatched ? maxNumberUnMatched : displaySizeUmatched;
		}
		results.put("unmatched", listOfUnMapped.subList(0,
				displaySizeUmatched <= listOfUnMapped.size() ? displaySizeUmatched : listOfUnMapped.size()));

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
	public void addDoc(@RequestBody
	Map<String, Object> request)
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
	public void unkonwnCode(@RequestBody
	Map<String, Object> request)
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
	public void removeCodedResult(@RequestBody
	Map<String, Object> request)
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
	public String threshold(@RequestParam("threshold")
	String threshold, Model model)
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
		else if (codingState.getThreshold() > previousThreshold)
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

		return "redirect:/recode";
	}

	@RequestMapping(value = "/upload", method = RequestMethod.POST, headers = "Content-Type=multipart/form-data")
	public String uploadFileHandler(@RequestParam("file")
	MultipartFile file, @RequestParam(value = "selectedCodeSystem", required = false)
	String codeSystem, Model model) throws InvalidFormatException, IOException
	{
		if (!file.isEmpty() && !StringUtils.isEmpty(codeSystem))
		{
			processUploadedVariableData(file, codeSystem);
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

	@Async
	private void processUploadedVariableData(MultipartFile file, String codeSystem) throws IOException
	{
		CsvRepository csvRepository = null;
		try
		{
			codingState.setSelectedCodeSystem(codeSystem);

			File serverFile = createFileOnServer(file);

			if (serverFile.exists())
			{
				csvRepository = new CsvRepository(new File(serverFile.getAbsolutePath()),
						Arrays.<CellProcessor> asList(new LowerCaseProcessor(), new TrimProcessor()), ';');

				if (validateExcelColumnHeaders(csvRepository.getEntityMetaData()))
				{
					// Map to store the activity name with corresponding
					// individuals
					Iterator<Entity> iterator = csvRepository.iterator();

					codingState.addColumns(csvRepository.getEntityMetaData().getAttributes());

					Date indexedDate = new Date();

					while (iterator.hasNext())
					{
						Entity entity = iterator.next();
						String individualIdentifier = entity.getString("Identifier");
						codingState.addInvalidIndividuals(individualIdentifier);

						for (int columnIndex = 0; columnIndex < codingState.getMaxNumColumns().size(); columnIndex++)
						{
							String columnName = codingState.getMaxNumColumns().get(columnIndex);

							if (columnName.equalsIgnoreCase("identifier")) continue;

							if (columnName.toLowerCase().startsWith("name"))
							{
								String activityName = entity.getString(columnName);

								if (!StringUtils.isEmpty(individualIdentifier) && !StringUtils.isEmpty(activityName))
								{
									codingState.removeInvalidIndividuals(individualIdentifier);

									List<Hit> searchHits = elasticSearchImp.search(codeSystem, activityName, null);
									nGramService.calculateNGramSimilarity(activityName, "name", searchHits);
									for (Hit hit : searchHits)
									{
										if (hit.getScore().intValue() >= codingState.getThreshold())
										{
											if (!codingState.getMappedActivities().containsKey(activityName))
											{
												codingState.getMappedActivities().put(activityName,
														new RecodeResponse(activityName, hit));
											}
											if (!codingState.getMappedActivities().get(activityName).getIdentifiers()
													.containsKey(individualIdentifier))
											{
												codingState.getMappedActivities().get(activityName).getIdentifiers()
														.put(individualIdentifier, new HashSet<Integer>());
											}
											codingState.getMappedActivities().get(activityName).getIdentifiers()
													.get(individualIdentifier).add(columnIndex);
											codingState.getMappedActivities().get(activityName)
													.setAddedDate(indexedDate);
										}
										else
										{
											if (!codingState.getRawActivities().containsKey(activityName))
											{
												codingState.getRawActivities().put(activityName,
														new RecodeResponse(activityName, hit));
											}
											if (!codingState.getRawActivities().get(activityName).getIdentifiers()
													.containsKey(individualIdentifier))
											{
												codingState.getRawActivities().get(activityName).getIdentifiers()
														.put(individualIdentifier, new HashSet<Integer>());
											}
											codingState.getRawActivities().get(activityName).getIdentifiers()
													.get(individualIdentifier).add(columnIndex);
											codingState.getRawActivities().get(activityName).setAddedDate(indexedDate);
										}
										break;
									}
								}
							}
						}
					}
					isRecoding = true;
				}
			}
		}
		catch (Throwable e)
		{
			codingState.clearState();
		}
		finally
		{
			if (csvRepository != null) csvRepository.close();
		}
	}

	private boolean validateExcelColumnHeaders(EntityMetaData entityMetaData)
	{
		for (AttributeMetaData attribute : entityMetaData.getAttributes())
		{
			if (!attribute.getName().toLowerCase().equals("identifier")
					&& !attribute.getName().toLowerCase().startsWith("name"))
			{
				return false;
			}
		}
		return true;
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

	public static File createFileOnServer(MultipartFile file) throws IOException
	{
		String rootPath = System.getProperty("java.io.tmpdir");
		File dir = new File(rootPath + File.separator + "tmpFiles");
		if (!dir.exists()) dir.mkdirs();
		// Create the file on server
		File serverFile = new File(dir.getAbsolutePath() + File.separator + file.getName() + ".csv");

		BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(serverFile));
		stream.write(file.getBytes());
		stream.close();
		logger.info("The uploaded file path is : " + serverFile.getAbsolutePath());
		return serverFile;

	}

	public static int getLineNumber(File file) throws IOException
	{
		int lineNumber = 0;
		if (file.exists())
		{
			LineNumberReader lnr = new LineNumberReader(new FileReader(file));
			try
			{
				lnr.skip(Long.MAX_VALUE);
				lineNumber = lnr.getLineNumber();
			}
			finally
			{
				lnr.close();
			}
		}
		return lineNumber;
	}
}