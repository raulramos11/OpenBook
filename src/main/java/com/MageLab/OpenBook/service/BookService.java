package com.MageLab.OpenBook.service;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import com.MageLab.OpenBook.model.BookSearchPage;
import com.MageLab.OpenBook.model.BookSort;
import com.MageLab.OpenBook.model.BookSource;
import com.MageLab.OpenBook.model.CommunityRating;
import com.MageLab.OpenBook.model.RatingSummary;
import com.MageLab.OpenBook.repository.BookRepository;
import com.MageLab.OpenBook.service.source.BookSourceClient;
import com.MageLab.OpenBook.service.source.BookSourceResult;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BookService {

	private static final Logger LOGGER = LoggerFactory.getLogger(BookService.class);
	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
	private static final Pattern YEAR = Pattern.compile("\\b(\\d{4})\\b");
	private static final Pattern TITLE_SEPARATOR = Pattern.compile("\\s+(?:-|/|\\|)\\s+|:");
	private static final String DEFAULT_SEARCH_TERM = "literatura brasileira";
	private static final int DEFAULT_PAGE_SIZE = 18;
	private static final int MAX_PAGE_SIZE = 60;
	private static final int SOURCE_RESULT_WINDOW = 80;
	private static final int CACHE_MAX_ENTRIES = 80;
	private static final Duration EXTERNAL_CACHE_TTL = Duration.ofMinutes(10);

	private final BookRepository bookRepository;
	private final List<BookSourceClient> bookSourceClients;
	private final RatingService ratingService;
	private final Map<ExternalSearchKey, CachedExternalBooks> externalSearchCache = new ConcurrentHashMap<>();

	@Autowired
	public BookService(
			BookRepository bookRepository,
			List<BookSourceClient> bookSourceClients,
			RatingService ratingService
	) {
		this.bookRepository = bookRepository;
		this.bookSourceClients = bookSourceClients;
		this.ratingService = ratingService;
		LOGGER.info(
				"Fontes externas carregadas: {}",
				bookSourceClients.stream().map(source -> source.getClass().getSimpleName()).toList()
		);
	}

	public BookService(BookRepository bookRepository, List<BookSourceClient> bookSourceClients) {
		this(bookRepository, bookSourceClients, null);
	}

	public BookService(BookRepository bookRepository) {
		this(bookRepository, List.of());
	}

	public List<Book> search(String term, String access) {
		return searchPage(term, access, 1, DEFAULT_PAGE_SIZE).books();
	}

	public BookSearchPage searchPage(String term, String access, int page, int size) {
		return searchPage(term, access, "ALL", page, size);
	}

	public BookSearchPage searchPage(String term, String access, String source, int page, int size) {
		return searchPage(
				term,
				access,
				source,
				BookSort.RELEVANCE.name(),
				null,
				null,
				null,
				false,
				false,
				page,
				size
		);
	}

	public BookSearchPage searchPage(
			String term,
			String access,
			String source,
			String sort,
			Integer yearFrom,
			Integer yearTo,
			Double minRating,
			boolean hasCover,
			boolean multipleSources,
			int page,
			int size
	) {
		int safePage = Math.max(page, 1);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		String normalizedTerm = normalize(term);
		AccessType selectedAccess = parseAccess(access);
		String selectedSource = source == null ? "ALL" : source.trim();
		BookSort selectedSort = BookSort.from(sort);
		int safeYearFrom = validYear(yearFrom);
		int safeYearTo = validYear(yearTo);

		if (safeYearFrom > 0 && safeYearTo > 0 && safeYearFrom > safeYearTo) {
			int swap = safeYearFrom;
			safeYearFrom = safeYearTo;
			safeYearTo = swap;
		}

		double safeMinRating = minRating == null ? 0 : Math.min(Math.max(minRating, 0), 5);
		ExternalSearchResult externalResult = searchExternalBooks(
				term,
				selectedAccess,
				selectedSource,
				selectedSort,
				safeYearFrom,
				safeYearTo,
				safeMinRating,
				hasCover,
				multipleSources,
				safePage,
				safeSize
		);

		if (externalResult.hadResults()) {
			return externalResult.page();
		}

		List<Book> localBooks = bookRepository.findAll().stream()
				.filter(book -> matchesTerm(book, normalizedTerm))
				.filter(book -> matchesAccess(book, selectedAccess))
				.filter(book -> matchesSource(book, selectedSource))
				.toList();

		return preparePage(
				summarizeDuplicateBooks(localBooks),
				normalizedTerm,
				selectedSort,
				safeYearFrom,
				safeYearTo,
				safeMinRating,
				hasCover,
				multipleSources,
				safePage,
				safeSize
		);
	}

	public List<String> sourceNames() {
		Set<String> names = new LinkedHashSet<>();

		bookSourceClients.stream()
				.filter(BookSourceClient::isEnabled)
				.map(BookSourceClient::sourceName)
				.filter(name -> !name.isBlank())
				.forEach(names::add);

		return new ArrayList<>(names);
	}

	private ExternalSearchResult searchExternalBooks(
			String term,
			AccessType selectedAccess,
			String selectedSource,
			BookSort sort,
			int yearFrom,
			int yearTo,
			double minRating,
			boolean hasCover,
			boolean multipleSources,
			int page,
			int size
	) {
		List<BookSourceClient> selectedClients = selectedClients(selectedSource);

		if (selectedClients.isEmpty()) {
			return new ExternalSearchResult(BookSearchPage.of(List.of(), page, size, 0), false);
		}

		String query = term == null || term.isBlank() ? DEFAULT_SEARCH_TERM : term.trim();
		List<Book> fetchedBooks = externalBooks(query, selectedSource, selectedClients);
		List<Book> books = fetchedBooks.stream()
				.filter(book -> matchesAccess(book, selectedAccess))
				.filter(book -> matchesSource(book, selectedSource))
				.toList();
		List<Book> summarizedBooks = summarizeDuplicateBooks(books);

		BookSearchPage preparedPage = preparePage(
				summarizedBooks,
				normalize(query),
				sort,
				yearFrom,
				yearTo,
				minRating,
				hasCover,
				multipleSources,
				page,
				size
		);

		return new ExternalSearchResult(preparedPage, !fetchedBooks.isEmpty());
	}

	private List<Book> externalBooks(String query, String selectedSource, List<BookSourceClient> selectedClients) {
		if (!isAllSources(selectedSource)) {
			CachedExternalBooks allSourcesCache = freshCache(new ExternalSearchKey(normalize(query), "ALL"));

			if (allSourcesCache != null) {
				return allSourcesCache.books();
			}
		}

		ExternalSearchKey key = new ExternalSearchKey(normalize(query), cacheSource(selectedSource));
		CachedExternalBooks cachedBooks = freshCache(key);

		if (cachedBooks != null) {
			return cachedBooks.books();
		}

		List<Book> books = fetchExternalBooks(query, selectedClients);
		pruneCache();
		externalSearchCache.put(key, new CachedExternalBooks(books, Instant.now().plus(EXTERNAL_CACHE_TTL)));

		return books;
	}

	private CachedExternalBooks freshCache(ExternalSearchKey key) {
		CachedExternalBooks cachedBooks = externalSearchCache.get(key);

		if (cachedBooks == null || cachedBooks.expiresAt().isBefore(Instant.now())) {
			return null;
		}

		return cachedBooks;
	}

	private List<Book> fetchExternalBooks(String query, List<BookSourceClient> selectedClients) {
		List<CompletableFuture<List<Book>>> searches = selectedClients.stream()
				.map(source -> CompletableFuture.supplyAsync(() -> searchSourceWindow(query, source)))
				.toList();
		List<Book> books = new ArrayList<>();

		searches.forEach(search -> books.addAll(search.join()));

		return books;
	}

	private List<Book> searchSourceWindow(String query, BookSourceClient bookSourceClient) {
		try {
			BookSourceResult result = bookSourceClient.search(query, 0, SOURCE_RESULT_WINDOW);
			LOGGER.debug(
					"Fonte {} retornou {} item(ns) na janela rapida",
					bookSourceClient.getClass().getSimpleName(),
					result.books().size()
			);
			return result.books();
		} catch (RuntimeException exception) {
			LOGGER.warn(
					"Consulta da fonte {} interrompida: {}",
					bookSourceClient.getClass().getSimpleName(),
					exception.getMessage()
			);
			return List.of();
		}
	}

	private void pruneCache() {
		if (externalSearchCache.size() < CACHE_MAX_ENTRIES) {
			return;
		}

		Instant now = Instant.now();
		externalSearchCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));

		if (externalSearchCache.size() >= CACHE_MAX_ENTRIES) {
			externalSearchCache.clear();
		}
	}

	private List<Book> summarizeDuplicateBooks(List<Book> books) {
		List<List<Book>> groups = new ArrayList<>();

		for (Book book : books) {
			List<Book> matchingGroup = groups.stream()
					.filter(group -> matchesSameBook(group.get(0), book))
					.findFirst()
					.orElse(null);

			if (matchingGroup == null) {
				List<Book> group = new ArrayList<>();
				group.add(book);
				groups.add(group);
			} else {
				matchingGroup.add(book);
			}
		}

		return groups.stream()
				.map(this::mergeBooks)
				.toList();
	}

	private Book mergeBooks(List<Book> books) {
		if (books.size() == 1) {
			return books.get(0);
		}

		Book primaryBook = books.get(0);
		List<BookSource> sources = mergedSources(books);
		String sourceLabel = sources.stream()
				.map(BookSource::name)
				.distinct()
				.collect(java.util.stream.Collectors.joining(", "));
		String externalUrl = sources.stream()
				.map(BookSource::url)
				.filter(url -> !url.isBlank())
				.findFirst()
				.orElse("");
		RatingSummary mergedRatings = RatingSummary.mergeExternal(
				books.stream().map(Book::ratings).toList()
		);
		long popularity = books.stream()
				.mapToLong(Book::popularity)
				.sum();

		return new Book(
				mergedId(books),
				bestText(books, Book::title, "Titulo desconhecido"),
				bestText(books, Book::author, "Autor desconhecido"),
				mergedSummary(books, sources),
				mergedSubject(books),
				mergedAccessType(books),
				sourceLabel,
				primaryBook.coverTone(),
				externalUrl,
				bestText(books, Book::coverUrl, ""),
				bestPublishedDate(books),
				bestText(books, Book::rating, ""),
				sources,
				popularity,
				mergedRatings
		);
	}

	private List<BookSource> mergedSources(List<Book> books) {
		Map<String, BookSource> sourcesByKey = new LinkedHashMap<>();

		books.stream()
				.flatMap(book -> book.sources().stream())
				.forEach(source -> sourcesByKey.putIfAbsent(
						normalize(source.name()),
						source
				));

		return new ArrayList<>(sourcesByKey.values());
	}

	private AccessType mergedAccessType(List<Book> books) {
		if (books.stream().anyMatch(book -> book.accessType() == AccessType.FREE)) {
			return AccessType.FREE;
		}

		if (books.stream().anyMatch(book -> book.accessType() == AccessType.PAID)) {
			return AccessType.PAID;
		}

		return AccessType.UNKNOWN;
	}

	private String mergedSummary(List<Book> books, List<BookSource> sources) {
		String summary = bestLongText(books, Book::summary, "Informacoes basicas encontradas nas fontes consultadas.");

		if (sources.size() == 1) {
			return summary;
		}

		String sourceNames = sources.stream()
				.map(BookSource::name)
				.distinct()
				.collect(java.util.stream.Collectors.joining(", "));

		return limitText("Encontrado em " + sources.size() + " fontes: " + sourceNames + ". " + summary, 2200);
	}

	private String mergedSubject(List<Book> books) {
		List<String> subjects = books.stream()
				.map(Book::subject)
				.filter(subject -> !isBlankOrFallback(subject, "Sem tema informado"))
				.distinct()
				.limit(2)
				.toList();

		return subjects.isEmpty() ? "Sem tema informado" : String.join(", ", subjects);
	}

	private String bestPublishedDate(List<Book> books) {
		int earliestYear = Integer.MAX_VALUE;
		String fallbackDate = "";

		for (Book book : books) {
			String publishedDate = book.publishedDate();

			if (publishedDate.isBlank()) {
				continue;
			}

			if (fallbackDate.isBlank()) {
				fallbackDate = publishedDate;
			}

			Matcher matcher = YEAR.matcher(publishedDate);

			if (matcher.find()) {
				earliestYear = Math.min(earliestYear, Integer.parseInt(matcher.group(1)));
			}
		}

		return earliestYear == Integer.MAX_VALUE ? fallbackDate : String.valueOf(earliestYear);
	}

	private String bestText(List<Book> books, Function<Book, String> field, String fallback) {
		return books.stream()
				.map(field)
				.filter(value -> !isBlankOrFallback(value, fallback))
				.findFirst()
				.orElse(fallback);
	}

	private String bestLongText(List<Book> books, Function<Book, String> field, String fallback) {
		return books.stream()
				.map(field)
				.filter(value -> !isBlankOrFallback(value, fallback))
				.max((first, second) -> Integer.compare(first.length(), second.length()))
				.orElse(fallback);
	}

	private Long mergedId(List<Book> books) {
		String key = deduplicationKey(books.get(0));
		long hash = Math.abs((long) key.hashCode());

		return hash == 0 ? 1L : hash;
	}

	private String limitText(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}

		return value.substring(0, maxLength - 3).trim() + "...";
	}

	private BookSearchPage preparePage(
			List<Book> books,
			String normalizedTerm,
			BookSort sort,
			int yearFrom,
			int yearTo,
			double minRating,
			boolean hasCover,
			boolean multipleSources,
			int page,
			int size
	) {
		List<Book> preparedBooks = withCommunityRatings(books).stream()
				.filter(book -> matchesPublishedYear(book, yearFrom, yearTo))
				.filter(book -> matchesMinimumRating(book, minRating))
				.filter(book -> !hasCover || !book.coverUrl().isBlank())
				.filter(book -> !multipleSources || book.sourceCount() > 1)
				.sorted(bookComparator(sort, normalizedTerm))
				.toList();

		return localPage(preparedBooks, page, size);
	}

	private List<Book> withCommunityRatings(Collection<Book> books) {
		if (ratingService == null || books.isEmpty()) {
			return new ArrayList<>(books);
		}

		try {
			Map<String, CommunityRating> ratingsByBook = ratingService.findForBooks(books);
			return books.stream()
					.map(book -> book.withCommunityRating(ratingsByBook.get(book.ratingKey())))
					.toList();
		} catch (RuntimeException exception) {
			LOGGER.warn("Avaliacoes da comunidade indisponiveis nesta busca: {}", exception.getMessage());
			return new ArrayList<>(books);
		}
	}

	private Comparator<Book> bookComparator(BookSort sort, String normalizedTerm) {
		Comparator<Book> byTitle = Comparator.comparing(book -> normalize(book.title()));
		Comparator<Book> byPopularity = Comparator
				.comparingDouble(this::popularityScore)
				.reversed()
				.thenComparing(Comparator.comparingDouble(this::ratingValue).reversed())
				.thenComparing(byTitle);

		return switch (sort) {
			case POPULAR -> byPopularity;
			case RATING -> Comparator
					.comparingDouble(this::ratingValue)
					.reversed()
					.thenComparing(Comparator.comparingLong(this::ratingCount).reversed())
					.thenComparing(byPopularity);
			case NEWEST -> Comparator
					.comparingInt((Book book) -> publishedYear(book) == 0 ? Integer.MIN_VALUE : publishedYear(book))
					.reversed()
					.thenComparing(byPopularity);
			case OLDEST -> Comparator
					.comparingInt((Book book) -> publishedYear(book) == 0 ? Integer.MAX_VALUE : publishedYear(book))
					.thenComparing(byPopularity);
			case TITLE -> byTitle.thenComparing(byPopularity);
			case RELEVANCE -> Comparator
					.comparingInt((Book book) -> relevanceScore(book, normalizedTerm))
					.reversed()
					.thenComparing(byPopularity);
		};
	}

	private int relevanceScore(Book book, String normalizedTerm) {
		if (normalizedTerm == null || normalizedTerm.isBlank()) {
			return 0;
		}

		String title = normalize(book.title());
		String author = normalize(book.author());
		String subject = normalize(book.subject());
		String summary = normalize(book.summary());
		int score = 0;

		if (title.equals(normalizedTerm)) {
			score += 120;
		} else if (title.startsWith(normalizedTerm)) {
			score += 85;
		} else if (title.contains(normalizedTerm)) {
			score += 60;
		}

		if (author.equals(normalizedTerm)) {
			score += 65;
		} else if (author.contains(normalizedTerm)) {
			score += 38;
		}

		if (subject.contains(normalizedTerm)) {
			score += 22;
		}

		if (summary.contains(normalizedTerm)) {
			score += 8;
		}

		return score + Math.min(book.sourceCount() * 3, 12);
	}

	private double popularityScore(Book book) {
		return Math.log1p(book.popularity())
				+ Math.log1p(book.ratings().externalCount()) * 0.65
				+ Math.log1p(book.ratings().communityCount()) * 0.9;
	}

	private double ratingValue(Book book) {
		return book.ratings().average() == null ? -1 : book.ratings().average();
	}

	private long ratingCount(Book book) {
		return book.ratings().totalCount();
	}

	private boolean matchesPublishedYear(Book book, int yearFrom, int yearTo) {
		if (yearFrom == 0 && yearTo == 0) {
			return true;
		}

		int year = publishedYear(book);
		return year > 0
				&& (yearFrom == 0 || year >= yearFrom)
				&& (yearTo == 0 || year <= yearTo);
	}

	private boolean matchesMinimumRating(Book book, double minRating) {
		return minRating <= 0
				|| book.ratings().average() != null && book.ratings().average() >= minRating;
	}

	private int publishedYear(Book book) {
		Matcher matcher = YEAR.matcher(book.publishedDate());
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
	}

	private int validYear(Integer year) {
		return year == null || year < 1 || year > 2200 ? 0 : year;
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

	private boolean matchesSource(Book book, String selectedSource) {
		return isAllSources(selectedSource)
				|| book.sources().stream().anyMatch(source -> matchesSource(source.name(), selectedSource))
				|| matchesSource(book.source(), selectedSource);
	}

	private boolean matchesSource(String source, String selectedSource) {
		return isAllSources(selectedSource)
				|| source != null && source.equalsIgnoreCase(selectedSource);
	}

	private boolean isAllSources(String selectedSource) {
		return selectedSource == null
				|| selectedSource.isBlank()
				|| "ALL".equalsIgnoreCase(selectedSource);
	}

	private List<BookSourceClient> selectedClients(String selectedSource) {
		return bookSourceClients.stream()
				.filter(BookSourceClient::isEnabled)
				.filter(source -> matchesSource(source.sourceName(), selectedSource))
				.toList();
	}

	private String deduplicationKey(Book book) {
		String titleKey = canonicalTitle(book.title());
		String authorKey = canonicalAuthor(book.author());

		if (titleKey.isBlank()) {
			return "book|" + book.source() + "|" + book.externalUrl() + "|" + book.id();
		}

		if (authorKey.isBlank() || "autor desconhecido".equals(authorKey)) {
			return titleKey;
		}

		return titleKey + "|" + authorKey;
	}

	private boolean matchesSameBook(Book firstBook, Book secondBook) {
		String firstTitle = canonicalTitle(firstBook.title());
		String secondTitle = canonicalTitle(secondBook.title());

		return !firstTitle.isBlank()
				&& firstTitle.equals(secondTitle)
				&& compatibleAuthors(canonicalAuthor(firstBook.author()), canonicalAuthor(secondBook.author()));
	}

	private boolean compatibleAuthors(String firstAuthor, String secondAuthor) {
		if (firstAuthor.isBlank()
				|| secondAuthor.isBlank()
				|| "autor desconhecido".equals(firstAuthor)
				|| "autor desconhecido".equals(secondAuthor)) {
			return true;
		}

		return firstAuthor.equals(secondAuthor)
				|| firstAuthor.contains(secondAuthor)
				|| secondAuthor.contains(firstAuthor);
	}

	private String canonicalTitle(String title) {
		String withoutBrackets = title == null ? "" : title
				.replaceAll("\\([^)]*\\)", " ")
				.replaceAll("\\[[^\\]]*\\]", " ");
		String baseTitle = TITLE_SEPARATOR.split(withoutBrackets, 2)[0];

		return normalize(baseTitle).replaceAll("[^a-z0-9]+", " ").trim();
	}

	private String canonicalAuthor(String author) {
		if (author == null) {
			return "";
		}

		String firstAuthor = author.split(",|;|\\band\\b|\\be\\b", 2)[0].trim();

		if (author.contains(",") && author.indexOf(",") < author.length() - 1) {
			String[] parts = author.split(",", 2);
			firstAuthor = parts[1].trim() + " " + parts[0].trim();
		}

		return normalize(firstAuthor).replaceAll("[^a-z0-9]+", " ").trim();
	}

	private String cacheSource(String selectedSource) {
		return isAllSources(selectedSource) ? "ALL" : normalize(selectedSource);
	}

	private boolean isBlankOrFallback(String value, String fallback) {
		return value == null || value.isBlank() || value.equalsIgnoreCase(fallback);
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

	private record ExternalSearchKey(String query, String source) {
	}

	private record CachedExternalBooks(List<Book> books, Instant expiresAt) {
	}

	private record ExternalSearchResult(BookSearchPage page, boolean hadResults) {
	}
}
