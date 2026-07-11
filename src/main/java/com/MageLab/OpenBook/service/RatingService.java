package com.MageLab.OpenBook.service;

import com.MageLab.OpenBook.model.Book;
import com.MageLab.OpenBook.model.BookIdentity;
import com.MageLab.OpenBook.model.CommunityRating;
import com.MageLab.OpenBook.model.RatingResponse;
import com.MageLab.OpenBook.model.RatingSubmission;
import com.MageLab.OpenBook.repository.RatingPersistenceException;
import com.MageLab.OpenBook.repository.RatingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RatingService {

	private static final Logger LOGGER = LoggerFactory.getLogger(RatingService.class);

	private final RatingRepository ratingRepository;

	public RatingService(RatingRepository ratingRepository) {
		this.ratingRepository = ratingRepository;
	}

	public RatingResponse rate(RatingSubmission submission) {
		String expectedBookKey = BookIdentity.key(submission.title(), submission.author());

		if (!secureEquals(expectedBookKey, submission.bookKey())) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"bookKey nao corresponde ao titulo e autor informados."
			);
		}

		if (submission.score() == null || submission.score() < 1 || submission.score() > 5) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score deve estar entre 1 e 5.");
		}

		String voterHash = hashVoter(expectedBookKey, submission.voterKey());

		try {
			CommunityRating rating = ratingRepository.upsert(expectedBookKey, voterHash, submission.score());
			return RatingResponse.from(expectedBookKey, rating, submission.score());
		} catch (RatingPersistenceException exception) {
			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"O sistema de avaliacoes esta temporariamente indisponivel.",
					exception
			);
		}
	}

	public Map<String, CommunityRating> findForBooks(Collection<Book> books) {
		if (books == null || books.isEmpty()) {
			return Map.of();
		}

		Set<String> bookKeys = new LinkedHashSet<>();
		for (Book book : books) {
			if (book == null) {
				continue;
			}

			String bookKey = book.ratingKey();
			if (bookKey != null && !bookKey.isBlank()) {
				bookKeys.add(bookKey);
			}
		}

		try {
			return ratingRepository.findByBookKeys(bookKeys);
		} catch (RatingPersistenceException exception) {
			LOGGER.warn("Avaliacoes da comunidade indisponiveis durante a busca: {}", exception.getMessage());
			return Map.of();
		}
	}

	private String hashVoter(String bookKey, String voterKey) {
		if (voterKey == null || voterKey.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "voterKey e obrigatorio.");
		}

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			String scopedVoterKey = bookKey + "\u001f" + voterKey.trim();
			return HexFormat.of().formatHex(digest.digest(scopedVoterKey.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 nao esta disponivel nesta JVM.", exception);
		}
	}

	private boolean secureEquals(String expected, String actual) {
		if (actual == null) {
			return false;
		}

		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.US_ASCII),
				actual.getBytes(StandardCharsets.US_ASCII)
		);
	}
}
