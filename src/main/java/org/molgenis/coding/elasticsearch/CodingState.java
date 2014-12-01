package org.molgenis.coding.elasticsearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.molgenis.coding.util.RecodeResponse;
import org.molgenis.data.AttributeMetaData;

public class CodingState
{
	private final Map<String, RecodeResponse> mappedActivities = new HashMap<String, RecodeResponse>();
	private final Map<String, RecodeResponse> rawActivities = new HashMap<String, RecodeResponse>();
	private final List<String> maxNumColumns = new ArrayList<String>();
	private final Set<String> invalidIndividuals = new HashSet<String>();

	private Integer threshold = 80;
	private Integer totalNumber = 0;
	private String selectedCodeSystem = null;
	private String codingJobName = null;
	private String errorMessage = null;
	private boolean isCoding = false;

	public Integer getThreshold()
	{
		return threshold;
	}

	public void setThreshold(Integer tHRESHOLD)
	{
		threshold = tHRESHOLD;
	}

	public String getSelectedCodeSystem()
	{
		return selectedCodeSystem;
	}

	public void setSelectedCodeSystem(String selectedCodeSystem)
	{
		this.selectedCodeSystem = selectedCodeSystem;
	}

	public Map<String, RecodeResponse> getMappedActivities()
	{
		return mappedActivities;
	}

	public Map<String, RecodeResponse> getRawActivities()
	{
		return rawActivities;
	}

	public List<String> getMaxNumColumns()
	{
		return maxNumColumns;
	}

	public Set<String> getInvalidIndividuals()
	{
		return invalidIndividuals;
	}

	public void addInvalidIndividuals(String identifier)
	{
		invalidIndividuals.add(identifier);
	}

	public void addColumns(Iterable<AttributeMetaData> attributes)
	{
		for (AttributeMetaData attributeMetaData : attributes)
		{
			maxNumColumns.add(attributeMetaData.getName());
		}
	}

	public void removeInvalidIndividuals(String individualIdentifier)
	{
		if (invalidIndividuals.contains(individualIdentifier)) invalidIndividuals.remove(individualIdentifier);
	}

	public void clearState()
	{
		isCoding = false;
		totalNumber = 0;
		rawActivities.clear();
		mappedActivities.clear();
		maxNumColumns.clear();
		invalidIndividuals.clear();
	}

	public boolean isCoding()
	{
		return isCoding;
	}

	public void setCoding(boolean isCoding)
	{
		this.isCoding = isCoding;
	}

	public void setCodingJobName(String codingJobName)
	{
		this.codingJobName = codingJobName;
	}

	public String getCodingJobName()
	{
		return codingJobName;
	}

	public String getErrorMessage()
	{
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage)
	{
		this.errorMessage = errorMessage;
	}

	public void setTotalNumber(Integer totalNumber)
	{
		this.totalNumber = totalNumber;
	}

	public Integer getTotalNumber()
	{
		return totalNumber;
	}
}