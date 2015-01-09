import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.molgenis.data.Entity;
import org.molgenis.data.csv.CsvRepository;
import org.molgenis.data.csv.CsvWriter;
import org.molgenis.data.processor.CellProcessor;

public class RecodeChecker
{
	public RecodeChecker(String[] args) throws IOException, InvalidFormatException
	{
		File file = new File(args[0]);
		CsvRepository inputDataRepository = new CsvRepository(file, Arrays.<CellProcessor> asList(), ';');

		CsvRepository downloadDataRepository = new CsvRepository(new File(args[1]), Arrays.<CellProcessor> asList());

		File outputFile = new File(file.getParent() + "/output" + new Date().getTime() + ".csv");

		CsvWriter csvWriter = new CsvWriter(outputFile, ';');
		csvWriter.writeAttributes(inputDataRepository.getEntityMetaData().getAtomicAttributes());

		Set<String> identifiers = new HashSet<String>();

		for (Entity entity : downloadDataRepository)
		{
			identifiers.add(entity.getString("Identifier"));
		}

		int count = 0;

		for (Entity entity : inputDataRepository)
		{
			if (!identifiers.contains(entity.getString("Identifier")))
			{
				count++;
				System.out.println(entity);
				csvWriter.add(entity);
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