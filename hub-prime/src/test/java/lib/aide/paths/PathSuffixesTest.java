package lib.aide.paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

public class PathSuffixesTest {

    @Test
    void givenNullSrc_thenThrowIllegalArgumentException() {
        assertThatThrownBy(() -> new PathSuffixes(null, PathSuffixes.DEFAULT_DELIMITER_PATTERN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source string cannot be null or empty");
    }

    @Test
    void givenEmptySrc_thenThrowIllegalArgumentException() {
        assertThatThrownBy(() -> new PathSuffixes("", PathSuffixes.DEFAULT_DELIMITER_PATTERN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source string cannot be null or empty");
    }

    @Test
    void givenNoDelimiterInSrc_thenReturnEmptySuffixes() {
        var src = "my-file";
        var suffixes = new PathSuffixes(src, PathSuffixes.DEFAULT_DELIMITER_PATTERN);
        
        assertThat(suffixes.src()).isEqualTo(src);
        assertThat(suffixes.delimiter()).isEqualTo(PathSuffixes.DEFAULT_DELIMITER_PATTERN);
        assertThat(suffixes.suffixes()).isEmpty();
    }

    @Test
    void givenSingleSuffixInSrc_thenReturnSingleSuffix() {
        var src = "my-file.md";
        var suffixes = new PathSuffixes(src, PathSuffixes.DEFAULT_DELIMITER_PATTERN);
        
        assertThat(suffixes.src()).isEqualTo(src);
        assertThat(suffixes.delimiter()).isEqualTo(PathSuffixes.DEFAULT_DELIMITER_PATTERN);
        assertThat(suffixes.suffixes()).containsExactly("md");
    }

    @Test
    void givenMultipleSuffixesInSrc_thenReturnSuffixesInCorrectOrder() {
        var src = "my-file.special-type.md";
        var suffixes = new PathSuffixes(src, PathSuffixes.DEFAULT_DELIMITER_PATTERN);
        
        assertThat(suffixes.src()).isEqualTo(src);
        assertThat(suffixes.delimiter()).isEqualTo(PathSuffixes.DEFAULT_DELIMITER_PATTERN);
        assertThat(suffixes.suffixes()).containsExactly("md", "special-type");
    }

    @Test
    void givenCustomDelimiter_thenReturnSuffixes() {
        var src = "my-file+special+type+md";
        var customDelimiter = Pattern.compile("\\+");
        var suffixes = new PathSuffixes(src, customDelimiter);
        
        assertThat(suffixes.src()).isEqualTo(src);
        assertThat(suffixes.delimiter()).isEqualTo(customDelimiter);
        assertThat(suffixes.suffixes()).containsExactly("md", "type", "special");
    }

    @Test
    void givenNoSuffixes_thenReturnEmptySuffixesList() {
        var src = "my-file";
        var suffixes = PathSuffixes.extractSuffixes(src, PathSuffixes.DEFAULT_DELIMITER_PATTERN);
        
        assertThat(suffixes).isEmpty();
    }

    @Test
    void givenOneSuffix_thenReturnSingleSuffixInList() {
        var src = "my-file.md";
        var suffixes = PathSuffixes.extractSuffixes(src, PathSuffixes.DEFAULT_DELIMITER_PATTERN);
        
        assertThat(suffixes).containsExactly("md");
    }

    @Test
    void givenMultipleSuffixes_thenReturnSuffixesInCorrectOrderInList() {
        var src = "my-file.special-type.md";
        var suffixes = PathSuffixes.extractSuffixes(src, PathSuffixes.DEFAULT_DELIMITER_PATTERN);
        
        assertThat(suffixes).containsExactly("md", "special-type");
    }

    @Test
    void givenCustomDelimiterAndMultipleSuffixes_thenReturnSuffixesInCorrectOrderInList() {
        var src = "my-file+special+type+md";
        var customDelimiter = Pattern.compile("\\+");
        var suffixes = PathSuffixes.extractSuffixes(src, customDelimiter);
        
        assertThat(suffixes).containsExactly("md", "type", "special");
    }
}
