package com.MageLab.OpenBook.controller;

import com.MageLab.OpenBook.model.Book;
import com.MageLab.OpenBook.service.BookService;
import java.util.List;
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
	public List<Book> search(
			@RequestParam(defaultValue = "") String term,
			@RequestParam(defaultValue = "ALL") String access
	) {
		return bookService.search(term, access);
	}
}
