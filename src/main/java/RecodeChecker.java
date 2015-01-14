import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.molgenis.data.Entity;
import org.molgenis.data.csv.CsvRepository;
import org.molgenis.data.csv.CsvWriter;
import org.molgenis.data.processor.CellProcessor;
import org.molgenis.data.support.MapEntity;

import com.google.common.collect.Iterables;

public class RecodeChecker
{
	private final static List<String> COLUMN_HEADERS = Arrays.asList("Name_1", "Name_2", "Name_3", "Name_4");

	public RecodeChecker(String[] args) throws IOException, InvalidFormatException
	{
		File file = new File(args[0]);
		CsvRepository inputDataRepository = new CsvRepository(file, Arrays.<CellProcessor> asList(), ';');

		CsvRepository downloadDataRepository = new CsvRepository(new File(args[1]), Arrays.<CellProcessor> asList());

		File outputFile = new File(file.getParent() + "/output" + new Date().getTime() + ".csv");

		CsvWriter csvWriter = new CsvWriter(outputFile, ';');
		csvWriter.writeAttributes(inputDataRepository.getEntityMetaData().getAtomicAttributes());

		Map<String, Map<Integer, String>> downloadDataSet = new HashMap<String, Map<Integer, String>>();
		for (Entity entity : downloadDataRepository)
		{
			String identifier = entity.getString("Identifier");
			downloadDataSet.put(identifier, new HashMap<Integer, String>());
			for (String columnHeader : COLUMN_HEADERS)
			{
				if (!StringUtils.isEmpty(entity.getString(columnHeader)))
				{
					downloadDataSet.get(identifier).put(COLUMN_HEADERS.indexOf(columnHeader),
							entity.getString(columnHeader));
				}
			}
		}

		int count = 0;
		for (Entity entity : inputDataRepository)
		{
			String identifier = entity.getString("Identifier");
			if (!downloadDataSet.containsKey(identifier))
			{
				count++;
				System.out.println(entity);
				// csvWriter.add(entity);
			}
			else
			{
				Entity outputEntity = new MapEntity();
				for (String columnHeader : COLUMN_HEADERS)
				{
					if (!StringUtils.isEmpty(entity.getString(columnHeader)))
					{
						if (!downloadDataSet.get(identifier).containsKey(COLUMN_HEADERS.indexOf(columnHeader)))
						{
							if (StringUtils.isEmpty(outputEntity.getString("Identifier"))) outputEntity.set(
									"Identifier", identifier);
							outputEntity.set(columnHeader, entity.getString(columnHeader));
						}
					}
				}
				if (Iterables.size(outputEntity.getAttributeNames()) != 0) csvWriter.add(outputEntity);
			}
		}

		System.out.println(count);

		inputDataRepository.close();
		downloadDataRepository.close();

		csvWriter.close();
	}

	public static void main(String[] args) throws IOException, InvalidFormatException
	{
		if (args.length == 2)
		{
			new RecodeChecker(args);
		}
	}
}