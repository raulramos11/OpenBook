package com.MageLab.OpenBook.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class BookIdentityTests {

	@Test
	void keyIsStableAcrossAccentsCaseAndPunctuation() {
		String first = BookIdentity.key("Dom Casmurro", "Machado de Assis");
		String second = BookIdentity.key("  DÓM--CASMURRO  ", "machado de assis");

		assertEquals(first, second);
		assertEquals(64, first.length());
	}

	@Test
	void keyChangesWhenAuthorChanges() {
		assertNotEquals(
				BookIdentity.key("Livro", "Primeiro Autor"),
				BookIdentity.key("Livro", "Outro Autor")
		);
	}

	@Test
	void keySurvivesEditionSuffixAndInvertedAuthorName() {
		assertEquals(
				BookIdentity.key("Dom Casmurro", "Machado de Assis"),
				BookIdentity.key("Dom Casmurro: edicao comentada", "Assis, Machado de")
		);
	}
}
