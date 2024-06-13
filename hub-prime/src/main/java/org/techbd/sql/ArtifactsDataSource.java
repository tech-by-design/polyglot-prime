package org.techbd.sql;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptException;
import org.techbd.util.ArtifactStore;
import org.techbd.util.ArtifactStore.Artifact;
import org.techbd.util.InterpolateEngine;
import org.techbd.util.SessionWithState;

public class ArtifactsDataSource {
    private static final Logger LOG = LoggerFactory.getLogger(ArtifactsDataSource.class);
    private final DataSource dataSource;
    private final Optional<Exception> dsException;
    private final BiFunction<Connection, ArtifactRecord, Optional<Exception>> persistDelegate;

    public ArtifactsDataSource(final DataSource dataSource, final Optional<Exception> dsException,
            BiFunction<Connection, ArtifactRecord, Optional<Exception>> persistDelegate) {
        this.dataSource = dataSource;
        this.dsException = dsException;
        this.persistDelegate = persistDelegate;
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
        if (!isValid()) {
            return dsException;
        }

        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            final var content = new StringWriter();
            try (Reader reader = artifact.getReader()) {
                reader.transferTo(content);
            } catch (IOException e) {
                return Optional.of(e);
            }

            return persistDelegate.apply(conn, new ArtifactRecord(
                    artifact.getArtifactId(),
                    artifact.getNamespace(),
                    content.toString(),
                    "application/json",
                    ArtifactStore.toJsonText(artifact.getProvenance())));
        } catch (SQLException e) {
            return Optional.of(e);
        }
    }

    public record ArtifactRecord(String artifactId, String namespace, String content, String contentType,
            String provenance) {
    }

    public static class PostgreSqlBuilder {
        private static final Pattern MIGRATABLE_SCRIPT_PATTERN = Pattern.compile("^\\d+.*\\.pg\\.sql$");
        private DataSource dataSource;
        private String url = "jdbc:postgresql://hostname/database-name?user=*****&password=*****&sslmode=require";
        private String scriptsPath = "sql/artifact";
        private BiFunction<Connection, ArtifactRecord, Optional<Exception>> persistFn = PostgreSqlBuilder::callStoredProcPersist;

        public PostgreSqlBuilder url(final String url) {
            this.url = url;
            return this;
        }

        public PostgreSqlBuilder scriptsPath(final String scriptPath) {
            this.scriptsPath = scriptPath;
            return this;
        }

        public PostgreSqlBuilder dataSource(final DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public PostgreSqlBuilder userAgentArgs(final Map<String, Object> args) {
            final var ie = new InterpolateEngine(args);

            Optional.ofNullable(args.get("url"))
                    .ifPresent(url -> url(ie.interpolate(url.toString())));

            Optional.ofNullable(args.get("persistFn"))
                    .ifPresent(persistFnValue -> {
                        switch (persistFnValue.toString()) {
                            case "insertDML" -> persistFn(PostgreSqlBuilder::insertPersist);
                            case "callStoredProc" -> persistFn(PostgreSqlBuilder::callStoredProcPersist);
                            default ->
                                throw new IllegalArgumentException("Unknown persist function: " + persistFnValue);
                        }
                    });
            return this;
        }

        public PostgreSqlBuilder persistFn(
                BiFunction<Connection, ArtifactRecord, Optional<Exception>> persistFunction) {
            this.persistFn = persistFunction;
            return this;
        }

        public ArtifactsDataSource build() {
            // if the data source is supplied use it, otherwise try to get it dynamically
            final var dataSource = Optional.ofNullable(this.dataSource).orElseGet(() -> {
                final var dmds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
                dmds.setDriverClassName("org.postgresql.Driver");
                dmds.setUrl(url);
                return dmds;
            });
            try (var connection = dataSource.getConnection()) {
                Optional<Exception> esException = Optional.empty();
                if (scriptsPath != null) {
                    esException = executeResourceScripts(connection, scriptsPath,
                            path -> {
                                final var matches = MIGRATABLE_SCRIPT_PATTERN.matcher(path.getFileName().toString())
                                        .matches();
                                LOG.info(String.format("PostgreSQL test match: %s (%s)",
                                        path.getFileName().toString(), matches));
                                return matches;
                            });
                }
                return new ArtifactsDataSource(dataSource, esException, persistFn);
            } catch (final SQLException e) {
                return new ArtifactsDataSource(dataSource, Optional.of(e), persistFn);
            }
        }

        public static Optional<Exception> insertPersist(final Connection connection, final ArtifactRecord record) {
            final var sql = """
                        INSERT INTO "artifact" ("artifact_id", "namespace", "content_type", "content", "provenance")
                        VALUES (?, ?, ?, ?, ?)
                    """;
            try (var stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, record.artifactId());
                stmt.setString(2, record.namespace());
                stmt.setString(3, record.contentType());
                stmt.setString(4, record.content());

                final var provenance = record.provenance();
                if (provenance != null) {
                    stmt.setObject(5, provenance, java.sql.Types.OTHER);
                } else {
                    stmt.setNull(5, java.sql.Types.OTHER);
                }

                final var result = stmt.executeUpdate();
                LOG.info(String.format("PostgreSqlBuilder::insertPersist %s execute result %d", record.artifactId(),
                        result));
                return Optional.empty();
            } catch (SQLException e) {
                return Optional.of(e);
            }
        }

        public static Optional<Exception> callStoredProcPersist(final Connection connection,
        final ArtifactRecord record) {

        SessionWithState sessionWithState = new SessionWithState();
        try {
            String sql = "{ call techbd_udi_ingress.udi_insert_session_with_state(?, ?, ?, ?, ?, ?, ?, ?, ?, ?) }";

            CallableStatement stmt = connection.prepareCall(sql);

            stmt.setString(1, ""); //TODO:
            stmt.setString(2, record.namespace);
            stmt.setString(3, record.content);
            stmt.setString(4, record.contentType);

            PGobject jsonObject1 = new PGobject();
            jsonObject1.setType("jsonb");
            jsonObject1.setValue(""); //TODO:
            stmt.setObject(5, jsonObject1);

            PGobject jsonObject2 = new PGobject();
            jsonObject2.setType("jsonb");
            jsonObject2.setValue(""); //TODO:
            stmt.setObject(6, jsonObject2);

            stmt.setString(7, "");

            if (record.provenance() != null) {
                stmt.setString(8, record.provenance());
            } else {
                stmt.setNull(8, java.sql.Types.VARCHAR);
            }

            //TODO: Setting from state and to state as null here.
            stmt.setNull(9, java.sql.Types.VARCHAR);
            stmt.setNull(10, java.sql.Types.VARCHAR);

            stmt.execute();
            ResultSet rs = stmt.getResultSet();
            if (rs.next()) {
                sessionWithState.setHubSessionId(rs.getString(1));
                sessionWithState.setHubSessionEntryId(rs.getString(2));
            }
            LOG.info(String.format("PostgreSqlBuilder::callStoredProcPersist new Artifact Id: %s", sessionWithState.getHubSessionId()));
        } catch (SQLException e) {
            return Optional.of(e);
        }
        return Optional.empty();}
    }

    public static class DuckDbBuilder {
        private static final Pattern MIGRATABLE_SCRIPT_PATTERN = Pattern.compile("^\\d+.*\\.duckdb\\.sql$");
        private String url = "jdbc:duckdb:" + System.getProperty("user.dir") + "/artifacts.duckdb";
        private String motherDuckToken;
        private String scriptsPath = "sql/artifact";
        private BiFunction<Connection, ArtifactRecord, Optional<Exception>> persistFn = DuckDbBuilder::defaultPersist;

        public DuckDbBuilder userAgentArgs(final Map<String, Object> args) {
            final var ie = new InterpolateEngine(args);

            Optional.ofNullable(args.get("url"))
                    .ifPresent(url -> url(ie.interpolate(url.toString())));

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

        public DuckDbBuilder persistFunction(
                BiFunction<Connection, ArtifactRecord, Optional<Exception>> persistFunction) {
            this.persistFn = persistFunction;
            return this;
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
                return new ArtifactsDataSource(dataSource, esException, persistFn);
            } catch (final SQLException e) {
                return new ArtifactsDataSource(dataSource, Optional.of(e), persistFn);
            }
        }

        public static Optional<Exception> defaultPersist(final Connection connection, final ArtifactRecord record) {
            final var sql = """
                        INSERT INTO "artifact" ("artifact_id", "namespace", "content_type", "content", "provenance")
                        VALUES (?, ?, ?, ?, ?)
                    """;
            try (var stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, record.artifactId());
                stmt.setString(2, record.namespace());
                stmt.setString(3, record.contentType());
                stmt.setString(4, record.content());

                final var provenance = record.provenance();
                if (provenance != null) {
                    stmt.setString(5, provenance);
                } else {
                    stmt.setNull(5, java.sql.Types.VARCHAR);
                }

                final var result = stmt.executeUpdate();
                LOG.info(String.format("DuckDbBuilder::defaultPersist %s execute result %d", record.artifactId(),
                        result));
                return Optional.empty();
            } catch (SQLException e) {
                return Optional.of(e);
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
