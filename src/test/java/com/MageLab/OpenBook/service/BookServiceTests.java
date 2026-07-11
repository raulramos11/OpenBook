package com.MageLab.OpenBook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import com.MageLab.OpenBook.model.BookSearchPage;
import com.MageLab.OpenBook.model.BookSource;
import com.MageLab.OpenBook.model.RatingSummary;
import com.MageLab.OpenBook.repository.BookRepository;
import com.MageLab.OpenBook.service.source.BookSourceClient;
import com.MageLab.OpenBook.service.source.BookSourceResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

	@Test
	void searchPageSummarizesSameBookAcrossSources() {
		Book gutenbergBook = new Book(
				301L,
				"Dom Casmurro",
				"Machado de Assis",
				"Resumo curto.",
				"Literatura brasileira",
				AccessType.FREE,
				"Project Gutenberg",
				"moss",
				"https://gutenberg.org/dom-casmurro",
				"",
				"1899",
				"",
				List.of()
		);
		Book openLibraryBook = new Book(
				302L,
				"Dom Casmurro",
				"Machado de Assis",
				"Resumo maior vindo de outra fonte para a tela conseguir mostrar uma descricao mais util.",
				"Romance",
				AccessType.UNKNOWN,
				"Open Library",
				"wine",
				"https://openlibrary.org/works/dom-casmurro",
				"",
				"1900",
				"4.2/5",
				List.of()
		);
		BookService service = new BookService(
				new BookRepository(),
				List.of(source("Project Gutenberg", gutenbergBook), source("Open Library", openLibraryBook))
		);

		BookSearchPage page = service.searchPage("dom casmurro", "ALL", "ALL", 1, 18);

		assertEquals(1, page.totalItems());
		assertEquals(2, page.books().getFirst().sourceCount());
		assertEquals(AccessType.FREE, page.books().getFirst().accessType());
		assertTrue(page.books().getFirst().summary().contains("Encontrado em 2 fontes"));
		assertEquals("1899", page.books().getFirst().publishedDate());
		assertEquals("4.2/5", page.books().getFirst().rating());
	}

	@Test
	void filteredSearchDoesNotAskSourcesForFullCounts() {
		AtomicBoolean fullCountCalled = new AtomicBoolean(false);
		Book freeBook = new Book(
				401L,
				"Livro gratuito",
				"Autor",
				"Resumo",
				"Tema",
				AccessType.FREE,
				"Fonte externa",
				"moss",
				"https://example.com/free",
				""
		);
		BookSourceClient source = new BookSourceClient() {
			@Override
			public BookSourceResult search(String term, int offset, int limit) {
				if (limit <= 0) {
					fullCountCalled.set(true);
					return new BookSourceResult(List.of(), 1);
				}

				return new BookSourceResult(List.of(freeBook), 1);
			}
		};
		BookService service = new BookService(new BookRepository(), List.of(source));

		BookSearchPage page = service.searchPage("livro", "FREE", "ALL", 1, 18);

		assertEquals(1, page.totalItems());
		assertTrue(page.books().contains(freeBook));
		assertEquals(false, fullCountCalled.get());
	}

	@Test
	void sourceFilterCanReuseAllSourcesCacheForSameTerm() {
		AtomicInteger calls = new AtomicInteger(0);
		Book openLibraryBook = new Book(
				501L,
				"Livro Biblioteca",
				"Autor",
				"Resumo",
				"Tema",
				AccessType.FREE,
				"Open Library",
				"moss",
				"https://openlibrary.org/works/example",
				""
		);
		BookSourceClient source = new BookSourceClient() {
			@Override
			public BookSourceResult search(String term, int offset, int limit) {
				calls.incrementAndGet();
				return new BookSourceResult(List.of(openLibraryBook), 1);
			}

			@Override
			public String sourceName() {
				return "Open Library";
			}
		};
		BookService service = new BookService(new BookRepository(), List.of(source));

		service.searchPage("livro", "ALL", "ALL", 1, 18);
		BookSearchPage filteredPage = service.searchPage("livro", "ALL", "Open Library", 1, 18);

		assertEquals(1, filteredPage.totalItems());
		assertEquals(1, calls.get());
	}

	@Test
	void ratingSortHappensBeforePagination() {
		Book lowRated = metricsBook(601L, "Livro nota baixa", "2001", "2.0/5 (10)", "", 10);
		Book bestRated = metricsBook(602L, "Livro nota alta", "1998", "4.9/5 (50)", "", 50);
		Book mediumRated = metricsBook(603L, "Livro nota media", "2020", "4.1/5 (20)", "", 20);
		BookService service = serviceWithBooks(lowRated, bestRated, mediumRated);

		BookSearchPage page = service.searchPage(
				"livro", "ALL", "ALL", "RATING",
				null, null, null, false, false, 1, 1
		);

		assertEquals(3, page.totalItems());
		assertEquals("Livro nota alta", page.books().getFirst().title());
	}

	@Test
	void usefulFiltersCanBeCombined() {
		Book firstSource = metricsBook(
				701L, "Livro compartilhado", "2018", "4.5/5 (20)", "https://example.com/cover.jpg", 100
		);
		Book secondSource = new Book(
				702L,
				"Livro compartilhado",
				"Autor",
				"Resumo mais completo da segunda fonte.",
				"Tema",
				AccessType.FREE,
				"Fonte B",
				"wine",
				"https://example.com/b",
				"",
				"2019",
				"4.7/5 (10)",
				List.of(new BookSource("Fonte B", "https://example.com/b")),
				50,
				RatingSummary.fromDisplay("4.7/5 (10)")
		);
		Book singleSource = metricsBook(703L, "Livro sem capa", "2021", "4.9/5 (200)", "", 500);
		BookService service = new BookService(
				new BookRepository(),
				List.of(source("Fonte A", firstSource), source("Fonte B", secondSource), source("Fonte C", singleSource))
		);

		BookSearchPage page = service.searchPage(
				"livro", "ALL", "ALL", "NEWEST",
				2010, 2020, 4.0, true, true, 1, 18
		);

		assertEquals(1, page.totalItems());
		assertEquals("Livro compartilhado", page.books().getFirst().title());
		assertEquals(2, page.books().getFirst().sourceCount());
	}

	@Test
	void emptyExternalFilterDoesNotFallBackToUnrelatedLocalBooks() {
		Book paidExternal = metricsBook(801L, "Livro comercial", "2020", "4.0/5 (2)", "", 2);
		paidExternal = new Book(
				paidExternal.id(), paidExternal.title(), paidExternal.author(), paidExternal.summary(), paidExternal.subject(),
				AccessType.PAID, paidExternal.source(), paidExternal.coverTone(), paidExternal.externalUrl(), paidExternal.coverUrl(),
				paidExternal.publishedDate(), paidExternal.rating(), paidExternal.sources(), paidExternal.popularity(), paidExternal.ratings()
		);
		BookService service = serviceWithBooks(paidExternal);

		BookSearchPage page = service.searchPage(
				"livro", "FREE", "ALL", "RELEVANCE",
				null, null, null, false, false, 1, 18
		);

		assertEquals(0, page.totalItems());
		assertTrue(page.books().isEmpty());
	}

	@Test
	void popularSortUsesPublicPopularitySignals() {
		Book niche = metricsBook(901L, "Livro de nicho", "2024", "5.0/5 (5)", "", 8);
		Book famous = metricsBook(902L, "Livro famoso", "1900", "4.0/5 (30)", "", 50_000);
		BookService service = serviceWithBooks(niche, famous);

		BookSearchPage page = service.searchPage(
				"livro", "ALL", "ALL", "POPULAR",
				null, null, null, false, false, 1, 18
		);

		assertEquals("Livro famoso", page.books().getFirst().title());
	}

	private BookService serviceWithBooks(Book... books) {
		return new BookService(new BookRepository(), List.of((term, offset, limit) ->
				new BookSourceResult(List.of(books), books.length)));
	}

	private Book metricsBook(
			long id,
			String title,
			String publishedDate,
			String rating,
			String coverUrl,
			long popularity
	) {
		return new Book(
				id,
				title,
				"Autor",
				"Resumo",
				"Tema",
				AccessType.FREE,
				"Fonte A",
				"moss",
				"https://example.com/" + id,
				coverUrl,
				publishedDate,
				rating,
				List.of(new BookSource("Fonte A", "https://example.com/" + id)),
				popularity,
				RatingSummary.fromDisplay(rating)
		);
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
