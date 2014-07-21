package org.molgenis.workshop.spring;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class HashMapDatabase implements Database
{
	private final Map<Integer, Workflow> workflows = new LinkedHashMap<Integer, Workflow>();

	public HashMapDatabase()
	{
		saveWorkflow(new Workflow(1, "Extract DNA"));
		saveWorkflow(new Workflow(2, "Sequence DNA"));
		saveWorkflow(new Workflow(3, "Analyze results"));
		saveWorkflow(new Workflow(4, "Publish results"));
	}

	public Collection<Workflow> findWorkflows()
	{
		return workflows.values();
	}

	public Workflow getWorkflowById(Integer id)
	{
		return workflows.get(id);
	}

	public void saveWorkflow(Workflow workflow)
	{
		if (workflow.getId() == null) throw new IllegalArgumentException("Workflow id cannot be null");
		if (workflow.getName() == null) throw new IllegalArgumentException("Workflow name cannot be null");

		workflows.put(workflow.getId(), workflow);
	}

	public void deleteWorkflow(Integer id)
	{
		workflows.remove(id);
	}

}
