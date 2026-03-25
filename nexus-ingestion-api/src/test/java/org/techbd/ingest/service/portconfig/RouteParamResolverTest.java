package org.techbd.ingest.service.portconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.model.RequestContext;

class RouteParamResolverTest {

    private final RouteParamResolver resolver = new RouteParamResolver();

    @Test
    @DisplayName("Should return empty when sourceId is null")
    void shouldReturnEmptyWhenSourceIdIsNull() {
        RequestContext context = new RequestContext("i-1", 8080, null, "ORU");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(mockEntry("SRC", "ORU", Constants.HTTP)), context, Constants.HTTP);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when msgType is null")
    void shouldReturnEmptyWhenMsgTypeIsNull() {
        RequestContext context = new RequestContext("i-2", 8080, "SRC", null);

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(mockEntry("SRC", "ORU", Constants.HTTP)), context, Constants.HTTP);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when both sourceId and msgType are null")
    void shouldReturnEmptyWhenBothNull() {
        RequestContext context = new RequestContext("i-3", 8080, null, null);

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(mockEntry("SRC", "ORU", Constants.HTTP)), context, Constants.HTTP);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when no entry matches sourceId + msgType")
    void shouldThrowWhenNoEntryMatches() {
        RequestContext context = new RequestContext("i-4", 8080, "SRC", "ORM");

        assertThatThrownBy(() ->
                resolver.resolve(
                        List.of(
                                mockEntry("SRC", "ORU", Constants.HTTP),
                                mockEntry("LAB", "ADT", Constants.HTTP)),
                        context, Constants.HTTP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SRC")
                .hasMessageContaining("ORM")
                .hasMessageContaining(Constants.HTTP);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when sourceId matches but msgType does not")
    void shouldThrowWhenOnlySourceIdMatches() {
        RequestContext context = new RequestContext("i-5", 8080, "SRC", "ORM");

        assertThatThrownBy(() ->
                resolver.resolve(List.of(mockEntry("SRC", "ORU", Constants.HTTP)), context, Constants.HTTP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ORM");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when msgType matches but sourceId does not")
    void shouldThrowWhenOnlyMsgTypeMatches() {
        RequestContext context = new RequestContext("i-6", 8080, "UNKNOWN", "ORU");

        assertThatThrownBy(() ->
                resolver.resolve(List.of(mockEntry("SRC", "ORU", Constants.HTTP)), context, Constants.HTTP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when portConfigList is empty")
    void shouldThrowWhenPortConfigListIsEmpty() {
        RequestContext context = new RequestContext("i-7", 8080, "SRC", "ORU");

        assertThatThrownBy(() ->
                resolver.resolve(Collections.emptyList(), context, Constants.HTTP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SRC")
                .hasMessageContaining("ORU");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when portConfigList contains only nulls")
    void shouldThrowWhenPortConfigListHasOnlyNulls() {
        RequestContext context = new RequestContext("i-8", 8080, "SRC", "ORU");

        List<PortConfig.PortEntry> list = new java.util.ArrayList<>();
        list.add(null);
        list.add(null);

        assertThatThrownBy(() ->
                resolver.resolve(list, context, Constants.HTTP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when protocol mismatches and no fallback exists")
    void shouldThrowWhenProtocolMismatchAndNoFallback() {
        PortConfig.PortEntry mllpEntry = mockEntry("SRC", "ORU", "MLLP");

        RequestContext context = new RequestContext("i-9", 8080, "SRC", "ORU");

        assertThatThrownBy(() ->
                resolver.resolve(List.of(mllpEntry), context, Constants.HTTP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SRC")
                .hasMessageContaining("ORU")
                .hasMessageContaining(Constants.HTTP);
    }

    // -------------------------------------------------------------------------
    // Exact protocol match
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should resolve entry with exact protocol match")
    void shouldResolveExactProtocolMatch() {
        PortConfig.PortEntry httpEntry  = mockEntry("SRC", "ORU", Constants.HTTP);
        PortConfig.PortEntry otherEntry = mockEntry("SRC", "ORU", "MLLP");

        RequestContext context = new RequestContext("i-10", 8080, "SRC", "ORU");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(otherEntry, httpEntry), context, Constants.HTTP);

        assertThat(result).isPresent().contains(httpEntry);
    }

    @Test
    @DisplayName("Exact protocol match should be preferred over null-protocol fallback")
    void exactMatchShouldTakePrecedenceOverFallback() {
        PortConfig.PortEntry nullProtocol  = mockEntry("SRC", "ORU", null);
        PortConfig.PortEntry exactProtocol = mockEntry("SRC", "ORU", Constants.HTTP);

        RequestContext context = new RequestContext("i-11", 8080, "SRC", "ORU");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(nullProtocol, exactProtocol), context, Constants.HTTP);

        assertThat(result).isPresent().contains(exactProtocol);
    }

    @Test
    @DisplayName("Protocol matching should be case-insensitive")
    void protocolMatchShouldBeCaseInsensitive() {
        PortConfig.PortEntry entry = mockEntry("SRC", "ORU", "http"); // lowercase in config

        RequestContext context = new RequestContext("i-12", 8080, "SRC", "ORU");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(entry), context, Constants.HTTP); // HTTP uppercase

        assertThat(result).isPresent().contains(entry);
    }

    // -------------------------------------------------------------------------
    // Null / blank protocol fallback
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should fall back to null-protocol entry when no exact protocol match exists")
    void shouldFallBackToNullProtocolEntry() {
        PortConfig.PortEntry nullProtocol = mockEntry("SRC", "ORU", null);

        RequestContext context = new RequestContext("i-13", 8080, "SRC", "ORU");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(nullProtocol), context, Constants.HTTP);

        assertThat(result).isPresent().contains(nullProtocol);
    }

    @Test
    @DisplayName("Should fall back to blank-protocol entry when no exact protocol match exists")
    void shouldFallBackToBlankProtocolEntry() {
        PortConfig.PortEntry blankProtocol = mockEntry("SRC", "ORU", "  ");

        RequestContext context = new RequestContext("i-14", 8080, "SRC", "ORU");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(blankProtocol), context, Constants.HTTP);

        assertThat(result).isPresent().contains(blankProtocol);
    }

    // -------------------------------------------------------------------------
    // Case-insensitive sourceId / msgType
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should resolve matching entry with mixed-case sourceId and msgType")
    void shouldResolveMatchingEntryIgnoringCase() {
        PortConfig.PortEntry matching    = mockEntry("lab1", "oru", null);
        PortConfig.PortEntry nonMatching = mockEntry("lab2", "adt", null);

        RequestContext context = new RequestContext("i-15", 8080, "LAB1", "ORU");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(nonMatching, matching), context, Constants.HTTP);

        assertThat(result).isPresent().contains(matching);
    }

    // -------------------------------------------------------------------------
    // Multiple candidates — first-match semantics
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Should return the first exact-protocol match when multiple candidates exist")
    void shouldReturnFirstExactMatchAmongMultiple() {
        PortConfig.PortEntry first  = mockEntry("SRC", "ORU", Constants.HTTP);
        PortConfig.PortEntry second = mockEntry("SRC", "ORU", Constants.HTTP);

        RequestContext context = new RequestContext("i-16", 8080, "SRC", "ORU");

        Optional<PortConfig.PortEntry> result =
                resolver.resolve(List.of(first, second), context, Constants.HTTP);

        assertThat(result).isPresent().contains(first);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private PortConfig.PortEntry mockEntry(String sourceId, String msgType, String protocol) {
        PortConfig.PortEntry entry = new PortConfig.PortEntry();
        entry.sourceId = sourceId;
        entry.msgType  = msgType;
        entry.setProtocol(protocol);
        return entry;
    }
}