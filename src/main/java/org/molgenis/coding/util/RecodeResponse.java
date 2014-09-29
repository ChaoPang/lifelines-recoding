package org.molgenis.coding.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.molgenis.coding.elasticsearch.Hit;

public class RecodeResponse
{
	private final String queryString;
	private final Map<String, Set<Integer>> identifiers;
	private Hit hit;
	private boolean isCustomSearched = false;

	public RecodeResponse(String queryString, Hit hit)
	{
		this.queryString = queryString;
		this.hit = hit;
		this.identifiers = new HashMap<String, Set<Integer>>();
	}

	public String getQueryString()
	{
		return queryString;
	}

	public Map<String, Set<Integer>> getIdentifiers()
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

	public void setHit(Hit hit)
	{
		this.hit = hit;
	}
}
