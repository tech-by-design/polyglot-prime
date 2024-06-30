package lib.aide.paths;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code PathSuffixes} class is designed to handle and process suffixes,
 * commonly known as file extensions, from a given file path or URL. The class
 * provides functionality to extract and manage these suffixes, which can be
 * used to determine the type of content based on its name and extension.
 *
 * This class is particularly useful in scenarios where the identification and
 * manipulation of file extensions are required, such as:
 * - Determining the type of a file or URL based on its extension(s).
 * - Extracting multiple suffixes from complex file names, where multiple
 * extensions are used to denote special transformations of files (md -> html).
 *
 * The class supports custom delimiters for suffix extraction, with a default
 * delimiter of "." (dot).
 *
 * <p>
 * Example usage:
 * 
 * <pre>
 * {@code
 * PathSuffixes suffixes = new PathSuffixes("my-file.special-type.md", PathSuffixes.DEFAULT_DELIMITER_PATTERN);
 * List<String> extractedSuffixes = suffixes.suffixes();
 * String extendedSuffix = suffixes.extendedSuffix();
 * }
 * </pre>
 *
 * <p>
 * In this example, the {@code extractedSuffixes} list will contain ["md",
 * "special-type"], and the {@code extendedSuffix}
 * will be ".special-type.md".
 *
 * @param src            the source string from which suffixes are to be
 *                       extracted
 * @param delimiter      the delimiter pattern used to separate suffixes in the
 *                       source string
 * @param suffixes       a list of extracted suffixes in the order of
 *                       significance
 * @param extendedSuffix the extended suffix string including the first
 *                       delimiter occurrence to the end
 */
public record PathSuffixes(String src, Pattern delimiter, List<String> suffixes, String extendedSuffix) {

    public static final Pattern DEFAULT_DELIMITER_PATTERN = Pattern.compile("\\.");

    public PathSuffixes(final String src, final Pattern delimiter) {
        this(src, delimiter, extractSuffixes(src, delimiter), extendedSuffix(src, delimiter));
    }

    /**
     * This method processes the source string to extract suffixes separated
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
     * @param suffixesSrc the string in which suffixes are found
     * @param delimiter   optional delimiter to use, defaults to the pattern "\\."
     * @throws IllegalArgumentException if src is null or empty
     */
    public static List<String> extractSuffixes(final String suffixesSrc, final Pattern delim) {
        if (suffixesSrc == null || suffixesSrc.isEmpty()) {
            throw new IllegalArgumentException("Source string cannot be null or empty");
        }

        final var delimiter = delim == null ? DEFAULT_DELIMITER_PATTERN : delim;
        final var parts = delimiter.split(suffixesSrc);
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

    /**
     * Checks if the delim matches any part of the suffixesSrc and returns the
     * substring from the first occurrence of the delimiter to the end of the
     * string, including the delimiter; if no match is found, it returns the
     * original input string.
     * 
     * For the source string "my-file.special-type.md" with the default
     * delimiter ".", the extendedSuffix will be `.special-type.md`.
     * 
     * @param suffixesSrc
     * @param delim
     * @return
     */
    public static String extendedSuffix(final String suffixesSrc, final Pattern delim) {
        if (suffixesSrc == null || suffixesSrc.isEmpty()) {
            throw new IllegalArgumentException("Source string cannot be null or empty");
        }

        final var delimiter = delim == null ? DEFAULT_DELIMITER_PATTERN : delim;
        final var matched = delimiter.matcher(suffixesSrc);
        return matched.find() ? suffixesSrc.substring(matched.start()) : "";
    }
}
