package org.molgenis.coding.ngram;

import java.util.Collections;
import java.util.List;

import org.molgenis.coding.elasticsearch.Hit;
import org.molgenis.coding.util.DutchNGramAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;

public class NGramService
{
	private final DutchNGramAlgorithm dutchNGramAlgorithm;
	private final static String REGEX = "[\\W]";
	private final static String REPLACEMENT = " ";

	@Autowired
	public NGramService(DutchNGramAlgorithm dutchNGramAlgorithm)
	{
		if (dutchNGramAlgorithm == null) throw new IllegalArgumentException("DutchNGramAlgorithm is null!");
		this.dutchNGramAlgorithm = dutchNGramAlgorithm;
	}

	public void calculateNGramSimilarity(String queryString, String field, List<Hit> searchResults)
	{
		for (Hit hit : searchResults)
		{
			if (hit.getColumnValueMap().containsKey(field))
			{
				hit.setScore(dutchNGramAlgorithm.stringMatching(replace(queryString), replace(hit.getColumnValueMap()
						.get(field).toString())));
			}
			else
			{
				hit.setScore((float) 0);
			}
		}
		Collections.sort(searchResults);
	}

	private String replace(String word)
	{
		if (word != null) return word.replaceAll(REGEX, REPLACEMENT);
		return word;
	}
}
