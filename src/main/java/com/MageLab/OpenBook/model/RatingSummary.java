package com.MageLab.OpenBook.model;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record RatingSummary(
		Double average,
		long totalCount,
		Double externalAverage,
		long externalCount,
		Double communityAverage,
		long communityCount
) {
	private static final Pattern DISPLAY_RATING = Pattern.compile(
			"^\\s*([0-5](?:[.,]\\d+)?)\\s*/\\s*5(?:\\s*\\((\\d+)\\))?.*$"
	);

	public RatingSummary {
		average = validAverage(average);
		externalAverage = validAverage(externalAverage);
		communityAverage = validAverage(communityAverage);
		totalCount = Math.max(totalCount, 0);
		externalCount = Math.max(externalCount, 0);
		communityCount = Math.max(communityCount, 0);
	}

	public static RatingSummary empty() {
		return new RatingSummary(null, 0, null, 0, null, 0);
	}

	public static RatingSummary external(Double average, long count) {
		Double safeAverage = validAverage(average);
		long safeCount = Math.max(count, 0);
		return new RatingSummary(safeAverage, safeCount, safeAverage, safeCount, null, 0);
	}

	public static RatingSummary fromDisplay(String rating) {
		if (rating == null || rating.isBlank()) {
			return empty();
		}

		Matcher matcher = DISPLAY_RATING.matcher(rating);
		if (!matcher.matches()) {
			return empty();
		}

		double average = Double.parseDouble(matcher.group(1).replace(',', '.'));
		long count = matcher.group(2) == null ? 0 : Long.parseLong(matcher.group(2));
		return external(average, count);
	}

	public static RatingSummary mergeExternal(Collection<RatingSummary> summaries) {
		double weightedTotal = 0;
		long totalWeight = 0;
		long totalCount = 0;

		for (RatingSummary summary : summaries) {
			if (summary == null || summary.externalAverage() == null) {
				continue;
			}

			long weight = Math.max(summary.externalCount(), 1);
			weightedTotal += summary.externalAverage() * weight;
			totalWeight += weight;
			totalCount += summary.externalCount();
		}

		if (totalWeight == 0) {
			return empty();
		}

		return external(weightedTotal / totalWeight, totalCount);
	}

	public RatingSummary withCommunity(CommunityRating communityRating) {
		if (communityRating == null || communityRating.count() <= 0) {
			return this;
		}

		Double safeCommunityAverage = validAverage(communityRating.average());
		if (safeCommunityAverage == null) {
			return this;
		}

		long externalWeight = externalAverage == null ? 0 : Math.max(externalCount, 1);
		long communityWeight = communityRating.count();
		double combinedAverage = (
				(externalAverage == null ? 0 : externalAverage * externalWeight)
						+ safeCommunityAverage * communityWeight
		) / (externalWeight + communityWeight);

		return new RatingSummary(
				combinedAverage,
				externalCount + communityRating.count(),
				externalAverage,
				externalCount,
				safeCommunityAverage,
				communityRating.count()
		);
	}

	public String displayAverage() {
		return average == null ? "" : String.format(Locale.ROOT, "%.1f", average);
	}

	private static Double validAverage(Double value) {
		return value == null || !Double.isFinite(value) || value <= 0 || value > 5 ? null : value;
	}
}
