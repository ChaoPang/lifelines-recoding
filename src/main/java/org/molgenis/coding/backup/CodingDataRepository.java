package org.molgenis.coding.backup;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.molgenis.coding.elasticsearch.Hit;
import org.molgenis.coding.elasticsearch.SearchService;
import org.molgenis.coding.ngram.NGramService;
import org.molgenis.data.AttributeMetaData;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityMetaData;
import org.molgenis.data.Repository;
import org.molgenis.data.csv.CsvRepository;
import org.molgenis.data.support.DefaultAttributeMetaData;
import org.molgenis.data.support.DefaultEntityMetaData;
import org.molgenis.data.support.MapEntity;

public class CodingDataRepository implements Repository
{
	private final String codingJobName;
	private final String codeSystem;
	private final CsvRepository csvRepository;
	private final SearchService elasticSearchImp;
	private final NGramService nGramService;
	private final int threshold;
	private DefaultEntityMetaData entityMetaData = null;

	public final static String IS_MAPPED_FIELD = "mapped";
	public final static String QUERYSTRING_FIELD = "queryString";
	public final static String IDENTIFIER_FIELD = "identifier";
	public final static String DOCUMENT_ID_FIELD = "documentId";
	public final static String SCORE_FIELD = "score";
	public final static String DOCUMENT_DATA_FIELD = "data";
	public final static String IS_CUSTOM_SEARCHED_FIELD = "isCustomSearched";
	public final static String IS_FINALIZED_FIELD = "finalSelection";
	public final static String ADDED_DATE_FIELD = "addedDate";
	public final static String ADDED_DATE_STRING_FIELD = "dateString";
	public final static String COLUMN_INDEX_FIELD = "columnIndex";

	public CodingDataRepository(String codingJobName, String codeSystem, Integer threshold,
			CsvRepository csvRepository, SearchService elasticSearchImp, NGramService nGramService)
	{
		if (nGramService == null) throw new IllegalArgumentException("NGramService cannot be null!");
		if (elasticSearchImp == null) throw new IllegalArgumentException("SearchService cannot be null!");
		if (threshold == null) throw new IllegalArgumentException("threshold cannot be null!");
		if (codingJobName == null) throw new IllegalArgumentException("codingJobName cannot be null!");
		if (codeSystem == null) throw new IllegalArgumentException("codeSystem cannot be null!");
		if (csvRepository == null) throw new IllegalArgumentException("CsvRepository cannot be null!");
		this.codingJobName = codingJobName;
		this.codeSystem = codeSystem;
		this.csvRepository = csvRepository;
		this.elasticSearchImp = elasticSearchImp;
		this.nGramService = nGramService;
		this.threshold = threshold;
	}

	@Override
	public String getName()
	{
		return codingJobName;
	}

	@Override
	public EntityMetaData getEntityMetaData()
	{
		if (entityMetaData == null)
		{
			entityMetaData = new DefaultEntityMetaData(codingJobName);
			entityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(QUERYSTRING_FIELD));
			entityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(IDENTIFIER_FIELD));
			entityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(DOCUMENT_ID_FIELD));
			entityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(SCORE_FIELD));
			entityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(DOCUMENT_DATA_FIELD));
			entityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(IS_CUSTOM_SEARCHED_FIELD));
			entityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(IS_FINALIZED_FIELD));
			entityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(ADDED_DATE_FIELD));
			entityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(ADDED_DATE_STRING_FIELD));
			entityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(IS_MAPPED_FIELD));
		}
		return entityMetaData;
	}

	@Override
	public Iterator<Entity> iterator()
	{
		final Iterator<Entity> iterator = csvRepository.iterator();
		return new Iterator<Entity>()
		{
			private final Date indexedDate = new Date();

			@Override
			public boolean hasNext()
			{
				return iterator.hasNext();
			}

			@Override
			public Entity next()
			{
				Entity csvEntity = iterator.next();

				String individualIdentifier = csvEntity.getString("Identifier");
				Entity entity = new MapEntity();
				int columnIndex = 0;
				for (AttributeMetaData attributeMetaData : csvRepository.getEntityMetaData().getAttributes())
				{
					if (attributeMetaData.getName().equalsIgnoreCase("identifier"))
					{
						entity.set(IDENTIFIER_FIELD, individualIdentifier);
					}
					else if (attributeMetaData.getName().toLowerCase().startsWith("name"))
					{
						String activityName = csvEntity.getString(attributeMetaData.getName());
						if (!StringUtils.isEmpty(individualIdentifier) && !StringUtils.isEmpty(activityName))
						{
							List<Hit> searchHits = elasticSearchImp.search(codeSystem, activityName, null);
							nGramService.calculateNGramSimilarity(activityName, "name", searchHits);
							for (Hit hit : searchHits)
							{
								entity.set(QUERYSTRING_FIELD, activityName);
								entity.set(COLUMN_INDEX_FIELD, columnIndex);
								entity.set(IS_CUSTOM_SEARCHED_FIELD, false);
								entity.set(IS_FINALIZED_FIELD, false);
								entity.set(ADDED_DATE_FIELD, indexedDate.getTime());
								entity.set(ADDED_DATE_STRING_FIELD, indexedDate.toString());
								entity.set(IS_MAPPED_FIELD, hit.getScore().intValue() >= threshold);
								entity.set(DOCUMENT_ID_FIELD, hit.getDocumentId());
								entity.set(SCORE_FIELD, hit.getScore());
								entity.set(DOCUMENT_DATA_FIELD, hit.getColumnValueMap());
								break;
							}
						}
					}
					columnIndex++;
				}
				return entity;
			}

			@Override
			public void remove()
			{
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public <E extends Entity> Iterable<E> iterator(Class<E> clazz)
	{
		return null;
	}

	@Override
	public String getUrl()
	{
		return null;
	}

	@Override
	public void close() throws IOException
	{
		// Do nothing
	}
}
