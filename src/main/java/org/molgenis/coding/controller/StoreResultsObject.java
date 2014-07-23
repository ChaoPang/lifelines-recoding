package org.molgenis.coding.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StoreResultsObject
{
	private final Map<String, Set<String>> reverseMap;
	private final Map<String, Set<String>> mappedActivities;
	private final Map<String, Set<String>> rawActivities;

	public StoreResultsObject()
	{
		this.reverseMap = new HashMap<String, Set<String>>();
		this.mappedActivities = new HashMap<String, Set<String>>();
		this.rawActivities = new HashMap<String, Set<String>>();
	}

	public Map<String, Set<String>> getMappedActivities()
	{
		return mappedActivities;
	}

	public Map<String, Set<String>> getRawActivities()
	{
		return rawActivities;
	}

	public Map<String, Set<String>> getReverseMap()
	{
		return reverseMap;
	}
}
