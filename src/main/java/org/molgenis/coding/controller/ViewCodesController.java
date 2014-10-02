package org.molgenis.coding.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.search.sort.SortOrder;
import org.molgenis.coding.elasticsearch.CodingState;
import org.molgenis.coding.elasticsearch.ElasticSearchImp;
import org.molgenis.coding.elasticsearch.Hit;
import org.molgenis.coding.elasticsearch.SearchService;
import org.molgenis.coding.ngram.NGramService;
import org.molgenis.coding.util.RecodeResponse;
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
	private final NGramService nGramService;
	private final CodingState codingState;

	@Autowired
	public ViewCodesController(SearchService elasticSearchImp, NGramService nGramService, CodingState codingState)
	{
		if (elasticSearchImp == null) throw new IllegalArgumentException("ElasticSearch is null");
		if (nGramService == null) throw new IllegalArgumentException("NGramService is null");
		if (codingState == null) throw new IllegalArgumentException("CodingState is null");
		this.elasticSearchImp = elasticSearchImp;
		this.codingState = codingState;
		this.nGramService = nGramService;
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
		if (!StringUtils.isEmpty(codeSystem))
		{
			results.put("results", elasticSearchImp.search(codeSystem, null, null, ElasticSearchImp.DEFAULT_DATE_FIELD,
					SortOrder.DESC));
		}
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
			String documentId = request.get("documentId").toString();
			String documentType = request.get(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD).toString();
			Hit hitToRemove = elasticSearchImp.getDocumentById(documentId, documentType);

			if (hitToRemove == null
					|| !Boolean.parseBoolean(hitToRemove.getColumnValueMap()
							.get(ElasticSearchImp.DEFAULT_ORIGINAL_FIELD).toString()))
			{
				boolean deleteDocumentById = elasticSearchImp.deleteDocumentById(documentId, documentType);

				// Remove all the coded data that are automatically matched with
				// threshold of 80%
				if (deleteDocumentById)
				{
					for (String activityName : new HashSet<String>(codingState.getMappedActivities().keySet()))
					{
						RecodeResponse recodeResponse = codingState.getMappedActivities().get(activityName);
						// Only remove those who are automatically matched to
						// the code Hit that was just removed!
						if (recodeResponse.getHit().getDocumentId().equals(documentId)
								&& !recodeResponse.isFinalSelection())
						{
							codingState.getMappedActivities().remove(activityName);

							// Reclassification of the current data item
							List<Hit> searchHits = elasticSearchImp.search(codingState.getSelectedCodeSystem(),
									activityName, null);

							nGramService.calculateNGramSimilarity(activityName, "name", searchHits);
							for (Hit hit : searchHits)
							{
								Map<String, RecodeResponse> activities = hit.getScore().intValue() >= codingState
										.getThreshold() ? codingState.getMappedActivities() : codingState
										.getRawActivities();
								if (!activities.containsKey(activityName))
								{
									activities.put(activityName, new RecodeResponse(activityName, hit));
								}

								activities.get(activityName).getIdentifiers().putAll(recodeResponse.getIdentifiers());
								activities.get(activityName).setAddedDate(recodeResponse.getAddedDate());
								break;
							}
						}
					}
				}

				results.put("success", deleteDocumentById);
				results.put(
						"message",
						deleteDocumentById ? "Document : " + request.get("documentId") + " was removed" : "Failed to removev document : "
								+ request.get("documentId"));
			}
			else
			{
				results.put("success", false);
				results.put("message", "Document " + request.get("documentId") + " is original and cannot be removed!");
			}
		}
		else
		{
			results.put("success", false);
			results.put("message", "Request does not contain required fields documentId and codesystem");
		}
		return results;
	}
}