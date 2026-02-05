package org.techbd.ingest.service.portconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

class PortResolverServiceTest {

    private PortConfig portConfig;
    private AppLogger appLogger;
    private TemplateLogger templateLogger;

    private PortConfigResolver resolver1;
    private PortConfigResolver resolver2;

    private PortResolverService service;

    private List<PortEntry> portEntries;

    @BeforeEach
    void setUp() {
        portConfig = mock(PortConfig.class);
        appLogger = mock(AppLogger.class);
        templateLogger = mock(TemplateLogger.class);

        when(appLogger.getLogger(PortResolverService.class))
                .thenReturn(templateLogger);

        resolver1 = mock(PortConfigResolver.class);
        resolver2 = mock(PortConfigResolver.class);

        portEntries = List.of(mockPortEntry(8080));
        when(portConfig.getPortConfigurationList()).thenReturn(portEntries);

        service = new PortResolverService(
                List.of(resolver1, resolver2),
                portConfig,
                appLogger
        );
    }

    @Test
    @DisplayName("Should return entry when first resolver matches")
    void shouldReturnEntryWhenFirstResolverMatches() {
        RequestContext context =
                new RequestContext("interaction-1", 8080, "SRC", "MSG");

        PortEntry entry = mockPortEntry(8080);

        when(resolver1.resolve(portEntries, context))
                .thenReturn(Optional.of(entry));
        when(resolver2.resolve(any(), any()))
                .thenReturn(Optional.empty());

        Optional<PortEntry> result = service.resolve(context);

        assertThat(result)
                .isPresent()
                .contains(entry);

        verify(resolver1).resolve(portEntries, context);
        verify(resolver2, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("Should return entry when second resolver matches")
    void shouldReturnEntryWhenSecondResolverMatches() {
        RequestContext context =
                new RequestContext("interaction-2", 9090, "SRC", "MSG");

        PortEntry entry = mockPortEntry(9090);

        when(resolver1.resolve(portEntries, context))
                .thenReturn(Optional.empty());
        when(resolver2.resolve(portEntries, context))
                .thenReturn(Optional.of(entry));

        Optional<PortEntry> result = service.resolve(context);

        assertThat(result)
                .isPresent()
                .contains(entry);

        verify(resolver1).resolve(portEntries, context);
        verify(resolver2).resolve(portEntries, context);
    }

    @Test
    @DisplayName("Should throw exception when no resolver matches")
    void shouldThrowExceptionWhenNoResolverMatches() {
        RequestContext context =
                new RequestContext("interaction-3", 7070, "SRC", "MSG");

        when(resolver1.resolve(portEntries, context))
                .thenReturn(Optional.empty());
        when(resolver2.resolve(portEntries, context))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No configuration found for the given port");

        verify(resolver1).resolve(portEntries, context);
        verify(resolver2).resolve(portEntries, context);
    }

    private PortEntry mockPortEntry(int port) {
        PortEntry entry = new PortEntry();
        entry.port = port;
        entry.sourceId = "SRC";
        entry.msgType = "MSG";
        return entry;
    }
}
