package com.MageLab.OpenBook.service.source;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class OpenLibraryBookSource extends BookSourceSupport implements BookSourceClient {

	private static final String SOURCE = "Open Library";
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenLibraryBookSource.class);

	private final ObjectMapper objectMapper;

	public OpenLibraryBookSource(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public BookSourceResult search(String term, int offset, int limit) {
		List<Book> books = new ArrayList<>();
		int safeLimit = Math.max(limit, 1);
		String url = "https://openlibrary.org/search.json?q=" + encode(term)
				+ "&offset=" + Math.max(offset, 0)
				+ "&limit=" + safeLimit
				+ "&fields=key,title,author_name,first_publish_year,subject,ebook_access,public_scan_b,cover_i,ratings_average,ratings_count,edition_count,readinglog_count,want_to_read_count,already_read_count";

		try {
			JsonNode root = objectMapper.readTree(get(url));
			JsonNode docs = root.path("docs");
			long totalFound = root.path("numFound").asLong(docs.size());

			if (limit <= 0 || !docs.isArray() || docs.isEmpty()) {
				return new BookSourceResult(List.of(), totalFound);
			}

			for (JsonNode item : docs) {
				String key = text(item, "key");
				String title = text(item, "title");
				String year = item.path("first_publish_year").asText("");
				String subject = firstText(item, "subject");
				String summary = year.isBlank()
						? "Resultado encontrado no catalogo da Open Library."
						: "Primeira publicacao registrada em " + year + ".";
				String coverId = item.path("cover_i").asText("");
				String coverUrl = coverId.isBlank() ? "" : "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg";

				books.add(book(
						SOURCE,
						key,
						title,
						joinTextArray(item, "author_name", "Autor desconhecido"),
						summary,
							subject,
							accessType(item),
							key.isBlank() ? "" : "https://openlibrary.org" + key,
							coverUrl,
						year,
						rating(item),
						popularity(item)
				));
			}

			return new BookSourceResult(books, totalFound);
		} catch (Exception exception) {
			LOGGER.warn("Nao foi possivel consultar a fonte {}: {}", SOURCE, exception.getMessage());
			return BookSourceResult.empty();
		}
	}

	@Override
	public String sourceName() {
		return SOURCE;
	}

	private AccessType accessType(JsonNode item) {
		if ("public".equalsIgnoreCase(text(item, "ebook_access")) || item.path("public_scan_b").asBoolean(false)) {
			return AccessType.FREE;
		}

		if ("no_ebook".equalsIgnoreCase(text(item, "ebook_access"))) {
			return AccessType.PAID;
		}

		return AccessType.UNKNOWN;
	}

	private String rating(JsonNode item) {
		if (!item.hasNonNull("ratings_average")) {
			return "";
		}

		double rating = item.path("ratings_average").asDouble(0);
		int ratingsCount = item.path("ratings_count").asInt(0);

		if (rating <= 0) {
			return "";
		}

		if (ratingsCount > 0) {
			return String.format(Locale.ROOT, "%.1f/5 (%d)", rating, ratingsCount);
		}

		return String.format(Locale.ROOT, "%.1f/5", rating);
	}

	private long popularity(JsonNode item) {
		return item.path("edition_count").asLong(0)
				+ item.path("readinglog_count").asLong(0)
				+ item.path("want_to_read_count").asLong(0)
				+ item.path("already_read_count").asLong(0)
				+ item.path("ratings_count").asLong(0);
	}
}
