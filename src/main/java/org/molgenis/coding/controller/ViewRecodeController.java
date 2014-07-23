package org.molgenis.coding.controller;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.molgenis.coding.elasticsearch.Hit;
import org.molgenis.coding.elasticsearch.SearchService;
import org.molgenis.coding.ngram.NGramService;
import org.molgenis.coding.util.RecodeResponse;
import org.molgenis.data.AttributeMetaData;
import org.molgenis.data.Entity;
import org.molgenis.data.excel.ExcelRepository;
import org.molgenis.data.excel.ExcelRepositoryCollection;
import org.molgenis.data.processor.LowerCaseProcessor;
import org.molgenis.data.processor.TrimProcessor;
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
	private final static String VIEW_NAME = "view-recode-report";
	private final SearchService elasticSearchImp;
	private final NGramService nGramService;
	private final static Integer THRESHOLD = 80;
	private final static List<String> ALLOWED_COLUMNS = Arrays.asList("identifier", "name");
	private final static Logger logger = Logger.getLogger(ViewRecodeController.class);
	private final Map<String, RecodeResponse> mappedActivities = new HashMap<String, RecodeResponse>();
	private final Map<String, RecodeResponse> rawActivities = new HashMap<String, RecodeResponse>();

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
		model.addAttribute("hidForm", isRecoding);
		model.addAttribute("viewId", VIEW_NAME);
		return VIEW_NAME;
	}

	@RequestMapping(value = "/finish", method = RequestMethod.GET)
	public String finishedRecoding(Model model)
	{
		isRecoding = false;
		mappedActivities.clear();
		rawActivities.clear();
		return defaultView(model);
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
	Map<String, Object> data)
	{
		if (data.get("name") != null && data.get("code") != null)
		{
			elasticSearchImp.indexDocument(data);
			String activityName = data.get("name").toString();
			rawActivities.get(activityName).getHit().setScore((float) 100);
			if (!mappedActivities.containsKey(activityName))
			{
				mappedActivities.put(activityName, rawActivities.get(activityName));
				rawActivities.remove(activityName);
			}
			else
			{
				mappedActivities.get(activityName).getIdentifiers()
						.addAll(rawActivities.get(activityName).getIdentifiers());
			}
		}
	}

	@RequestMapping(value = "/upload", method = RequestMethod.POST, headers = "Content-Type=multipart/form-data")
	public String uploadFileHandler(@RequestParam("file")
	MultipartFile file, Model model) throws InvalidFormatException
	{
		if (!file.isEmpty())
		{
			byte[] bytes;
			try
			{
				bytes = file.getBytes();
				String rootPath = System.getProperty("java.io.tmpdir");
				File dir = new File(rootPath + File.separator + "tmpFiles");
				if (!dir.exists()) dir.mkdirs();
				// Create the file on server
				File serverFile = new File(dir.getAbsolutePath() + File.separator + file.getName() + ".xls");
				BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(serverFile));
				stream.write(bytes);
				stream.close();
				logger.info("The uploaded file path is : " + serverFile.getAbsolutePath());
				ExcelRepositoryCollection collection = new ExcelRepositoryCollection(new File(
						serverFile.getAbsolutePath()), new LowerCaseProcessor(), new TrimProcessor());
				ExcelRepository sheet = collection.getSheet(0);
				if (collection.getNumberOfSheets() > 0)
				{
					if (validateExcelColumnHeaders(sheet))
					{
						// Map to store the activity name with corresponding
						// individuals
						int count = 2;
						Iterator<Entity> iterator = sheet.iterator();
						// Collect individual row numbers for later report
						Set<Integer> invalidIndividuals = new HashSet<Integer>();

						while (iterator.hasNext())
						{
							Entity entity = iterator.next();
							String individualIdentifier = entity.getString("identifier");
							String activityName = entity.getString("name");
							if (individualIdentifier != null && activityName != null)
							{
								List<Hit> searchHits = elasticSearchImp.search(activityName, null);
								nGramService.calculateNGramSimilarity(activityName, "name", searchHits);
								for (Hit hit : searchHits)
								{
									if (hit.getScore().intValue() >= THRESHOLD)
									{
										if (!mappedActivities.containsKey(activityName))
										{
											mappedActivities.put(activityName, new RecodeResponse(activityName, hit));
										}
										mappedActivities.get(activityName).getIdentifiers().add(individualIdentifier);
									}
									else
									{
										if (!rawActivities.containsKey(activityName))
										{
											rawActivities.put(activityName, new RecodeResponse(activityName, hit));
										}
										rawActivities.get(activityName).getIdentifiers().add(individualIdentifier);
									}
									break;
								}
							}
							else
							{
								invalidIndividuals.add(count);
							}
							count++;
						}
						isRecoding = true;
					}
					else
					{
						model.addAttribute("message", "The columns are invalid. Please look at the example!");
					}
				}
			}
			catch (IOException e)
			{
				model.addAttribute("message", e.getMessage());
			}
		}
		return defaultView(model);
	}

	private boolean validateExcelColumnHeaders(ExcelRepository sheet)
	{
		int count = 0;
		for (AttributeMetaData attribute : sheet.getEntityMetaData().getAttributes())
		{
			if (!ALLOWED_COLUMNS.contains(attribute.getName().toLowerCase()))
			{
				return false;
			}
			count++;
		}
		return count == ALLOWED_COLUMNS.size();
	}
}