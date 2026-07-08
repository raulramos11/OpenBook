package com.MageLab.OpenBook.service.source;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class GoogleBooksSource extends BookSourceSupport implements BookSourceClient {

	private static final String SOURCE = "Google Books";
	private static final int PAGE_SIZE = 40;
	private static final Logger LOGGER = LoggerFactory.getLogger(GoogleBooksSource.class);

	private final ObjectMapper objectMapper;
	private final String apiKey;

	public GoogleBooksSource(ObjectMapper objectMapper, @Value("${openbook.google-books.api-key:}") String apiKey) {
		this.objectMapper = objectMapper;
		this.apiKey = apiKey;
	}

	@Override
	public BookSourceResult search(String term, int offset, int limit) {
		if (apiKey.isBlank()) {
			return BookSourceResult.empty();
		}

		List<Book> books = new ArrayList<>();
		int startIndex = Math.max(offset, 0);
		int totalItems = Integer.MAX_VALUE;

		if (limit <= 0) {
			String url = "https://www.googleapis.com/books/v1/volumes?q=" + encode(term)
					+ "&startIndex=0"
					+ "&maxResults=1"
					+ "&printType=books";

			url += "&key=" + encode(apiKey);

			try {
				JsonNode root = objectMapper.readTree(get(url));

				if (root.has("error")) {
					LOGGER.warn("Nao foi possivel consultar a fonte {}: {}", SOURCE, root.path("error").path("message").asText());
					return BookSourceResult.empty();
				}

				return new BookSourceResult(List.of(), root.path("totalItems").asLong(0));
			} catch (Exception exception) {
				LOGGER.warn("Consulta da fonte {} interrompida no indice 0: {}", SOURCE, exception.getMessage());
				return BookSourceResult.empty();
			}
		}

		while (books.size() < limit && startIndex < totalItems) {
			int requestSize = Math.max(1, Math.min(PAGE_SIZE, limit - books.size()));
			String url = "https://www.googleapis.com/books/v1/volumes?q=" + encode(term)
					+ "&startIndex=" + startIndex
					+ "&maxResults=" + requestSize
					+ "&printType=books";

			url += "&key=" + encode(apiKey);

			try {
				JsonNode root = objectMapper.readTree(get(url));

				if (root.has("error")) {
					LOGGER.warn("Nao foi possivel consultar a fonte {}: {}", SOURCE, root.path("error").path("message").asText());
					return new BookSourceResult(books, books.size());
				}

				JsonNode items = root.path("items");

				if (totalItems == Integer.MAX_VALUE) {
					totalItems = root.path("totalItems").asInt(items.size());
				}

				if (limit <= 0 || !items.isArray() || items.isEmpty()) {
					break;
				}

				for (JsonNode item : items) {
					JsonNode volumeInfo = item.path("volumeInfo");
					JsonNode saleInfo = item.path("saleInfo");
					JsonNode accessInfo = item.path("accessInfo");

					String id = text(item, "id");
					String description = limit(text(volumeInfo, "description"), 180);
					String summary = description.isBlank() ? googleSummary(saleInfo, accessInfo) : description;

					books.add(book(
							SOURCE,
							id,
							text(volumeInfo, "title"),
							joinTextArray(volumeInfo, "authors", "Autor desconhecido"),
							summary,
							firstText(volumeInfo, "categories"),
							accessType(saleInfo, accessInfo),
							text(volumeInfo, "infoLink"),
							https(volumeInfo.path("imageLinks").path("thumbnail").asText(""))
					));
				}

				startIndex += items.size();
			} catch (Exception exception) {
				LOGGER.warn("Consulta da fonte {} interrompida no indice {}: {}", SOURCE, startIndex, exception.getMessage());
				break;
			}
		}

		long total = totalItems == Integer.MAX_VALUE ? books.size() : totalItems;
		return new BookSourceResult(books, total);
	}

	@Override
	public String sourceName() {
		return SOURCE;
	}

	@Override
	public boolean isEnabled() {
		return !apiKey.isBlank();
	}

	private AccessType accessType(JsonNode saleInfo, JsonNode accessInfo) {
		String saleability = text(saleInfo, "saleability");
		String viewability = text(accessInfo, "viewability");

		if ("FREE".equalsIgnoreCase(saleability) || "ALL_PAGES".equalsIgnoreCase(viewability)) {
			return AccessType.FREE;
		}

		if ("FOR_SALE".equalsIgnoreCase(saleability)) {
			return AccessType.PAID;
		}

		return AccessType.UNKNOWN;
	}

	private String googleSummary(JsonNode saleInfo, JsonNode accessInfo) {
		String saleability = text(saleInfo, "saleability");
		String viewability = text(accessInfo, "viewability");

		if ("FOR_SALE".equalsIgnoreCase(saleability)) {
			return "Resultado encontrado com indicacao de compra no Google Books.";
		}

		if ("PARTIAL".equalsIgnoreCase(viewability)) {
			return "Resultado encontrado com pre-visualizacao parcial no Google Books.";
		}

		return "Resultado encontrado no catalogo do Google Books.";
	}
}
