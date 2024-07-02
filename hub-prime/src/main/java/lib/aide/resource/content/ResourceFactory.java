package lib.aide.resource.content;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import lib.aide.paths.PathSuffixes;
import lib.aide.resource.Nature;
import lib.aide.resource.Resource;
import lib.aide.resource.TextResource;
import lib.aide.resource.content.JsonResource.JsonNature;
import lib.aide.resource.content.YamlResource.YamlNature;

public class ResourceFactory {
    public static final Pattern DEFAULT_DELIMITER_PATTERN = Pattern.compile("\\.");

    public record SuffixedTextResourceFactory<T, R extends TextResource<? extends Nature>>(
            BiFunction<Supplier<String>, Optional<PathSuffixes>, R> resourceFactory,
            PathSuffixes suffixes) {
        public static final Map<String, BiFunction<Supplier<String>, Optional<PathSuffixes>, TextResource<? extends Nature>>> SUFFIXED_RF_MAP = new HashMap<>();

        static {
            SUFFIXED_RF_MAP.put(".md",
                    (content, suffixes) -> new MarkdownResource<>(content, new MarkdownResource.UntypedMarkdownNature(),
                            suffixes));
            SUFFIXED_RF_MAP.put(".mdx",
                    (content, suffixes) -> new MdxResource<>(content, new MdxResource.UntypedMdxNature(),
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
        public static final Map<String, BiFunction<Supplier<?>, Optional<PathSuffixes>, Resource<? extends Nature, ?>>> SUFFIXED_RF_MAP = new HashMap<>();

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
     * Optional<Resource>.
     *
     * @param <T>         the type of the content
     * @param suffixesSrc the source path in which suffixes are found
     * @param content     the supplier of the content to be used for the resource
     * @param delimiter   an optional delimiter to use for suffixes, defaults to the
     *                    pattern "\\."
     * @return an Optional containing Resource if the resource can be created,
     *         otherwise Optional.empty()
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<Resource<? extends Nature, T>> resourceFromSuffix(final String suffixesSrc,
            final Supplier<T> content, final Optional<Pattern> delimiter) {
        if (suffixesSrc == null || suffixesSrc.isEmpty()) {
            return Optional.empty();
        }

        final var suffixPattern = delimiter.orElse(DEFAULT_DELIMITER_PATTERN);
        final var pathSuffixes = new PathSuffixes(suffixesSrc, suffixPattern);

        if (pathSuffixes.suffixes().isEmpty()) {
            return Optional.empty();
        }

        final var resourceFactory = (BiFunction<Supplier<T>, Optional<PathSuffixes>, Resource<? extends Nature, T>>) (BiFunction<?, ?, ?>) SuffixedResourceFactory.SUFFIXED_RF_MAP
                .get(pathSuffixes.extendedSuffix());

        if (resourceFactory == null) {
            return Optional.empty();
        }

        // Explicitly specify the types for SuffixedResourceFactory
        return Optional.of(resourceFactory.apply(content,
                Optional.of(new PathSuffixes(suffixesSrc, delimiter.orElse(DEFAULT_DELIMITER_PATTERN)))));
    }
}
