package org.techbd.ingest.service.portconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.model.RequestContext;

class RouteParamResolverTest {

    private final RouteParamResolver resolver = new RouteParamResolver();

    @Test
    @DisplayName("Should return empty when sourceId is null")
    void shouldReturnEmptyWhenSourceIdIsNull() {
        RequestContext context =
                new RequestContext("interaction-1", 8080, null, "ORU");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(mockEntry("SRC", "ORU")), context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when msgType is null")
    void shouldReturnEmptyWhenMsgTypeIsNull() {
        RequestContext context =
                new RequestContext("interaction-2", 8080, "SRC", null);

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(mockEntry("SRC", "ORU")), context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should resolve matching entry (case-insensitive)")
    void shouldResolveMatchingEntry() {
        PortConfig.PortEntry matching = mockEntry("lab1", "oru");
        PortConfig.PortEntry nonMatching = mockEntry("lab2", "adt");

        RequestContext context =
                new RequestContext("interaction-3", 8080, "LAB1", "ORU");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(nonMatching, matching), context);

        assertThat(result)
                .isPresent()
                .contains(matching);
    }

    @Test
    @DisplayName("Should return empty when no entry matches")
    void shouldReturnEmptyWhenNoEntryMatches() {
        RequestContext context =
                new RequestContext("interaction-4", 8080, "SRC", "ORM");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(
                        List.of(mockEntry("SRC", "ORU"), mockEntry("LAB", "ADT")),
                        context);

        assertThat(result).isEmpty();
    }

    private PortConfig.PortEntry mockEntry(String sourceId, String msgType) {
        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.sourceId = sourceId;
        entry.msgType = msgType;
        return entry;
    }
}
