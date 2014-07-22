package org.molgenis.coding.elasticsearch;

import java.util.Map;

public class Hit implements Comparable<Hit>
{
	private final String documentId;
	private final Map<String, Object> columnValueMap;
	private final Integer frequency;
	private Float score;

	public Hit(String documentId, Float score, Map<String, Object> columnValueMap)
	{
		this.documentId = documentId;
		this.score = score;
		this.columnValueMap = columnValueMap;
		if (this.columnValueMap.containsKey(ElasticSearchImp.DEFAULT_FREQUENCY_FIELD))
		{
			frequency = Integer.parseInt(this.columnValueMap.get(ElasticSearchImp.DEFAULT_FREQUENCY_FIELD).toString());
		}
		else
		{
			frequency = 1;
		}
	}

	public String getDocumentId()
	{
		return documentId;
	}

	public void setScore(Float score)
	{
		this.score = score;
	}

	public Float getScore()
	{
		return score;
	}

	public Map<String, Object> getColumnValueMap()
	{
		return columnValueMap;
	}

	public Integer getFrequency()
	{
		return frequency;
	}

	@Override
	public int compareTo(Hit o)
	{
		// If the scores are equal then sort on frequency
		if (score.intValue() == o.getScore().intValue())
		{
			if (frequency < o.getFrequency())
			{
				return 1;
			}
			else if (frequency > o.getFrequency())
			{
				return -1;
			}
			else
			{
				return 0;
			}
		}

		if (score.intValue() > o.getScore().intValue()) return -1;
		else return 1;
	}
}
