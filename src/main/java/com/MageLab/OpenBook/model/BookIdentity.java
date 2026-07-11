package com.MageLab.OpenBook.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

public final class BookIdentity {

	private static final String NON_ALPHANUMERIC = "[^\\p{L}\\p{N}]+";
	private static final Pattern TITLE_SEPARATOR = Pattern.compile("\\s+(?:-|/|\\|)\\s+|:");
	private static final Pattern AUTHOR_SEPARATOR = Pattern.compile(";|\\s+(?:and|e)\\s+", Pattern.CASE_INSENSITIVE);

	private BookIdentity() {
	}

	public static String key(String title, String author) {
		String canonicalBook = canonicalTitle(title) + "\u001f" + canonicalAuthor(author);
		return sha256(canonicalBook);
	}

	private static String canonicalTitle(String title) {
		String withoutEditionNotes = title == null ? "" : title
				.replaceAll("\\([^)]*\\)", " ")
				.replaceAll("\\[[^\\]]*\\]", " ");
		return normalize(TITLE_SEPARATOR.split(withoutEditionNotes, 2)[0]);
	}

	private static String canonicalAuthor(String author) {
		String firstAuthor = author == null ? "" : AUTHOR_SEPARATOR.split(author, 2)[0].trim();

		if (firstAuthor.contains(",")) {
			String[] parts = firstAuthor.split(",", 2);
			if (!parts[0].isBlank() && !parts[1].isBlank()) {
				firstAuthor = parts[1].trim() + " " + parts[0].trim();
			}
		}

		return normalize(firstAuthor);
	}

	private static String normalize(String value) {
		if (value == null) {
			return "";
		}

		return Normalizer.normalize(value, Normalizer.Form.NFKD)
				.replaceAll("\\p{M}+", "")
				.toLowerCase(Locale.ROOT)
				.replaceAll(NON_ALPHANUMERIC, " ")
				.trim()
				.replaceAll("\\s+", " ");
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 nao esta disponivel nesta JVM.", exception);
		}
	}
}
