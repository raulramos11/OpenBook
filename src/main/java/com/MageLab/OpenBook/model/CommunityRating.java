package com.MageLab.OpenBook.model;

public record CommunityRating(
		Double average,
		long count
) {
	public static CommunityRating empty() {
		return new CommunityRating(null, 0);
	}
}
