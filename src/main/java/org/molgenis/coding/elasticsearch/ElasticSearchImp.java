package org.molgenis.coding.elasticsearch;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.deletebyquery.IndexDeleteByQueryResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.molgenis.data.Entity;
import org.molgenis.data.Repository;
import org.molgenis.util.RepositoryUtils;
import org.tartarus.snowball.ext.DutchStemmer;

public class ElasticSearchImp implements SearchService
{
	private final String indexName;
	private final Client client;
	private static final Logger LOG = Logger.getLogger(ElasticSearchImp.class);
	private static final DutchStemmer dutchStemmer = new DutchStemmer();
	public static final String INDEX_TYPE = "coding";
	public static final String DEFAULT_NAME_FIELD = "name";
	public static final String DEFAULT_CODE_FIELD = "code";
	public static final String DEFAULT_FREQUENCY_FIELD = "frequency";
	public static final String DEFAULT_ORIGINAL_FIELD = "original";
	public static final String DEFAULT_CODESYSTEM_FIELD = "codesystem";
	public static final String DEFAULT_DATE_FIELD = "date";
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("E yyyy.MM.dd 'at' hh:mm:ss a zzz");

	public ElasticSearchImp(String indexName, Client client)
	{
		if (indexName == null) throw new IllegalArgumentException("indexName is null");
		if (client == null) throw new IllegalArgumentException("Client is null");

		this.indexName = indexName;
		this.client = client;
		createIndexIfNotExists();
	}

	@Override
	public void indexRepository(Repository repository) throws IOException
	{
		Iterator<BulkRequestBuilder> requests = buildIndexRequest(repository);
		while (requests.hasNext())
		{
			BulkRequestBuilder request = requests.next();
			LOG.info("Request created");
			if (LOG.isDebugEnabled())
			{
				LOG.debug("BulkRequest:" + request);
			}

			BulkResponse response = request.execute().actionGet();
			LOG.info("Request done");
			if (LOG.isDebugEnabled())
			{
				LOG.debug("BulkResponse:" + response);
			}

			if (response.hasFailures())
			{
				throw new ElasticsearchException(response.buildFailureMessage());
			}
		}
	}

	@Override
	public void indexDocument(Map<String, Object> doc)
	{
		indexDocument(doc, INDEX_TYPE);
	}

	@Override
	public void indexDocument(Map<String, Object> doc, String documentType)
	{
		BulkRequestBuilder bulkRequest = client.prepareBulk();
		IndexRequestBuilder indexRequestBuilder = client.prepareIndex(indexName, documentType);
		indexRequestBuilder.setSource(doc);
		bulkRequest.add(indexRequestBuilder);
		BulkResponse response = bulkRequest.setRefresh(true).execute().actionGet();
		if (LOG.isDebugEnabled())
		{
			LOG.debug("BulkResponse:" + response);
		}
		if (response.hasFailures())
		{
			throw new ElasticsearchException(response.buildFailureMessage());
		}
		LOG.info("Request done");
	}

	@Override
	public void deleteDocumentsByType(String documentType)
	{
		LOG.info("Going to delete all documents of type [" + documentType + "]");

		DeleteByQueryResponse deleteResponse = client.prepareDeleteByQuery(indexName)
				.setQuery(new TermQueryBuilder("_type", documentType)).execute().actionGet();

		if (deleteResponse != null)
		{
			IndexDeleteByQueryResponse idbqr = deleteResponse.getIndex(indexName);
			if ((idbqr != null) && (idbqr.getFailedShards() > 0))
			{
				throw new ElasticsearchException("Delete failed. Returned headers:" + idbqr.getHeaders());
			}
		}

		LOG.info("Delete done.");
	}

	@Override
	public boolean deleteDocumentById(String documentId, String documentType)
	{
		DeleteResponse response = client
				.prepareDelete(indexName, documentType == null ? INDEX_TYPE : documentType, documentId).execute()
				.actionGet();
		return response.isFound();
	}

	@Override
	public Hit getDocumentById(String documentId, String codeSystem)
	{
		GetResponse response = client.prepareGet(indexName, codeSystem == null ? INDEX_TYPE : codeSystem, documentId)
				.execute().actionGet();
		Hit hit = null;
		if (response.isExists())
		{
			hit = new Hit(response.getId(), (float) 1, response.getSourceAsMap());
		}
		return hit;
	}

	@Override
	public List<Hit> search(String documentType, String query, String field)
	{
		return search(documentType, query, field, null, null);
	}

	@Override
	public List<Hit> search(String documentType, String query, String field, String sortField, SortOrder sortOrder)
	{
		SearchRequestBuilder searchRequestBuilder = client.prepareSearch(indexName);
		searchRequestBuilder.setSize(Integer.MAX_VALUE);

		if (documentType != null) searchRequestBuilder.setTypes(documentType);

		if (query != null)
		{
			StringBuilder queryStringBuilder = new StringBuilder();
			String fuzzyQueryProcess = fuzzyQueryProcess(escapeValue(query));
			if (field != null) queryStringBuilder.append(field).append(":(").append(fuzzyQueryProcess).append(')');
			else queryStringBuilder.append(fuzzyQueryProcess);

			searchRequestBuilder.setQuery(QueryBuilders.queryString(queryStringBuilder.toString()));
		}

		if (sortField != null && sortOrder != null)
		{
			FieldSortBuilder sortBuilder = SortBuilders.fieldSort(sortField).order(sortOrder).sortMode("min");
			searchRequestBuilder.addSort(sortBuilder);
		}

		return parseSearchResponse(searchRequestBuilder);
	}

	@Override
	public void updateIndex(String documentId, String updateScript, String documentType)
	{
		LOG.info("Going to update document of type [" + INDEX_TYPE + "] with Id : " + documentId);

		UpdateResponse updateResponse = client
				.prepareUpdate(indexName, documentType == null ? INDEX_TYPE : documentType, documentId)
				.setRetryOnConflict(3).setRefresh(true).setScript("ctx._source." + updateScript).execute().actionGet();

		if (updateResponse == null)
		{
			throw new ElasticsearchException("update failed.");
		}

		LOG.info("Update done.");
	}

	private List<Hit> parseSearchResponse(SearchRequestBuilder searchRequestbuilder)
	{
		List<Hit> documents = new ArrayList<Hit>();
		SearchResponse searchResponse = searchRequestbuilder.execute().actionGet();
		ShardSearchFailure[] failures = searchResponse.getShardFailures();
		if ((failures != null) && (failures.length > 0))
		{
			StringBuilder sb = new StringBuilder("Exception while searching:\n");
			for (ShardSearchFailure failure : failures)
			{
				sb.append(failure.shard()).append(":").append(failure.reason());
			}
			return documents;
		}

		for (SearchHit hit : searchResponse.getHits().hits())
		{
			documents.add(new Hit(hit.getId(), hit.getScore(), hit.sourceAsMap()));
		}
		return documents;
	}

	private String fuzzyQueryProcess(String query)
	{
		StringBuilder fuzzyQueryString = new StringBuilder();
		String[] words = query.trim().split("\\s+");
		for (int i = 0; i < words.length; i++)
		{
			if (!words[i].isEmpty())
			{
				dutchStemmer.setCurrent(words[i].trim());
				dutchStemmer.stem();
				fuzzyQueryString.append(dutchStemmer.getCurrent()).append("~0.6");
				if (i < words.length - 1)
				{
					fuzzyQueryString.append(' ');
				}
			}
		}
		return fuzzyQueryString.toString();
	}

	private Iterator<BulkRequestBuilder> buildIndexRequest(final Repository repository)
	{
		return new Iterator<BulkRequestBuilder>()
		{
			private final long rows = RepositoryUtils.count(repository);
			private static final int docsPerBulk = 1000;
			private final Iterator<? extends Entity> it = repository.iterator();
			private int row = 0;

			@Override
			public boolean hasNext()
			{
				return it.hasNext();
			}

			@Override
			public BulkRequestBuilder next()
			{
				BulkRequestBuilder bulkRequest = client.prepareBulk();
				final long maxRow = Math.min(row + docsPerBulk, rows);
				for (; row < maxRow; ++row)
				{
					Entity entity = it.next();
					Map<String, Object> doc = new HashMap<String, Object>();
					for (String attrName : entity.getAttributeNames())
					{
						doc.put(attrName, entity.get(attrName));
					}

					IndexRequestBuilder indexRequestBuilder = client.prepareIndex(indexName, repository.getName());
					indexRequestBuilder.setSource(doc);
					bulkRequest.add(indexRequestBuilder);
					if ((row + 1) % 100 == 0) LOG.info("Added [" + (row + 1) + "] documents");
				}
				return bulkRequest;
			}

			@Override
			public void remove()
			{
				throw new UnsupportedOperationException();
			}
		};
	}

	private void createIndexIfNotExists()
	{
		// Wait until elasticsearch is ready
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
		boolean hasIndex = client.admin().indices().exists(new IndicesExistsRequest(indexName)).actionGet().isExists();
		if (!hasIndex)
		{
			CreateIndexResponse response = client.admin().indices().prepareCreate(indexName).execute().actionGet();
			if (!response.isAcknowledged())
			{
				throw new ElasticsearchException("Creation of index [" + indexName + "] failed. Response=" + response);
			}
			LOG.info("Index [" + indexName + "] created");
		}
	}

	public static String escapeValue(String value)
	{
		StringBuilder stringBuilder = new StringBuilder();
		for (String eachWord : value.toLowerCase().replaceAll("[\\W]", " ").split("\\s+"))
		{
			if (!eachWord.equalsIgnoreCase("AND") && !eachWord.equalsIgnoreCase("OR"))
			{
				stringBuilder.append(eachWord).append(' ');
			}
		}
		return stringBuilder.toString().trim();
	}

	public static Map<String, Object> addDefaultFields(Map<String, Object> data)
	{
		if (!data.containsKey(DEFAULT_FREQUENCY_FIELD)) data.put(DEFAULT_FREQUENCY_FIELD, Integer.valueOf(1));
		if (!data.containsKey(DEFAULT_ORIGINAL_FIELD)) data.put(DEFAULT_ORIGINAL_FIELD, false);
		if (!data.containsKey(DEFAULT_DATE_FIELD)) data.put(DEFAULT_DATE_FIELD, new Date());
		return data;
	}

	@Override
	public void indexCodeSystem(Entity entity)
	{
		Map<String, Object> data = new HashMap<String, Object>();
		data.put(ElasticSearchImp.DEFAULT_NAME_FIELD, entity.getString(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD));
		indexDocument(data);
	}

	@Override
	public List<Hit> exactMatch(String documentType, String query, String field)
	{
		List<Hit> hits = new ArrayList<Hit>();

		for (Hit hit : search(documentType, query, field))
		{
			if (hit.getColumnValueMap().get(field).toString().equalsIgnoreCase(query))
			{
				hits.add(hit);
			}
		}
		return hits;
	}
}