package com.MageLab.OpenBook.repository;

import com.MageLab.OpenBook.model.CommunityRating;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class RatingRepository {

	private static final Logger LOGGER = LoggerFactory.getLogger(RatingRepository.class);
	private static final String CREATE_TABLE_SQL = """
			CREATE TABLE IF NOT EXISTS book_ratings (
				book_key VARCHAR(64) NOT NULL,
				voter_hash VARCHAR(64) NOT NULL,
				score SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 5),
				created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
				updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
				PRIMARY KEY (book_key, voter_hash)
			)
			""";
	private static final String UPSERT_SQL = """
			INSERT INTO book_ratings (book_key, voter_hash, score)
			VALUES (?, ?, ?)
			ON CONFLICT (book_key, voter_hash)
			DO UPDATE SET score = EXCLUDED.score, updated_at = CURRENT_TIMESTAMP
			""";

	private final DatabaseConnection databaseConnection;
	private final boolean memoryEnabled;
	private final Throwable databaseInitializationFailure;
	private final Map<String, Map<String, Integer>> memoryRatings = new ConcurrentHashMap<>();

	@Autowired
	public RatingRepository(
			@Value("${openbook.ratings.database-url:}") String databaseUrl,
			@Value("${openbook.ratings.database-username:}") String databaseUsername,
			@Value("${openbook.ratings.database-password:}") String databasePassword,
			@Value("${openbook.ratings.require-database:false}") boolean requireDatabase
	) {
		DatabaseInitialization initialization = initializeDatabase(
				databaseUrl,
				databaseUsername,
				databasePassword,
				requireDatabase
		);
		this.databaseConnection = initialization.connection();
		this.memoryEnabled = initialization.memoryEnabled();
		this.databaseInitializationFailure = initialization.failure();
	}

	private RatingRepository() {
		this.databaseConnection = null;
		this.memoryEnabled = true;
		this.databaseInitializationFailure = null;
	}

	public static RatingRepository inMemory() {
		return new RatingRepository();
	}

	public CommunityRating upsert(String bookKey, String voterHash, int score) {
		if (databaseConnection == null) {
			if (memoryEnabled) {
				return upsertInMemory(bookKey, voterHash, score);
			}

			throw databaseUnavailable();
		}

		return upsertInDatabase(bookKey, voterHash, score);
	}

	public Map<String, CommunityRating> findByBookKeys(Collection<String> bookKeys) {
		Set<String> uniqueKeys = sanitizeKeys(bookKeys);

		if (uniqueKeys.isEmpty()) {
			return Map.of();
		}

		if (databaseConnection == null) {
			if (memoryEnabled) {
				return findInMemory(uniqueKeys);
			}

			throw databaseUnavailable();
		}

		return findInDatabase(uniqueKeys);
	}

	private CommunityRating upsertInMemory(String bookKey, String voterHash, int score) {
		Map<String, Integer> votes = memoryRatings.computeIfAbsent(bookKey, ignored -> new ConcurrentHashMap<>());

		synchronized (votes) {
			votes.put(voterHash, score);
			return aggregate(votes.values());
		}
	}

	private Map<String, CommunityRating> findInMemory(Set<String> bookKeys) {
		Map<String, CommunityRating> ratings = new LinkedHashMap<>();

		for (String bookKey : bookKeys) {
			Map<String, Integer> votes = memoryRatings.get(bookKey);

			if (votes == null || votes.isEmpty()) {
				continue;
			}

			synchronized (votes) {
				ratings.put(bookKey, aggregate(votes.values()));
			}
		}

		return Map.copyOf(ratings);
	}

	private CommunityRating upsertInDatabase(String bookKey, String voterHash, int score) {
		try (Connection connection = databaseConnection.open()) {
			connection.setAutoCommit(false);

			try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
				statement.setString(1, bookKey);
				statement.setString(2, voterHash);
				statement.setInt(3, score);
				statement.executeUpdate();
			}

			CommunityRating rating = selectAggregate(connection, bookKey);
			connection.commit();
			return rating;
		} catch (SQLException exception) {
			throw new RatingPersistenceException("Nao foi possivel salvar a avaliacao.", exception);
		}
	}

	private Map<String, CommunityRating> findInDatabase(Set<String> bookKeys) {
		String placeholders = String.join(", ", java.util.Collections.nCopies(bookKeys.size(), "?"));
		String sql = "SELECT book_key, AVG(score) AS average, COUNT(*) AS total "
				+ "FROM book_ratings WHERE book_key IN (" + placeholders + ") GROUP BY book_key";
		Map<String, CommunityRating> ratings = new LinkedHashMap<>();

		try (Connection connection = databaseConnection.open();
				 PreparedStatement statement = connection.prepareStatement(sql)) {
			int index = 1;
			for (String bookKey : bookKeys) {
				statement.setString(index++, bookKey);
			}

			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					ratings.put(
							resultSet.getString("book_key"),
							new CommunityRating(
									round(resultSet.getBigDecimal("average")),
									resultSet.getLong("total")
							)
				);
				}
			}
		} catch (SQLException exception) {
			throw new RatingPersistenceException("Nao foi possivel carregar as avaliacoes.", exception);
		}

		return Map.copyOf(ratings);
	}

	private CommunityRating selectAggregate(Connection connection, String bookKey) throws SQLException {
		String sql = "SELECT AVG(score) AS average, COUNT(*) AS total FROM book_ratings WHERE book_key = ?";

		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, bookKey);

			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return CommunityRating.empty();
				}

				return new CommunityRating(
						round(resultSet.getBigDecimal("average")),
						resultSet.getLong("total")
				);
			}
		}
	}

	private CommunityRating aggregate(Collection<Integer> votes) {
		if (votes.isEmpty()) {
			return CommunityRating.empty();
		}

		long total = 0;
		for (int vote : votes) {
			total += vote;
		}

		double average = round((double) total / votes.size());
		return new CommunityRating(average, votes.size());
	}

	private Set<String> sanitizeKeys(Collection<String> bookKeys) {
		if (bookKeys == null || bookKeys.isEmpty()) {
			return Set.of();
		}

		Set<String> uniqueKeys = new LinkedHashSet<>();
		for (String bookKey : bookKeys) {
			if (bookKey != null && !bookKey.isBlank()) {
				uniqueKeys.add(bookKey);
			}
		}
		return uniqueKeys;
	}

	private Double round(java.math.BigDecimal average) {
		return average == null ? null : round(average.doubleValue());
	}

	private double round(double average) {
		return Math.round(average * 100.0) / 100.0;
	}

	private RatingPersistenceException databaseUnavailable() {
		return new RatingPersistenceException(
				"O banco obrigatorio de avaliacoes nao esta configurado ou esta indisponivel.",
				databaseInitializationFailure
		);
	}

	private DatabaseInitialization initializeDatabase(
			String databaseUrl,
			String databaseUsername,
			String databasePassword,
			boolean requireDatabase
	) {
		if (databaseUrl == null || databaseUrl.isBlank()) {
			if (requireDatabase) {
				IllegalStateException failure = new IllegalStateException(
						"DATABASE_URL ou JDBC_DATABASE_URL ausente com require-database=true."
				);
				LOGGER.warn("Avaliacoes desabilitadas: {}", failure.getMessage());
				return new DatabaseInitialization(null, false, failure);
			}

			LOGGER.info("Avaliacoes usando armazenamento em memoria; indicado apenas para desenvolvimento local.");
			return new DatabaseInitialization(null, true, null);
		}

		try {
			DatabaseConnection connection = DatabaseConnection.from(databaseUrl, databaseUsername, databasePassword);
			initializeSchema(connection);
			LOGGER.info("Armazenamento PostgreSQL de avaliacoes inicializado.");
			return new DatabaseInitialization(connection, false, null);
		} catch (RuntimeException | SQLException exception) {
			if (requireDatabase) {
				LOGGER.warn("Avaliacoes desabilitadas porque o PostgreSQL nao iniciou: {}", exception.getMessage());
				return new DatabaseInitialization(null, false, exception);
			}

			LOGGER.warn(
					"Banco de avaliacoes indisponivel; usando memoria porque require-database=false: {}",
					exception.getMessage()
			);
			return new DatabaseInitialization(null, true, exception);
		}
	}

	private void initializeSchema(DatabaseConnection database) throws SQLException {
		try (Connection connection = database.open(); Statement statement = connection.createStatement()) {
			statement.execute(CREATE_TABLE_SQL);
		}
	}

	private record DatabaseInitialization(
			DatabaseConnection connection,
			boolean memoryEnabled,
			Throwable failure
	) {
	}

	private record DatabaseConnection(String jdbcUrl, String username, String password) {

		private static DatabaseConnection from(String databaseUrl, String username, String password) {
			String url = databaseUrl.trim();

			if (url.startsWith("jdbc:postgresql:")) {
				return new DatabaseConnection(url, clean(username), clean(password));
			}

			if (!url.startsWith("postgresql://") && !url.startsWith("postgres://")) {
				throw new IllegalArgumentException("A URL configurada nao e uma URL PostgreSQL valida.");
			}

			URI uri = URI.create(url);
			if (uri.getHost() == null || uri.getRawPath() == null || uri.getRawPath().length() <= 1) {
				throw new IllegalArgumentException("A URL PostgreSQL precisa informar host e banco.");
			}

			String resolvedUsername = clean(username);
			String resolvedPassword = clean(password);
			String userInfo = uri.getRawUserInfo();

			if (userInfo != null && !userInfo.isBlank()) {
				String[] credentials = userInfo.split(":", 2);
				if (resolvedUsername.isBlank()) {
					resolvedUsername = decode(credentials[0]);
				}
				if (resolvedPassword.isBlank() && credentials.length > 1) {
					resolvedPassword = decode(credentials[1]);
				}
			}

			String host = uri.getHost().contains(":") ? "[" + uri.getHost() + "]" : uri.getHost();
			String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
			String query = uri.getRawQuery() == null || uri.getRawQuery().isBlank() ? "" : "?" + uri.getRawQuery();
			String jdbcUrl = "jdbc:postgresql://" + host + port + uri.getRawPath() + query;

			return new DatabaseConnection(jdbcUrl, resolvedUsername, resolvedPassword);
		}

		private Connection open() throws SQLException {
			if (username.isBlank() && password.isBlank()) {
				return DriverManager.getConnection(jdbcUrl);
			}

			Properties properties = new Properties();
			if (!username.isBlank()) {
				properties.setProperty("user", username);
			}
			if (!password.isBlank()) {
				properties.setProperty("password", password);
			}
			return DriverManager.getConnection(jdbcUrl, properties);
		}

		private static String clean(String value) {
			return value == null ? "" : value.trim();
		}

		private static String decode(String value) {
			return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
		}
	}
}
