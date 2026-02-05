package org.techbd.ingest.service.portconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.model.RequestContext;

class PortResolverTest {

    private final PortResolver resolver = new PortResolver();

    @Test
    @DisplayName("Should return empty when destination port is null")
    void shouldReturnEmptyWhenDestinationPortIsNull() {
        RequestContext context =
                new RequestContext("interaction-1", 0, "source", "msgType");
        context.setDestinationPort(null);

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(mockPortEntry(8080)), context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should resolve matching port entry")
    void shouldResolveMatchingPortEntry() {
        PortConfig.PortEntry entry8080 = mockPortEntry(8080);
        PortConfig.PortEntry entry9090 = mockPortEntry(9090);

        RequestContext context =
                new RequestContext("interaction-2", 8080, "source", "msgType");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(entry9090, entry8080), context);

        assertThat(result)
                .isPresent()
                .contains(entry8080);
    }

    @Test
    @DisplayName("Should return empty when no port matches")
    void shouldReturnEmptyWhenNoPortMatches() {
        RequestContext context =
                new RequestContext("interaction-3", 7070, "source", "msgType");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(
                        List.of(mockPortEntry(8080), mockPortEntry(9090)),
                        context);

        assertThat(result).isEmpty();
    }

    private PortConfig.PortEntry mockPortEntry(int port) {
        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.port = port; // direct field access (no setter exists)
        return entry;
    }
}
