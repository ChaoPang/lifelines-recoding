package org.molgenis.coding.elasticsearch;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.ImmutableSettings.Builder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;

/**
 * Factory for creating an embedded ElasticSearch server service. An elastic search config file named
 * 'elasticsearch.yml' must be on the classpath
 * 
 * @author erwin
 * 
 */
public class EmbeddedElasticSearchServiceFactory implements Closeable
{
	private static final Logger LOG = Logger.getLogger(EmbeddedElasticSearchServiceFactory.class);
	private static final String CONFIG_FILE_NAME = "elasticsearch.yml";
	private static final String DEFAULT_INDEX_NAME = "data";
	private final Client client;
	private final Node node;

	/**
	 * Create an embedded ElasticSearch server service with the given index name using 'elasticsearch.yml' and provided
	 * settings. The provided settings override settings specified in 'elasticsearch.yml'
	 * 
	 * @param indexName
	 * @param providedSettings
	 */
	public EmbeddedElasticSearchServiceFactory()
	{
		String molgenisHomeDir = System.getProperty("molgenis.home");
		LOG.info("The lifelines recoding system home folder is : " + molgenisHomeDir);
		if (molgenisHomeDir == null)
		{
			throw new IllegalArgumentException("missing required java system property 'molgenis.home'");
		}
		if (!molgenisHomeDir.endsWith("/")) molgenisHomeDir = molgenisHomeDir + '/';

		// create molgenis data directory if not exists
		String homeDataDirStr = molgenisHomeDir + "data";
		File molgenisDataDir = new File(homeDataDirStr);
		if (!molgenisDataDir.exists())
		{
			if (!molgenisDataDir.mkdir())
			{
				throw new RuntimeException("failed to create directory: " + homeDataDirStr);
			}
		}

		Builder builder = ImmutableSettings.settingsBuilder().loadFromClasspath(CONFIG_FILE_NAME);
		builder.put("path.data", homeDataDirStr);

		Settings settings = builder.build();
		node = nodeBuilder().settings(settings).local(true).node();
		client = node.client();

		LOG.info("Embedded elasticsearch server started, data path=[" + settings.get("path.data") + "]");
	}

	public ElasticSearchImp create()
	{
		return new ElasticSearchImp(DEFAULT_INDEX_NAME, client);
	}

	@Override
	public void close() throws IOException
	{

		try
		{
			client.close();
		}
		catch (Exception e)
		{
			LOG.error("Error closing client", e);
		}

		try
		{
			node.close();
		}
		catch (Exception e)
		{
			LOG.error("Error closing node", e);
		}

		LOG.info("Elastic search server stopped");
	}

}
