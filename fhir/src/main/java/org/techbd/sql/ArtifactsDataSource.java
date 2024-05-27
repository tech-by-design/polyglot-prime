package org.techbd.sql;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.techbd.util.ArtifactStore.Artifact;
import org.techbd.util.InterpolateEngine;

public class ArtifactsDataSource {
    private static final Logger LOG = LoggerFactory.getLogger(ArtifactsDataSource.class);
    private final Map<String, PreparedStatement> statementCache = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final Optional<Exception> dsException;

    private ArtifactsDataSource(final DataSource dataSource, final Optional<Exception> dsException) {
        this.dataSource = dataSource;
        this.dsException = dsException;
    }

    public Optional<Exception> dsException() {
        return dsException;
    }

    public boolean isValid() {
        return dsException.isEmpty();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public Optional<Exception> persistArtifact(Artifact artifact) {
        if (!isValid())
            return dsException;

        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            final var content = new StringWriter();
            try (Reader reader = artifact.getReader()) {
                reader.transferTo(content);
            } catch (IOException e) {
                return Optional.of(e);
            }

            // we insert as little as possible because all the other columns
            // are computed in the database automatically
            final var sql = """
                        INSERT INTO "artifact" ("artifact_id", "namespace", "content_type", "content")
                        VALUES (?, ?, ?, ?)
                    """;
            var statement = statementCache.computeIfAbsent(sql, k -> {
                try {
                    return conn.prepareStatement(k);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });

            statement.setString(1, artifact.getArtifactId());
            statement.setString(2, ArtifactsDataSource.class.getName());
            statement.setString(3, "application/json");
            statement.setString(4, content.toString());

            final var result = statement.executeUpdate();
            LOG.info(String.format("persistArtifact %s execute result %d", artifact.getArtifactId(), result));
            return Optional.empty();
        } catch (SQLException e) {
            return Optional.of(e);
        }
    }

    public static class DuckDbBuilder {
        private static final Pattern MIGRATABLE_SCRIPT_PATTERN = Pattern.compile("^\\d+.*\\.duckdb\\.sql$");
        private String url = "jdbc:duckdb:" + System.getProperty("user.dir") + "/artifacts.duckdb";
        private String motherDuckToken;
        private String scriptsPath = "sql/artifact";

        public DuckDbBuilder userAgentArgs(final Map<String, Object> args) {
            final var ie = new InterpolateEngine(args);

            // if there's a `{ "url": X }` available
            Optional.ofNullable(args.get("url"))
                    .ifPresent(url -> url(ie.interpolate(url.toString())));

            // if there's a structure like `{ motherDuck: { token: X, db: Y } }`
            Optional.ofNullable(args.get("motherDuck"))
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .ifPresent(mdArgs -> Optional.ofNullable(mdArgs.get("token"))
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .ifPresent(token -> Optional.ofNullable(mdArgs.get("db"))
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .ifPresent(db -> motherDuckDatabase(ie.interpolate(token), ie.interpolate(db)))));

            return this;
        }

        public DuckDbBuilder url(final String url) {
            this.url = url;
            return this;
        }

        public DuckDbBuilder scriptsPath(final String scriptPath) {
            this.scriptsPath = scriptPath;
            return this;
        }

        public DuckDbBuilder localFsPath(final String localFsPath) {
            return this.url("jdbc:duckdb:" + localFsPath);
        }

        public DuckDbBuilder userDirFsPath(final String localFsPath) {
            return this.url("jdbc:duckdb:" + System.getProperty("user.dir") + (localFsPath.startsWith("/") ? "" : "/")
                    + localFsPath);
        }

        public DuckDbBuilder motherDuckDatabase(final String token, final String mdbName) {
            this.motherDuckToken = token;
            return this.url("jdbc:duckdb:md:" + mdbName);
        }

        public ArtifactsDataSource build() {
            final var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource();
            dataSource.setDriverClassName("org.duckdb.DuckDBDriver");
            dataSource.setUrl(url);
            if (motherDuckToken != null) {
                final var config = new Properties();
                config.setProperty("motherduck_token", motherDuckToken);
                config.setProperty("custom_user_agent", ArtifactsDataSource.DuckDbBuilder.class.getName());
                dataSource.setConnectionProperties(config);
            }
            try (var connection = dataSource.getConnection()) {
                Optional<Exception> esException = Optional.empty();
                if (scriptsPath != null) {
                    esException = executeResourceScripts(connection, scriptsPath,
                            path -> {
                                final var matches = MIGRATABLE_SCRIPT_PATTERN.matcher(path.getFileName().toString())
                                        .matches();
                                LOG.info(String.format("DuckDbBuilder test match: %s (%s)",
                                        path.getFileName().toString(), matches));
                                return matches;
                            });
                }
                return new ArtifactsDataSource(dataSource, esException);
            } catch (final SQLException e) {
                return new ArtifactsDataSource(dataSource, Optional.of(e));
            }
        }
    }

    @SafeVarargs
    public static Optional<Exception> executeResourceScripts(final Connection connection, final String scriptsPath,
            Predicate<Path>... predicates) {
        LOG.info("preparing executeResourceScripts");
        final var populator = new ResourceDatabasePopulator();
        try (Stream<Path> paths = Files.walk(Paths.get(new ClassPathResource(scriptsPath).getURI()))) {
            var filteredPaths = paths.filter(Files::isRegularFile);
            for (Predicate<Path> predicate : predicates) {
                filteredPaths = filteredPaths.filter(predicate);
            }

            filteredPaths.sorted(Comparator.comparing(Path::getFileName))
                    .forEach(path -> {
                        final var relativePath = scriptsPath + "/" + path.getFileName().toString();
                        final var cpr = new ClassPathResource(relativePath);
                        LOG.info(String.format("%s discovered in %s (executeScripts)", cpr.toString(), scriptsPath));
                        populator.addScript(cpr);
                    });

            populator.populate(connection);
            LOG.info(String.format("%s populated in connection", populator.toString()));
            return Optional.empty();
        } catch (IOException | ScriptException e) {
            LOG.error(String.format("%s exception in executeScripts", e), e);
            return Optional.of(e);
        }
    }
}
