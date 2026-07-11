package com.MageLab.OpenBook.service.source;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import com.MageLab.OpenBook.model.BookSource;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

abstract class BookSourceSupport {

	private static final List<String> COVER_TONES = List.of("moss", "wine", "navy", "slate", "clay", "olive");
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(REQUEST_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	protected String get(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.header("User-Agent", "OpenBook/0.1")
				.GET()
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalStateException("Fonte respondeu com status " + response.statusCode());
		}

		return response.body();
	}

	protected String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	protected Long sourceId(String source, String key) {
		long hash = Math.abs((source + ":" + key).hashCode());
		return hash == 0 ? 1L : hash;
	}

	protected String text(JsonNode node, String field) {
		return node.path(field).asText("");
	}

	protected String firstText(JsonNode node, String field) {
		JsonNode values = node.path(field);

		if (values.isArray() && !values.isEmpty()) {
			return values.get(0).asText("");
		}

		return "";
	}

	protected String joinTextArray(JsonNode node, String field, String fallback) {
		JsonNode values = node.path(field);

		if (!values.isArray() || values.isEmpty()) {
			return fallback;
		}

		List<String> texts = new ArrayList<>();
		values.forEach(value -> {
			String text = value.asText("");
			if (!text.isBlank()) {
				texts.add(text);
			}
		});

		return texts.isEmpty() ? fallback : String.join(", ", texts);
	}

	protected String limit(String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return "";
		}

		String cleanValue = value.replaceAll("<[^>]+>", "")
				.replaceAll("\\s+", " ")
				.trim();

		if (cleanValue.length() <= maxLength) {
			return cleanValue;
		}

		return cleanValue.substring(0, maxLength - 3) + "...";
	}

	protected String coverTone(String title) {
		int index = Math.abs(title == null ? 0 : title.length()) % COVER_TONES.size();
		return COVER_TONES.get(index);
	}

	protected Book book(
			String source,
			String key,
			String title,
			String author,
			String summary,
			String subject,
			AccessType accessType,
			String externalUrl,
			String coverUrl
	) {
		return book(source, key, title, author, summary, subject, accessType, externalUrl, coverUrl, "", "");
	}

	protected Book book(
			String source,
			String key,
			String title,
			String author,
			String summary,
			String subject,
			AccessType accessType,
			String externalUrl,
			String coverUrl,
			String publishedDate,
			String rating
	) {
		return book(
				source,
				key,
				title,
				author,
				summary,
				subject,
				accessType,
				externalUrl,
				coverUrl,
				publishedDate,
				rating,
				0
		);
	}

	protected Book book(
			String source,
			String key,
			String title,
			String author,
			String summary,
			String subject,
			AccessType accessType,
			String externalUrl,
			String coverUrl,
			String publishedDate,
			String rating,
			long popularity
	) {
		return new Book(
				sourceId(source, key),
				blankFallback(title, "Titulo desconhecido"),
				blankFallback(author, "Autor desconhecido"),
				blankFallback(summary, "Informacoes basicas encontradas na fonte consultada."),
				blankFallback(subject, "Sem tema informado"),
				accessType,
				source,
				coverTone(title),
				blankFallback(externalUrl, ""),
				blankFallback(coverUrl, ""),
				blankFallback(publishedDate, ""),
				blankFallback(rating, ""),
				List.of(new BookSource(source, blankFallback(externalUrl, ""))),
				Math.max(popularity, 0),
				com.MageLab.OpenBook.model.RatingSummary.fromDisplay(rating)
		);
	}

	protected String blankFallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	protected String https(String url) {
		if (url == null || url.isBlank()) {
			return "";
		}

		return url.toLowerCase(Locale.ROOT).startsWith("http://")
				? "https://" + url.substring("http://".length())
				: url;
	}
}
