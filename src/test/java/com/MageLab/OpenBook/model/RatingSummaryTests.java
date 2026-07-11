package com.MageLab.OpenBook.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RatingSummaryTests {

	@Test
	void parsesAndMergesRealExternalRatingCounts() {
		RatingSummary merged = RatingSummary.mergeExternal(List.of(
				RatingSummary.fromDisplay("4.0/5 (10)"),
				RatingSummary.fromDisplay("5.0/5 (30)")
		));

		assertEquals(4.75, merged.externalAverage(), 0.001);
		assertEquals(40, merged.externalCount());
	}

	@Test
	void communityVotesAreKeptSeparateWhileOverallRatingIsCombined() {
		RatingSummary summary = RatingSummary
				.fromDisplay("4.0/5 (3)")
				.withCommunity(new CommunityRating(5.0, 1));

		assertEquals(4.25, summary.average(), 0.001);
		assertEquals(4.0, summary.externalAverage(), 0.001);
		assertEquals(5.0, summary.communityAverage(), 0.001);
		assertEquals(4, summary.totalCount());
	}
}
