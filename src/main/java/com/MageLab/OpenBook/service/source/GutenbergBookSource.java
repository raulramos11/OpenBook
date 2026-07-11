package com.MageLab.OpenBook.service.source;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class GutenbergBookSource extends BookSourceSupport implements BookSourceClient {

	private static final String SOURCE = "Project Gutenberg";
	private static final Logger LOGGER = LoggerFactory.getLogger(GutenbergBookSource.class);

	private final ObjectMapper objectMapper;

	public GutenbergBookSource(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public BookSourceResult search(String term, int offset, int limit) {
		String url = "https://gutendex.com/books/?search=" + encode(term);
		List<Book> books = new ArrayList<>();
		long totalItems = 0;
		int skipped = 0;
		int safeOffset = Math.max(offset, 0);

		while (!url.isBlank()) {
			try {
				JsonNode root = objectMapper.readTree(get(url));
				JsonNode results = root.path("results");
				totalItems = root.path("count").asLong(totalItems);

				if (limit <= 0 || !results.isArray() || results.isEmpty()) {
					break;
				}

				for (JsonNode item : results) {
					if (skipped < safeOffset) {
						skipped++;
						continue;
					}

					if (books.size() >= limit) {
						break;
					}

					String id = item.path("id").asText("");
					String summary = firstText(item, "summaries");
					String htmlUrl = item.path("formats").path("text/html").asText("");

					books.add(book(
							SOURCE,
							id,
							text(item, "title"),
							authors(item),
							limit(summary, 1800),
							firstText(item, "subjects"),
							item.path("copyright").asBoolean(true) ? AccessType.UNKNOWN : AccessType.FREE,
							htmlUrl.isBlank() ? "https://www.gutenberg.org/ebooks/" + id : htmlUrl,
							item.path("formats").path("image/jpeg").asText(""),
							"",
							"",
							item.path("download_count").asLong(0)
					));
				}

				if (books.size() >= limit) {
					break;
				}

				url = root.path("next").asText("");
			} catch (Exception exception) {
				LOGGER.warn("Consulta da fonte {} interrompida: {}", SOURCE, exception.getMessage());
				break;
			}
		}

		return new BookSourceResult(books, totalItems);
	}

	@Override
	public String sourceName() {
		return SOURCE;
	}

	private String authors(JsonNode item) {
		JsonNode authors = item.path("authors");

		if (!authors.isArray() || authors.isEmpty()) {
			return "Autor desconhecido";
		}

		List<String> names = new ArrayList<>();
		authors.forEach(author -> {
			String name = text(author, "name");
			if (!name.isBlank()) {
				names.add(name);
			}
		});

		return names.isEmpty() ? "Autor desconhecido" : String.join(", ", names);
	}
}
