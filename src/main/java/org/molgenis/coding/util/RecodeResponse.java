package org.molgenis.coding.util;

import java.util.HashSet;
import java.util.Set;

import org.molgenis.coding.elasticsearch.Hit;

public class RecodeResponse
{
	private final String queryString;
	private final Set<String> identifiers;
	// private final Float originalSimilarityScore;
	private Hit hit;
	private boolean isCustomSearched = false;

	public RecodeResponse(String queryString, Hit hit)
	{
		this.queryString = queryString;
		this.hit = hit;
		this.identifiers = new HashSet<String>();
		// this.originalSimilarityScore = originalSimilarityScore;
	}

	public String getQueryString()
	{
		return queryString;
	}

	public Set<String> getIdentifiers()
	{
		return identifiers;
	}

	public Hit getHit()
	{
		return hit;
	}

	public boolean isCustomSearched()
	{
		return isCustomSearched;
	}

	public void setCustomSearched(boolean isCustomSearched)
	{
		this.isCustomSearched = isCustomSearched;
	}

	//
	// public int getOriginalSimilarityScore()
	// {
	// return originalSimilarityScore.intValue();
	// }

	public void setHit(Hit hit)
	{
		this.hit = hit;
	}
}
