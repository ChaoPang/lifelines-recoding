package org.molgenis.coding.controller;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
	private Integer THRESHOLD = 80;
	private String selectedCodeSystem = null;
	private final static String VIEW_NAME = "view-recode-report";
	private final static String UNKNOWN_CODE = "99999";
	private final static String UNKNOWN_CODE_NAME = "Unknown";
	private final SearchService elasticSearchImp;
	private final NGramService nGramService;
	private final static List<String> ALLOWED_COLUMNS = Arrays.asList("identifier", "name");
	private final static Logger logger = Logger.getLogger(ViewRecodeController.class);
	private final Map<String, RecodeResponse> mappedActivities = new HashMap<String, RecodeResponse>();
	private final Map<String, RecodeResponse> rawActivities = new HashMap<String, RecodeResponse>();
	private final List<String> maxNumColumns = new ArrayList<String>();
	private final Set<String> invalidIndividuals = new HashSet<String>();

	@Autowired
	public ViewRecodeController(SearchService elasticSearchImp, NGramService nGramService)
	{
		if (elasticSearchImp == null) throw new IllegalArgumentException("ElasticSearch is null");
		if (nGramService == null) throw new IllegalArgumentException("NGramService is null");
		this.elasticSearchImp = elasticSearchImp;
		this.nGramService = nGramService;
	}

	@RequestMapping(method = RequestMethod.GET)
	public String defaultView(Model model)
	{
		model.addAttribute("selectedCodeSystem", selectedCodeSystem);
		model.addAttribute("threshold", THRESHOLD);
		model.addAttribute("hidForm", isRecoding);
		model.addAttribute("viewId", VIEW_NAME);
		return VIEW_NAME;
	}

	@RequestMapping(value = "/finish", method = RequestMethod.GET)
	public String finishedRecoding(Model model)
	{
		if (rawActivities.size() == 0)
		{
			isRecoding = false;
			mappedActivities.clear();
			rawActivities.clear();
			maxNumColumns.clear();
			invalidIndividuals.clear();
		}
		return "redirect:/recode";
	}

	@RequestMapping(value = "/retrieve", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> retrieveReport()
	{
		Map<String, Object> results = new HashMap<String, Object>();
		results.put("matched", mappedActivities.values());
		results.put("unmatched", rawActivities.values());
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
			Map<String, Object> data = convertData(request.get("data"));
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

					for (String activityName : new HashSet<String>(rawActivities.keySet()))
					{
						List<Hit> searchHits = elasticSearchImp.search(documentType, activityName, null);
						nGramService.calculateNGramSimilarity(activityName, "name", searchHits);
						for (Hit hit : searchHits)
						{
							if (hit.getScore().intValue() >= THRESHOLD)
							{
								// Add/Remove the items to the matched
								// /unmatched
								// cateogires for which the
								// matching scores have improved due to the
								// manual
								// curation
								if (!mappedActivities.containsKey(activityName))
								{
									mappedActivities.put(activityName, rawActivities.get(activityName));
									mappedActivities.get(activityName).setHit(new Hit(hit.getDocumentId(), null, data));
									mappedActivities.get(activityName).getHit().setScore(score);
									mappedActivities.get(activityName).getIdentifiers()
											.putAll(rawActivities.get(activityName).getIdentifiers());
									rawActivities.remove(activityName);
									break;
								}
							}
						}
					}
				}
				else
				{
					// Only code the this query
					if (!mappedActivities.containsKey(queryString) && rawActivities.containsKey(queryString))
					{
						mappedActivities.put(queryString, rawActivities.get(queryString));
						mappedActivities.get(queryString).setHit(new Hit(documentId, null, data));
						mappedActivities.get(queryString).getHit().setScore(score);
						mappedActivities.get(queryString).getIdentifiers()
								.putAll(rawActivities.get(queryString).getIdentifiers());
						rawActivities.remove(queryString);
					}
				}
				mappedActivities.get(queryString).setFinalSelection(true);
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
			if (!mappedActivities.containsKey(queryString) && rawActivities.containsKey(queryString))
			{
				mappedActivities.put(queryString, rawActivities.get(queryString));
				mappedActivities.get(queryString).setHit(
						new Hit(hits.get(0).getDocumentId(), null, hits.get(0).getColumnValueMap()));
				mappedActivities.get(queryString).getHit().setScore((float) 0);
				mappedActivities.get(queryString).getIdentifiers()
						.putAll(rawActivities.get(queryString).getIdentifiers());
				mappedActivities.get(queryString).setFinalSelection(true);
				rawActivities.remove(queryString);
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
			if (mappedActivities.containsKey(queryString))
			{
				rawActivities.put(queryString, mappedActivities.get(queryString));
				List<Hit> searchHits = elasticSearchImp.search(selectedCodeSystem, queryString, null);
				nGramService.calculateNGramSimilarity(queryString, "name", searchHits);
				if (searchHits.size() > 0) mappedActivities.get(queryString).setHit(searchHits.get(0));
				else mappedActivities.get(queryString).setHit(null);

				mappedActivities.remove(queryString);
			}
		}
	}

	public static Map<String, Object> translateDataToMap(Map<String, Object> data, String query)
	{
		Map<String, Object> doc = new HashMap<String, Object>();
		doc.put(ElasticSearchImp.DEFAULT_NAME_FIELD, query);
		doc.put(ElasticSearchImp.DEFAULT_CODE_FIELD, data.get(ElasticSearchImp.DEFAULT_CODE_FIELD));
		doc.put(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD, data.get(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD));
		return doc;
	}

	@RequestMapping(value = "/threshold", method = RequestMethod.POST)
	public String threshold(@RequestParam("threshold")
	String threshold, Model model)
	{
		Integer previousThreshold = THRESHOLD;
		try
		{
			THRESHOLD = Integer.parseInt(threshold);
		}
		catch (Exception e)
		{
			THRESHOLD = previousThreshold;
		}

		// New threshold is smaller than the previous one, some of the unmatched
		// items should be moved to the matched category
		if (THRESHOLD < previousThreshold)
		{
			Set<String> keys = new HashSet<String>(rawActivities.keySet());
			for (String activityName : keys)
			{
				RecodeResponse recodeResponse = rawActivities.get(activityName);
				if (recodeResponse.getHit().getScore() >= THRESHOLD)
				{
					if (!mappedActivities.containsKey(activityName))
					{
						mappedActivities.put(activityName, recodeResponse);
						rawActivities.remove(activityName);
					}
				}
			}
		}
		else if (THRESHOLD > previousThreshold)
		{
			Set<String> keys = new HashSet<String>(mappedActivities.keySet());
			for (String activityName : keys)
			{
				RecodeResponse recodeResponse = mappedActivities.get(activityName);
				if (recodeResponse.getHit().getScore() < THRESHOLD)
				{
					if (!rawActivities.containsKey(activityName)
							&& !mappedActivities.get(activityName).isFinalSelection())
					{
						rawActivities.put(activityName, recodeResponse);
						mappedActivities.remove(activityName);
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
			CsvRepository csvRepository = null;
			try
			{
				selectedCodeSystem = codeSystem;

				File serverFile = createFileOnServer(file);

				// ExcelRepositoryCollection collection = new
				// ExcelRepositoryCollection(new File(
				// serverFile.getAbsolutePath()), new LowerCaseProcessor(), new
				// TrimProcessor());
				// ExcelRepository sheet = collection.getSheet(0);
				if (serverFile.exists())
				{
					List<CellProcessor> cellProcessors = new ArrayList<CellProcessor>();
					cellProcessors.add(new LowerCaseProcessor());
					cellProcessors.add(new TrimProcessor());
					csvRepository = new CsvRepository(new File(serverFile.getAbsolutePath()), cellProcessors, ';');

					if (validateExcelColumnHeaders(csvRepository.getEntityMetaData()))
					{
						// Map to store the activity name with corresponding
						// individuals
						Iterator<Entity> iterator = csvRepository.iterator();

						for (AttributeMetaData attributeMetaData : csvRepository.getEntityMetaData()
								.getAtomicAttributes())
						{
							maxNumColumns.add(attributeMetaData.getName());
						}

						while (iterator.hasNext())
						{
							Entity entity = iterator.next();
							String individualIdentifier = entity.getString("Identifier");
							invalidIndividuals.add(individualIdentifier);
							for (String columnName : maxNumColumns)
							{
								if (columnName.equalsIgnoreCase("identifier")) continue;

								if (columnName.toLowerCase().startsWith("name"))
								{
									String activityName = entity.getString(columnName);
									Integer columnIndex = maxNumColumns.indexOf(columnName);

									if (!StringUtils.isEmpty(individualIdentifier)
											&& !StringUtils.isEmpty(activityName))
									{
										if (invalidIndividuals.contains(individualIdentifier)) invalidIndividuals
												.remove(individualIdentifier);

										List<Hit> searchHits = elasticSearchImp.search(codeSystem, activityName, null);
										nGramService.calculateNGramSimilarity(activityName, "name", searchHits);
										for (Hit hit : searchHits)
										{
											if (hit.getScore().intValue() >= THRESHOLD)
											{
												if (!mappedActivities.containsKey(activityName))
												{
													mappedActivities.put(activityName, new RecodeResponse(activityName,
															hit));
												}
												if (!mappedActivities.get(activityName).getIdentifiers()
														.containsKey(individualIdentifier))
												{
													mappedActivities.get(activityName).getIdentifiers()
															.put(individualIdentifier, new HashSet<Integer>());
												}
												mappedActivities.get(activityName).getIdentifiers()
														.get(individualIdentifier).add(columnIndex);
											}
											else
											{
												if (!rawActivities.containsKey(activityName))
												{
													rawActivities.put(activityName, new RecodeResponse(activityName,
															hit));
												}
												if (!rawActivities.get(activityName).getIdentifiers()
														.containsKey(individualIdentifier))
												{
													rawActivities.get(activityName).getIdentifiers()
															.put(individualIdentifier, new HashSet<Integer>());
												}
												rawActivities.get(activityName).getIdentifiers()
														.get(individualIdentifier).add(columnIndex);
											}
											break;
										}
									}
								}
							}
						}

						isRecoding = true;
					}
					else
					{
						model.addAttribute("message", "The columns are invalid. Please look at the example!");
					}
				}
			}
			catch (Throwable e)
			{
				if (csvRepository != null) csvRepository.close();
				model.addAttribute("message", e.getMessage());
				rawActivities.clear();
				mappedActivities.clear();
				maxNumColumns.clear();
				invalidIndividuals.clear();
			}
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
			columnHeaders.add("code");
			columnHeaders.add("codename");
			columnHeaders.add("codesystem");
			columnHeaders.add("similarity");

			File fileForSummaryTable = new File(property + "recoding-download-" + dateFormat.format(new Date())
					+ ".0.csv");
			CsvWriter summaryCsvWritier = new CsvWriter(fileForSummaryTable);
			filePaths.add(fileForSummaryTable.getAbsolutePath());

			summaryCsvWritier.writeAttributeNames(maxNumColumns);

			Map<Integer, CsvWriter> sheetMap = new HashMap<Integer, CsvWriter>();

			for (int index = 1; index < maxNumColumns.size(); index++)
			{
				File file = new File(property + "recoding-download-" + dateFormat.format(new Date()) + "." + index
						+ ".csv");
				filePaths.add(file.getAbsolutePath());
				sheetMap.put(index, new CsvWriter(file));
				sheetMap.get(index).writeAttributeNames(columnHeaders);
			}
			Map<String, MapEntity> entityMap = new HashMap<String, MapEntity>();
			for (Entry<String, RecodeResponse> entry : mappedActivities.entrySet())
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

						entityMap.get(identifier).set(maxNumColumns.get(columnIndex),
								columnValueMap.get(ElasticSearchImp.DEFAULT_CODE_FIELD));
					}
				}
			}

			summaryCsvWritier.add(entityMap.values());
			for (String identifier : invalidIndividuals)
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

	private void zipFile(ZipOutputStream zipOutputStream, List<String> filePaths) throws IOException
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

	private File createFileOnServer(MultipartFile file) throws IOException
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

	public static Map<String, Object> convertData(Object data)
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
}