package org.molgenis.coding.util;

import java.util.HashSet;
import java.util.Set;

import org.molgenis.coding.elasticsearch.Hit;

public class RecodeResponse
{
	private final String queryString;
	private final Set<String> identifiers;
	private final Hit hit;

	public RecodeResponse(String queryString, Hit hit)
	{
		this.queryString = queryString;
		this.hit = hit;
		this.identifiers = new HashSet<String>();
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
}
