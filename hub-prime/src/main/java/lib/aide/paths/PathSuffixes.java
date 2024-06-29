package lib.aide.paths;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record PathSuffixes(String src, Pattern delimiter, List<String> suffixes) {

    public static final Pattern DEFAULT_DELIMITER_PATTERN = Pattern.compile("\\.");

    /**
     * Constructs a PathSuffixes instance by parsing the provided source string
     * with the given delimiter.
     *
     * This constructor processes the source string to extract suffixes separated
     * by the provided delimiter. The suffixes are ordered such that the last suffix
     * in the source string is considered the most significant and will appear first
     * in the list.
     *
     * For example:
     * <ul>
     * <li>For the source string "my-file.md" with the default delimiter ".", the
     * suffixes list will be ["md"].</li>
     * <li>For the source string "my-file.special-type.md" with the default
     * delimiter ".", the suffixes list will be ["md", "special-type"].</li>
     * </ul>
     *
     * @param src       the string in which suffixes are found
     * @param delimiter optional delimiter to use, defaults to the pattern "\\."
     * @throws IllegalArgumentException if src is null or empty
     */
    public PathSuffixes(final String src, final Pattern delimiter) {
        this(src, delimiter, extractSuffixes(src, delimiter));
    }

    public static List<String> extractSuffixes(final String src, final Pattern delim) {
        if (src == null || src.isEmpty()) {
            throw new IllegalArgumentException("Source string cannot be null or empty");
        }

        final var delimiter = delim == null ? DEFAULT_DELIMITER_PATTERN : delim;
        final var parts = delimiter.split(src);
        if (parts.length <= 1) {
            return Collections.emptyList();
        } else {
            final var suffixes = Stream.of(parts)
                    .skip(1)
                    .collect(Collectors.toList());
            Collections.reverse(suffixes); // Reverse the list to make the last suffix the most significant
            return suffixes;
        }
    }
}
