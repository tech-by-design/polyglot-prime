package org.techbd.ingest.service.portconfig;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

@ExtendWith(MockitoExtension.class)
class PortConfigApplierServiceTest {

    @Mock
    private PortResolverService portEntryResolver;

    @Mock
    private PortConfigAttributeResolver resolver1;

    @Mock
    private PortConfigAttributeResolver resolver2;

    @Mock
    private AppLogger appLogger;

    @Mock
    private TemplateLogger templateLogger;

    private PortConfigApplierService service;

    @BeforeEach
    void setUp() {
        when(appLogger.getLogger(PortConfigApplierService.class))
                .thenReturn(templateLogger);

        service = new PortConfigApplierService(
                portEntryResolver,
                List.of(resolver1, resolver2),
                appLogger
        );
    }

    @Test
    void shouldReturnContextUnchangedWhenNoPortEntryResolved() {
        // given
        RequestContext context = mock(RequestContext.class);
        when(context.getInteractionId()).thenReturn("interaction-123");
        when(portEntryResolver.resolve(context)).thenReturn(Optional.empty());

        // when
        RequestContext result = service.applyPortConfigOverrides(context);

        // then
        verify(portEntryResolver).resolve(context);
        verifyNoInteractions(resolver1, resolver2);

        verify(templateLogger).debug(
                contains("No port entry resolved"),
                eq("interaction-123")
        );

        assert result == context;
    }

    @Test
    void shouldApplyAllResolversWhenPortEntryResolved() {
        // given
        RequestContext context = mock(RequestContext.class);
        when(context.getInteractionId()).thenReturn("interaction-456");
        when(context.getSourceId()).thenReturn("SRC1");
        when(context.getMsgType()).thenReturn("ADT");

        PortEntry entry = mock(PortEntry.class);
        entry.port = 8080;

        when(portEntryResolver.resolve(context))
                .thenReturn(Optional.of(entry));

        // when
        RequestContext result = service.applyPortConfigOverrides(context);

        // then
        verify(resolver1).resolve(context, entry, "interaction-456");
        verify(resolver2).resolve(context, entry, "interaction-456");

        verify(templateLogger).info(
                contains("CHECK_FOR_PORT_CONFIG_OVERRIDES"),
                eq(8080),
                eq("SRC1"),
                eq("ADT"),
                eq("interaction-456")
        );

        verify(templateLogger).info(
                contains("All port config overrides applied successfully"),
                eq("interaction-456")
        );

        assert result == context;
    }
}
