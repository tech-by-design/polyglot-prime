package lib.aide.resource;

public record ResourceProvenance<P extends Provenance, R extends Resource<? extends Nature, ?>>(P provenance,
                R resource) {
}
