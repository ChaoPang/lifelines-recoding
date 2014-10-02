package org.molgenis.coding.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.HashMap;
import java.util.Map;

import org.molgenis.coding.backup.BackupCodesInState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/backup")
public class ViewBackupController
{

	// private final SearchService elasticSearchImp;
	private final BackupCodesInState backupCodesInState;
	private final static String VIEW_NAME = "view-all-backup";

	@Autowired
	public ViewBackupController(BackupCodesInState backupCodesInState)
	{
		if (backupCodesInState == null) throw new IllegalArgumentException("BackupCodesInState is null");
		this.backupCodesInState = backupCodesInState;
	}

	@RequestMapping(method = RequestMethod.GET)
	public String defaultView(Model model)
	{
		model.addAttribute("viewId", VIEW_NAME);
		model.addAttribute("backupExists", backupCodesInState.backupExisits().size() > 0);
		return VIEW_NAME;

	}

	@RequestMapping(value = "/list", method = RequestMethod.GET, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> backup()
	{
		Map<String, Object> results = new HashMap<String, Object>();
		results.put("results", backupCodesInState.backupExisits());
		return results;
	}
}
