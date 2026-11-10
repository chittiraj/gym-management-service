package com.gym.management.repository;

import java.util.Locale;

/** LIKE pattern helper. User input is escaped so % and _ match literally. */
public final class LikePatterns {

	/** Escape character. '!' avoids backslash handling differences between drivers. */
	public static final char ESCAPE = '!';

	private LikePatterns() {
	}

	/** Lower-cased '%term%' pattern with wildcards in the term escaped. */
	public static String contains(String term) {
		String escaped = term.toLowerCase(Locale.ROOT)
				.replace(String.valueOf(ESCAPE), "" + ESCAPE + ESCAPE)
				.replace("%", ESCAPE + "%")
				.replace("_", ESCAPE + "_");
		return "%" + escaped + "%";
	}

}
