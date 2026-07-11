const searchForm = document.querySelector("#searchForm");
const searchInput = document.querySelector("#searchInput");
const bookGrid = document.querySelector("#bookGrid");
const resultCount = document.querySelector("#resultCount");
const filterButtons = document.querySelectorAll("[data-access]");
const sourceFilter = document.querySelector("#sourceFilter");
const sortFilter = document.querySelector("#sortFilter");
const yearFromInput = document.querySelector("#yearFrom");
const yearToInput = document.querySelector("#yearTo");
const minRatingFilter = document.querySelector("#minRating");
const hasCoverFilter = document.querySelector("#hasCover");
const multipleSourcesFilter = document.querySelector("#multipleSources");
const clearFiltersButton = document.querySelector("#clearFilters");
const activeFilterCount = document.querySelector("#activeFilterCount");
const advancedFilters = document.querySelector("#advancedFilters");
const filterFeedback = document.querySelector("#filterFeedback");
const pagination = document.querySelector("#pagination");
const bookDialog = document.querySelector("#bookDialog");
const bookDialogContent = document.querySelector("#bookDialogContent");
const bookDialogClose = document.querySelector("#bookDialogClose");
const VALID_SORTS = new Set(["RELEVANCE", "POPULAR", "RATING", "NEWEST", "OLDEST", "TITLE"]);
const VALID_ACCESS_TYPES = new Set(["ALL", "FREE", "PAID", "UNKNOWN"]);
const VOTER_STORAGE_KEY = "openbook-voter-key";
const ratingNumberFormat = new Intl.NumberFormat("pt-BR", {
	minimumFractionDigits: 1,
	maximumFractionDigits: 1
});
const countNumberFormat = new Intl.NumberFormat("pt-BR");
const DEFAULT_STATE = Object.freeze({
	term: "",
	access: "ALL",
	source: "ALL",
	sort: "RELEVANCE",
	yearFrom: "",
	yearTo: "",
	minRating: "",
	hasCover: false,
	multipleSources: false,
	page: 1,
	size: 18
});

let activeRequestController;
let openRatingControl;
let cardSequence = 0;
let voterKeyCache;
let dialogReturnFocus;
const submittedRatings = new Map();
const communityRatingsCache = new Map();
const ratingControlsByBook = new Map();

const state = { ...DEFAULT_STATE };

bookDialogClose.addEventListener("click", () => bookDialog.close());
bookDialog.addEventListener("click", (event) => {
	if (event.target === bookDialog) {
		bookDialog.close();
	}
});
bookDialog.addEventListener("close", () => {
	document.body.classList.remove("modal-open");
	unregisterRatingControls(bookDialogContent);
	bookDialogContent.replaceChildren();
	if (dialogReturnFocus?.isConnected) {
		dialogReturnFocus.focus();
	}
	dialogReturnFocus = null;
});

restoreStateFromUrl();
syncControlsFromState();

searchForm.addEventListener("submit", (event) => {
	event.preventDefault();
	state.term = searchInput.value.trim();
	state.page = 1;
	loadBooks();
});

filterButtons.forEach((button) => {
	button.addEventListener("click", () => {
		state.access = button.dataset.access;
		state.page = 1;
		syncAccessButtons();
		loadBooks();
	});
});

sourceFilter.addEventListener("change", () => {
	state.source = sourceFilter.value;
	state.page = 1;
	loadBooks();
});

sortFilter.addEventListener("change", () => {
	state.sort = VALID_SORTS.has(sortFilter.value) ? sortFilter.value : DEFAULT_STATE.sort;
	state.page = 1;
	loadBooks();
});

[yearFromInput, yearToInput, minRatingFilter, hasCoverFilter, multipleSourcesFilter].forEach((control) => {
	control.addEventListener("change", applyAdvancedFilters);
});

clearFiltersButton.addEventListener("click", clearFilters);

window.addEventListener("popstate", () => {
	restoreStateFromUrl();
	syncControlsFromState();
	loadBooks();
});

function applyAdvancedFilters() {
	const yearFrom = yearFromInput.value.trim();
	const yearTo = yearToInput.value.trim();

	if (!validateYearRange(yearFrom, yearTo)) {
		return;
	}

	state.yearFrom = yearFrom;
	state.yearTo = yearTo;
	state.minRating = minRatingFilter.value;
	state.hasCover = hasCoverFilter.checked;
	state.multipleSources = multipleSourcesFilter.checked;
	state.page = 1;
	updateActiveFilterCount();
	loadBooks();
}

function validateYearRange(yearFrom, yearTo) {
	const fromNumber = yearFrom ? Number(yearFrom) : null;
	const toNumber = yearTo ? Number(yearTo) : null;
	const fromInvalid = fromNumber !== null && (!Number.isInteger(fromNumber) || fromNumber < 1000 || fromNumber > 2100);
	const toInvalid = toNumber !== null && (!Number.isInteger(toNumber) || toNumber < 1000 || toNumber > 2100);

	yearFromInput.setAttribute("aria-invalid", String(fromInvalid));
	yearToInput.setAttribute("aria-invalid", String(toInvalid));

	if (fromInvalid || toInvalid) {
		filterFeedback.textContent = "Informe anos entre 1000 e 2100.";
		return false;
	}

	if (fromNumber !== null && toNumber !== null && fromNumber > toNumber) {
		yearFromInput.setAttribute("aria-invalid", "true");
		yearToInput.setAttribute("aria-invalid", "true");
		filterFeedback.textContent = "O ano inicial precisa ser menor ou igual ao ano final.";
		return false;
	}

	filterFeedback.textContent = "";
	return true;
}

function clearFilters() {
	Object.assign(state, {
		access: DEFAULT_STATE.access,
		source: DEFAULT_STATE.source,
		sort: DEFAULT_STATE.sort,
		yearFrom: DEFAULT_STATE.yearFrom,
		yearTo: DEFAULT_STATE.yearTo,
		minRating: DEFAULT_STATE.minRating,
		hasCover: DEFAULT_STATE.hasCover,
		multipleSources: DEFAULT_STATE.multipleSources,
		page: 1
	});
	filterFeedback.textContent = "Filtros removidos.";
	syncControlsFromState();
	loadBooks();
}

function syncControlsFromState() {
	searchInput.value = state.term;
	sourceFilter.value = state.source;
	sortFilter.value = state.sort;
	yearFromInput.value = state.yearFrom;
	yearToInput.value = state.yearTo;
	minRatingFilter.value = state.minRating;
	hasCoverFilter.checked = state.hasCover;
	multipleSourcesFilter.checked = state.multipleSources;
	yearFromInput.setAttribute("aria-invalid", "false");
	yearToInput.setAttribute("aria-invalid", "false");
	syncAccessButtons();
	updateActiveFilterCount();
}

function syncAccessButtons() {
	filterButtons.forEach((button) => {
		const isActive = button.dataset.access === state.access;
		button.classList.toggle("active", isActive);
		button.setAttribute("aria-pressed", String(isActive));
	});
}

function updateActiveFilterCount() {
	const count = [
		Boolean(state.yearFrom),
		Boolean(state.yearTo),
		Boolean(state.minRating),
		state.hasCover,
		state.multipleSources
	].filter(Boolean).length;

	activeFilterCount.textContent = String(count);
	activeFilterCount.hidden = count === 0;

	if (count > 0) {
		advancedFilters.open = true;
	}
}

function restoreStateFromUrl() {
	Object.assign(state, DEFAULT_STATE);
	const params = new URLSearchParams(window.location.search);
	const requestedAccess = params.get("access") || DEFAULT_STATE.access;
	const requestedSort = params.get("sort") || DEFAULT_STATE.sort;
	const requestedPage = Number(params.get("page"));

	state.term = (params.get("term") || "").trim();
	state.access = VALID_ACCESS_TYPES.has(requestedAccess) ? requestedAccess : DEFAULT_STATE.access;
	state.source = (params.get("source") || DEFAULT_STATE.source).trim() || DEFAULT_STATE.source;
	state.sort = VALID_SORTS.has(requestedSort) ? requestedSort : DEFAULT_STATE.sort;
	state.yearFrom = validYearParam(params.get("yearFrom"));
	state.yearTo = validYearParam(params.get("yearTo"));
	state.minRating = ["3", "4", "4.5"].includes(params.get("minRating")) ? params.get("minRating") : "";
	state.hasCover = params.get("hasCover") === "true";
	state.multipleSources = params.get("multipleSources") === "true";
	state.page = Number.isInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1;

	if (state.yearFrom && state.yearTo && Number(state.yearFrom) > Number(state.yearTo)) {
		state.yearFrom = "";
		state.yearTo = "";
	}
}

function validYearParam(value) {
	if (!value) {
		return "";
	}

	const year = Number(value);
	return Number.isInteger(year) && year >= 1000 && year <= 2100 ? String(year) : "";
}

function syncUrlFromState() {
	const params = new URLSearchParams();

	setNonDefaultParam(params, "term", state.term, DEFAULT_STATE.term);
	setNonDefaultParam(params, "access", state.access, DEFAULT_STATE.access);
	setNonDefaultParam(params, "source", state.source, DEFAULT_STATE.source);
	setNonDefaultParam(params, "sort", state.sort, DEFAULT_STATE.sort);
	setNonDefaultParam(params, "yearFrom", state.yearFrom, DEFAULT_STATE.yearFrom);
	setNonDefaultParam(params, "yearTo", state.yearTo, DEFAULT_STATE.yearTo);
	setNonDefaultParam(params, "minRating", state.minRating, DEFAULT_STATE.minRating);
	setNonDefaultParam(params, "hasCover", state.hasCover, DEFAULT_STATE.hasCover);
	setNonDefaultParam(params, "multipleSources", state.multipleSources, DEFAULT_STATE.multipleSources);
	setNonDefaultParam(params, "page", state.page, DEFAULT_STATE.page);

	const query = params.toString();
	const nextUrl = `${window.location.pathname}${query ? `?${query}` : ""}${window.location.hash}`;
	window.history.replaceState(null, "", nextUrl);
}

function setNonDefaultParam(params, name, value, defaultValue) {
	if (value !== defaultValue && value !== "" && value !== false) {
		params.set(name, String(value));
	}
}

async function loadSources() {
	try {
		const response = await fetch("/api/books/sources");

		if (!response.ok) {
			throw new Error("Falha ao buscar fontes");
		}

		const sources = await response.json();
		renderSourceOptions(sources);
	} catch (error) {
		renderSourceOptions([]);
	}
}

function renderSourceOptions(sources) {
	sourceFilter.replaceChildren(createSourceOption("ALL", "Todas as fontes"));

	sources.forEach((source) => {
		if (!source) {
			return;
		}

		sourceFilter.append(createSourceOption(source, source));
	});

	const hasSelectedSource = Array.from(sourceFilter.options).some((option) => option.value === state.source);
	if (!hasSelectedSource) {
		state.source = DEFAULT_STATE.source;
	}

	sourceFilter.value = state.source;
}

function createSourceOption(value, label) {
	const option = document.createElement("option");
	option.value = value;
	option.textContent = label;
	return option;
}

async function loadBooks() {
	if (activeRequestController) {
		activeRequestController.abort();
	}

	const requestController = new AbortController();
	activeRequestController = requestController;

	const params = new URLSearchParams({
		term: state.term,
		access: state.access,
		source: state.source,
		sort: state.sort,
		hasCover: String(state.hasCover),
		multipleSources: String(state.multipleSources),
		page: state.page,
		size: state.size
	});

	if (state.yearFrom) {
		params.set("yearFrom", state.yearFrom);
	}

	if (state.yearTo) {
		params.set("yearTo", state.yearTo);
	}

	if (state.minRating) {
		params.set("minRating", state.minRating);
	}

	syncUrlFromState();
	openRatingControl = null;
	bookGrid.replaceChildren();
	bookGrid.setAttribute("aria-busy", "true");
	pagination.replaceChildren();
	resultCount.textContent = "Buscando...";

	try {
		const response = await fetch(`/api/books?${params.toString()}`, {
			signal: requestController.signal
		});

		if (!response.ok) {
			throw new Error("Falha ao buscar dados");
		}

		const pageData = await response.json();
		if (requestController !== activeRequestController) {
			return;
		}

		renderBooks(pageData);
	} catch (error) {
		if (error.name === "AbortError") {
			return;
		}

		resultCount.textContent = "0 livros";
		renderEmptyState(`Erro ao buscar livros: ${error.message}`);
	} finally {
		if (requestController === activeRequestController) {
			activeRequestController = null;
			bookGrid.setAttribute("aria-busy", "false");
		}
	}
}

function renderBooks(pageData) {
	const books = Array.isArray(pageData.books) ? pageData.books : [];

	if (bookDialog.open) {
		bookDialog.close();
	}
	ratingControlsByBook.clear();
	cardSequence = 0;
	openRatingControl = null;
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
	const bookTitle = book.title || "Titulo desconhecido";
	const bookAuthor = book.author || "Autor desconhecido";

	const cover = document.createElement("div");
	cover.className = `book-cover tone-${book.coverTone || "moss"}`;

	if (book.coverUrl) {
		cover.classList.add("has-image");

		const coverImage = document.createElement("img");
		coverImage.src = book.coverUrl;
		coverImage.alt = `Capa de ${bookTitle}`;
		coverImage.loading = "lazy";
		cover.append(coverImage);
	} else {
		const coverTitle = document.createElement("span");
		coverTitle.textContent = shortTitle(bookTitle);
		cover.append(coverTitle);
	}

	const info = document.createElement("div");
	info.className = "book-info";

	const title = document.createElement("h3");
	title.textContent = bookTitle;

	const author = document.createElement("p");
	author.className = "book-author";
	author.textContent = bookAuthor;

	const meta = document.createElement("dl");
	meta.className = "book-meta book-meta-single";
	meta.append(metaItem("Publicacao", publicationText(book)));

	const rating = createRatingControl(book, bookTitle, bookAuthor);

	const availability = document.createElement("span");
	const accessType = (book.accessType || "UNKNOWN").toLowerCase();
	availability.className = `availability availability-${accessType}`;
	availability.textContent = book.accessLabel || "A verificar";

	const summary = document.createElement("p");
	summary.className = "book-summary";
	summary.textContent = book.summary || "Sem resumo disponivel.";

	const sources = createSources(book);
	const detailsHint = document.createElement("button");
	detailsHint.type = "button";
	detailsHint.className = "card-details-hint";
	detailsHint.textContent = "Ver detalhes";
	detailsHint.setAttribute("aria-haspopup", "dialog");
	detailsHint.setAttribute("aria-label", `Ver detalhes de ${bookTitle}`);
	detailsHint.addEventListener("click", () => openBookDetails(book, detailsHint));

	info.append(title, author, meta, rating, availability, summary, sources, detailsHint);
	card.append(cover, info);
	card.addEventListener("click", (event) => {
		if (!isInteractiveTarget(event.target)) {
			openBookDetails(book, detailsHint);
		}
	});

	return card;
}

function isInteractiveTarget(target) {
	return target instanceof Element
			&& Boolean(target.closest("a, button, input, label, select, textarea, summary"));
}

function openBookDetails(book, returnFocus) {
	unregisterRatingControls(bookDialogContent);
	bookDialogContent.replaceChildren();
	dialogReturnFocus = returnFocus || document.activeElement;

	const bookTitle = book.title || "Titulo desconhecido";
	const bookAuthor = book.author || "Autor desconhecido";
	const layout = document.createElement("div");
	layout.className = "book-detail-layout";

	const visual = document.createElement("div");
	visual.className = "book-detail-visual";
	visual.append(createDetailCover(book, bookTitle));

	const content = document.createElement("div");
	content.className = "book-detail-content";

	const eyebrow = document.createElement("p");
	eyebrow.className = "eyebrow";
	eyebrow.textContent = "Detalhes do livro";

	const title = document.createElement("h2");
	title.id = "bookDialogTitle";
	title.textContent = bookTitle;

	const author = document.createElement("p");
	author.className = "book-detail-author";
	author.textContent = bookAuthor;

	const badges = document.createElement("div");
	badges.className = "book-detail-badges";
	const availability = document.createElement("span");
	const accessType = (book.accessType || "UNKNOWN").toLowerCase();
	availability.className = `availability availability-${accessType}`;
	availability.textContent = book.accessLabel || "A verificar";
	badges.append(availability);

	if (Number(book.sourceCount) > 1) {
		const sourceBadge = document.createElement("span");
		sourceBadge.className = "detail-source-badge";
		sourceBadge.textContent = `${book.sourceCount} fontes confirmadas`;
		badges.append(sourceBadge);
	}

	const meta = document.createElement("dl");
	meta.className = "book-detail-meta";
	meta.append(
		metaItem("Publicacao", publicationText(book)),
		metaItem("Tema", book.subject || "Nao informado"),
		metaItem("Fontes", sourceCountText(book))
	);

	const popularity = countValue(book.popularity);
	if (popularity > 0) {
		meta.append(metaItem("Popularidade publica", `${countNumberFormat.format(popularity)} registros`));
	}

	const rating = createRatingControl(book, bookTitle, bookAuthor);
	rating.classList.add("book-detail-rating");

	const summaryTitle = document.createElement("h3");
	summaryTitle.className = "book-detail-section-title";
	summaryTitle.textContent = "Sinopse e informacoes";

	const summary = document.createElement("p");
	summary.className = "book-detail-summary";
	summary.textContent = book.summary || "Sem resumo disponivel.";

	const sourcesTitle = document.createElement("h3");
	sourcesTitle.className = "book-detail-section-title";
	sourcesTitle.textContent = "Onde encontrar";
	const sources = createSources(book);
	sources.classList.add("book-detail-sources");

	content.append(eyebrow, title, author, badges, meta, rating, summaryTitle, summary, sourcesTitle, sources);
	layout.append(visual, content);
	bookDialogContent.append(layout);

	if (!bookDialog.open) {
		if (typeof bookDialog.showModal === "function") {
			bookDialog.showModal();
		} else {
			bookDialog.setAttribute("open", "");
		}
	}

	document.body.classList.add("modal-open");
	bookDialogClose.focus();
}

function createDetailCover(book, bookTitle) {
	const cover = document.createElement("div");
	cover.className = `book-detail-cover tone-${book.coverTone || "moss"}`;

	if (book.coverUrl) {
		cover.classList.add("has-image");
		const image = document.createElement("img");
		image.src = book.coverUrl;
		image.alt = `Capa de ${bookTitle}`;
		cover.append(image);
	} else {
		const title = document.createElement("span");
		title.textContent = shortTitle(bookTitle);
		cover.append(title);
	}

	return cover;
}

function sourceCountText(book) {
	const count = Math.max(Number(book.sourceCount) || 0, Array.isArray(book.sources) ? book.sources.length : 0, 1);
	return count === 1 ? "1 catalogo" : `${count} catalogos`;
}

function publicationText(book) {
	if (book.publishedYear) {
		return String(book.publishedYear);
	}

	return book.publishedDate || "Nao informado";
}

function createRatingControl(book, bookTitle, bookAuthor) {
	const bookKey = String(book.ratingKey || book.id || "").trim();
	const controlId = ++cardSequence;
	const data = normalizeRatingData(book, bookKey);
	const wrapper = document.createElement("div");
	wrapper.className = "book-rating";

	const row = document.createElement("div");
	row.className = "rating-row";

	const summary = document.createElement("div");
	summary.className = "rating-summary";

	const icon = document.createElement("span");
	icon.className = "rating-icon";
	icon.setAttribute("aria-hidden", "true");
	icon.textContent = "\u2605";

	const value = document.createElement("strong");
	value.className = "rating-value";

	const count = document.createElement("span");
	count.className = "rating-count";
	summary.append(icon, value, count);

	const trigger = document.createElement("button");
	trigger.type = "button";
	trigger.className = "rate-trigger";
	trigger.setAttribute("aria-expanded", "false");
	trigger.setAttribute("aria-controls", `rating-editor-${controlId}`);

	const editor = document.createElement("div");
	editor.className = "rating-editor";
	editor.id = `rating-editor-${controlId}`;
	editor.hidden = true;

	const fieldset = document.createElement("fieldset");
	const legend = document.createElement("legend");
	legend.className = "sr-only";
	legend.textContent = `Avalie ${bookTitle}`;

	const starOptions = document.createElement("div");
	starOptions.className = "star-options";

	const status = document.createElement("p");
	status.className = "rating-status";
	status.id = `rating-status-${controlId}`;
	status.setAttribute("role", "status");
	status.setAttribute("aria-live", "polite");

	fieldset.append(legend, starOptions);
	editor.append(fieldset, status);

	const details = document.createElement("p");
	details.className = "rating-details";

	const control = {
		bookKey,
		data,
		wrapper,
		summary,
		value,
		count,
		details,
		trigger,
		editor,
		fieldset,
		starOptions,
		status
	};

	for (let score = 1; score <= 5; score++) {
		const input = document.createElement("input");
		input.className = "sr-only star-input";
		input.type = "radio";
		input.name = `rating-${controlId}`;
		input.id = `rating-${controlId}-${score}`;
		input.value = String(score);
		input.setAttribute("aria-describedby", status.id);

		const label = document.createElement("label");
		label.className = "star-label";
		label.htmlFor = input.id;
		label.dataset.score = String(score);
		label.title = score === 1 ? "1 estrela" : `${score} estrelas`;
		label.setAttribute("aria-label", label.title);
		label.textContent = "\u2605";

		input.addEventListener("change", () => {
			setRatingSelection(starOptions, score);
			submitRating(bookKey, bookTitle, bookAuthor, score, control);
		});

		starOptions.append(input, label);
	}

	if (!bookKey) {
		trigger.disabled = true;
		trigger.title = "Este livro ainda nao possui identificador para avaliacao.";
	}

	trigger.addEventListener("click", () => toggleRatingControl(control));
	editor.addEventListener("keydown", (event) => {
		if (event.key === "Escape") {
			event.preventDefault();
			closeRatingControl(control, true);
		}
	});

	row.append(summary, trigger);
	wrapper.append(row, details, editor);
	wrapper.ratingControl = control;
	wrapper.addEventListener("click", (event) => event.stopPropagation());
	registerRatingControl(control);
	renderRatingControl(control);
	return wrapper;
}

function registerRatingControl(control) {
	if (!control.bookKey) {
		return;
	}

	const controls = ratingControlsByBook.get(control.bookKey) || new Set();
	controls.add(control);
	ratingControlsByBook.set(control.bookKey, controls);
}

function unregisterRatingControls(container) {
	container.querySelectorAll?.(".book-rating").forEach((wrapper) => {
		const control = wrapper.ratingControl;
		if (!control?.bookKey) {
			return;
		}
		if (openRatingControl === control) {
			openRatingControl = null;
		}

		const controls = ratingControlsByBook.get(control.bookKey);
		controls?.delete(control);
		if (controls?.size === 0) {
			ratingControlsByBook.delete(control.bookKey);
		}
	});
}

function normalizeRatingData(book, bookKey) {
	const ratings = book.ratings && typeof book.ratings === "object" ? book.ratings : {};
	const legacy = parseLegacyRating(book.rating);
	const savedScore = submittedRatings.get(bookKey);
	const cachedCommunity = communityRatingsCache.get(bookKey);
	const externalAverage = numberOrNull(ratings.externalAverage) ?? legacy.average;
	const externalCount = countValue(ratings.externalCount || legacy.count);
	const communityAverage = numberOrNull(cachedCommunity?.average ?? ratings.communityAverage);
	const communityCount = countValue(cachedCommunity?.count ?? ratings.communityCount);
	const combinedAverage = combineRatingAverages(
		externalAverage,
		externalCount,
		communityAverage,
		communityCount
	);

	return {
		average: combinedAverage ?? numberOrNull(ratings.average) ?? legacy.average,
		totalCount: externalCount + communityCount || countValue(ratings.totalCount || legacy.count),
		externalAverage,
		externalCount,
		communityAverage,
		communityCount,
		userScore: savedScore || scoreValue(ratings.userScore)
	};
}

function combineRatingAverages(externalAverage, externalCount, communityAverage, communityCount) {
	const externalWeight = externalAverage === null ? 0 : Math.max(externalCount, 1);
	const communityWeight = communityAverage === null ? 0 : communityCount;
	const totalWeight = externalWeight + communityWeight;

	if (totalWeight === 0) {
		return null;
	}

	return (
		(externalAverage === null ? 0 : externalAverage * externalWeight)
				+ (communityAverage === null ? 0 : communityAverage * communityWeight)
	) / totalWeight;
}

function parseLegacyRating(rating) {
	const match = String(rating || "").match(/(\d+(?:[.,]\d+)?)(?:\s*\/\s*5)?(?:\s*\((\d+)\))?/);

	if (!match) {
		return { average: null, count: 0 };
	}

	return {
		average: numberOrNull(match[1]),
		count: countValue(match[2])
	};
}

function renderRatingControl(control) {
	const { data, summary, value, count, details, trigger, starOptions } = control;

	if (data.average !== null) {
		value.textContent = ratingNumberFormat.format(data.average);
		count.textContent = data.totalCount > 0
				? `· ${ratingCountText(data.totalCount)}`
				: "· nota inicial";
		summary.setAttribute(
				"aria-label",
				`Nota ${ratingNumberFormat.format(data.average)} de 5, ${ratingCountText(data.totalCount)}`
		);
	} else {
		value.textContent = "Sem nota";
		count.textContent = "· avalie primeiro";
		summary.setAttribute("aria-label", "Livro ainda sem avaliacao");
	}

	const detailParts = [];
	if (data.externalAverage !== null) {
		detailParts.push(`Fontes ${ratingNumberFormat.format(data.externalAverage)}${ratingCountSuffix(data.externalCount)}`);
	}

	if (data.communityAverage !== null && data.communityCount > 0) {
		detailParts.push(`OpenBook ${ratingNumberFormat.format(data.communityAverage)}${ratingCountSuffix(data.communityCount)}`);
	}

	details.textContent = detailParts.length > 0 ? detailParts.join(" · ") : "Avaliacoes da comunidade OpenBook";
	trigger.textContent = data.userScore ? `Sua nota: ${data.userScore}` : "Avaliar";
	setRatingSelection(starOptions, data.userScore || 0);
}

function ratingCountText(count) {
	if (count === 1) {
		return "1 avaliacao";
	}

	return `${countNumberFormat.format(count)} avaliacoes`;
}

function ratingCountSuffix(count) {
	return count > 0 ? ` (${countNumberFormat.format(count)})` : "";
}

function toggleRatingControl(control) {
	if (openRatingControl === control) {
		closeRatingControl(control, true);
		return;
	}

	if (openRatingControl) {
		closeRatingControl(openRatingControl, false);
	}

	control.editor.hidden = false;
	control.trigger.setAttribute("aria-expanded", "true");
	openRatingControl = control;

	window.requestAnimationFrame(() => {
		const selectedInput = control.starOptions.querySelector("input:checked");
		const firstInput = control.starOptions.querySelector("input");
		(selectedInput || firstInput)?.focus();
	});
}

function closeRatingControl(control, restoreFocus) {
	control.editor.hidden = true;
	control.trigger.setAttribute("aria-expanded", "false");

	if (openRatingControl === control) {
		openRatingControl = null;
	}

	if (restoreFocus) {
		control.trigger.focus();
	}
}

function setRatingSelection(starOptions, selectedScore) {
	starOptions.querySelectorAll(".star-input").forEach((input) => {
		input.checked = Number(input.value) === Number(selectedScore);
	});

	starOptions.querySelectorAll(".star-label").forEach((label) => {
		label.classList.toggle("selected", Number(label.dataset.score) <= Number(selectedScore));
	});
}

async function submitRating(bookKey, title, author, score, control) {
	const previousScore = control.data.userScore || 0;
	control.fieldset.disabled = true;
	control.status.classList.remove("rating-status-error");
	control.status.textContent = "Salvando sua avaliacao...";

	try {
		const response = await fetch("/api/ratings", {
			method: "POST",
			headers: {
				"Content-Type": "application/json"
			},
			body: JSON.stringify({
				bookKey,
				title,
				author,
				score,
				voterKey: getVoterKey()
			})
		});
		const responseData = await response.json().catch(() => ({}));

		if (!response.ok) {
			throw new Error(responseData.detail || responseData.message || "Nao foi possivel salvar sua avaliacao.");
		}

		const userScore = scoreValue(responseData.userScore) || score;
		const updatedAverage = numberOrNull(responseData.average) ?? score;
		const updatedCount = countValue(responseData.count);
		submittedRatings.set(bookKey, userScore);
		communityRatingsCache.set(bookKey, { average: updatedAverage, count: updatedCount });
		updateRatingControls(bookKey, updatedAverage, updatedCount, userScore);
		control.status.textContent = `Sua avaliacao de ${userScore} ${userScore === 1 ? "estrela foi salva" : "estrelas foi salva"}.`;
	} catch (error) {
		control.data.userScore = previousScore;
		setRatingSelection(control.starOptions, previousScore);
		control.status.classList.add("rating-status-error");
		control.status.textContent = error.message || "Nao foi possivel salvar. Tente novamente.";
	} finally {
		control.fieldset.disabled = false;
	}
}

function updateRatingControls(bookKey, communityAverage, communityCount, userScore) {
	const controls = ratingControlsByBook.get(bookKey) || [];

	controls.forEach((item) => {
		item.data.communityAverage = communityAverage;
		item.data.communityCount = communityCount;
		item.data.average = combineRatingAverages(
			item.data.externalAverage,
			item.data.externalCount,
			communityAverage,
			communityCount
		);
		item.data.totalCount = item.data.externalCount + communityCount;
		item.data.userScore = userScore;
		renderRatingControl(item);
	});
}

function getVoterKey() {
	if (voterKeyCache) {
		return voterKeyCache;
	}

	try {
		const savedKey = window.localStorage.getItem(VOTER_STORAGE_KEY);
		if (isUuid(savedKey)) {
			voterKeyCache = savedKey;
			return voterKeyCache;
		}
	} catch (error) {
		// O navegador pode bloquear o armazenamento; o voto ainda funciona nesta aba.
	}

	voterKeyCache = createUuid();

	try {
		window.localStorage.setItem(VOTER_STORAGE_KEY, voterKeyCache);
	} catch (error) {
		// Mantem a chave em memoria quando o armazenamento local nao esta disponivel.
	}

	return voterKeyCache;
}

function isUuid(value) {
	return typeof value === "string"
			&& /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function createUuid() {
	if (window.crypto?.randomUUID) {
		return window.crypto.randomUUID();
	}

	const bytes = new Uint8Array(16);
	if (window.crypto?.getRandomValues) {
		window.crypto.getRandomValues(bytes);
	} else {
		for (let index = 0; index < bytes.length; index++) {
			bytes[index] = Math.floor(Math.random() * 256);
		}
	}

	bytes[6] = (bytes[6] & 0x0f) | 0x40;
	bytes[8] = (bytes[8] & 0x3f) | 0x80;
	const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0"));
	return `${hex.slice(0, 4).join("")}-${hex.slice(4, 6).join("")}-${hex.slice(6, 8).join("")}-${hex.slice(8, 10).join("")}-${hex.slice(10).join("")}`;
}

function numberOrNull(value) {
	if (value === null || value === undefined || value === "") {
		return null;
	}

	const number = Number(String(value).replace(",", "."));
	return Number.isFinite(number) ? number : null;
}

function countValue(value) {
	const number = Number(value);
	return Number.isFinite(number) && number > 0 ? Math.trunc(number) : 0;
}

function scoreValue(value) {
	const number = Number(value);
	return Number.isInteger(number) && number >= 1 && number <= 5 ? number : 0;
}

function metaItem(label, value) {
	const wrapper = document.createElement("div");
	wrapper.className = "meta-item";

	const term = document.createElement("dt");
	term.textContent = label;

	const description = document.createElement("dd");
	description.textContent = value;

	wrapper.append(term, description);
	return wrapper;
}

function createSources(book) {
	const wrapper = document.createElement("div");
	wrapper.className = "book-sources";

	const label = document.createElement("span");
	label.className = "source-label";
	label.textContent = book.sourceCount > 1 ? "Fontes" : "Fonte";
	wrapper.append(label);

	const sources = Array.isArray(book.sources) && book.sources.length > 0
			? book.sources
			: [{ name: book.source, url: book.externalUrl }];

	sources.forEach((item) => {
		const source = item.url ? document.createElement("a") : document.createElement("span");
		source.className = item.url ? "source-chip source-chip-link" : "source-chip";
		source.textContent = item.name || "Fonte";

		if (item.url) {
			source.href = item.url;
			source.target = "_blank";
			source.rel = "noreferrer";
		}

		wrapper.append(source);
	});

	return wrapper;
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
	if (!title) {
		return "";
	}

	return title.length > 34 ? `${title.slice(0, 31)}...` : title;
}

loadSources();
loadBooks();
