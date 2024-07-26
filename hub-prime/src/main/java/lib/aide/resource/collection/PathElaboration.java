package lib.aide.resource.collection;

import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

import lib.aide.paths.Paths;
import lib.aide.paths.Paths.DeepMergeOperation;
import lib.aide.resource.Nature;
import lib.aide.resource.Provenance;
import lib.aide.resource.Resource;
import lib.aide.resource.ResourceProvenance;
import lib.aide.resource.TextResource;
import lib.aide.resource.content.ResourceFactory;
import lib.aide.resource.content.ResourceFactory.MapFromTextResult;

public record PathElaboration(MapFromTextResult elaboration,
        ResourceProvenance<?, Resource<? extends Nature, ?>> rp,
        List<Entry<String, DeepMergeOperation>> merged) {

    /**
     * If the file is `.path.yml` `.path.yaml` or `.path.json` or
     * `.[parent-dir-name].path.yml` or `[parent-dir-name].path.yml` or `.yaml` or
     * `.json` it's a special file which "elaborates" the current path, not a
     * content resource. Usually this means we just take all the contents of the
     * file and merge them as the parent node's attributes.
     * 
     * @param basename the basename to test
     * @param parent   the parent to test
     * @return true if the basename contains path elaboration content
     */
    public static <T extends Provenance> Optional<PathElaboration> fromBasename(final String basename,
            final Paths<String, ResourceProvenance<T, Resource<? extends Nature, ?>>>.Node parent,
            final Paths<String, ResourceProvenance<T, Resource<? extends Nature, ?>>>.Node newNode) {
        // if the file is `.path.yml` `.path.yaml` or `.path.json` or
        // `.<parent-dir-name>.path.yml` or `<parent-dir-name>.path.yml` or `.yaml` or
        // `.json` it's a special file which
        // "elaborates" the current path, not a content resource
        if (basename.matches("(\\.?%s)?\\.path\\.(yml|yaml|json)".formatted(parent.basename().orElse("ROOT")))
                &&
                newNode.payload().isPresent()) {
            final var rp = newNode.payload().orElseThrow();
            final var elaboration = ResourceFactory.mapFromText(basename,
                    Optional.of(() -> ((TextResource<?>) rp.resource()).content()));
            // if there are any issues in reading the path elaboration yaml
            List<Entry<String, DeepMergeOperation>> merged;
            if (elaboration.issues() != null && elaboration.issues().size() > 0) {
                elaboration.issues().forEach(ei -> parent.addIssue(ei));
                merged = List.of();
            } else {
                parent.addAttribute("isPathElaboration", elaboration);
                merged = parent.mergeAttributes(elaboration.result());
            }
            return Optional.of(new PathElaboration(elaboration, rp, merged));
        }
        return Optional.empty();
    }
}
