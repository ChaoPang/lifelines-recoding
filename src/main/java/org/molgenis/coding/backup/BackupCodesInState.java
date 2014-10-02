package org.molgenis.coding.backup;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.molgenis.coding.elasticsearch.CodingState;
import org.molgenis.coding.elasticsearch.ElasticSearchImp;
import org.molgenis.coding.elasticsearch.Hit;
import org.molgenis.coding.elasticsearch.SearchService;
import org.molgenis.coding.util.RecodeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;

public class BackupCodesInState
{
	private final static String IS_MAPPED_FIELD = "mapped";
	private final static String QUERYSTRING_FIELD = "queryString";
	private final static String IDENTIFIERS_FIELD = "identifiers";
	private final static String DOCUMENT_ID_FIELD = "documentId";
	private final static String SCORE_FIELD = "score";
	private final static String DOCUMENT_DATA_FIELD = "data";
	private final static String IS_CUSTOM_SEARCHED_FIELD = "isCustomSearched";
	private final static String IS_FINALIZED_FIELD = "finalSelection";
	private final static String ADDED_DATE_FIELD = "addedDate";
	private final static String ADDED_DATE_STRING_FIELD = "dateString";
	private final static String THRESHOLD_FIELD = "threshold";
	private final static String INPUT_COLUMN_FIELD = "input_column";
	private final static String BACKUP_VALID_TYPE_FIELD = "backup_valid_data";
	private final static String BACKUP_INVALID_TYPE_FIELD = "backup_general_info";
	private final AtomicInteger isRecovering = new AtomicInteger();

	private final SearchService elasticSearchImp;
	private final CodingState codingState;

	@Autowired
	public BackupCodesInState(SearchService elasticSearchImp, CodingState codingState)
	{
		if (elasticSearchImp == null) throw new IllegalArgumentException("ElasticSearch is null");
		if (codingState == null) throw new IllegalArgumentException("CodingState is null");
		this.elasticSearchImp = elasticSearchImp;
		this.codingState = codingState;
	}

	private Map<String, Object> translate(RecodeResponse recodeResponse, boolean isMapped)
	{
		Map<String, Object> doc = new HashMap<String, Object>();
		doc.put(QUERYSTRING_FIELD, recodeResponse.getQueryString());
		doc.put(IDENTIFIERS_FIELD, recodeResponse.getIdentifiers());
		doc.put(DOCUMENT_ID_FIELD, recodeResponse.getHit().getDocumentId());
		doc.put(SCORE_FIELD, recodeResponse.getHit().getScore());
		doc.put(DOCUMENT_DATA_FIELD, recodeResponse.getHit().getColumnValueMap());
		doc.put(IS_CUSTOM_SEARCHED_FIELD, recodeResponse.isCustomSearched());
		doc.put(IS_FINALIZED_FIELD, recodeResponse.isFinalSelection());
		doc.put(ADDED_DATE_FIELD, recodeResponse.getAddedDate().getTime());
		doc.put(ADDED_DATE_STRING_FIELD, recodeResponse.getDateString());
		doc.put(IS_MAPPED_FIELD, isMapped);
		return doc;
	}

	private boolean isRecoveryRunning()
	{
		return (isRecovering.get() > 0);
	}

	@Async
	public void index()
	{
		if ((codingState.getMappedActivities().size() != 0 || codingState.getRawActivities().size() != 0)
				&& !isRecoveryRunning())
		{
			elasticSearchImp.deleteDocumentsByType(BACKUP_VALID_TYPE_FIELD);
			elasticSearchImp.deleteDocumentsByType(BACKUP_INVALID_TYPE_FIELD);

			for (RecodeResponse recodeResponse : codingState.getMappedActivities().values())
			{
				elasticSearchImp.indexDocument(translate(recodeResponse, true), BACKUP_VALID_TYPE_FIELD);
			}

			for (RecodeResponse recodeResponse : codingState.getRawActivities().values())
			{
				elasticSearchImp.indexDocument(translate(recodeResponse, false), BACKUP_VALID_TYPE_FIELD);
			}

			Map<String, Object> doc = new HashMap<String, Object>();
			doc.put(IDENTIFIERS_FIELD, codingState.getInvalidIndividuals());
			doc.put(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD, codingState.getSelectedCodeSystem());
			doc.put(THRESHOLD_FIELD, codingState.getThreshold());
			doc.put(INPUT_COLUMN_FIELD, codingState.getMaxNumColumns());
			elasticSearchImp.indexDocument(doc, BACKUP_INVALID_TYPE_FIELD);

		}
	}

	public boolean backupExisits()
	{
		List<Hit> backUpHits = elasticSearchImp.search(BACKUP_VALID_TYPE_FIELD, null, null);
		List<Hit> identifierHits = elasticSearchImp.search(BACKUP_INVALID_TYPE_FIELD, null, null);
		return backUpHits.size() > 0 || identifierHits.size() > 0;
	}

	public Map<String, Object> recovery()
	{
		Map<String, Object> result = new HashMap<String, Object>();
		result.put("success", true);
		try
		{
			isRecovering.incrementAndGet();
			List<Hit> backUpHits = elasticSearchImp.search(BACKUP_VALID_TYPE_FIELD, null, null);
			if (backUpHits.size() > 0)
			{
				for (Hit backUpHit : backUpHits)
				{
					Map<String, Object> columnValueMap = backUpHit.getColumnValueMap();
					String queryString = columnValueMap.get(QUERYSTRING_FIELD).toString();
					String documentId = columnValueMap.get(DOCUMENT_ID_FIELD).toString();
					Float ngramScore = Float.parseFloat(columnValueMap.get(SCORE_FIELD).toString());
					Object documentDataObject = columnValueMap.get(DOCUMENT_DATA_FIELD);
					Object identifiersObject = columnValueMap.get(IDENTIFIERS_FIELD);
					Object isCustomSearched = columnValueMap.get(IS_CUSTOM_SEARCHED_FIELD);
					Object isFinalized = columnValueMap.get(IS_FINALIZED_FIELD);
					Object addedDate = columnValueMap.get(ADDED_DATE_FIELD);
					Object addedDateString = columnValueMap.get(ADDED_DATE_STRING_FIELD);
					Map<String, Object> documentDataMap = new HashMap<String, Object>();
					Map<String, Set<Integer>> identifierMap = new HashMap<String, Set<Integer>>();

					if (documentDataObject instanceof Map<?, ?>)
					{
						for (Entry<?, ?> entry : ((Map<?, ?>) documentDataObject).entrySet())
						{
							documentDataMap.put(entry.getKey().toString(), entry.getValue());
						}
					}

					if (identifiersObject instanceof Map<?, ?>)
					{
						for (Entry<?, ?> entry : ((Map<?, ?>) identifiersObject).entrySet())
						{
							Set<Integer> columnIndices = new HashSet<Integer>();
							if (entry.getValue() instanceof Set<?>)
							{
								for (Object columnIndex : (Set<?>) entry.getValue())
								{
									columnIndices.add(Integer.parseInt(columnIndex.toString()));
								}
							}
							identifierMap.put(entry.getKey().toString(), columnIndices);
						}
					}

					Hit hit = new Hit(documentId.toString(), null, documentDataMap);
					hit.setScore(ngramScore);
					RecodeResponse recodeResponse = new RecodeResponse(queryString, hit);
					recodeResponse.setCustomSearched(isCustomSearched != null ? Boolean.parseBoolean(isCustomSearched
							.toString()) : null);
					recodeResponse
							.setFinalSelection(isFinalized != null ? Boolean.parseBoolean(isFinalized.toString()) : null);
					recodeResponse.getIdentifiers().putAll(identifierMap);
					recodeResponse.setAddedDate(new Date(Long.parseLong(addedDate.toString())));
					recodeResponse.setDateString(addedDateString != null ? addedDateString.toString() : null);

					if (Boolean.parseBoolean(columnValueMap.get(IS_MAPPED_FIELD).toString()))
					{
						codingState.getMappedActivities().put(queryString, recodeResponse);
					}
					else
					{
						codingState.getRawActivities().put(queryString, recodeResponse);
					}
				}
			}

			List<Hit> identifierHits = elasticSearchImp.search(BACKUP_INVALID_TYPE_FIELD, null, null);
			if (identifierHits.size() > 0)
			{
				for (Hit hit : identifierHits)
				{
					Object identifierObject = hit.getColumnValueMap().get(IDENTIFIERS_FIELD);
					Object thresholdObject = hit.getColumnValueMap().get(THRESHOLD_FIELD);
					Object codeSystemObject = hit.getColumnValueMap().get(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD);
					Object inputColumnsObject = hit.getColumnValueMap().get(INPUT_COLUMN_FIELD);
					if (identifierObject instanceof List<?>)
					{
						for (Object identifier : (List<?>) identifierObject)
						{
							codingState.getInvalidIndividuals().add(identifier.toString());
						}
					}
					if (inputColumnsObject instanceof List<?>)
					{
						for (Object columnName : (List<?>) inputColumnsObject)
						{
							codingState.getMaxNumColumns().add(columnName.toString());
						}
					}

					codingState.setSelectedCodeSystem(codeSystemObject.toString());
					codingState.setThreshold(Integer.parseInt(thresholdObject.toString()));

				}
			}
		}
		catch (Exception e)
		{
			result.put("success", false);
			result.put("message", e.getMessage());
		}
		finally
		{
			isRecovering.decrementAndGet();
		}

		return result;
	}
}