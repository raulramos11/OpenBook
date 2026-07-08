package com.MageLab.OpenBook.repository;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class BookRepository {

	private final List<Book> books = List.of(
			new Book(
					1L,
					"Dom Casmurro",
					"Machado de Assis",
					"Classico brasileiro com dominio publico em fontes digitais.",
					"Literatura brasileira",
					AccessType.FREE,
					"Dominio Publico",
					"moss",
					"",
					""
			),
			new Book(
					2L,
					"Memorias Postumas de Bras Cubas",
					"Machado de Assis",
					"Romance essencial para validar buscas por autor e obras gratuitas.",
					"Romance",
					AccessType.FREE,
					"Biblioteca Brasiliana",
					"wine",
					"",
					""
			),
			new Book(
					3L,
					"O Hobbit",
					"J. R. R. Tolkien",
					"Exemplo de obra comercial para diferenciar versoes pagas.",
					"Fantasia",
					AccessType.PAID,
					"Editora",
					"navy",
					"",
					""
			),
			new Book(
					4L,
					"Clean Code",
					"Robert C. Martin",
					"Livro tecnico usado como exemplo de consulta com disponibilidade paga.",
					"Programacao",
					AccessType.PAID,
					"Loja parceira",
					"slate",
					"",
					""
			),
			new Book(
					5L,
					"Alice no Pais das Maravilhas",
					"Lewis Carroll",
					"Obra em dominio publico em varios catalogos internacionais.",
					"Infantil",
					AccessType.FREE,
					"Project Gutenberg",
					"clay",
					"https://www.gutenberg.org/",
					""
			),
			new Book(
					6L,
					"Designing Data-Intensive Applications",
					"Martin Kleppmann",
					"Marcado como pendente para representar livros que precisam de verificacao online.",
					"Arquitetura de software",
					AccessType.UNKNOWN,
					"A verificar",
					"olive",
					"",
					""
			)
	);

	public List<Book> findAll() {
		return books;
	}
}
