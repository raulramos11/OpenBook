package com.MageLab.OpenBook.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public record Book(
		Long id,
		String title,
		String author,
		String summary,
		String subject,
		AccessType accessType,
		String source,
		String coverTone,
		String externalUrl,
		String coverUrl,
		String publishedDate,
		String rating,
		List<BookSource> sources,
		long popularity,
		RatingSummary ratings
) {
	public Book(
			Long id,
			String title,
			String author,
			String summary,
			String subject,
			AccessType accessType,
			String source,
			String coverTone,
			String externalUrl,
			String coverUrl
	) {
		this(
				id,
				title,
				author,
				summary,
				subject,
				accessType,
				source,
				coverTone,
				externalUrl,
				coverUrl,
				"",
				"",
				sourceList(source, externalUrl)
		);
	}

	public Book(
			Long id,
			String title,
			String author,
			String summary,
			String subject,
			AccessType accessType,
			String source,
			String coverTone,
			String externalUrl,
			String coverUrl,
			String publishedDate,
			String rating,
			List<BookSource> sources
	) {
		this(
				id,
				title,
				author,
				summary,
				subject,
				accessType,
				source,
				coverTone,
				externalUrl,
				coverUrl,
				publishedDate,
				rating,
				sources,
				RatingSummary.fromDisplay(rating).externalCount(),
				RatingSummary.fromDisplay(rating)
		);
	}

	public Book {
		title = blankFallback(title, "Titulo desconhecido");
		author = blankFallback(author, "Autor desconhecido");
		summary = blankFallback(summary, "Informacoes basicas encontradas na fonte consultada.");
		subject = blankFallback(subject, "Sem tema informado");
		accessType = accessType == null ? AccessType.UNKNOWN : accessType;
		source = blankFallback(source, "Fonte nao informada");
		coverTone = blankFallback(coverTone, "moss");
		externalUrl = blankFallback(externalUrl, "");
		coverUrl = blankFallback(coverUrl, "");
		publishedDate = blankFallback(publishedDate, "");
		rating = blankFallback(rating, "");
		sources = normalizeSources(sources, source, externalUrl);
		popularity = Math.max(popularity, 0);
		ratings = ratings == null ? RatingSummary.fromDisplay(rating) : ratings;
	}

	@JsonProperty("accessLabel")
	public String accessLabel() {
		return accessType.getLabel();
	}

	@JsonProperty("sourceCount")
	public int sourceCount() {
		return sources.size();
	}

	@JsonProperty("sourceNames")
	public List<String> sourceNames() {
		return sources.stream()
				.map(BookSource::name)
				.toList();
	}

	@JsonProperty("ratingKey")
	public String ratingKey() {
		return BookIdentity.key(title, author);
	}

	public Book withCommunityRating(CommunityRating communityRating) {
		return new Book(
				id,
				title,
				author,
				summary,
				subject,
				accessType,
				source,
				coverTone,
				externalUrl,
				coverUrl,
				publishedDate,
				rating,
				sources,
				popularity,
				ratings.withCommunity(communityRating)
		);
	}

	private static List<BookSource> sourceList(String source, String externalUrl) {
		return List.of(new BookSource(blankFallback(source, "Fonte nao informada"), blankFallback(externalUrl, "")));
	}

	private static List<BookSource> normalizeSources(List<BookSource> sources, String fallbackSource, String fallbackUrl) {
		List<BookSource> normalizedSources = new ArrayList<>();

		if (sources != null) {
			sources.stream()
					.filter(source -> source != null)
					.forEach(source -> addSource(normalizedSources, source.name(), source.url()));
		}

		if (normalizedSources.isEmpty()) {
			addSource(normalizedSources, fallbackSource, fallbackUrl);
		}

		return List.copyOf(normalizedSources);
	}

	private static void addSource(List<BookSource> sources, String name, String url) {
		String safeName = blankFallback(name, "Fonte nao informada");
		String safeUrl = blankFallback(url, "");
		boolean exists = sources.stream()
				.anyMatch(source -> source.name().equalsIgnoreCase(safeName) && source.url().equalsIgnoreCase(safeUrl));

		if (!exists) {
			sources.add(new BookSource(safeName, safeUrl));
		}
	}

	private static String blankFallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}
}
