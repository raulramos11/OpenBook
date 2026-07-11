package com.MageLab.OpenBook.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.MageLab.OpenBook.model.BookIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"openbook.ratings.database-url=",
		"openbook.ratings.require-database=false"
})
@AutoConfigureMockMvc
class RatingControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void savesAValidRating() throws Exception {
		String title = "Livro do teste HTTP";
		String author = "Autora Teste";

		mockMvc.perform(post("/api/ratings")
					.contentType(MediaType.APPLICATION_JSON)
					.content(json(title, author, 5)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookKey").value(BookIdentity.key(title, author)))
				.andExpect(jsonPath("$.average").value(5.0))
				.andExpect(jsonPath("$.count").value(1))
				.andExpect(jsonPath("$.userScore").value(5));
	}

	@Test
	void rejectsScoresOutsideOneToFive() throws Exception {
		String title = "Livro invalido";
		String author = "Autor Teste";

		mockMvc.perform(post("/api/ratings")
					.contentType(MediaType.APPLICATION_JSON)
					.content(json(title, author, 0)))
				.andExpect(status().isBadRequest());
	}

	private String json(String title, String author, int score) {
		return """
				{
				  "bookKey": "%s",
				  "title": "%s",
				  "author": "%s",
				  "score": %d,
				  "voterKey": "8de4cc0c-94d5-4f57-92f1-369148476bd8"
				}
				""".formatted(BookIdentity.key(title, author), title, author, score);
	}
}
