package org.molgenis.coding.util;

import org.tartarus.snowball.ext.DutchStemmer;

public class DutchStemmerTest
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		String test1 = "mountainbiking";
		String test2 = "mountainbike";
		String test3 = "mountainbiken";

		System.out.println("1: Test string is " + test1 + "; stemmed string is " + stem(test1));
		System.out.println("2: Test string is " + test2 + "; stemmed string is " + stem(test2));
		System.out.println("3: Test string is " + test3 + "; stemmed string is " + stem(test3));
	}

	public static String stem(String word)
	{
		DutchStemmer dutchStemmer = new DutchStemmer();
		dutchStemmer.setCurrent(word);
		dutchStemmer.stem();
		return dutchStemmer.getCurrent();
	}
}
