package com.MageLab.OpenBook.service.source;

import com.MageLab.OpenBook.model.Book;
import java.util.List;

public interface BookSourceClient {

	BookSourceResult search(String term, int offset, int limit);

	default String sourceName() {
		return getClass().getSimpleName();
	}

	default boolean isEnabled() {
		return true;
	}

	default List<Book> search(String term) {
		return search(term, 0, 50).books();
	}
}
