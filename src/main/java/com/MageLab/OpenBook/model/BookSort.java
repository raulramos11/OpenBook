package com.MageLab.OpenBook.model;

import java.util.Locale;

public enum BookSort {
	RELEVANCE,
	POPULAR,
	RATING,
	NEWEST,
	OLDEST,
	TITLE;

	public static BookSort from(String value) {
		if (value == null || value.isBlank()) {
			return RELEVANCE;
		}

		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return RELEVANCE;
		}
	}
}
