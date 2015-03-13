package org.molgenis.coding.elasticsearch;

import java.util.List;
import java.util.Map;

import org.elasticsearch.search.sort.SortOrder;
import org.molgenis.data.Entity;
import org.molgenis.data.Repository;

public interface SearchService
{
	public void indexRepository(Repository repository);

	public void indexDocument(Map<String, Object> doc);

	public void indexDocument(Map<String, Object> doc, String documentType);

	public void updateIndex(String documentId, String updateScript, String documentType);

	public Hit getDocumentById(String documentId, String documentType);

	public List<Hit> search(String documentType, String query, String field);

	public List<Hit> search(String documentType, String query, String field, String sortField, SortOrder sortOrder);

	public boolean deleteDocumentById(String documentId, String documentType);

	public void deleteDocumentsByType(String documentType);

	public void indexCodeSystem(Entity entity);

	public List<Hit> exactMatch(String backupDocumentType, String codingJobName, String defaultNameField);
}