package org.molgenis.coding.controller;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.molgenis.coding.elasticsearch.ElasticSearchImp;
import org.molgenis.coding.elasticsearch.SearchService;
import org.molgenis.data.AttributeMetaData;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityMetaData;
import org.molgenis.data.Repository;
import org.molgenis.data.excel.ExcelRepository;
import org.molgenis.data.excel.ExcelRepositoryCollection;
import org.molgenis.data.processor.LowerCaseProcessor;
import org.molgenis.data.processor.TrimProcessor;
import org.molgenis.data.support.MapEntity;
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
	private final static List<String> ALLOWED_COLUMNS = Arrays.asList("name", "code", "codesystem");
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
	public String uploadFileHandler(@RequestParam("file") MultipartFile file, Model model)
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

				if (collection.getNumberOfSheets() > 0)
				{
					final ExcelRepository sheet = collection.getSheet(0);
					Iterator<Entity> iterator = sheet.iterator();
					if (iterator.hasNext())
					{
						Entity firstEntity = iterator.next();
						final String documentType = firstEntity.getString(ElasticSearchImp.DEFAULT_CODESYSTEM_FIELD);
						final Date indexDataTime = new Date();
						Repository repository = new Repository()
						{
							@Override
							public String getName()
							{
								return documentType;
							}

							@Override
							public EntityMetaData getEntityMetaData()
							{
								return sheet.getEntityMetaData();
							}

							@Override
							public Iterator<Entity> iterator()
							{
								final Iterator<Entity> iterator = sheet.iterator();
								return new Iterator<Entity>()
								{
									@Override
									public boolean hasNext()
									{
										return iterator.hasNext();
									}

									@Override
									public Entity next()
									{
										Entity entity = new MapEntity(iterator.next());
										if (entity.get(ElasticSearchImp.DEFAULT_FREQUENCY_FIELD) == null) entity.set(
												ElasticSearchImp.DEFAULT_FREQUENCY_FIELD, Integer.valueOf(1));
										if (entity.get(ElasticSearchImp.DEFAULT_ORIGINAL_FIELD) == null) entity.set(
												ElasticSearchImp.DEFAULT_ORIGINAL_FIELD, true);
										if (entity.get(ElasticSearchImp.DEFAULT_DATE_FIELD) == null) entity.set(
												ElasticSearchImp.DEFAULT_DATE_FIELD, indexDataTime);
										return entity;
									}

									@Override
									public void remove()
									{
										throw new UnsupportedOperationException();
									}
								};
							}

							@Override
							public <E extends Entity> Iterable<E> iterator(Class<E> clazz)
							{
								return null;
							}

							@Override
							public String getUrl()
							{
								return null;
							}

							@Override
							public void close() throws IOException
							{
								// Nothing
							}

						};

						if (validateExcelColumnHeaders(repository))
						{
							elasticSearchImp.indexCodeSystem(firstEntity);
							elasticSearchImp.indexRepository(repository);
						}
						else
						{
							model.addAttribute("message", "The columns are invalid. Please look at the example!");
						}
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
				model.addAttribute("message", e.getMessage());
			}
		}
		return defaultView(model);
	}

	private boolean validateExcelColumnHeaders(Repository sheet)
	{
		int count = 0;
		for (AttributeMetaData attribute : sheet.getEntityMetaData().getAttributes())
		{
			if (!ALLOWED_COLUMNS.contains(attribute.getName().toLowerCase()))
			{
				return false;
			}
			count++;
		}
		return count == ALLOWED_COLUMNS.size();
	}
}