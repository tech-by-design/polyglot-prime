package org.techbd.service.api.http;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.techbd.util.JsonText;
import org.techbd.util.JsonText.ByteArrayToStringOrJsonSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;

@Component
public class Interactions {
    private static final int MAX_IN_MEMORY_HISTORY = 50;
    private static final JsonText jsonText = new JsonText();
    private static final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private final Map<UUID, RequestResponseEncountered> history = Collections
            .synchronizedMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, RequestResponseEncountered> eldest) {
                    return size() > MAX_IN_MEMORY_HISTORY;
                }
            });

    public RequestResponseEncountered addHistory(final @NotNull RequestResponseEncountered rre) {
        history.put(rre.interactionId(), rre);
        return rre;
    }

    public Map<UUID, RequestResponseEncountered> getHistory() {
        return Collections.unmodifiableMap(history);
    }

    public record Tenant(String tenantId, String name) {
        public Tenant(@NonNull String tenantId) {
            this(tenantId, "unspecified");
        }

        public Tenant(final @NonNull HttpServletRequest request) {
            this(request.getHeader("TECH_BD_FHIR_SERVICE_QE_IDENTIFIER"),
                    request.getHeader("TECH_BD_FHIR_SERVICE_QE_NAME"));
        }
    }

    public record Header(String name, String value) {
        public static Header persistenceError(String message) {
            return new Header("TECH_BD_FHIR_SERVICE_PERSIST_ERROR", message);
        }
    }

    public record RequestEncountered(
            UUID requestId,
            Tenant tenant,
            String method,
            String uri,
            String clientIpAddress,
            String userAgent,
            Instant encounteredAt,
            List<Header> headers,
            Map<String, String[]> parameters,
            String contentType,
            String queryString,
            String protocol,
            String servletSessionId,
            List<Cookie> cookies,
            @JsonSerialize(using = ByteArrayToStringOrJsonSerializer.class) byte[] requestBody) {

        public RequestEncountered(HttpServletRequest request, byte[] body) throws IOException {
            this(
                    UUID.randomUUID(),
                    new Tenant(request),
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    Instant.now(),
                    StreamSupport
                            .stream(((Iterable<String>) () -> request.getHeaderNames().asIterator()).spliterator(),
                                    false)
                            .map(headerName -> new Header(headerName, request.getHeader(headerName)))
                            .collect(Collectors.toList()),
                    request.getParameterMap(),
                    request.getContentType(), // Content type
                    request.getQueryString(), // Query string
                    request.getProtocol(), // Protocol
                    request.getSession(false) != null ? request.getSession(false).getId() : null,
                    Arrays.asList(request.getCookies() != null ? request.getCookies() : new Cookie[0]),
                    body // Request body
            );
        }
    }

    public record ResponseEncountered(
            UUID requestId,
            UUID responseId,
            int status,
            Instant encounteredAt,
            List<Header> headers,
            @JsonSerialize(using = ByteArrayToStringOrJsonSerializer.class) byte[] responseBody) {

        public ResponseEncountered(HttpServletResponse response, RequestEncountered requestEncountered,
                byte[] responseBody) {
            this(
                    requestEncountered.requestId(),
                    UUID.randomUUID(),
                    response.getStatus(),
                    Instant.now(),
                    response.getHeaderNames().stream()
                            .map(headerName -> new Header(headerName, response.getHeader(headerName)))
                            .collect(Collectors.toList()),
                    responseBody);
        }
    }

    public record RequestResponseEncountered(
            UUID interactionId,
            Tenant tenant,
            RequestEncountered request,
            ResponseEncountered response) {
        public RequestResponseEncountered(RequestEncountered request, ResponseEncountered response) {
            this(request.requestId(), request.tenant(), request, response);
        }
    }

    public interface PersistenceStrategy {
        Map<String, PersistenceStrategy> CACHED = new HashMap<>();

        // returns NULL if no header(s) should be persisted or list of Headers for
        // informing callers in response headers for location of where storage occurs
        List<Header> persist(final @NotNull RequestResponseEncountered rre);
    }

    public static class DiagnosticPersistence implements PersistenceStrategy {
        private String identifier = "DiagnosticPersistence";

        public DiagnosticPersistence() {
        }

        public DiagnosticPersistence(final @NonNull String identifer) {
            this.identifier = identifer;
        }

        public List<Header> persist(final @NotNull RequestResponseEncountered rre) {
            return Arrays.asList(new Header("TECH_BD_INTERACTION_PERSISTENCE_RESULT",
                    "[" + identifier + "] interactionId: " + rre.interactionId()));
        }
    }

    public static class BlobStorePersistence implements PersistenceStrategy {
        // build on top of https://jclouds.apache.org/
        // private final String apiKey;
        // private final String bucketName;
        // private final String collectionName;

        public BlobStorePersistence(final Map<String, Object> args, final String origJson) {

        }

        public List<Header> persist(final @NotNull RequestResponseEncountered rre) {
            return Arrays.asList(new Header("TECH_BD_INTERACTION_PERSISTENCE_RESULT", "AWS-object-id"));
        }
    }

    public static class FileSysPersistence implements PersistenceStrategy {
        private String fsHome;

        public FileSysPersistence(final String fsHomeDefault, final Map<String, Object> args, final String origJson) {
            this.fsHome = fsHomeDefault;
            final var fsHome = args.get("home");
            if (fsHome != null && fsHome instanceof String) {
                this.fsHome = (String) fsHome;
            }
        }

        public List<Header> persist(final @NotNull RequestResponseEncountered rre) {
            // Convert object to JSON string
            String jsonString;
            try {
                jsonString = objectMapper.writeValueAsString(rre);
            } catch (JsonProcessingException e) {
                return List.of(Header.persistenceError("Failed to convert object to JSON: " + e.toString()));
            }

            // we store the file in fsHome/<Year>/<Month>/<Day>/<Hour>/<interactionId>.json
            String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"));
            String formattedFilePath = String.format("%s/%s/%s.json", fsHome, formattedDate, rre.interactionId);

            // Write the JSON string to the file
            Path path = Path.of(formattedFilePath);
            try {
                Files.createDirectories(path.getParent());
                try (FileWriter fileWriter = new FileWriter(new java.io.File(formattedFilePath))) {
                    fileWriter.write(jsonString);
                }
            } catch (IOException e) {
                return List.of(
                        Header.persistenceError("Unable to write JSON to " + formattedFilePath + ": " + e.toString()));
            }

            // return the location where the file was stored in the response header
            return Arrays.asList(new Header("TECH_BD_INTERACTION_PERSISTENCE_FS_RESULT", formattedFilePath));
        }
    }

    public record PersistenceSuggestion(String strategyJson, String defaultFsHome) {

        public PersistenceSuggestion(final @NonNull HttpServletRequest request,
                final @NonNull String defaultStrategyJson, final @NonNull String defaultFsHome) {
            this(request.getHeader("TECH_BD_INTERACTION_PERSISTENCE"), defaultFsHome);
        }

        public PersistenceStrategy cached(final @NonNull PersistenceStrategy ps) {
            PersistenceStrategy.CACHED.put(strategyJson(), ps);
            return ps;
        }

        public StrategyResult getStrategy() {
            if (strategyJson() == null) {
                return new StrategyResult.Persist(new DiagnosticPersistence());
            }

            final var existing = PersistenceStrategy.CACHED.get(strategyJson());
            if (existing != null) {
                return new StrategyResult.Persist(existing);
            }

            final var result = jsonText.getJsonObject(strategyJson());
            switch (result) {
                case JsonText.JsonObjectResult.ValidUntypedResult validUntypedResult -> {
                    // shape of JSON is { nature: "", arg1: x, arg2: y, etc. }
                    final var natureO = validUntypedResult.jsonObject().get("nature");
                    if (natureO instanceof String) {
                        final var nature = (String) natureO;
                        switch (nature) {
                            case "diags":
                            case "diagnostics": {
                                return new StrategyResult.Persist(new DiagnosticPersistence());
                            }
                            case "fs": {
                                return new StrategyResult.Persist(this.cached(new FileSysPersistence(
                                        defaultFsHome(), validUntypedResult.jsonObject(),
                                        validUntypedResult.originalText())));
                            }
                            case "aws-s3": {
                                return new StrategyResult.Persist(this.cached(new BlobStorePersistence(
                                        validUntypedResult.jsonObject(), validUntypedResult.originalText())));
                            }
                            default: {
                                // TODO: Handle the default behavior
                                return null;
                            }
                        }
                    }
                    return new StrategyResult.Persist(new DiagnosticPersistence());
                }
                case JsonText.JsonObjectResult.ValidResult<?> validResult -> {
                    if (validResult.instance() instanceof PersistenceStrategy) {
                        return new StrategyResult.Persist((PersistenceStrategy) validResult.instance());
                    } else {
                        return new StrategyResult.Invalid(List.of(Header.persistenceError(strategyJson),
                                Header.persistenceError("not instanceof PersistenceStrategy")));
                    }
                }
                case JsonText.JsonObjectResult.ValidResultClassNotFound classNotFoundResult -> {
                    // TODO: add exceptions into Header
                    return new StrategyResult.Invalid(
                            List.of(Header.persistenceError(strategyJson), Header.persistenceError("class not found")));
                }
                case JsonText.JsonObjectResult.ValidResultClassNotInstantiated classNotInstantiatedResult -> {
                    // TODO: add exceptions into Header
                    return new StrategyResult.Invalid(List.of(Header.persistenceError(strategyJson),
                            Header.persistenceError("class not instantiatable (check shape and constructor)")));
                }
                case JsonText.JsonObjectResult.InvalidResult invalidResult -> {
                    // TODO: add exceptions into Header
                    return new StrategyResult.Invalid(List.of(Header.persistenceError(strategyJson),
                            Header.persistenceError("TODO: add exceptions")));
                }
            }
        }

        public sealed interface StrategyResult permits StrategyResult.Persist, StrategyResult.Invalid {
            record Persist(@NonNull PersistenceStrategy instance) implements StrategyResult {
            }

            record Invalid(@NonNull List<Header> headers) implements StrategyResult {
            }
        }

    }
}