package org.molgenis.coding.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.queryparser.classic.ParseException;
import org.molgenis.coding.elasticsearch.ElasticSearchImp;
import org.molgenis.coding.elasticsearch.Hit;
import org.molgenis.coding.elasticsearch.SearchService;
import org.molgenis.coding.ngram.NGramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/")
public class FindCodesController
{
	private final static String VIEW_NAME = "view-find-codes";
	private final SearchService elasticSearchImp;
	private final NGramService nGramService;

	@Autowired
	public FindCodesController(SearchService elasticSearchImp, NGramService ngramService)
	{
		if (elasticSearchImp == null) throw new IllegalArgumentException("ElasticSearch is null");
		if (ngramService == null) throw new IllegalArgumentException("NGramService is null");
		this.elasticSearchImp = elasticSearchImp;
		this.nGramService = ngramService;
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
			String queryString = request.get("query").toString();
			String originalQueryString = (request.containsKey("originalQuery") && request.get("originalQuery") != null) ? request
					.get("originalQuery").toString() : queryString;
			String codeSystem = (request.containsKey("codeSystem") && request.get("codeSystem") != null) ? request.get(
					"codeSystem").toString() : null;
			List<Hit> searchResults = elasticSearchImp.search(codeSystem, queryString,
					field == null ? null : field.toString());
			nGramService.calculateNGramSimilarity(originalQueryString, ElasticSearchImp.DEFAULT_NAME_FIELD,
					searchResults);
			results.put("results", searchResults);
		}
		return results;
	}

	@RequestMapping(value = "/add", method = RequestMethod.POST, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> addDoc(@RequestBody
	Map<String, Object> request) throws IOException, ParseException
	{
		Map<String, Object> results = new HashMap<String, Object>();

		if (request.containsKey("data") && request.get("data") != null && request.containsKey("query")
				&& request.get("query") != null)
		{
			Map<String, Object> data = ViewRecodeController.convertData(request.get("data"));
			String query = request.get("query").toString();
			results.put("data", data);
			for (String validField : Arrays.asList("name", "code", "codesystem"))
			{
				if (!data.containsKey(validField))
				{
					results.put("success", false);
					results.put("message", "data request does not contain valid field " + validField);
					return results;
				}
			}
			String documentType = data.containsKey(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD)
					&& data.get(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD) != null ? data.get(
					ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD).toString() : null;

			if (documentType == null)
			{
				results.put("success", false);
				results.put("message", "code system is invalid");
				return results;
			}

			elasticSearchImp.indexDocument(
					ElasticSearchImp.addDefaultFields(ViewRecodeController.translateDataToMap(data, query)),
					documentType);
			results.put("success", true);
			results.put("message", "new term " + data.get("name") + " has been added to the code " + data.get("code"));
		}

		return results;
	}

	@RequestMapping(value = "/update/{documentId}", method = RequestMethod.POST, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> updateDoc(@PathVariable("documentId")
	String documentId) throws IOException, ParseException
	{
		Map<String, Object> results = new HashMap<String, Object>();
		Hit hit = elasticSearchImp.getDocumentById(documentId);
		StringBuilder updateScriptStringBuilder = new StringBuilder();
		updateScriptStringBuilder.append("frequency").append('=').append((hit.getFrequency() + 1));
		elasticSearchImp.updateIndex(hit.getDocumentId(), updateScriptStringBuilder.toString());
		results.put("message", "The code "
				+ hit.getColumnValueMap().get(ElasticSearchImp.DEFAULT_CODE_FIELD).toString() + ", name "
				+ hit.getColumnValueMap().get(ElasticSearchImp.DEFAULT_NAME_FIELD) + " has been updated!");
		return results;
	}

	@RequestMapping(value = "/validate", method = RequestMethod.POST, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> validateDoc(@RequestParam(required = true)
	String query, @RequestParam(required = true)
	String name) throws IOException, ParseException
	{
		Map<String, Object> results = new HashMap<String, Object>();

		if (query != null && name != null)
		{
			Float score = nGramService.ngramSimilarity(query, name);
			if (score.intValue() >= 80 && score.intValue() <= 100)
			{
				results.put("success", true);
				results.put("message", "The input query <u>" + query
						+ "</u> is very similar to the selected code. Do you agree to the suggested code?");
			}
			else
			{
				results.put("success", false);
				results.put("message", "The query <u>" + query
						+ "</u> is not very similar to the selected code. Would you like to add <u>" + query
						+ "</u> to code group?");
			}
		}
		else
		{
			results.put("alert", "error");
			results.put("message", "Failed to update the code dataset!");
		}

		return results;
	}
}