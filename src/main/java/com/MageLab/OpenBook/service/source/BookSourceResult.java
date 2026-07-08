package com.MageLab.OpenBook.service.source;

import com.MageLab.OpenBook.model.Book;
import java.util.List;

public record BookSourceResult(
		List<Book> books,
		long totalItems
) {
	public static BookSourceResult empty() {
		return new BookSourceResult(List.of(), 0);
	}
}
