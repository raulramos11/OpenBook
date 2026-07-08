const searchForm = document.querySelector("#searchForm");
const searchInput = document.querySelector("#searchInput");
const bookGrid = document.querySelector("#bookGrid");
const resultCount = document.querySelector("#resultCount");
const filterButtons = document.querySelectorAll("[data-access]");
const pagination = document.querySelector("#pagination");

const state = {
	term: "",
	access: "ALL",
	page: 1,
	size: 18
};

searchForm.addEventListener("submit", (event) => {
	event.preventDefault();
	state.term = searchInput.value.trim();
	state.page = 1;
	loadBooks();
});

filterButtons.forEach((button) => {
	button.addEventListener("click", () => {
		filterButtons.forEach((item) => item.classList.remove("active"));
		button.classList.add("active");
		state.access = button.dataset.access;
		state.page = 1;
		loadBooks();
	});
});

async function loadBooks() {
	const params = new URLSearchParams({
		term: state.term,
		access: state.access,
		page: state.page,
		size: state.size
	});

	bookGrid.replaceChildren();
	pagination.replaceChildren();
	resultCount.textContent = "Buscando...";

	try {
		const response = await fetch(`/api/books?${params.toString()}`);

		if (!response.ok) {
			throw new Error("Falha ao buscar dados");
		}

		const pageData = await response.json();
		renderBooks(pageData);
	} catch (error) {
		resultCount.textContent = "0 livros";
		renderEmptyState(`Erro ao buscar livros: ${error.message}`);
	}
}

function renderBooks(pageData) {
	const books = pageData.books || [];

	bookGrid.replaceChildren();
	resultCount.textContent = resultText(pageData);

	if (books.length === 0) {
		renderEmptyState("Nenhum livro encontrado para esta busca.");
		renderPagination(pageData);
		return;
	}

	books.forEach((book) => {
		bookGrid.append(createBookCard(book));
	});

	renderPagination(pageData);
}

function createBookCard(book) {
	const card = document.createElement("article");
	card.className = "book-card";

	const cover = document.createElement("div");
	cover.className = `book-cover tone-${book.coverTone}`;

	if (book.coverUrl) {
		cover.classList.add("has-image");

		const coverImage = document.createElement("img");
		coverImage.src = book.coverUrl;
		coverImage.alt = `Capa de ${book.title}`;
		coverImage.loading = "lazy";
		cover.append(coverImage);
	} else {
		const coverTitle = document.createElement("span");
		coverTitle.textContent = shortTitle(book.title);
		cover.append(coverTitle);
	}

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

	const source = createSource(book);

	info.append(title, author, availability, summary, source);
	card.append(cover, info);

	return card;
}

function createSource(book) {
	if (!book.externalUrl) {
		const source = document.createElement("p");
		source.className = "book-source";
		source.textContent = `Fonte: ${book.source}`;
		return source;
	}

	const source = document.createElement("a");
	source.className = "book-source book-source-link";
	source.href = book.externalUrl;
	source.target = "_blank";
	source.rel = "noreferrer";
	source.textContent = `Fonte: ${book.source}`;
	return source;
}

function renderEmptyState(message) {
	bookGrid.replaceChildren();

	const emptyState = document.createElement("p");
	emptyState.className = "empty-state";
	emptyState.textContent = message;
	bookGrid.append(emptyState);
}

function renderPagination(pageData) {
	pagination.replaceChildren();

	if (!pageData.totalPages || pageData.totalPages <= 1) {
		return;
	}

	const previousButton = paginationButton("Anterior", pageData.page - 1, !pageData.hasPrevious);
	pagination.append(previousButton);

	visiblePages(pageData.page, pageData.totalPages).forEach((pageNumber) => {
		if (pageNumber === "...") {
			const gap = document.createElement("span");
			gap.className = "pagination-gap";
			gap.textContent = "...";
			pagination.append(gap);
			return;
		}

		const button = paginationButton(String(pageNumber), pageNumber, false);
		button.classList.toggle("active", pageNumber === pageData.page);
		pagination.append(button);
	});

	const nextButton = paginationButton("Proxima", pageData.page + 1, !pageData.hasNext);
	pagination.append(nextButton);
}

function paginationButton(label, page, disabled) {
	const button = document.createElement("button");
	button.type = "button";
	button.textContent = label;
	button.disabled = disabled;
	button.addEventListener("click", () => {
		state.page = page;
		loadBooks();
		document.querySelector("#acervo").scrollIntoView({ behavior: "smooth", block: "start" });
	});

	return button;
}

function visiblePages(currentPage, totalPages) {
	const pages = new Set([1, totalPages]);
	const start = Math.max(1, currentPage - 2);
	const end = Math.min(totalPages, currentPage + 2);

	for (let page = start; page <= end; page++) {
		pages.add(page);
	}

	return [...pages]
			.sort((a, b) => a - b)
			.flatMap((page, index, sortedPages) => {
				if (index === 0 || page - sortedPages[index - 1] === 1) {
					return [page];
				}

				return ["...", page];
			});
}

function resultText(pageData) {
	const total = pageData.totalItems || 0;

	if (total === 0) {
		return "0 livros";
	}

	const page = pageData.page || 1;
	const totalPages = pageData.totalPages || 1;
	const label = total === 1 ? "livro" : "livros";

	return `${total} ${label} - pagina ${page} de ${totalPages}`;
}

function shortTitle(title) {
	return title.length > 34 ? `${title.slice(0, 31)}...` : title;
}

loadBooks();
