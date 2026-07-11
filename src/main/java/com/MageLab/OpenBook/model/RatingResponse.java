package com.MageLab.OpenBook.model;

public record RatingResponse(
		String bookKey,
		Double average,
		long count,
		int userScore
) {
	public static RatingResponse from(String bookKey, CommunityRating rating, int userScore) {
		return new RatingResponse(bookKey, rating.average(), rating.count(), userScore);
	}
}
