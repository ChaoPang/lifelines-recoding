package org.molgenis.coding.util;

import java.util.regex.Pattern;

public class MapperTypeSanitizer
{
	private static final String INVALID_CHARS = "^[_]|[#,\\.]";
	private static final Pattern PATTERN = Pattern.compile(INVALID_CHARS);
	private static final String REPLACEMENT_STRING = "";

	public static String sanitizeMapperType(String documentTypeName)
	{
		return documentTypeName != null ? PATTERN.matcher(documentTypeName).replaceAll(REPLACEMENT_STRING) : null;
	}
}