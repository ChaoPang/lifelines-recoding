package org.molgenis.coding.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.search.sort.SortOrder;
import org.molgenis.coding.elasticsearch.ElasticSearchImp;
import org.molgenis.coding.elasticsearch.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/view")
public class ViewCodesController
{
	private final static String VIEW_NAME = "view-all-codes";
	private final SearchService elasticSearchImp;

	@Autowired
	public ViewCodesController(SearchService elasticSearchImp)
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

	@RequestMapping(method = RequestMethod.GET, value = "/codesystems", produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> getDocs() throws IOException
	{
		Map<String, Object> results = new HashMap<String, Object>();
		results.put("results", elasticSearchImp.search(ElasticSearchImp.INDEX_TYPE, null, null));
		return results;
	}

	@RequestMapping(method = RequestMethod.GET, value = "/alldocs", produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> getDocs(@RequestParam("codeSystem")
	String codeSystem) throws IOException
	{
		Map<String, Object> results = new HashMap<String, Object>();
		results.put("results",
				elasticSearchImp.search(codeSystem, null, null, ElasticSearchImp.DEFAULT_DATE_FIELD, SortOrder.DESC));
		return results;
	}

	@RequestMapping(method = RequestMethod.POST, value = "/delete", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> deleteCode(@RequestBody
	Map<String, Object> request)
	{
		Map<String, Object> results = new HashMap<String, Object>();
		if (request.containsKey("documentId") && request.get("documentId") != null
				&& request.containsKey(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD)
				&& request.get(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD) != null)
		{
			boolean deleteDocumentById = elasticSearchImp.deleteDocumentById(request.get("documentId").toString(),
					request.get(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD).toString());
			results.put("success", deleteDocumentById);
			results.put(
					"message",
					deleteDocumentById ? "Document : " + request.get("documentId") + " was removed" : "Failed to removev document : "
							+ request.get("documentId"));
		}
		else
		{
			results.put("success", false);
			results.put("message", "Request does not contain required fields documentId and codesystem");
		}
		return results;
	}
}