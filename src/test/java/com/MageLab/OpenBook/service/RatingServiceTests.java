package com.MageLab.OpenBook.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import com.MageLab.OpenBook.model.BookIdentity;
import com.MageLab.OpenBook.model.CommunityRating;
import com.MageLab.OpenBook.model.RatingResponse;
import com.MageLab.OpenBook.model.RatingSubmission;
import com.MageLab.OpenBook.repository.RatingRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RatingServiceTests {

	private static final String TITLE = "Dom Casmurro";
	private static final String AUTHOR = "Machado de Assis";
	private static final String BOOK_KEY = BookIdentity.key(TITLE, AUTHOR);

	private final RatingService ratingService = new RatingService(RatingRepository.inMemory());

	@Test
	void sameVoterUpdatesExistingVoteInsteadOfIncreasingCount() {
		String voterKey = "8de4cc0c-94d5-4f57-92f1-369148476bd8";

		RatingResponse first = ratingService.rate(submission(5, voterKey));
		RatingResponse updated = ratingService.rate(submission(3, voterKey));

		assertEquals(5.0, first.average());
		assertEquals(1, first.count());
		assertEquals(3.0, updated.average());
		assertEquals(1, updated.count());
		assertEquals(3, updated.userScore());
	}

	@Test
	void differentVotersProduceCommunityAverage() {
		ratingService.rate(submission(3, "8de4cc0c-94d5-4f57-92f1-369148476bd8"));
		RatingResponse response = ratingService.rate(submission(5, "ddf0e759-c449-45f7-b4e5-c43a6b52cf24"));

		assertEquals(4.0, response.average());
		assertEquals(2, response.count());
	}

	@Test
	void rejectsBookKeyThatDoesNotMatchTitleAndAuthor() {
		RatingSubmission invalid = new RatingSubmission(
				"0".repeat(64),
				TITLE,
				AUTHOR,
				5,
				"8de4cc0c-94d5-4f57-92f1-369148476bd8"
		);

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> ratingService.rate(invalid)
		);
		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
	}

	@Test
	void findsRatingsForBooksInOneRepositoryCall() {
		ratingService.rate(submission(4, "8de4cc0c-94d5-4f57-92f1-369148476bd8"));
		Book book = new Book(
				1L,
				TITLE,
				AUTHOR,
				"Resumo",
				"Romance",
				AccessType.FREE,
				"Fonte",
				"moss",
				"",
				""
		);

		Map<String, CommunityRating> ratings = ratingService.findForBooks(List.of(book));

		assertEquals(4.0, ratings.get(book.ratingKey()).average());
		assertEquals(1, ratings.get(book.ratingKey()).count());
	}

	private RatingSubmission submission(int score, String voterKey) {
		return new RatingSubmission(BOOK_KEY, TITLE, AUTHOR, score, voterKey);
	}
}
