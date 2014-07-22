package org.molgenis.coding.elasticsearch;

import java.util.List;

import org.molgenis.data.Repository;

public interface SearchService
{
	public void indexRepository(Repository repository);

	public void indexDocument(String field, Object value);

	public List<Hit> getAllDocuments();

	public List<Hit> search(String query, String field);
}