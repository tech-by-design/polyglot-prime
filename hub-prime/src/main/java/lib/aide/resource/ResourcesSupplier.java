package lib.aide.resource;

import lib.aide.paths.Paths;

import java.net.URI;
import java.util.List;

public interface ResourcesSupplier<P extends Provenance, C, R extends Resource<? extends Nature, ?>> {
    URI identity();

    Paths<C, ResourceProvenance<P, R>> paths();

    List<ResourceProvenance<P, R>> resources();
}
