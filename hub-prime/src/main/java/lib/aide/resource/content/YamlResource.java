package lib.aide.resource.content;

import java.util.Optional;
import java.util.function.Supplier;

import lib.aide.paths.PathSuffixes;
import lib.aide.resource.Nature;
import lib.aide.resource.TextResource;

public class YamlResource implements TextResource<YamlResource.YamlNature> {
    private final Supplier<String> src;
    private final YamlNature nature;
    private final Optional<PathSuffixes> suffixes;

    public YamlResource(final String src, YamlNature nature, Optional<PathSuffixes> suffixes) {
        this.src = () -> src;
        this.nature = nature;
        this.suffixes = suffixes;
    }

    public YamlResource(final Supplier<String> src, YamlNature nature, Optional<PathSuffixes> suffixes) {
        this.src = src;
        this.nature = nature;
        this.suffixes = suffixes;
    }

    @Override
    public YamlNature nature() {
        return nature;
    }

    @Override
    public String content() {
        return src.get();
    }

    public Optional<PathSuffixes> suffixes() {
        return suffixes;
    }

    public static class YamlNature implements Nature {
        @Override
        public String mimeType() {
            return "application/x-yaml";
        }
    }
}
