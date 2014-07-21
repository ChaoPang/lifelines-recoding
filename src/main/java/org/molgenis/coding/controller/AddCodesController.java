package org.molgenis.coding.controller;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.molgenis.coding.elasticsearch.SearchService;
import org.molgenis.data.excel.ExcelRepositoryCollection;
import org.molgenis.data.processor.LowerCaseProcessor;
import org.molgenis.data.processor.TrimProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/add")
public class AddCodesController
{
	private final SearchService elasticSearchImp;
	private final static String VIEW_NAME = "view-upload-codes";
	private final static Logger logger = Logger.getLogger(AddCodesController.class);

	@Autowired
	public AddCodesController(SearchService elasticSearchImp)
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

	@RequestMapping(value = "/upload", method = RequestMethod.POST, headers = "Content-Type=multipart/form-data")
	public String uploadFileHandler(@RequestParam("file")
	MultipartFile file, Model model) throws InvalidFormatException
	{
		if (!file.isEmpty())
		{
			byte[] bytes;
			try
			{
				bytes = file.getBytes();
				String rootPath = System.getProperty("java.io.tmpdir");
				File dir = new File(rootPath + File.separator + "tmpFiles");
				if (!dir.exists()) dir.mkdirs();
				// Create the file on server
				File serverFile = new File(dir.getAbsolutePath() + File.separator + file.getName() + ".xls");
				BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(serverFile));
				stream.write(bytes);
				stream.close();
				logger.info("The uploaded file path is : " + serverFile.getAbsolutePath());
				ExcelRepositoryCollection collection = new ExcelRepositoryCollection(new File(
						serverFile.getAbsolutePath()), new LowerCaseProcessor(), new TrimProcessor());
				if (collection.getNumberOfSheets() > 0) elasticSearchImp.indexRepository(collection.getSheet(0));
			}
			catch (IOException e)
			{
				model.addAttribute("message", e.getMessage());
			}
		}
		return defaultView(model);
	}
}