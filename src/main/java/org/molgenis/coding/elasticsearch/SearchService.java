package org.molgenis.coding.elasticsearch;

import java.util.List;
import java.util.Map;

import org.molgenis.data.Repository;

public interface SearchService
{
	public void indexRepository(Repository repository);

	public void indexDocument(Map<String, Object> doc);

	public List<Hit> getAllDocuments();

	public List<Hit> search(String query, String field);

	public void updateIndex(String documentId, String updateScript);

	public Hit getDocumentById(String documentId);
}