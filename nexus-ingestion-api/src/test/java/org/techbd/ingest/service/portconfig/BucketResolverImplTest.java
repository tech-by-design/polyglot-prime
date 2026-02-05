package org.techbd.ingest.service.portconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.config.PortConfig.PortEntry;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;

/**
 * Comprehensive unit tests for BucketResolverImpl.
 * Tests cover all scenarios including /hold route logic, default routing,
 * null handling, and bucket update logic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BucketResolverImpl Tests")
class BucketResolverImplTest {

    @Mock
    private AppConfig appConfig;

    @Mock
    private AppLogger appLogger;

    @Mock
    private TemplateLogger templateLogger;

    @Mock
    private AppConfig.Aws aws;

    @Mock
    private AppConfig.Aws.S3 s3Config;

    @Mock
    private AppConfig.Aws.S3.BucketConfig defaultConfig;

    @Mock
    private AppConfig.Aws.S3.BucketConfig holdConfig;

    private BucketResolverImpl bucketResolver;

    // Test constants
    private static final String DEFAULT_DATA_BUCKET = "default-data-bucket";
    private static final String DEFAULT_METADATA_BUCKET = "default-metadata-bucket";
    private static final String HOLD_DATA_BUCKET = "hold-data-bucket";
    private static final String HOLD_METADATA_BUCKET = "hold-metadata-bucket";
    private static final String INTERACTION_ID = "test-interaction-123";

    @BeforeEach
    void setUp() {
        // Setup mock hierarchy for AppConfig
        when(appLogger.getLogger(BucketResolverImpl.class)).thenReturn(templateLogger);
        when(appConfig.getAws()).thenReturn(aws);
        when(aws.getS3()).thenReturn(s3Config);
        when(s3Config.getDefaultConfig()).thenReturn(defaultConfig);
        when(s3Config.getHoldConfig()).thenReturn(holdConfig);

        // Setup default bucket configuration
        when(defaultConfig.getBucket()).thenReturn(DEFAULT_DATA_BUCKET);
        when(defaultConfig.getMetadataBucket()).thenReturn(DEFAULT_METADATA_BUCKET);

        // Setup hold bucket configuration
        when(holdConfig.getBucket()).thenReturn(HOLD_DATA_BUCKET);
        when(holdConfig.getMetadataBucket()).thenReturn(HOLD_METADATA_BUCKET);

        bucketResolver = new BucketResolverImpl(appConfig, appLogger);
    }


    @Nested
    @DisplayName("Resolve Method - Hold Route Tests")
    class HoldRouteTests {

        @Test
        @DisplayName("Should resolve hold buckets when port entry has /hold route")
        void shouldResolveHoldBucketsForHoldRoute() {
            // Given
            PortEntry portEntry = createPortEntry("/hold");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then
            assertEquals(HOLD_DATA_BUCKET, context.getDataBucketName());
            assertEquals(HOLD_METADATA_BUCKET, context.getMetaDataBucketName());

            // Verify logging
            verify(templateLogger).info(
                eq("[BUCKET_RESOLVER] Resolved data bucket: {} for route: {}, interactionId: {}"),
                eq(HOLD_DATA_BUCKET),
                eq("/hold"),
                eq(INTERACTION_ID)
            );
            verify(templateLogger).info(
                eq("[BUCKET_RESOLVER] Resolved metadata bucket: {} for route: {}, interactionId: {}"),
                eq(HOLD_METADATA_BUCKET),
                eq("/hold"),
                eq(INTERACTION_ID)
            );
            verify(templateLogger).debug(
                eq("[BUCKET_RESOLVER] Data bucket updated to: {} interactionId={}"),
                eq(HOLD_DATA_BUCKET),
                eq(INTERACTION_ID)
            );
            verify(templateLogger).debug(
                eq("[BUCKET_RESOLVER] Metadata bucket updated to: {} interactionId={}"),
                eq(HOLD_METADATA_BUCKET),
                eq(INTERACTION_ID)
            );
        }

        @Test
        @DisplayName("Should not update buckets when hold buckets already set in context")
        void shouldNotUpdateWhenHoldBucketsAlreadySet() {
            // Given
            PortEntry portEntry = createPortEntry("/hold");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");
            context.setDataBucketName(HOLD_DATA_BUCKET);
            context.setMetaDataBucketName(HOLD_METADATA_BUCKET);

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then
            assertEquals(HOLD_DATA_BUCKET, context.getDataBucketName());
            assertEquals(HOLD_METADATA_BUCKET, context.getMetaDataBucketName());

            // Verify no debug logs for updates (since buckets didn't change)
            verify(templateLogger, never()).debug(
                eq("[BUCKET_RESOLVER] Data bucket updated to: {} interactionId={}"),
                any(),
                any()
            );
            verify(templateLogger, never()).debug(
                eq("[BUCKET_RESOLVER] Metadata bucket updated to: {} interactionId={}"),
                any(),
                any()
            );

            // But info logs should still be present
            verify(templateLogger).info(
                eq("[BUCKET_RESOLVER] Resolved data bucket: {} for route: {}, interactionId: {}"),
                eq(HOLD_DATA_BUCKET),
                eq("/hold"),
                eq(INTERACTION_ID)
            );
        }
    }

    @Nested
    @DisplayName("Resolve Method - Default Route Tests")
    class DefaultRouteTests {

        @Test
        @DisplayName("Should resolve default buckets when port entry has non-hold route")
        void shouldResolveDefaultBucketsForNonHoldRoute() {
            // Given
            PortEntry portEntry = createPortEntry("/api/ingest");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then
            assertEquals(DEFAULT_DATA_BUCKET, context.getDataBucketName());
            assertEquals(DEFAULT_METADATA_BUCKET, context.getMetaDataBucketName());

            // Verify logging
            verify(templateLogger).info(
                eq("[BUCKET_RESOLVER] Resolved data bucket: {} for route: {}, interactionId: {}"),
                eq(DEFAULT_DATA_BUCKET),
                eq("/api/ingest"),
                eq(INTERACTION_ID)
            );
            verify(templateLogger).info(
                eq("[BUCKET_RESOLVER] Resolved metadata bucket: {} for route: {}, interactionId: {}"),
                eq(DEFAULT_METADATA_BUCKET),
                eq("/api/ingest"),
                eq(INTERACTION_ID)
            );
        }

        @Test
        @DisplayName("Should resolve default buckets when port entry route is null")
        void shouldResolveDefaultBucketsWhenRouteIsNull() {
            // Given
            PortEntry portEntry = new PortEntry();
            portEntry.route = null;
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then
            assertEquals(DEFAULT_DATA_BUCKET, context.getDataBucketName());
            assertEquals(DEFAULT_METADATA_BUCKET, context.getMetaDataBucketName());

            // Verify logging with null route
            verify(templateLogger).info(
                eq("[BUCKET_RESOLVER] Resolved data bucket: {} for route: {}, interactionId: {}"),
                eq(DEFAULT_DATA_BUCKET),
                eq((String) null),
                eq(INTERACTION_ID)
            );
        }

        @Test
        @DisplayName("Should resolve default buckets when port entry route is empty string")
        void shouldResolveDefaultBucketsWhenRouteIsEmpty() {
            // Given
            PortEntry portEntry = createPortEntry("");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then
            assertEquals(DEFAULT_DATA_BUCKET, context.getDataBucketName());
            assertEquals(DEFAULT_METADATA_BUCKET, context.getMetaDataBucketName());
        }

        @Test
        @DisplayName("Should not update buckets when default buckets already set in context")
        void shouldNotUpdateWhenDefaultBucketsAlreadySet() {
            // Given
            PortEntry portEntry = createPortEntry("/api/ingest");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");
            context.setDataBucketName(DEFAULT_DATA_BUCKET);
            context.setMetaDataBucketName(DEFAULT_METADATA_BUCKET);

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then
            assertEquals(DEFAULT_DATA_BUCKET, context.getDataBucketName());
            assertEquals(DEFAULT_METADATA_BUCKET, context.getMetaDataBucketName());

            // Verify no debug logs for updates
            verify(templateLogger, never()).debug(
                eq("[BUCKET_RESOLVER] Data bucket updated to: {} interactionId={}"),
                any(),
                any()
            );
            verify(templateLogger, never()).debug(
                eq("[BUCKET_RESOLVER] Metadata bucket updated to: {} interactionId={}"),
                any(),
                any()
            );
        }
    }

    @Nested
    @DisplayName("Resolve Method - Null Port Entry Tests")
    class NullPortEntryTests {

        @Test
        @DisplayName("Should resolve default buckets when port entry is null")
        void shouldResolveDefaultBucketsWhenPortEntryIsNull() {
            // Given
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");

            // When
            bucketResolver.resolve(context, null, INTERACTION_ID);

            // Then
            assertEquals(DEFAULT_DATA_BUCKET, context.getDataBucketName());
            assertEquals(DEFAULT_METADATA_BUCKET, context.getMetaDataBucketName());

            // Verify logging with null port entry
            verify(templateLogger).info(
                eq("[BUCKET_RESOLVER] Resolved data bucket: {} for route: {}, interactionId: {}"),
                eq(DEFAULT_DATA_BUCKET),
                eq("null"),
                eq(INTERACTION_ID)
            );
            verify(templateLogger).info(
                eq("[BUCKET_RESOLVER] Resolved metadata bucket: {} for route: {}, interactionId: {}"),
                eq(DEFAULT_METADATA_BUCKET),
                eq("null"),
                eq(INTERACTION_ID)
            );
        }
    }

    @Nested
    @DisplayName("Bucket Update Logic Tests")
    class BucketUpdateLogicTests {

        @Test
        @DisplayName("Should update only data bucket when metadata bucket already matches")
        void shouldUpdateOnlyDataBucketWhenMetadataBucketMatches() {
            // Given
            PortEntry portEntry = createPortEntry("/hold");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");
            context.setDataBucketName(DEFAULT_DATA_BUCKET); // Different
            context.setMetaDataBucketName(HOLD_METADATA_BUCKET); // Already matches

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then
            assertEquals(HOLD_DATA_BUCKET, context.getDataBucketName());
            assertEquals(HOLD_METADATA_BUCKET, context.getMetaDataBucketName());

            // Verify only data bucket debug log
            verify(templateLogger).debug(
                eq("[BUCKET_RESOLVER] Data bucket updated to: {} interactionId={}"),
                eq(HOLD_DATA_BUCKET),
                eq(INTERACTION_ID)
            );
            verify(templateLogger, never()).debug(
                eq("[BUCKET_RESOLVER] Metadata bucket updated to: {} interactionId={}"),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("Should update only metadata bucket when data bucket already matches")
        void shouldUpdateOnlyMetadataBucketWhenDataBucketMatches() {
            // Given
            PortEntry portEntry = createPortEntry("/hold");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");
            context.setDataBucketName(HOLD_DATA_BUCKET); // Already matches
            context.setMetaDataBucketName(DEFAULT_METADATA_BUCKET); // Different

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then
            assertEquals(HOLD_DATA_BUCKET, context.getDataBucketName());
            assertEquals(HOLD_METADATA_BUCKET, context.getMetaDataBucketName());

            // Verify only metadata bucket debug log
            verify(templateLogger).debug(
                eq("[BUCKET_RESOLVER] Metadata bucket updated to: {} interactionId={}"),
                eq(HOLD_METADATA_BUCKET),
                eq(INTERACTION_ID)
            );
            verify(templateLogger, never()).debug(
                eq("[BUCKET_RESOLVER] Data bucket updated to: {} interactionId={}"),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("Should update both buckets when both are different from resolved values")
        void shouldUpdateBothBucketsWhenBothAreDifferent() {
            // Given
            PortEntry portEntry = createPortEntry("/hold");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");
            context.setDataBucketName(DEFAULT_DATA_BUCKET); // Different
            context.setMetaDataBucketName(DEFAULT_METADATA_BUCKET); // Different

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then
            assertEquals(HOLD_DATA_BUCKET, context.getDataBucketName());
            assertEquals(HOLD_METADATA_BUCKET, context.getMetaDataBucketName());

            // Verify both debug logs
            verify(templateLogger).debug(
                eq("[BUCKET_RESOLVER] Data bucket updated to: {} interactionId={}"),
                eq(HOLD_DATA_BUCKET),
                eq(INTERACTION_ID)
            );
            verify(templateLogger).debug(
                eq("[BUCKET_RESOLVER] Metadata bucket updated to: {} interactionId={}"),
                eq(HOLD_METADATA_BUCKET),
                eq(INTERACTION_ID)
            );
        }

        @Test
        @DisplayName("Should handle null values in context buckets and update them")
        void shouldHandleNullValuesInContextBuckets() {
            // Given
            PortEntry portEntry = createPortEntry("/hold");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");
            // Context buckets are null by default

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then
            assertEquals(HOLD_DATA_BUCKET, context.getDataBucketName());
            assertEquals(HOLD_METADATA_BUCKET, context.getMetaDataBucketName());

            // Verify both buckets were updated
            verify(templateLogger).debug(
                eq("[BUCKET_RESOLVER] Data bucket updated to: {} interactionId={}"),
                eq(HOLD_DATA_BUCKET),
                eq(INTERACTION_ID)
            );
            verify(templateLogger).debug(
                eq("[BUCKET_RESOLVER] Metadata bucket updated to: {} interactionId={}"),
                eq(HOLD_METADATA_BUCKET),
                eq(INTERACTION_ID)
            );
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle /hold route with different case sensitivity")
        void shouldHandleHoldRouteExactMatch() {
            // Given - route must be exactly "/hold" not "/Hold" or "/HOLD"
            PortEntry portEntry = createPortEntry("/Hold");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then - should use default buckets (case sensitive match)
            assertEquals(DEFAULT_DATA_BUCKET, context.getDataBucketName());
            assertEquals(DEFAULT_METADATA_BUCKET, context.getMetaDataBucketName());
        }

        @Test
        @DisplayName("Should handle route with /hold as substring but not exact match")
        void shouldHandleHoldAsSubstring() {
            // Given
            PortEntry portEntry = createPortEntry("/holding");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");

            // When
            bucketResolver.resolve(context, portEntry, INTERACTION_ID);

            // Then - should use default buckets (not exact match)
            assertEquals(DEFAULT_DATA_BUCKET, context.getDataBucketName());
            assertEquals(DEFAULT_METADATA_BUCKET, context.getMetaDataBucketName());
        }

        @Test
        @DisplayName("Should handle multiple resolve calls on same context")
        void shouldHandleMultipleResolveCalls() {
            // Given
            PortEntry holdEntry = createPortEntry("/hold");
            PortEntry defaultEntry = createPortEntry("/api");
            RequestContext context = new RequestContext(INTERACTION_ID, 8080, "source1", "msgType1");

            // When - first resolve with hold
            bucketResolver.resolve(context, holdEntry, INTERACTION_ID);

            // Then
            assertEquals(HOLD_DATA_BUCKET, context.getDataBucketName());
            assertEquals(HOLD_METADATA_BUCKET, context.getMetaDataBucketName());

            // When - second resolve with default
            bucketResolver.resolve(context, defaultEntry, INTERACTION_ID);

            // Then
            assertEquals(DEFAULT_DATA_BUCKET, context.getDataBucketName());
            assertEquals(DEFAULT_METADATA_BUCKET, context.getMetaDataBucketName());

            // Verify logging happened for both calls
            verify(templateLogger, times(2)).info(
                eq("[BUCKET_RESOLVER] Resolved data bucket: {} for route: {}, interactionId: {}"),
                any(),
                any(),
                eq(INTERACTION_ID)
            );
        }

        @Test
        @DisplayName("Should handle different interaction IDs correctly")
        void shouldHandleDifferentInteractionIds() {
            // Given
            PortEntry portEntry = createPortEntry("/hold");
            String interactionId1 = "interaction-1";
            String interactionId2 = "interaction-2";
            RequestContext context1 = new RequestContext(interactionId1, 8080, "source1", "msgType1");
            RequestContext context2 = new RequestContext(interactionId2, 8081, "source2", "msgType2");

            // When
            bucketResolver.resolve(context1, portEntry, interactionId1);
            bucketResolver.resolve(context2, portEntry, interactionId2);

            // Then
            assertEquals(HOLD_DATA_BUCKET, context1.getDataBucketName());
            assertEquals(HOLD_DATA_BUCKET, context2.getDataBucketName());

            // Verify logging with different interaction IDs
            verify(templateLogger).info(
                eq("[BUCKET_RESOLVER] Resolved data bucket: {} for route: {}, interactionId: {}"),
                eq(HOLD_DATA_BUCKET),
                eq("/hold"),
                eq(interactionId1)
            );
            verify(templateLogger).info(
                eq("[BUCKET_RESOLVER] Resolved data bucket: {} for route: {}, interactionId: {}"),
                eq(HOLD_DATA_BUCKET),
                eq("/hold"),
                eq(interactionId2)
            );
        }
    }

    // Helper method to create PortEntry with a specific route
    private PortEntry createPortEntry(String route) {
        PortEntry entry = new PortEntry();
        entry.route = route;
        return entry;
    }
}