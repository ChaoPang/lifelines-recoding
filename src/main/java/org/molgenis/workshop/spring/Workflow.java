package org.molgenis.workshop.spring;

public class Workflow
{
	private Integer id;
	private String name;

	public Workflow()
	{
		super();
	}

	public Workflow(Integer id, String name)
	{
		super();
		this.id = id;
		this.name = name;
	}

	public Integer getId()
	{
		return id;
	}

	public void setId(Integer id)
	{
		this.id = id;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return "Workflow [id=" + id + ", name=" + name + "]";
	}

}
