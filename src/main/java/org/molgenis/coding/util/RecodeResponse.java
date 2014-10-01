package org.molgenis.coding.util;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.molgenis.coding.elasticsearch.Hit;

public class RecodeResponse implements Comparable<RecodeResponse>
{
	private final String queryString;
	private final Map<String, Set<Integer>> identifiers;
	private Hit hit;
	private boolean isCustomSearched = false;
	private boolean finalSelection = false;
	private Date addedDate;
	private String dateString;

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

	public boolean isFinalSelection()
	{
		return finalSelection;
	}

	public void setFinalSelection(boolean finalSelection)
	{
		this.finalSelection = finalSelection;
	}

	public Date getAddedDate()
	{
		return addedDate;
	}

	public void setAddedDate(Date addedDate)
	{
		this.addedDate = addedDate;
		if (addedDate != null) setDateString(addedDate.toString());
	}

	public String getDateString()
	{
		return dateString;
	}

	public void setDateString(String dateString)
	{
		this.dateString = dateString;
	}

	@Override
	public int compareTo(RecodeResponse o)
	{
		if (o.getAddedDate().compareTo(getAddedDate()) == 0)
		{
			return o.getHit().getScore().compareTo(getHit().getScore());
		}
		return o.getAddedDate().compareTo(getAddedDate());
	}

}