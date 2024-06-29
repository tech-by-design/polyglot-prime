package lib.aide.resource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import lib.aide.paths.PathSuffixes;

public class ResourceFactory {
    public static final Pattern DEFAULT_SUFFIX_PATTERN = Pattern.compile("\\.");

    public record SuffixedTextResourceFactory<T, R extends Resource<? extends Nature, T>>(
            BiFunction<String, Optional<PathSuffixes>, R> resourceFactory,
            PathSuffixes suffixes) {
        public static final Map<String, BiFunction<String, Optional<PathSuffixes>, Resource<? extends Nature, String>>> SUFFIXED_RF_MAP = new HashMap<>();

        static {
            SUFFIXED_RF_MAP.put("md",
                    (content, suffixes) -> new MarkdownResource<>(content, new MarkdownResource.UntypedMarkdownNature(),
                            suffixes));
            SUFFIXED_RF_MAP.put("json",
                    (content, suffixes) -> new JsonResource(content, new JsonNature(), suffixes));
            SUFFIXED_RF_MAP.put("yaml",
                    (content, suffixes) -> new YamlResource(content, new YamlNature(), suffixes));
            SUFFIXED_RF_MAP.put("yml",
                    (content, suffixes) -> new YamlResource(content, new YamlNature(), suffixes));
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
     * @param src       the source path in which suffixes are found
     * @param delimiter an optional delimiter to use for suffixes, defaults to the
     *                  pattern "\\."
     * @return an Optional containing SuffixedResourceFactory if the nature can be
     *         determined, otherwise Optional.empty()
     */
    public Optional<SuffixedTextResourceFactory<String, Resource<? extends Nature, String>>> textResourceFactoryFromSuffix(
            final String src, final Optional<Pattern> delimiter) {
        if (src == null || src.isEmpty()) {
            return Optional.empty();
        }

        final var suffixPattern = delimiter.orElse(DEFAULT_SUFFIX_PATTERN);
        final var pathSuffixes = new PathSuffixes(src, suffixPattern);

        if (pathSuffixes.suffixes().isEmpty()) {
            return Optional.empty();
        }

        final var mostSignificantSuffix = pathSuffixes.suffixes().get(0).toLowerCase();
        final var resourceFactory = SuffixedTextResourceFactory.SUFFIXED_RF_MAP.get(mostSignificantSuffix);

        if (resourceFactory == null) {
            return Optional.empty();
        }

        // Explicitly specify the types for SuffixedResourceFactory
        return Optional.of(new SuffixedTextResourceFactory<>(resourceFactory, pathSuffixes));
    }
}
