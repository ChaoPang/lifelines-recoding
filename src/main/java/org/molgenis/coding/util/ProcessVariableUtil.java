package org.molgenis.coding.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.molgenis.coding.elasticsearch.CodingState;
import org.molgenis.coding.elasticsearch.Hit;
import org.molgenis.coding.elasticsearch.SearchService;
import org.molgenis.coding.ngram.NGramService;
import org.molgenis.data.AttributeMetaData;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityMetaData;
import org.molgenis.data.csv.CsvRepository;
import org.molgenis.data.processor.CellProcessor;
import org.molgenis.data.processor.LowerCaseProcessor;
import org.molgenis.data.processor.TrimProcessor;
import org.molgenis.util.FileStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;

public class ProcessVariableUtil
{
	@Autowired
	private FileStore fileStore;

	private final SearchService elasticSearchImp;
	private final CodingState codingState;
	private final NGramService nGramService;

	private final AtomicInteger isProcessRunning = new AtomicInteger();
	private final AtomicInteger totalLineNumber = new AtomicInteger();
	private final AtomicInteger finishedLineNumber = new AtomicInteger();

	@Autowired
	public ProcessVariableUtil(SearchService elasticSearchImp, CodingState codingState, NGramService nGramService)
	{
		if (elasticSearchImp == null) throw new IllegalArgumentException("ElasticSearch is null");
		if (codingState == null) throw new IllegalArgumentException("CodingState is null");
		if (nGramService == null) throw new IllegalArgumentException("NGramService is null");
		this.elasticSearchImp = elasticSearchImp;
		this.codingState = codingState;
		this.nGramService = nGramService;
	}

	public boolean isUploading()
	{
		return isProcessRunning.get() > 0;
	}

	public int percentage()
	{
		if (totalLineNumber.get() != 0)
		{
			return (int) (finishedLineNumber.doubleValue() / totalLineNumber.doubleValue() * 100);
		}
		return 0;
	}

	@Async
	public void processUploadedVariableData(InputStream inputStream, String codeSystem, String codingJobName)
			throws IOException
	{
		isProcessRunning.incrementAndGet();
		CsvRepository csvRepository = null;
		try
		{
			codingState.setSelectedCodeSystem(codeSystem);
			codingState.setCodingJobName(codingJobName);
			codingState.setCoding(true);

			File serverFile = fileStore.store(inputStream, codingJobName + ".csv");

			if (serverFile.exists())
			{
				totalLineNumber.set(getLineNumber(serverFile));
				// codingState.setTotalNumber((codingState.getTotalNumber() + totalLineNumber.get()));

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
						codingState.addIndividuals(individualIdentifier);
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
									if (searchHits.size() > 0)
									{
										for (Hit hit : searchHits)
										{
											Map<String, RecodeResponse> activities = hit.getScore().intValue() >= codingState
													.getThreshold() ? codingState.getMappedActivities() : codingState
													.getRawActivities();
											if (!activities.containsKey(activityName))
											{
												activities.put(activityName, new RecodeResponse(activityName, hit));
											}
											if (!activities.get(activityName).getIdentifiers()
													.containsKey(individualIdentifier))
											{
												activities.get(activityName).getIdentifiers()
														.put(individualIdentifier, new HashSet<Integer>());
											}
											activities.get(activityName).getIdentifiers().get(individualIdentifier)
													.add(columnIndex);
											activities.get(activityName).setAddedDate(indexedDate);
											break;
										}
									}
									else
									{
										Map<String, RecodeResponse> activities = codingState.getRawActivities();
										if (!activities.containsKey(activityName))
										{
											Hit hit = new Hit(StringUtils.EMPTY, (float) 0,
													Collections.<String, Object> emptyMap());
											hit.setScore((float) 0);
											activities.put(activityName, new RecodeResponse(activityName, hit));
										}
										if (!activities.get(activityName).getIdentifiers()
												.containsKey(individualIdentifier))
										{
											activities.get(activityName).getIdentifiers()
													.put(individualIdentifier, new HashSet<Integer>());
										}
										activities.get(activityName).getIdentifiers().get(individualIdentifier)
												.add(columnIndex);
										activities.get(activityName).setAddedDate(indexedDate);
									}
								}
							}
						}
						finishedLineNumber.incrementAndGet();
					}
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
			if (totalLineNumber.get() != finishedLineNumber.get())
			{
				codingState.clearState();
				codingState.setErrorMessage("There are errors in line number " + (finishedLineNumber.get() + 2)
						+ ", please check the input file!");
			}
			totalLineNumber.set(0);
			finishedLineNumber.set(0);
			isProcessRunning.decrementAndGet();
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
