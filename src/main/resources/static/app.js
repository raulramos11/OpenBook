const searchForm = document.querySelector("#searchForm");
const searchInput = document.querySelector("#searchInput");
const bookGrid = document.querySelector("#bookGrid");
const resultCount = document.querySelector("#resultCount");
const filterButtons = document.querySelectorAll("[data-access]");

const state = {
	access: "ALL"
};

let currentPage = 1;
const booksPerPage = 25;
let booksData = [];

searchForm.addEventListener("submit", (event) => {
	event.preventDefault();

	const query = searchInput.value.trim();

	if (!query) {
		renderEmptyState("Digite um titulo, autor ou tema para pesquisar.");
		return;
	}

	fetchBooks(query);
});

filterButtons.forEach((button) => {
	button.addEventListener("click", () => {
		filterButtons.forEach((item) => item.classList.remove("active"));
		button.classList.add("active");
		state.access = button.dataset.access;
		currentPage = 1;
		displayBooks();
	});
});

async function fetchBooks(query) {
	const apiUrl = `https://openlibrary.org/search.json?q=${encodeURIComponent(query)}&limit=50`;

	bookGrid.replaceChildren();
	resultCount.textContent = "Buscando...";

	try {
		const response = await fetch(apiUrl);

		if (!response.ok) {
			throw new Error("Falha ao buscar dados");
		}

		const data = await response.json();
		booksData = data.docs || [];
		currentPage = 1;
		displayBooks();
	} catch (error) {
		resultCount.textContent = "0 livros";
		renderEmptyState(`Erro ao buscar livros: ${error.message}`);
	}
}

function displayBooks() {
	const filteredBooks = booksData.filter(matchesAccessFilter);
	const start = (currentPage - 1) * booksPerPage;
	const end = start + booksPerPage;
	const booksToShow = filteredBooks.slice(start, end);

	bookGrid.replaceChildren();
	resultCount.textContent = `${filteredBooks.length} ${filteredBooks.length === 1 ? "livro" : "livros"}`;

	if (booksToShow.length === 0) {
		renderEmptyState("Nenhum livro encontrado para esta busca.");
		return;
	}

	booksToShow.forEach((book) => {
		bookGrid.append(createBookCard(book));
	});
}

function createBookCard(book) {
	const card = document.createElement("article");
	card.className = "book-card";

	const cover = document.createElement("div");
	cover.className = `book-cover tone-${coverTone(book)}`;

	if (book.cover_i) {
		cover.classList.add("has-image");

		const coverImage = document.createElement("img");
		coverImage.src = `https://covers.openlibrary.org/b/id/${book.cover_i}-M.jpg`;
		coverImage.alt = `Capa de ${book.title || "livro"}`;
		coverImage.loading = "lazy";
		cover.append(coverImage);
	} else {
		const coverTitle = document.createElement("span");
		coverTitle.textContent = shortTitle(book.title || "Sem titulo");
		cover.append(coverTitle);
	}

	const info = document.createElement("div");
	info.className = "book-info";

	const title = document.createElement("h3");
	title.textContent = book.title || "Titulo desconhecido";

	const author = document.createElement("p");
	author.className = "book-author";
	author.textContent = getAuthor(book);

	const accessType = getAccessType(book);
	const availability = document.createElement("span");
	availability.className = `availability availability-${accessType.toLowerCase()}`;
	availability.textContent = getAccessLabel(accessType);

	const summary = document.createElement("p");
	summary.className = "book-summary";
	summary.textContent = getSummary(book);

	const source = document.createElement("p");
	source.className = "book-source";
	source.textContent = "Fonte: Open Library";

	info.append(title, author, availability, summary, source);
	card.append(cover, info);

	return card;
}

function matchesAccessFilter(book) {
	if (state.access === "ALL") {
		return true;
	}

	return getAccessType(book) === state.access;
}

function getAccessType(book) {
	if (book.ebook_access === "public" || book.public_scan_b) {
		return "FREE";
	}

	if (book.ebook_access === "no_ebook") {
		return "PAID";
	}

	return "UNKNOWN";
}

function getAccessLabel(accessType) {
	const labels = {
		FREE: "Gratuito",
		PAID: "Sem eBook na Open Library",
		UNKNOWN: "A verificar"
	};

	return labels[accessType];
}

function getAuthor(book) {
	if (!book.author_name || book.author_name.length === 0) {
		return "Autor desconhecido";
	}

	return book.author_name.join(", ");
}

function getSummary(book) {
	const year = book.first_publish_year ? `Publicado pela primeira vez em ${book.first_publish_year}.` : "";
	const editions = book.edition_count ? `${book.edition_count} edicoes encontradas.` : "";
	const language = book.language?.includes("por") ? "Possui resultado em portugues." : "";

	return [year, editions, language].filter(Boolean).join(" ");
}

function coverTone(book) {
	const tones = ["moss", "wine", "navy", "slate", "clay", "olive"];
	const titleLength = book.title?.length || 0;
	return tones[titleLength % tones.length];
}

function renderEmptyState(message) {
	bookGrid.replaceChildren();

	const emptyState = document.createElement("p");
	emptyState.className = "empty-state";
	emptyState.textContent = message;
	bookGrid.append(emptyState);
}

function shortTitle(title) {
	return title.length > 34 ? `${title.slice(0, 31)}...` : title;
}

fetchBooks("literatura brasileira");
