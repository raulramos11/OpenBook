package com.MageLab.OpenBook.service;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import com.MageLab.OpenBook.model.BookSearchPage;
import com.MageLab.OpenBook.repository.BookRepository;
import com.MageLab.OpenBook.service.source.BookSourceClient;
import com.MageLab.OpenBook.service.source.BookSourceResult;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BookService {

	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
	private static final String DEFAULT_SEARCH_TERM = "literatura brasileira";
	private static final int DEFAULT_PAGE_SIZE = 18;
	private static final int MAX_PAGE_SIZE = 60;
	private static final int SOURCE_CHUNK_SIZE = 1000;

	private final BookRepository bookRepository;
	private final List<BookSourceClient> bookSourceClients;

	@Autowired
	public BookService(BookRepository bookRepository, List<BookSourceClient> bookSourceClients) {
		this.bookRepository = bookRepository;
		this.bookSourceClients = bookSourceClients;
	}

	public BookService(BookRepository bookRepository) {
		this(bookRepository, List.of());
	}

	public List<Book> search(String term, String access) {
		return searchPage(term, access, 1, DEFAULT_PAGE_SIZE).books();
	}

	public BookSearchPage searchPage(String term, String access, int page, int size) {
		int safePage = Math.max(page, 1);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		String normalizedTerm = normalize(term);
		AccessType selectedAccess = parseAccess(access);
		BookSearchPage externalPage = searchExternalBooks(term, selectedAccess, safePage, safeSize);

		if (externalPage.totalItems() > 0) {
			return externalPage;
		}

		List<Book> localBooks = bookRepository.findAll().stream()
				.filter(book -> matchesTerm(book, normalizedTerm))
				.filter(book -> matchesAccess(book, selectedAccess))
				.toList();

		return localPage(localBooks, safePage, safeSize);
	}

	private BookSearchPage searchExternalBooks(String term, AccessType selectedAccess, int page, int size) {
		if (bookSourceClients.isEmpty()) {
			return BookSearchPage.of(List.of(), page, size, 0);
		}

		String query = term == null || term.isBlank() ? DEFAULT_SEARCH_TERM : term.trim();

		if (selectedAccess != null) {
			return searchFilteredExternalBooks(query, selectedAccess, page, size);
		}

		Map<String, Book> booksByKey = new LinkedHashMap<>();
		long totalItems = 0;
		int targetStart = (page - 1) * size;
		int targetEnd = targetStart + size;
		long sourceStart = 0;

		for (BookSourceClient bookSourceClient : bookSourceClients) {
			BookSourceResult countResult = bookSourceClient.search(query, 0, 0);
			long sourceTotal = countResult.totalItems();
			long sourceEnd = sourceStart + sourceTotal;
			totalItems += sourceTotal;

			if (targetEnd > sourceStart && targetStart < sourceEnd && booksByKey.size() < size) {
				int sourceOffset = (int) Math.max(0, targetStart - sourceStart);
				int sourceLimit = size - booksByKey.size();
				BookSourceResult pageResult = bookSourceClient.search(query, sourceOffset, sourceLimit);

				pageResult.books().stream()
						.filter(book -> matchesAccess(book, selectedAccess))
						.forEach(book -> booksByKey.putIfAbsent(deduplicationKey(book), book));
			}

			sourceStart = sourceEnd;
		}

		return BookSearchPage.of(booksByKey.values().stream().toList(), page, size, totalItems);
	}

	private BookSearchPage searchFilteredExternalBooks(String query, AccessType selectedAccess, int page, int size) {
		Map<String, Book> booksByKey = new LinkedHashMap<>();

		for (BookSourceClient bookSourceClient : bookSourceClients) {
			BookSourceResult countResult = bookSourceClient.search(query, 0, 0);
			long sourceTotal = countResult.totalItems();
			int offset = 0;

			while (offset < sourceTotal) {
				BookSourceResult chunk = bookSourceClient.search(query, offset, SOURCE_CHUNK_SIZE);

				if (chunk.books().isEmpty()) {
					break;
				}

				chunk.books().stream()
						.filter(book -> matchesAccess(book, selectedAccess))
						.forEach(book -> booksByKey.putIfAbsent(deduplicationKey(book), book));

				offset += chunk.books().size();
			}
		}

		return localPage(booksByKey.values().stream().toList(), page, size);
	}

	private BookSearchPage localPage(List<Book> books, int page, int size) {
		int start = Math.min((page - 1) * size, books.size());
		int end = Math.min(start + size, books.size());

		return BookSearchPage.of(new ArrayList<>(books.subList(start, end)), page, size, books.size());
	}

	private boolean matchesTerm(Book book, String normalizedTerm) {
		if (normalizedTerm.isBlank()) {
			return true;
		}

		String searchableContent = normalize(String.join(" ",
				book.title(),
				book.author(),
				book.subject(),
				book.summary()
		));

		return searchableContent.contains(normalizedTerm);
	}

	private boolean matchesAccess(Book book, AccessType selectedAccess) {
		return selectedAccess == null || book.accessType() == selectedAccess;
	}

	private String deduplicationKey(Book book) {
		return book.source() + "|" + book.externalUrl() + "|" + book.id();
	}

	private AccessType parseAccess(String access) {
		if (access == null || access.isBlank() || "ALL".equalsIgnoreCase(access)) {
			return null;
		}

		try {
			return AccessType.valueOf(access.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private String normalize(String value) {
		if (value == null) {
			return "";
		}

		String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
		return DIACRITICS.matcher(normalized).replaceAll("");
	}
}
