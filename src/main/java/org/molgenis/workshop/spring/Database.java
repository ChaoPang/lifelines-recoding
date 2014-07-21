package org.molgenis.workshop.spring;

import java.util.Collection;

public interface Database
{
	Collection<Workflow> findWorkflows();

	Workflow getWorkflowById(Integer id);

	void saveWorkflow(Workflow workflow);

	void deleteWorkflow(Integer id);
}
