package com.MageLab.OpenBook.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RatingSubmission(
		@NotBlank(message = "bookKey e obrigatorio.")
		@Pattern(regexp = "^[a-f0-9]{64}$", message = "bookKey deve ser um SHA-256 valido.")
		String bookKey,

		@NotBlank(message = "title e obrigatorio.")
		@Size(max = 300, message = "title deve ter no maximo 300 caracteres.")
		String title,

		@NotBlank(message = "author e obrigatorio.")
		@Size(max = 300, message = "author deve ter no maximo 300 caracteres.")
		String author,

		@NotNull(message = "score e obrigatorio.")
		@Min(value = 1, message = "score deve ser no minimo 1.")
		@Max(value = 5, message = "score deve ser no maximo 5.")
		Integer score,

		@NotBlank(message = "voterKey e obrigatorio.")
		@Pattern(
				regexp = "^[A-Za-z0-9_-]{16,128}$",
				message = "voterKey deve ser um UUID ou token valido de 16 a 128 caracteres."
		)
		String voterKey
) {
}
