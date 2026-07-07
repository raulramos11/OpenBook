package com.MageLab.OpenBook.service;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import com.MageLab.OpenBook.repository.BookRepository;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class BookService {

	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

	private final BookRepository bookRepository;

	public BookService(BookRepository bookRepository) {
		this.bookRepository = bookRepository;
	}

	public List<Book> search(String term, String access) {
		String normalizedTerm = normalize(term);
		AccessType selectedAccess = parseAccess(access);

		return bookRepository.findAll().stream()
				.filter(book -> matchesTerm(book, normalizedTerm))
				.filter(book -> matchesAccess(book, selectedAccess))
				.toList();
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
