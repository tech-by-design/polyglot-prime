package lib.aide.resource.content;

import java.util.Optional;
import java.util.function.Supplier;

import lib.aide.paths.PathSuffixes;
import lib.aide.resource.Nature;
import lib.aide.resource.TextResource;

public class JsonResource implements TextResource<JsonResource.JsonNature> {
    private final Supplier<String> src;
    private final JsonNature nature;
    private final Optional<PathSuffixes> suffixes;

    public JsonResource(final String src, JsonNature nature, Optional<PathSuffixes> suffixes) {
        this.src = () -> src;
        this.nature = nature;
        this.suffixes = suffixes;
    }

    public JsonResource(final Supplier<String> src, JsonNature nature, Optional<PathSuffixes> suffixes) {
        this.src = src;
        this.nature = nature;
        this.suffixes = suffixes;
    }

    @Override
    public JsonNature nature() {
        return nature;
    }

    @Override
    public String content() {
        return src.get();
    }

    public Optional<PathSuffixes> suffixes() {
        return suffixes;
    }

    public static class JsonNature implements Nature {
        @Override
        public String mimeType() {
            return "application/json";
        }
    }
}
