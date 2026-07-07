package com.MageLab.OpenBook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import com.MageLab.OpenBook.repository.BookRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookServiceTests {

	private final BookService bookService = new BookService(new BookRepository());

	@Test
	void searchIgnoresAccents() {
		List<Book> results = bookService.search("memorias", "ALL");

		assertTrue(results.stream().anyMatch(book -> book.title().equals("Memorias Postumas de Bras Cubas")));
	}

	@Test
	void searchCanFilterFreeBooks() {
		List<Book> results = bookService.search("", "FREE");

		assertTrue(results.stream().allMatch(book -> book.accessType() == AccessType.FREE));
		assertEquals(3, results.size());
	}
}
