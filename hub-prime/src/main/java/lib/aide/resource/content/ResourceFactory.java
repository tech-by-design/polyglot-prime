package lib.aide.resource.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lib.aide.paths.PathSuffixes;
import lib.aide.resource.Nature;
import lib.aide.resource.Resource;
import lib.aide.resource.TextResource;
import lib.aide.resource.content.JsonResource.JsonNature;
import lib.aide.resource.content.YamlResource.YamlNature;

public class ResourceFactory {
    public static final Pattern DEFAULT_DELIMITER_PATTERN = Pattern.compile("\\.");
    public static final String DEFAULT_ASSEMBLER_TEXT = ".";

    public record SuffixedTextResourceFactory<T, R extends TextResource<? extends Nature>>(
            BiFunction<Supplier<String>, Optional<PathSuffixes>, R> resourceFactory,
            PathSuffixes suffixes) {
        public static final Map<String, BiFunction<Supplier<String>, Optional<PathSuffixes>, TextResource<? extends Nature>>> SUFFIXED_RF_MAP = new HashMap<>();

        static {
            SUFFIXED_RF_MAP.put(".md",
                    (content, suffixes) -> new MarkdownResource<>(content, new MarkdownResource.UntypedMarkdownNature(content),
                            suffixes));
            SUFFIXED_RF_MAP.put(".mdx",
                    (content, suffixes) -> new MdxResource<>(content, new MdxResource.UntypedMdxNature(content),
                            suffixes));
            SUFFIXED_RF_MAP.put(".json",
                    (content, suffixes) -> new JsonResource(content, new JsonNature(), suffixes));
            SUFFIXED_RF_MAP.put(".yaml",
                    (content, suffixes) -> new YamlResource(content, new YamlNature(), suffixes));
            SUFFIXED_RF_MAP.put(".yml",
                    (content, suffixes) -> new YamlResource(content, new YamlNature(), suffixes));
        }
    }

    @SuppressWarnings("unchecked")
    public record SuffixedResourceFactory<T, R extends Resource<? extends Nature, T>>(
            BiFunction<Supplier<T>, Optional<PathSuffixes>, R> resourceFactory,
            PathSuffixes suffixes) {
        static final Map<String, BiFunction<Supplier<?>, Optional<PathSuffixes>, Resource<? extends Nature, ?>>> SUFFIXED_RF_MAP = new HashMap<>();

        static {
            for (Map.Entry<String, BiFunction<Supplier<String>, Optional<PathSuffixes>, TextResource<? extends Nature>>> entry : SuffixedTextResourceFactory.SUFFIXED_RF_MAP
                    .entrySet()) {
                SUFFIXED_RF_MAP.put(entry.getKey(),
                        (BiFunction<Supplier<?>, Optional<PathSuffixes>, Resource<? extends Nature, ?>>) (BiFunction<?, ?, ?>) entry
                                .getValue());
            }
            // Additional resource factories can be added here
        }
    }

    /**
     * Detect the nature of a resource from the suffixes supplied.
     *
     * This method processes the source path to extract suffixes using the provided
     * delimiter or a default delimiter. Based on the most significant suffix (the
     * last one in the path), it attempts to determine the nature of the resource.
     * If no suffixes are found, or if the nature cannot be determined, it returns
     * an empty Optional.
     *
     * Examples:
     * <ul>
     * <li>For the source path "my-file.md" with the default delimiter ".", it might
     * detect a markdown nature.</li>
     * <li>For the source path "my-file.special-type.md" with the default delimiter
     * ".", it might detect a special markdown nature.</li>
     * </ul>
     *
     * @param suffixesSrc the source path in which suffixes are found
     * @param delimiter   an optional delimiter to use for suffixes, defaults to the
     *                    pattern "\\."
     * @return an Optional containing SuffixedTextResourceFactory if the nature can
     *         be determined, otherwise Optional.empty()
     */
    public Optional<SuffixedTextResourceFactory<String, TextResource<? extends Nature>>> textResourceFactoryFromSuffix(
            final String suffixesSrc, final Optional<Pattern> delimiter) {
        if (suffixesSrc == null || suffixesSrc.isEmpty()) {
            return Optional.empty();
        }

        final var suffixPattern = delimiter.orElse(DEFAULT_DELIMITER_PATTERN);
        final var pathSuffixes = new PathSuffixes(suffixesSrc, suffixPattern);

        if (pathSuffixes.suffixes().isEmpty()) {
            return Optional.empty();
        }

        final var resourceFactory = SuffixedTextResourceFactory.SUFFIXED_RF_MAP.get(pathSuffixes.extendedSuffix());

        if (resourceFactory == null) {
            return Optional.empty();
        }

        // Explicitly specify the types for SuffixedTextResourceFactory
        return Optional.of(new SuffixedTextResourceFactory<>(resourceFactory, pathSuffixes));
    }

    /**
     * Determine if a suffix has a text resource factory and return an
     * Optional<TextResource>.
     *
     * @param suffixesSrc the source path in which suffixes are found
     * @param content     the supplier of the content to be used for the resource
     * @param delimiter   an optional delimiter to use for suffixes, defaults to the
     *                    pattern "\\."
     * @return an Optional containing TextResource if the resource can be created,
     *         otherwise Optional.empty()
     */
    public Optional<TextResource<? extends Nature>> textResourceFromSuffix(final String suffixesSrc,
            final Supplier<String> content, final Optional<Pattern> delimiter) {
        return textResourceFactoryFromSuffix(suffixesSrc, delimiter)
                .map(factory -> factory.resourceFactory().apply(content,
                        Optional.of(new PathSuffixes(suffixesSrc, delimiter.orElse(DEFAULT_DELIMITER_PATTERN)))));
    }

    /**
     * Determine if a suffix has a resource factory and return an
     * Optional<Resource>. After parsing suffixes it looks in the ResourceFactory
     * for the match with the most suffixes to the least suffixes.
     * 
     * For example, if a suffixesSrc is `a.b.c.d` then we search for `.b.c.d` first,
     * then `.c.d` and finally `.d`.
     *
     * @param <T>         the type of the content
     * @param suffixesSrc the source path in which suffixes are found
     * @param content     the supplier of the content to be used for the resource
     * @param delimiter   an optional delimiter to use for suffixes, defaults to the
     *                    pattern "\\."
     * @param assembler   an optional delimiter to use for reassembling suffixes,
     *                    defaults to string "."
     * @return an Optional containing Resource if the resource can be created,
     *         otherwise Optional.empty()
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<Resource<? extends Nature, T>> resourceFromSuffix(final String suffixesSrc,
            final Supplier<T> content, final Optional<Pattern> delimiter, final Optional<String> assembler) {
        if (suffixesSrc == null || suffixesSrc.isEmpty()) {
            return Optional.empty();
        }

        final var suffixPattern = delimiter.orElse(DEFAULT_DELIMITER_PATTERN);
        final var suffixAssembler = assembler.orElse(DEFAULT_ASSEMBLER_TEXT);
        final var pathSuffixes = new PathSuffixes(suffixesSrc, suffixPattern);
        final var suffixes = pathSuffixes.suffixes();

        if (suffixes.isEmpty()) {
            return Optional.empty();
        }

        // try all the combination of suffixes available and match the longest one
        for (int s = 0; s < suffixes.size(); s++) {
            final var trySuffix = suffixAssembler + String.join(suffixAssembler, suffixes.subList(0, s + 1));
            final var resourceFactory = (BiFunction<Supplier<T>, Optional<PathSuffixes>, Resource<? extends Nature, T>>) (BiFunction<?, ?, ?>) SuffixedResourceFactory.SUFFIXED_RF_MAP
                    .get(trySuffix);
            if (resourceFactory != null) {
                return Optional.of(resourceFactory.apply(content, Optional.of(pathSuffixes)));
            }
        }

        return Optional.empty();
    }

    public enum MapFromTextContentType {
        JSON, YAML
    }

    /**
     * A record to encapsulate the result of mapping a file's content to a Map, the
     * validity of the operation,
     * any issues encountered during the process, and the type of content parsed.
     *
     * @param result      The resulting map from the file's content.
     * @param isValid     Whether the mapping operation was successful.
     * @param issues      A list of exceptions encountered during the mapping
     *                    process.
     * @param textContent The original text content that was parsed.
     * @param contentType The type of content that was parsed (JSON or YAML).
     */
    public record MapFromTextResult(Map<String, Object> result, boolean isValid, List<Exception> issues,
            String textContent, MapFromTextContentType contentType) {
    }

    /**
     * Maps the content of a file or a provided text supplier to a Map<String,
     * Object>.
     * Supports JSON and YAML files based on the file extension.
     *
     * @param fileName     The name of the file to read from if no text supplier is
     *                     provided.
     * @param textSupplier An optional supplier that provides the text content to be
     *                     mapped.
     * @return A MapFromTextResult containing the resulting map, validity status,
     *         any issues encountered, the original text content, and the type of
     *         content parsed.
     */
    @SuppressWarnings("unchecked")
    public static MapFromTextResult mapFromText(final String fileName, final Optional<Supplier<String>> textSupplier) {
        final var issues = new ArrayList<Exception>();
        Map<String, Object> result = null;
        String textContent = null;
        MapFromTextContentType contentType = null;

        try {
            if (textSupplier.isPresent()) {
                textContent = textSupplier.get().get();
            } else {
                final var filePath = Path.of(fileName);
                textContent = Files.readString(filePath);
            }

            final int lastDotIndex = fileName.lastIndexOf('.');
            final String fileExtension = (lastDotIndex == -1) ? "" : fileName.substring(lastDotIndex + 1).toLowerCase();

            final var objectMapper = switch (fileExtension) {
                case "json" -> {
                    contentType = MapFromTextContentType.JSON;
                    yield Optional.of(new ObjectMapper());
                }
                case "yaml", "yml" -> {
                    contentType = MapFromTextContentType.YAML;
                    yield Optional.of(new ObjectMapper(new YAMLFactory()));
                }
                default -> {
                    issues.add(new IllegalArgumentException("Unsupported file extension for file: " + fileName));
                    yield Optional.<ObjectMapper>empty();
                }
            };

            if (objectMapper.isPresent()) {
                result = objectMapper.orElseThrow().readValue(textContent, HashMap.class);
            }
        } catch (Exception e) {
            issues.add(e);
        }

        return new MapFromTextResult(result, issues.isEmpty(), issues, textContent, contentType);
    }
}
