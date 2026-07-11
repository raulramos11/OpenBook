package com.MageLab.OpenBook.controller;

import com.MageLab.OpenBook.model.RatingResponse;
import com.MageLab.OpenBook.model.RatingSubmission;
import com.MageLab.OpenBook.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ratings")
public class RatingController {

	private final RatingService ratingService;

	public RatingController(RatingService ratingService) {
		this.ratingService = ratingService;
	}

	@PostMapping
	public RatingResponse rate(@Valid @RequestBody RatingSubmission submission) {
		return ratingService.rate(submission);
	}
}
