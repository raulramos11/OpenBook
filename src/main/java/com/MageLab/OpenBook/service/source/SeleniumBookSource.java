package com.MageLab.OpenBook.service.source;

import com.MageLab.OpenBook.model.AccessType;
import com.MageLab.OpenBook.model.Book;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class SeleniumBookSource extends BookSourceSupport implements BookSourceClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(SeleniumBookSource.class);
	private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration ELEMENT_WAIT_TIMEOUT = Duration.ofSeconds(10);

	@Value("${openbook.selenium-source.enabled:false}")
	private boolean enabled;

	@Value("${openbook.selenium-source.url:}")
	private String sourceUrl;

	@Value("${openbook.selenium-source.name:Fonte Selenium}")
	private String sourceName;

	@Value("${openbook.selenium-source.result-selector:a[href]}")
	private String resultSelector;

	@Value("${openbook.selenium-source.title-selector:}")
	private String titleSelector;

	@Value("${openbook.selenium-source.cover-selector:img}")
	private String coverSelector;

	@Value("${openbook.selenium-source.browser-binary:}")
	private String browserBinary;

	@Value("${openbook.selenium-source.driver-path:}")
	private String driverPath;

	@Override
	public BookSourceResult search(String term, int offset, int limit) {
		if (!enabled || sourceUrl.isBlank()) {
			return BookSourceResult.empty();
		}

		List<Book> pageBooks = new ArrayList<>();
		WebDriver driver = null;
		int matchedItems = 0;
		int safeOffset = Math.max(offset, 0);
		int safeLimit = Math.max(limit, 0);

		try {
			configureDriver();

			driver = new EdgeDriver(edgeOptions());
			driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT);

			String url = buildUrl(term);
			LOGGER.info("[{}] Abrindo fonte configurada via Selenium", sourceName);
			driver.get(url);

			List<WebElement> results = waitForResults(driver);
			LOGGER.info("[{}] Elementos encontrados pelo seletor '{}': {}", sourceName, resultSelector, results.size());

			for (WebElement result : results) {
				Book scrapedBook = toBook(result);

				if (scrapedBook == null || !matchesTerm(scrapedBook.title(), term)) {
					logIgnoredTitle(scrapedBook);
					continue;
				}

				if (matchedItems >= safeOffset && pageBooks.size() < safeLimit) {
					pageBooks.add(scrapedBook);
					LOGGER.info("[{}] Livro retornado pelo Selenium: '{}'", sourceName, scrapedBook.title());
				}

				matchedItems++;
			}
		} catch (Exception e) {
			LOGGER.warn("[{}] Nao foi possivel consultar a fonte Selenium: {}", sourceName, e.getMessage());
		} finally {
			if (driver != null) {
				try {
					driver.quit();
				} catch (Exception ignored) {
					// Browser ja foi encerrado ou falhou ao fechar.
				}
			}
		}

		LOGGER.info(
				"[{}] Selenium finalizou: {} livro(s) compativel(is), {} livro(s) nesta pagina",
				sourceName,
				matchedItems,
				pageBooks.size()
		);

		return new BookSourceResult(pageBooks, matchedItems);
	}

	@Override
	public String sourceName() {
		return sourceName;
	}

	@Override
	public boolean isEnabled() {
		return enabled && !sourceUrl.isBlank();
	}

	private EdgeOptions edgeOptions() {
		EdgeOptions options = new EdgeOptions();

		if (!browserBinary.isBlank()) {
			options.setBinary(browserBinary);
		}

		options.addArguments(
				"--headless=new",
				"--disable-gpu",
				"--window-size=1280,900",
				"--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
						+ "AppleWebKit/537.36 (KHTML, like Gecko) "
						+ "Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"
		);

		return options;
	}

	private void configureDriver() {
		if (!driverPath.isBlank()) {
			System.setProperty("webdriver.edge.driver", driverPath);
			return;
		}

		WebDriverManager.edgedriver().setup();
	}

	private String buildUrl(String term) {
		if (sourceUrl.contains("{term}")) {
			return sourceUrl.replace("{term}", encode(term));
		}

		return sourceUrl;
	}

	private List<WebElement> waitForResults(WebDriver driver) {
		WebDriverWait wait = new WebDriverWait(driver, ELEMENT_WAIT_TIMEOUT);
		return wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector(resultSelector)));
	}

	private Book toBook(WebElement result) {
		try {
			WebElement link = linkElement(result);
			String href = link.getAttribute("href");
			String title = titleFrom(result, link);

			if (title.isBlank() || href == null || href.isBlank()) {
				return null;
			}

			String coverUrl = coverFrom(result);

			return book(
					sourceName,
					href,
					title,
					"Autor desconhecido",
					"Encontrado em fonte externa configurada localmente.",
					"Fonte externa",
					AccessType.UNKNOWN,
					href,
					coverUrl
			);
		} catch (Exception e) {
			return null;
		}
	}

	private WebElement linkElement(WebElement result) {
		if ("a".equalsIgnoreCase(result.getTagName())) {
			return result;
		}

		return result.findElement(By.cssSelector("a[href]"));
	}

	private String titleFrom(WebElement result, WebElement link) {
		if (!titleSelector.isBlank()) {
			try {
				String selectorTitle = result.findElement(By.cssSelector(titleSelector)).getText().trim();
				if (!selectorTitle.isBlank()) {
					return selectorTitle;
				}
			} catch (Exception ignored) {
				// Fallback para o texto do link.
			}
		}

		String title = link.getText().trim();

		if (title.isBlank()) {
			title = blankFallback(link.getAttribute("title"), "");
		}

		if (title.isBlank()) {
			title = blankFallback(link.getAttribute("aria-label"), "");
		}

		return title.trim();
	}

	private String coverFrom(WebElement result) {
		if (coverSelector.isBlank()) {
			return "";
		}

		try {
			WebElement img = result.findElement(By.cssSelector(coverSelector));
			String src = blankFallback(img.getAttribute("src"), img.getAttribute("data-src"));
			return blankFallback(src, "");
		} catch (Exception ignored) {
			return "";
		}
	}

	private boolean matchesTerm(String title, String term) {
		if (term == null || term.isBlank()) {
			return true;
		}

		return title.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
	}

	private void logIgnoredTitle(Book scrapedBook) {
		if (scrapedBook == null || !LOGGER.isDebugEnabled()) {
			return;
		}

		LOGGER.debug("[{}] Titulo ignorado pelo filtro: '{}'", sourceName, scrapedBook.title());
	}
}
