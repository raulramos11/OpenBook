const searchForm = document.querySelector("#searchForm");
const searchInput = document.querySelector("#searchInput");
const bookGrid = document.querySelector("#bookGrid");
const resultCount = document.querySelector("#resultCount");
const filterButtons = document.querySelectorAll("[data-access]");

const state = {
	term: "",
	access: "ALL"
};

searchForm.addEventListener("submit", (event) => {
	event.preventDefault();
	loadBooks();
});

filterButtons.forEach((button) => {
	button.addEventListener("click", () => {
		filterButtons.forEach((item) => item.classList.remove("active"));
		button.classList.add("active");
		state.access = button.dataset.access;
		loadBooks();
	});
});

async function loadBooks() {
	state.term = searchInput.value.trim();

	const params = new URLSearchParams({
		term: state.term,
		access: state.access
	});

	const response = await fetch(`/api/books?${params.toString()}`);
	const books = await response.json();
	renderBooks(books);
}

function renderBooks(books) {
	bookGrid.replaceChildren();
	resultCount.textContent = `${books.length} ${books.length === 1 ? "livro" : "livros"}`;

	if (books.length === 0) {
		const emptyState = document.createElement("p");
		emptyState.className = "empty-state";
		emptyState.textContent = "Nenhum livro encontrado para esta busca.";
		bookGrid.append(emptyState);
		return;
	}

	books.forEach((book) => {
		bookGrid.append(createBookCard(book));
	});
}

function createBookCard(book) {
	const card = document.createElement("article");
	card.className = "book-card";

	const cover = document.createElement("div");
	cover.className = `book-cover tone-${book.coverTone}`;

	const coverTitle = document.createElement("span");
	coverTitle.textContent = shortTitle(book.title);
	cover.append(coverTitle);

	const info = document.createElement("div");
	info.className = "book-info";

	const title = document.createElement("h3");
	title.textContent = book.title;

	const author = document.createElement("p");
	author.className = "book-author";
	author.textContent = book.author;

	const availability = document.createElement("span");
	availability.className = `availability availability-${book.accessType.toLowerCase()}`;
	availability.textContent = book.accessLabel;

	const summary = document.createElement("p");
	summary.className = "book-summary";
	summary.textContent = book.summary;

	const source = document.createElement("p");
	source.className = "book-source";
	source.textContent = `Fonte: ${book.source}`;

	info.append(title, author, availability, summary, source);
	card.append(cover, info);

	return card;
}

function shortTitle(title) {
	return title.length > 34 ? `${title.slice(0, 31)}...` : title;
}

loadBooks();
