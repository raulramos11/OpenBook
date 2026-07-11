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
@Order(4)
public class InternetArchiveBookSource extends BookSourceSupport implements BookSourceClient {

	private static final String SOURCE = "Internet Archive";
	private static final int SEARCH_PAGE_SIZE = 50;
	private static final Logger LOGGER = LoggerFactory.getLogger(InternetArchiveBookSource.class);

	private final ObjectMapper objectMapper;

	public InternetArchiveBookSource(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public BookSourceResult search(String term, int offset, int limit) {
		int safeOffset = Math.max(offset, 0);
		int safeLimit = Math.max(limit, 0);

		try {
			if (limit <= 0) {
				long totalFound = totalFound(term);
				return new BookSourceResult(List.of(), totalFound);
			}

			List<Book> books = new ArrayList<>();
			long totalFound = 0;
			int apiPage = (safeOffset / SEARCH_PAGE_SIZE) + 1;
			int skipInPage = safeOffset % SEARCH_PAGE_SIZE;

			while (books.size() < safeLimit) {
				JsonNode root = objectMapper.readTree(get(searchUrl(term, apiPage, SEARCH_PAGE_SIZE)));
				JsonNode response = root.path("response");
				JsonNode docs = response.path("docs");
				totalFound = response.path("numFound").asLong(totalFound);

				if (!docs.isArray() || docs.isEmpty()) {
					break;
				}

				for (JsonNode item : docs) {
					if (skipInPage > 0) {
						skipInPage--;
						continue;
					}

					if (books.size() >= safeLimit) {
						break;
					}

					String identifier = text(item, "identifier");
					String itemUrl = identifier.isBlank() ? "" : "https://archive.org/details/" + identifier;
					long downloadCount = item.path("downloads").asLong(0);
					String downloads = downloadCount > 0 ? String.valueOf(downloadCount) : "";
					String summary = downloads.isBlank()
							? "Texto encontrado no acervo do Internet Archive."
							: "Texto encontrado no acervo do Internet Archive. Downloads registrados: " + downloads + ".";
					String publishedDate = text(item, "date");

					books.add(book(
							SOURCE,
							identifier,
							text(item, "title"),
							creator(item),
							limit(text(item, "description").isBlank() ? summary : text(item, "description"), 1800),
							subject(item),
							AccessType.FREE,
							itemUrl,
							identifier.isBlank() ? "" : "https://archive.org/services/img/" + identifier,
							publishedDate,
							"",
							downloadCount
					));
				}

				apiPage++;
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

	private long totalFound(String term) throws Exception {
		JsonNode root = objectMapper.readTree(get(searchUrl(term, 1, 0)));
		return root.path("response").path("numFound").asLong(0);
	}

	private String searchUrl(String term, int page, int rows) {
		return "https://archive.org/advancedsearch.php?q="
				+ encode("(" + term + ") AND mediatype:(texts) AND -access-restricted-item:(true)")
				+ "&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=description&fl[]=subject&fl[]=downloads&fl[]=date"
				+ "&rows=" + rows
				+ "&page=" + page
				+ "&output=json";
	}

	private String creator(JsonNode item) {
		JsonNode creator = item.path("creator");

		if (creator.isArray()) {
			List<String> creators = new ArrayList<>();
			creator.forEach(value -> {
				String text = value.asText("");
				if (!text.isBlank()) {
					creators.add(text);
				}
			});
			return creators.isEmpty() ? "Autor desconhecido" : String.join(", ", creators);
		}

		return blankFallback(creator.asText(""), "Autor desconhecido");
	}

	private String subject(JsonNode item) {
		JsonNode subject = item.path("subject");

		if (subject.isArray()) {
			List<String> subjects = new ArrayList<>();
			subject.forEach(value -> {
				String text = value.asText("");
				if (!text.isBlank()) {
					subjects.add(text);
				}
			});
			return subjects.isEmpty() ? "Texto digital" : String.join(", ", subjects);
		}

		return blankFallback(subject.asText(""), "Texto digital");
	}
}
