package org.molgenis.coding.elasticsearch;

import java.util.ArrayList;
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
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.molgenis.data.Entity;
import org.molgenis.data.Repository;
import org.molgenis.util.RepositoryUtils;
import org.springframework.scheduling.annotation.Async;
import org.tartarus.snowball.ext.DutchStemmer;

public class ElasticSearchImp implements SearchService
{
	private final String indexName;
	private final Client client;
	private static final String INDEX_TYPE = "coding";
	private static final Logger LOG = Logger.getLogger(ElasticSearchImp.class);
	private static final DutchStemmer dutchStemmer = new DutchStemmer();
	public static final String DEFAULT_FREQUENCY_FIELD = "frequency";

	public ElasticSearchImp(String indexName, Client client)
	{
		if (indexName == null) throw new IllegalArgumentException("indexName is null");
		if (client == null) throw new IllegalArgumentException("Client is null");

		this.indexName = indexName;
		this.client = client;
		createIndexIfNotExists();
	}

	@Async
	@Override
	public void indexRepository(Repository repository)
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
	public void indexDocument(String field, Object value)
	{
		BulkRequestBuilder bulkRequest = client.prepareBulk();
		Map<String, Object> doc = new HashMap<String, Object>();
		doc.put(field, value);
		IndexRequestBuilder indexRequestBuilder = client.prepareIndex(indexName, INDEX_TYPE);
		indexRequestBuilder.setSource(doc);
		bulkRequest.add(indexRequestBuilder);
		BulkResponse response = bulkRequest.execute().actionGet();
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

	@Override
	public List<Hit> getAllDocuments()
	{
		SearchRequestBuilder searchRequestBuilder = client.prepareSearch(indexName);
		searchRequestBuilder.setTypes(INDEX_TYPE);
		searchRequestBuilder.setSize(Integer.MAX_VALUE);
		return parseSearchResponse(searchRequestBuilder);
	}

	//
	// @Override
	// public Collection<Hit> calculateTermFrequency(String sportField)
	// {
	// Map<String, Hit> termFrequency = new HashMap<String, Hit>();
	// for (Hit hit : getAllDocuments())
	// {
	// if (hit.getColumnValueMap().containsKey((sportField)))
	// {
	// Object sport = hit.getColumnValueMap().get(sportField);
	// if (sport != null)
	// {
	// if (!termFrequency.containsKey(sport.toString()))
	// {
	// termFrequency.put(sport.toString(), hit);
	// }
	// else
	// {
	// termFrequency.get(sport.toString()).incrementFrequency();
	// }
	// }
	// }
	// }
	// return termFrequency.values();
	// }

	@Override
	public List<Hit> search(String query, String field)
	{
		SearchRequestBuilder searchRequestbuilder = client.prepareSearch(indexName);
		searchRequestbuilder.setTypes(INDEX_TYPE);

		StringBuilder queryStringBuilder = new StringBuilder();
		if (field != null) queryStringBuilder.append(field).append(":(").append(fuzzyQueryProcess(query)).append(')');
		else queryStringBuilder.append(fuzzyQueryProcess(query));

		searchRequestbuilder.setQuery(QueryBuilders.queryString(queryStringBuilder.toString()));
		return parseSearchResponse(searchRequestbuilder);
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
			if (hit.sourceAsMap().get(DEFAULT_FREQUENCY_FIELD) != null)
			{
				documents.add(new Hit(hit.getId(), hit.getScore(), hit.sourceAsMap()));
			}
		}
		return documents;
	}

	private String fuzzyQueryProcess(String query)
	{
		StringBuilder fuzzyQueryString = new StringBuilder();
		String[] words = query.split("\\s");
		for (int i = 0; i < words.length; i++)
		{
			dutchStemmer.setCurrent(words[i]);
			dutchStemmer.stem();
			fuzzyQueryString.append(dutchStemmer.getCurrent()).append("~0.7");
			if (i < words.length - 1)
			{
				fuzzyQueryString.append(' ');
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
					doc.put(DEFAULT_FREQUENCY_FIELD, 1);
					IndexRequestBuilder indexRequestBuilder = client.prepareIndex(indexName, INDEX_TYPE);
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
}