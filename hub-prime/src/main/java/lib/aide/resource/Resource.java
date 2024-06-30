package lib.aide.resource;

public interface Resource<N extends Nature, C> {
    N nature();

    C content();
}
