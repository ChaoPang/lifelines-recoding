package org.molgenis.coding.elasticsearch;

import java.util.Map;

public class Hit
{
	private final String documentId;
	private final Float score;
	private final Map<String, Object> columnValueMap;

	public Hit(String documentId, Float score, Map<String, Object> columnValueMap)
	{
		this.documentId = documentId;
		this.score = score;
		this.columnValueMap = columnValueMap;
	}

	public String getDocumentId()
	{
		return documentId;
	}

	public Float getScore()
	{
		return score;
	}

	public Map<String, Object> getColumnValueMap()
	{
		return columnValueMap;
	}
}
