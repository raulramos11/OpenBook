package com.MageLab.OpenBook.model;

import java.util.List;

public record BookSearchPage(
		List<Book> books,
		int page,
		int size,
		long totalItems,
		int totalPages,
		boolean hasPrevious,
		boolean hasNext
) {
	public static BookSearchPage of(List<Book> books, int page, int size, long totalItems) {
		int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);

		return new BookSearchPage(
				books,
				page,
				size,
				totalItems,
				totalPages,
				page > 1,
				totalPages > 0 && page < totalPages
		);
	}
}
