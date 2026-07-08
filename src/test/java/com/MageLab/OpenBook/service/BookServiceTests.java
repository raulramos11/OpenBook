package com.MageLab.OpenBook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import com.MageLab.OpenBook.model.BookSearchPage;
import com.MageLab.OpenBook.repository.BookRepository;
import com.MageLab.OpenBook.service.source.BookSourceClient;
import com.MageLab.OpenBook.service.source.BookSourceResult;
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

	@Test
	void searchUsesExternalSourcesWhenAvailable() {
		Book externalBook = new Book(
				99L,
				"Dom Casmurro",
				"Machado de Assis",
				"Resultado vindo de uma fonte externa.",
				"Literatura brasileira",
				AccessType.FREE,
				"Fonte externa",
				"moss",
				"https://example.com/dom-casmurro",
				"https://example.com/dom-casmurro.jpg"
		);
		BookService service = new BookService(new BookRepository(), List.of((term, offset, limit) -> {
			if (limit <= 0) {
				return new BookSourceResult(List.of(), 1);
			}

			return new BookSourceResult(List.of(externalBook), 1);
		}));

		List<Book> results = service.search("dom casmurro", "FREE");

		assertEquals(List.of(externalBook), results);
	}

	@Test
	void searchPageUsesSelectedPageOnly() {
		Book firstBook = new Book(
				101L,
				"Livro 1",
				"Autor",
				"Resumo",
				"Tema",
				AccessType.UNKNOWN,
				"Fonte externa",
				"moss",
				"https://example.com/1",
				""
		);
		Book secondBook = new Book(
				102L,
				"Livro 2",
				"Autor",
				"Resumo",
				"Tema",
				AccessType.UNKNOWN,
				"Fonte externa",
				"wine",
				"https://example.com/2",
				""
		);
		BookService service = new BookService(new BookRepository(), List.of((term, offset, limit) -> {
			List<Book> allBooks = List.of(firstBook, secondBook);

			if (limit <= 0) {
				return new BookSourceResult(List.of(), allBooks.size());
			}

			int end = Math.min(offset + limit, allBooks.size());
			return new BookSourceResult(allBooks.subList(offset, end), allBooks.size());
		}));

		BookSearchPage page = service.searchPage("livro", "ALL", 2, 1);

		assertEquals(2, page.totalItems());
		assertEquals(2, page.totalPages());
		assertEquals(List.of(secondBook), page.books());
	}

	@Test
	void searchPageCanFilterByExternalSource() {
		Book archiveBook = new Book(
				201L,
				"Livro Arquivo",
				"Autor",
				"Resumo",
				"Tema",
				AccessType.FREE,
				"Internet Archive",
				"moss",
				"https://archive.org/details/livro",
				""
		);
		Book openLibraryBook = new Book(
				202L,
				"Livro Biblioteca",
				"Autor",
				"Resumo",
				"Tema",
				AccessType.FREE,
				"Open Library",
				"wine",
				"https://openlibrary.org/works/example",
				""
		);
		BookService service = new BookService(
				new BookRepository(),
				List.of(source("Internet Archive", archiveBook), source("Open Library", openLibraryBook))
		);

		BookSearchPage page = service.searchPage("livro", "ALL", "Internet Archive", 1, 18);

		assertEquals(1, page.totalItems());
		assertEquals(List.of(archiveBook), page.books());
	}

	private BookSourceClient source(String sourceName, Book book) {
		return new BookSourceClient() {
			@Override
			public BookSourceResult search(String term, int offset, int limit) {
				if (limit <= 0) {
					return new BookSourceResult(List.of(), 1);
				}

				return new BookSourceResult(List.of(book), 1);
			}

			@Override
			public String sourceName() {
				return sourceName;
			}
		};
	}
}
