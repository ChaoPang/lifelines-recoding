package org.molgenis.coding.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.queryparser.classic.ParseException;
import org.molgenis.coding.elasticsearch.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/")
public class FindCodesController
{
	private final static String VIEW_NAME = "view-find-codes";
	private final SearchService elasticSearchImp;

	@Autowired
	public FindCodesController(SearchService elasticSearchImp)
	{
		if (elasticSearchImp == null) throw new IllegalArgumentException("ElasticSearch is null");
		this.elasticSearchImp = elasticSearchImp;
	}

	@RequestMapping(method = RequestMethod.GET)
	public String defaultView(Model model)
	{
		model.addAttribute("viewId", VIEW_NAME);
		return VIEW_NAME;
	}

	@RequestMapping(value = "/find", method = RequestMethod.POST, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> searchDoc(@RequestBody
	Map<String, Object> request) throws IOException, ParseException
	{
		Map<String, Object> results = new HashMap<String, Object>();
		if (request.containsKey("query"))
		{
			Object field = request.get("field");
			results.put("results",
					elasticSearchImp.search(request.get("query").toString(), field == null ? null : field.toString()));
		}
		return results;
	}
}