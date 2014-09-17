package org.molgenis.coding.elasticsearch;

import java.util.List;
import java.util.Map;

import org.molgenis.data.Repository;

public interface SearchService
{
	public void indexRepository(Repository repository);

	public void indexDocument(Map<String, Object> doc);

	public void indexDocument(Map<String, Object> doc, String documentType);

	public List<Hit> getAllCodeSystems();

	public List<Hit> getAllCodeSystems(String documentType);

	public List<Hit> search(String documentType, String query, String field);

	public void updateIndex(String documentId, String updateScript);

	public Hit getDocumentById(String documentId);
}