package lib.aide.resource.content;

import java.util.Optional;
import java.util.function.Supplier;

import lib.aide.paths.PathSuffixes;
import lib.aide.resource.TextResource;

public class EmptyResource implements TextResource<EmptyNature> {
    public static final EmptyResource SINGLETON = new EmptyResource();

    private final Supplier<String> src;
    private final EmptyNature nature;
    private final Optional<PathSuffixes> suffixes;

    public EmptyResource() {
        this.src = () -> "EMPTY";
        this.nature = new EmptyNature();
        this.suffixes = Optional.empty();
    }

    public EmptyResource(final String src, EmptyNature nature, Optional<PathSuffixes> suffixes) {
        this.src = () -> src;
        this.nature = nature;
        this.suffixes = suffixes;
    }

    public EmptyResource(final Supplier<String> src, EmptyNature nature, Optional<PathSuffixes> suffixes) {
        this.src = src;
        this.nature = nature;
        this.suffixes = suffixes;
    }

    @Override
    public EmptyNature nature() {
        return nature;
    }

    @Override
    public String content() {
        return src.get();
    }

    public Optional<PathSuffixes> suffixes() {
        return suffixes;
    }
}
