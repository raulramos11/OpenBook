package com.MageLab.OpenBook.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Book(
		Long id,
		String title,
		String author,
		String summary,
		String subject,
		AccessType accessType,
		String source,
		String coverTone
) {
	@JsonProperty("accessLabel")
	public String accessLabel() {
		return accessType.getLabel();
	}
}
