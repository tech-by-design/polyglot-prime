package lib.aide.resource.collection;

import lib.aide.paths.Paths;
import lib.aide.resource.Nature;
import lib.aide.resource.Provenance;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;
import lib.aide.resource.ResourcesSupplier;

import java.util.ArrayList;
import java.util.List;

public class Resources<C, R extends Resource<? extends Nature, ?>> {
    private final List<Paths<C, ResourceProvenance<?, R>>> paths;
    private final List<ResourceProvenance<?, R>> resources;

    public Resources(final List<ResourceProvenance<?, R>> resources,
            final List<Paths<C, ResourceProvenance<?, R>>> paths) {
        this.resources = resources;
        this.paths = paths;
    }

    public List<ResourceProvenance<?, R>> getResources() {
        return resources;
    }

    public List<Paths<C, ResourceProvenance<?, R>>> getPaths() {
        return paths;
    }

    public static class Builder<C, R extends Resource<? extends Nature, ?>> {
        private final List<ResourcesSupplier<?, C, R>> suppliers = new ArrayList<>();

        public <P extends Provenance> Builder<C, R> withSupplier(ResourcesSupplier<P, C, R> supplier) {
            suppliers.add(supplier);
            return this;
        }

        @SuppressWarnings("unchecked")
        public Resources<C, R> build() {
            final var allPaths = new ArrayList<Paths<C, ResourceProvenance<?, R>>>();
            final var allResources = new ArrayList<ResourceProvenance<?, R>>();

            for (final var supplier : suppliers) {
                final var supplierPaths = (Paths<C, ? extends ResourceProvenance<?, R>>) supplier.paths();
                final var supplierResources = (List<? extends ResourceProvenance<?, R>>) supplier.resources();

                allResources.addAll((List<ResourceProvenance<?, R>>) supplierResources);
                allPaths.add((Paths<C, ResourceProvenance<?, R>>) supplierPaths);
            }

            return new Resources<>(allResources, allPaths);
        }
    }
}
