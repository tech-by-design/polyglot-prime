package lib.aide.resource;

public interface ForwardableResource<N extends Nature, C> extends Resource<N, C> {
    Class<C> contentClass();
}
