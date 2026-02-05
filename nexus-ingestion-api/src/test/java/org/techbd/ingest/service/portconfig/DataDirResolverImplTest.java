package org.techbd.ingest.service.portconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

/**
 * Comprehensive unit tests for DataDirResolverImpl.
 * Tests cover all scenarios including hold/normal routing, tenant ID resolution,
 * prefix application, error paths, and S3 path generation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DataDirResolverImpl Tests")
class DataDirResolverImplTest {

    @Mock
    private AppLogger appLogger;

    @Mock
    private TemplateLogger templateLogger;

    private DataDirResolverImpl dataDirResolver;

    // Test constants
    private static final String INTERACTION_ID = "test-interaction-123";
    private static final String SOURCE_ID = "netspective";
    private static final String MSG_TYPE = "pnr";
    private static final String TIMESTAMP = "20251202T143000Z";
    private static final String FILE_NAME = "sample.xml";
    private static final String DATA_BUCKET = "test-data-bucket";
    private static final String METADATA_BUCKET = "test-metadata-bucket";
    private static final int TEST_PORT = 8080;

    private ZonedDateTime testUploadTime;

    @BeforeEach
    void setUp() {
        when(appLogger.getLogger(DataDirResolverImpl.class)).thenReturn(templateLogger);
        dataDirResolver = new DataDirResolverImpl(appLogger);
        
        // Setup test upload time: 2025-12-02T14:30:00Z
        testUploadTime = ZonedDateTime.parse("2025-12-02T14:30:00Z", DateTimeFormatter.ISO_DATE_TIME);
    }

    @Nested
    @DisplayName("Normal Mode Data Key Tests")
    class NormalModeDataKeyTests {

        @Test
        @DisplayName("Should resolve data key with tenant ID (sourceId and msgType present)")
        void shouldResolveDataKeyWithTenantId() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("data/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
            verify(templateLogger).info(
                eq("[DATA_DIR_RESOLVER] Resolved Data Key: {} interactionId={}"),
                eq(expectedKey),
                eq(INTERACTION_ID)
            );
        }

        @Test
        @DisplayName("Should resolve data key without tenant ID (no sourceId or msgType)")
        void shouldResolveDataKeyWithoutTenantId() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(null, null);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("data/2025/12/02/%s_%s", INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should resolve data key with only sourceId as tenant")
        void shouldResolveDataKeyWithOnlySourceId() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(SOURCE_ID, null);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("data/%s/2025/12/02/%s_%s", 
                SOURCE_ID, INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should resolve data key with only msgType as tenant")
        void shouldResolveDataKeyWithOnlyMsgType() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(null, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("data/%s/2025/12/02/%s_%s", 
                MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should apply dataDir prefix to data key")
        void shouldApplyDataDirPrefix() {
            // Given
            PortEntry entry = createPortEntry("/api", "my-prefix/subdir", null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String baseKey = String.format("data/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            String expectedKey = "my-prefix/subdir/" + baseKey;
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should strip leading and trailing slashes from dataDir prefix")
        void shouldStripSlashesFromPrefix() {
            // Given
            PortEntry entry = createPortEntry("/api", "/my-prefix/subdir/", null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String baseKey = String.format("data/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            String expectedKey = "my-prefix/subdir/" + baseKey;
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should not apply prefix when dataDir is null")
        void shouldNotApplyPrefixWhenNull() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("data/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should not apply prefix when dataDir is blank")
        void shouldNotApplyPrefixWhenBlank() {
            // Given
            PortEntry entry = createPortEntry("/api", "   ", null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("data/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }
    }

    @Nested
    @DisplayName("Normal Mode Metadata Key Tests")
    class NormalModeMetadataKeyTests {

        @Test
        @DisplayName("Should resolve metadata key with tenant ID")
        void shouldResolveMetadataKeyWithTenantId() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("metadata/%s_%s/2025/12/02/%s_%s_metadata.json", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getMetadataKey());
        }

        @Test
        @DisplayName("Should resolve metadata key without tenant ID")
        void shouldResolveMetadataKeyWithoutTenantId() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(null, null);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("metadata/2025/12/02/%s_%s_metadata.json", 
                INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getMetadataKey());
        }

        @Test
        @DisplayName("Should apply metadataDir prefix to metadata key")
        void shouldApplyMetadataDirPrefix() {
            // Given
            PortEntry entry = createPortEntry("/api", null, "meta-prefix");
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String baseKey = String.format("metadata/%s_%s/2025/12/02/%s_%s_metadata.json", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            String expectedKey = "meta-prefix/" + baseKey;
            assertEquals(expectedKey, context.getMetadataKey());
        }
    }

    @Nested
    @DisplayName("Hold Mode Data Key Tests")
    class HoldModeDataKeyTests {

        @Test
        @DisplayName("Should resolve hold data key with tenant ID and timestamped filename")
        void shouldResolveHoldDataKeyWithTenantId() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("hold/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, TIMESTAMP, FILE_NAME);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should resolve hold data key without tenant ID using port number")
        void shouldResolveHoldDataKeyWithPort() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, null);
            RequestContext context = createContext(null, null);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("hold/%d/2025/12/02/%s_%s", 
                TEST_PORT, TIMESTAMP, FILE_NAME);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should apply dataDir prefix to hold data key")
        void shouldApplyPrefixToHoldDataKey() {
            // Given
            PortEntry entry = createPortEntry("/hold", "prefix/hold", null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String baseKey = String.format("hold/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, TIMESTAMP, FILE_NAME);
            String expectedKey = "prefix/hold/" + baseKey;
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should handle filename without extension in hold mode")
        void shouldHandleFilenameWithoutExtension() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setFileName("sample");

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("hold/%s_%s/2025/12/02/%s_sample", 
                SOURCE_ID, MSG_TYPE, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should handle null filename in hold mode")
        void shouldHandleNullFilename() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setFileName(null);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("hold/%s_%s/2025/12/02/%s_body", 
                SOURCE_ID, MSG_TYPE, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should handle blank filename in hold mode")
        void shouldHandleBlankFilename() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setFileName("   ");

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("hold/%s_%s/2025/12/02/%s_body", 
                SOURCE_ID, MSG_TYPE, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should handle filename with multiple dots")
        void shouldHandleFilenameWithMultipleDots() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setFileName("sample.data.xml");

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("hold/%s_%s/2025/12/02/%s_sample.data.xml", 
                SOURCE_ID, MSG_TYPE, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }
    }

    @Nested
    @DisplayName("Hold Mode Metadata Key Tests")
    class HoldModeMetadataKeyTests {

        @Test
        @DisplayName("Should resolve hold metadata key with tenant ID")
        void shouldResolveHoldMetadataKeyWithTenantId() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("hold/metadata/%s_%s/2025/12/02/%s_%s_metadata.json", 
                SOURCE_ID, MSG_TYPE, TIMESTAMP, FILE_NAME);
            assertEquals(expectedKey, context.getMetadataKey());
        }

        @Test
        @DisplayName("Should resolve hold metadata key without tenant ID using port")
        void shouldResolveHoldMetadataKeyWithPort() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, null);
            RequestContext context = createContext(null, null);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("hold/metadata/%d/2025/12/02/%s_%s_metadata.json", 
                TEST_PORT, TIMESTAMP, FILE_NAME);
            assertEquals(expectedKey, context.getMetadataKey());
        }

        @Test
        @DisplayName("Should apply metadataDir prefix to hold metadata key")
        void shouldApplyPrefixToHoldMetadataKey() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, "prefix/metadata");
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String baseKey = String.format("hold/metadata/%s_%s/2025/12/02/%s_%s_metadata.json", 
                SOURCE_ID, MSG_TYPE, TIMESTAMP, FILE_NAME);
            String expectedKey = "prefix/metadata/" + baseKey;
            assertEquals(expectedKey, context.getMetadataKey());
        }
    }

    @Nested
    @DisplayName("Error Mode Tests - No Prefix Applied")
    class ErrorModeTests {

        @Test
        @DisplayName("Should NOT apply prefix to data key when ingestion failed")
        void shouldNotApplyPrefixToDataKeyOnError() {
            // Given
            PortEntry entry = createPortEntry("/api", "my-prefix", null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setIngestionFailed(true);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("error/2025/12/02/%s_%s", 
                INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should NOT apply prefix to metadata key when ingestion failed")
        void shouldNotApplyPrefixToMetadataKeyOnError() {
            // Given
            PortEntry entry = createPortEntry("/api", null, "meta-prefix");
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setIngestionFailed(true);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("error/2025/12/02/%s_%s_metadata.json", 
                INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getMetadataKey());
        }

        @Test
        @DisplayName("Should use error path for hold mode when ingestion failed")
        void shouldUseErrorPathForHoldModeOnError() {
            // Given
            PortEntry entry = createPortEntry("/hold", "hold-prefix", null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setIngestionFailed(true);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedDataKey = String.format("error/2025/12/02/%s_%s", 
                INTERACTION_ID, TIMESTAMP);
            String expectedMetadataKey = String.format("error/2025/12/02/%s_%s_metadata.json", 
                INTERACTION_ID, TIMESTAMP);
            
            assertEquals(expectedDataKey, context.getObjectKey());
            assertEquals(expectedMetadataKey, context.getMetadataKey());
        }
    }

    @Nested
    @DisplayName("Ack Object Key Tests")
    class AckObjectKeyTests {

        @Test
        @DisplayName("Should resolve ack object key as data key plus _ack suffix")
        void shouldResolveAckObjectKey() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedAckKey = context.getObjectKey() + "_ack";
            assertEquals(expectedAckKey, context.getAckObjectKey());
            verify(templateLogger).info(
                eq("[DATA_DIR_RESOLVER] Resolved Ack Object Key: {} interactionId={}"),
                eq(expectedAckKey),
                eq(INTERACTION_ID)
            );
        }

        @Test
        @DisplayName("Should resolve ack object key for hold mode")
        void shouldResolveAckObjectKeyForHoldMode() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedAckKey = context.getObjectKey() + "_ack";
            assertEquals(expectedAckKey, context.getAckObjectKey());
        }
    }

    @Nested
    @DisplayName("Full S3 Path Tests")
    class FullS3PathTests {

        @Test
        @DisplayName("Should update full S3 data path")
        void shouldUpdateFullS3DataPath() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setDataBucketName(DATA_BUCKET);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedPath = Constants.S3_PREFIX + DATA_BUCKET + "/" + context.getObjectKey();
            assertEquals(expectedPath, context.getFullS3DataPath());
            verify(templateLogger).debug(
                eq("[DATA_DIR_RESOLVER] Updated Full S3 Data Path: {} interactionId={}"),
                eq(expectedPath),
                eq(INTERACTION_ID)
            );
        }

        @Test
        @DisplayName("Should update full S3 metadata path")
        void shouldUpdateFullS3MetadataPath() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setMetaDataBucketName(METADATA_BUCKET);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedPath = Constants.S3_PREFIX + METADATA_BUCKET + "/" + context.getMetadataKey();
            assertEquals(expectedPath, context.getFullS3MetadataPath());
        }

        @Test
        @DisplayName("Should update full S3 ack path")
        void shouldUpdateFullS3AckPath() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setDataBucketName(DATA_BUCKET);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedPath = Constants.S3_PREFIX + DATA_BUCKET + "/" + context.getAckObjectKey();
            assertEquals(expectedPath, context.getFullS3AckMessagePath());
        }

        @Test
        @DisplayName("Should not update full S3 paths when bucket names are null")
        void shouldNotUpdatePathsWhenBucketsNull() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            // Buckets are null by default

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            assertNull(context.getFullS3DataPath());
            assertNull(context.getFullS3MetadataPath());
            assertNull(context.getFullS3AckMessagePath());
        }
    }

    @Nested
    @DisplayName("Update Logic Tests")
    class UpdateLogicTests {

        @Test
        @DisplayName("Should not update keys when they already match resolved values")
        void shouldNotUpdateWhenKeysMatch() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            
            String expectedDataKey = String.format("data/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            String expectedMetadataKey = String.format("metadata/%s_%s/2025/12/02/%s_%s_metadata.json", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            String expectedAckKey = expectedDataKey + "_ack";
            
            context.setObjectKey(expectedDataKey);
            context.setMetadataKey(expectedMetadataKey);
            context.setAckObjectKey(expectedAckKey);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then - keys should remain the same
            assertEquals(expectedDataKey, context.getObjectKey());
            assertEquals(expectedMetadataKey, context.getMetadataKey());
            assertEquals(expectedAckKey, context.getAckObjectKey());

            // Verify no info logs for updates (since keys didn't change)
            verify(templateLogger, never()).info(
                eq("[DATA_DIR_RESOLVER] Resolved Data Key: {} interactionId={}"),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("Should update only changed keys")
        void shouldUpdateOnlyChangedKeys() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            
            String expectedDataKey = String.format("data/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            
            // Set data key to match, but leave metadata key different
            context.setObjectKey(expectedDataKey);
            context.setMetadataKey("different-metadata-key");

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            assertEquals(expectedDataKey, context.getObjectKey());
            assertNotNull(context.getMetadataKey());

            // Verify only metadata key info log
            verify(templateLogger, never()).info(
                eq("[DATA_DIR_RESOLVER] Resolved Data Key: {} interactionId={}"),
                any(),
                any()
            );
            verify(templateLogger).info(
                eq("[DATA_DIR_RESOLVER] Resolved Metadata Key: {} interactionId={}"),
                any(),
                eq(INTERACTION_ID)
            );
        }
    }

    @Nested
    @DisplayName("Null Port Entry Tests")
    class NullPortEntryTests {

        @Test
        @DisplayName("Should handle null port entry and use default routing")
        void shouldHandleNullPortEntry() {
            // Given
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, null, INTERACTION_ID);

            // Then - should use normal mode (not hold)
            String expectedKey = String.format("data/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle blank sourceId and msgType")
        void shouldHandleBlankSourceIdAndMsgType() {
            // Given
            PortEntry entry = createPortEntry("/api", null, null);
            RequestContext context = createContext("  ", "  ");

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then - should treat as no tenant
            String expectedKey = String.format("data/2025/12/02/%s_%s", INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should handle prefix with only slashes")
        void shouldHandlePrefixWithOnlySlashes() {
            // Given
            PortEntry entry = createPortEntry("/api", "///", null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then - should treat as no prefix
            String expectedKey = String.format("data/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should handle different route (not /hold)")
        void shouldHandleDifferentRoute() {
            // Given
            PortEntry entry = createPortEntry("/custom-route", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then - should use normal mode
            String expectedKey = String.format("data/%s_%s/2025/12/02/%s_%s", 
                SOURCE_ID, MSG_TYPE, INTERACTION_ID, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should handle filename with dot at end")
        void shouldHandleFilenameWithDotAtEnd() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setFileName("sample.");

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("hold/%s_%s/2025/12/02/%s_sample.", 
                SOURCE_ID, MSG_TYPE, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }

        @Test
        @DisplayName("Should handle filename with dot at start")
        void shouldHandleFilenameWithDotAtStart() {
            // Given
            PortEntry entry = createPortEntry("/hold", null, null);
            RequestContext context = createContext(SOURCE_ID, MSG_TYPE);
            context.setFileName(".hidden");

            // When
            dataDirResolver.resolve(context, entry, INTERACTION_ID);

            // Then
            String expectedKey = String.format("hold/%s_%s/2025/12/02/%s_.hidden", 
                SOURCE_ID, MSG_TYPE, TIMESTAMP);
            assertEquals(expectedKey, context.getObjectKey());
        }
    }

    // Helper methods

    private RequestContext createContext(String sourceId, String msgType) {
        RequestContext context = new RequestContext(INTERACTION_ID, TEST_PORT, sourceId, msgType);
        context.setTimestamp(TIMESTAMP);
        context.setFileName(FILE_NAME);
        context.setUploadTime(testUploadTime);
        return context;
    }

    private PortEntry createPortEntry(String route, String dataDir, String metadataDir) {
        PortEntry entry = new PortEntry();
        entry.route = route;
        entry.dataDir = dataDir;
        entry.metadataDir = metadataDir;
        entry.port = TEST_PORT;
        return entry;
    }
}