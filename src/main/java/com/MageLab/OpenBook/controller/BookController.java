package com.MageLab.OpenBook.controller;

import com.MageLab.OpenBook.model.BookSearchPage;
import com.MageLab.OpenBook.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

	private final BookService bookService;

	public BookController(BookService bookService) {
		this.bookService = bookService;
	}

	@GetMapping
	public BookSearchPage search(
			@RequestParam(defaultValue = "") String term,
			@RequestParam(defaultValue = "ALL") String access,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "18") int size
	) {
		return bookService.searchPage(term, access, page, size);
	}
}
