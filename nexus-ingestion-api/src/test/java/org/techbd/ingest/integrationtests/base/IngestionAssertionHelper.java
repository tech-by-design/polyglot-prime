package org.techbd.ingest.integrationtests.base;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.api.SoftAssertions;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Generic, reusable S3 + SQS assertion helper for all integration test flows.
 *
 * <p>Covers every combination described in {@code list.json}:
 * <ul>
 *   <li><b>Default flow</b> – data in {@code DEFAULT_DATA_BUCKET}, metadata in
 *       {@code DEFAULT_METADATA_BUCKET}, key prefix {@code data/YYYY/MM/DD/…}</li>
 *   <li><b>Hold flow</b> – data <em>and</em> metadata both in {@code HOLD_BUCKET},
 *       key prefixes driven by {@code dataDir}/{@code metadataDir} and optional
 *       {@code port} from the port-config entry.</li>
 *   <li><b>Error flow</b> – payload stored under {@code error/YYYY/MM/DD/…}; SQS
 *       assertion is skipped.</li>
 *   <li><b>Tenant-aware flow</b> – when {@code sourceId} / {@code msgType} are
 *       present in the port-config entry the tenant segment is injected into the
 *       S3 key path.</li>
 * </ul>
 *
 * <h3>Entry point methods</h3>
 * <ul>
 *   <li>{@link #assertDefaultFlow} – default two-bucket flow (no route / hold)</li>
 *   <li>{@link #assertHoldFlow} – single-bucket hold flow</li>
 *   <li>{@link #assertErrorFlow} – error bucket flow (no SQS check)</li>
 *   <li>{@link #assertCustomFlow} – fully parameterised; used for any list.json
 *       combination not covered by the convenience methods above.</li>
 * </ul>
 *
 * <h3>Path derivation from metadata JSON</h3>
 * Instead of re-computing expected keys from scratch, the helper reads the
 * metadata object that the application writes to S3, extracts
 * {@code interactionId} and {@code timestamp} from it, and then verifies that
 * the actual S3 keys follow the expected naming convention.  This approach is
 * robust against small timestamp-format changes in the application code.
 *
 * <h3>Multi-message session support</h3>
 * When {@code expectedPayload} is set and multiple S3 objects exist in the
 * bucket (e.g. two HL7 messages sent on one keep-alive TCP session), the helper
 * locates the <em>exact</em> payload key by content match rather than picking
 * the first key found. The corresponding metadata key and SQS message are then
 * located by matching on the derived {@code s3DataObjectPath}, ensuring all
 * assertions are anchored to the same ingestion event.
 */
public class IngestionAssertionHelper {

    // ── Constants ──────────────────────────────────────────────────────────────

    /** Prefix used by the default data bucket. */
    public static final String PREFIX_DATA = "data";
    /** Prefix used by the error bucket path. */
    public static final String PREFIX_ERROR = "error";
    /** Sub-path separator used in hold keys. */
    public static final String HOLD_SEGMENT = "hold";
    /** Sub-path for metadata inside the hold bucket. */
    public static final String HOLD_META_SEGMENT = "hold/metadata";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Infrastructure references (supplied at construction time) ──────────────

    private final S3Client s3Client;
    private final SqsClient sqsClient;

    public IngestionAssertionHelper(S3Client s3Client, SqsClient sqsClient) {
        this.s3Client = s3Client;
        this.sqsClient = sqsClient;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public entry-point methods
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validates the <em>default</em> two-bucket flow (no route or hold).
     */
    public void assertDefaultFlow(FlowAssertionParams params, SoftAssertions softly) throws Exception {
        assertCustomFlow(params, softly);
    }

    /**
     * Validates the <em>hold</em> single-bucket flow.
     */
    public void assertHoldFlow(FlowAssertionParams params, SoftAssertions softly) throws Exception {
        assertCustomFlow(params, softly);
    }

    /**
     * Validates the <em>error</em> flow.
     */
    public void assertErrorFlow(FlowAssertionParams params, SoftAssertions softly) throws Exception {
        assertCustomFlow(params, softly);
    }

    /**
     * Fully-parameterised assertion method that covers every {@code list.json}
     * combination. All convenience methods delegate here.
     *
     * <p><b>Key-selection strategy</b>:
     * <ol>
     *   <li>List all objects in the data bucket.</li>
     *   <li>Collect all candidate payload keys (no {@code _ack}, no {@code _metadata}).</li>
     *   <li>If {@code expectedPayload} is set, read each candidate in turn and pick
     *       the one whose normalised content matches the normalised expected payload.
     *       This is the <em>content-aware</em> selection that makes multi-message
     *       sessions work correctly — each call finds its own S3 object regardless
     *       of how many objects are in the bucket.</li>
     *   <li>If {@code expectedPayload} is null (or no match found after retries),
     *       fall back to the first candidate — preserving existing behaviour for
     *       SOAP/TCP tests that rely on single-object buckets.</li>
     *   <li>Locate the matching metadata key by matching on the
     *       {@code s3DataObjectPath} embedded in each metadata JSON — so the
     *       metadata is always the one that belongs to the selected payload.</li>
     *   <li>Locate the SQS message by matching its {@code s3DataObjectPath} field
     *       against the path derived from the selected payload key.</li>
     * </ol>
     */
    public void assertCustomFlow(FlowAssertionParams params, SoftAssertions softly) throws Exception {

        // ── 1. Wait for the payload key for THIS specific assertion ────────────
        // When expectedPayload is set this blocks until the specific object is
        // durably written — critical for multi-message keep-alive sessions where
        // MSG1 is already present when asserting MSG2.
        String payloadKey = waitForMatchingPayloadKey(params.dataBucket, params);

        softly.assertThat(payloadKey)
                .as("[S3] Could not locate a payload key matching the expected content in '%s'",
                        params.dataBucket)
                .isNotNull();
        if (payloadKey == null) return;

        // ── 2. Locate the metadata key that belongs to THIS payload ─────────────────────
        String metaBucket = params.metadataBucket != null ? params.metadataBucket : params.dataBucket;

        // Build the expected data s3:// URI from the selected payload key.
        // We use it to find the right metadata JSON among potentially many.
        String expectedDataS3Uri = "s3://" + params.dataBucket + "/" + payloadKey;

        String metadataKey = waitForMatchingMetadataKey(metaBucket, expectedDataS3Uri);

        softly.assertThat(metadataKey)
                .as("[S3] Metadata key anchored to payload '%s' must exist in bucket '%s'",
                        payloadKey, metaBucket)
                .isNotNull();
        if (metadataKey == null) return;

        // ── 3. Parse metadata JSON ────────────────────────────────────────────
        String metadataContent = readS3(metaBucket, metadataKey);
        JsonNode meta      = MAPPER.readTree(metadataContent);
        String keyFromMeta = meta.get("key").asText();
        JsonNode jsonMeta  = meta.get("json_metadata");

        String interactionId = jsonMeta.has("interactionId") ? jsonMeta.get("interactionId").asText() : null;
        String timestamp     = jsonMeta.has("timestamp")     ? jsonMeta.get("timestamp").asText()     : null;
        String fileName      = jsonMeta.has("fileName")      ? jsonMeta.get("fileName").asText()      : null;
        String s3DataPath    = jsonMeta.get("s3DataObjectPath").asText();
        String s3MetaPath    = jsonMeta.get("fullS3MetaDataPath").asText();
        String s3AckPath     = params.ackExpected && jsonMeta.has("fullS3AcknowledgementPath")
                               ? jsonMeta.get("fullS3AcknowledgementPath").asText() : null;

        // ── 4. Locate ACK key (if expected) ───────────────────────────────────
        String ackKey = null;
        if (params.ackExpected && s3AckPath != null) {
            // Derive the ack key from the canonical ack path stored in metadata —
            // this is safe even when multiple ack objects exist in the bucket.
            ackKey = s3AckPath.replaceFirst("^s3://[^/]+/", "");
        } else if (params.ackExpected) {
            // Fallback: find an ack key that shares the same key stem as payloadKey
            ackKey = findAckKeyForPayload(params.dataBucket, payloadKey);
        }

        if (params.ackExpected) {
            softly.assertThat(ackKey)
                    .as("[S3] ACK key must exist for payload '%s'", payloadKey)
                    .isNotNull();
        }

        // ── 5. Build expected key structures ──────────────────────────────────
        String datePath          = todayDatePath();
        String expectedFileStem  = buildExpectedFileStem(interactionId, timestamp, fileName, params);
        String expectedDataPrefix = buildExpectedDataPrefix(params, datePath);
        String expectedMetaPrefix = buildExpectedMetaPrefix(params, datePath);

        // ── 6. Assert key prefixes ────────────────────────────────────────────
        softly.assertThat(payloadKey)
                .as("[S3] Payload key must start with '%s'", expectedDataPrefix)
                .startsWith(expectedDataPrefix);

        softly.assertThat(metadataKey)
                .as("[S3] Metadata key must start with '%s'", expectedMetaPrefix)
                .startsWith(expectedMetaPrefix);

        // ── 7. Assert file-stem / suffix consistency ──────────────────────────
        if (expectedFileStem != null) {
            softly.assertThat(payloadKey)
                    .as("[S3] Payload key must contain file stem '%s'", expectedFileStem)
                    .contains(expectedFileStem);

            softly.assertThat(metadataKey)
                    .as("[S3] Metadata key must contain file stem '%s'", expectedFileStem)
                    .contains(expectedFileStem);

            if (params.ackExpected && ackKey != null) {
                if (params.isHoldFlow) {
                    softly.assertThat(ackKey)
                            .as("[S3] ACK key must contain timestamp stem '%s' and end with '_ack'",
                                    expectedFileStem)
                            .contains(expectedFileStem)
                            .endsWith("_ack");
                } else {
                    softly.assertThat(ackKey)
                            .as("[S3] ACK key must contain file stem + '_ack': '%s_ack'", expectedFileStem)
                            .contains(expectedFileStem + "_ack");
                }
            }
        }

        // ── 8. Assert full s3:// URI consistency ─────────────────────────────
        softly.assertThat(s3DataPath)
                .as("[S3] s3DataObjectPath must equal s3://%s/%s", params.dataBucket, keyFromMeta)
                .isEqualTo("s3://" + params.dataBucket + "/" + keyFromMeta);

        softly.assertThat(s3MetaPath)
                .as("[S3] fullS3MetaDataPath must equal s3://%s/%s", metaBucket, metadataKey)
                .isEqualTo("s3://" + metaBucket + "/" + metadataKey);

        if (params.ackExpected && s3AckPath != null && ackKey != null) {
            softly.assertThat(s3AckPath)
                    .as("[S3] fullS3AcknowledgementPath must equal s3://%s/%s", params.dataBucket, ackKey)
                    .isEqualTo("s3://" + params.dataBucket + "/" + ackKey);
        }

        // ── 9. Optional payload content verification ─────────────────────────
        if (params.expectedPayload != null) {
            String actualPayload = readS3(params.dataBucket, payloadKey);
            softly.assertThat(params.normalizePayload(actualPayload))
                    .as("[S3] Stored payload must match sent request")
                    .isEqualTo(params.normalizePayload(params.expectedPayload));
        }

        // ── 10. Optional ACK content verification ─────────────────────────────
        if (params.ackExpected && params.ackXPathAssertions != null && ackKey != null) {
            String actualAck = readS3(params.dataBucket, ackKey);
            params.ackXPathAssertions.accept(actualAck, softly);
        }

        // ── 11. SQS consistency check (skipped for error flow) ────────────────
        if (!params.isErrorFlow && params.queueUrl != null) {
            assertSqsConsistency(
                    params.queueUrl,
                    s3DataPath,
                    s3MetaPath,
                    s3AckPath,
                    keyFromMeta,
                    params.expectedMessageGroupId,
                    softly);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SQS assertion
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Polls {@code queueUrl} and validates all SQS message fields.
     *
     * <p>When multiple messages are present (e.g. two HL7 messages on one keep-alive
     * session), the poll finds the message whose {@code s3DataObjectPath} equals
     * {@code expectedDataPath} — anchoring the SQS assertion to the correct
     * ingestion event rather than assuming arrival order.
     */
    public void assertSqsConsistency(
            String queueUrl,
            String expectedDataPath,
            String expectedMetaPath,
            String expectedAckPath,
            String expectedObjectId,
            String expectedGroupId,
            SoftAssertions softly) throws Exception {

        Message msg = waitForSqsMessageMatchingDataPath(queueUrl, expectedDataPath);

        softly.assertThat(msg)
                .as("[SQS] Queue '%s' must contain a message with s3DataObjectPath='%s'",
                        queueUrl, expectedDataPath)
                .isNotNull();
        if (msg == null) return;

        JsonNode sqsJson = MAPPER.readTree(msg.body());

        softly.assertThat(sqsJson.get("s3DataObjectPath").asText())
                .as("[SQS] s3DataObjectPath").isEqualTo(expectedDataPath);

        softly.assertThat(sqsJson.get("fullS3MetaDataPath").asText())
                .as("[SQS] fullS3MetaDataPath").isEqualTo(expectedMetaPath);

        if (expectedAckPath != null) {
            softly.assertThat(sqsJson.get("fullS3AcknowledgementPath").asText())
                    .as("[SQS] fullS3AcknowledgementPath").isEqualTo(expectedAckPath);
        }

        softly.assertThat(sqsJson.get("s3ObjectId").asText())
                .as("[SQS] s3ObjectId").isEqualTo(expectedObjectId);

        if (expectedGroupId != null) {
            softly.assertThat(sqsJson.get("messageGroupId").asText())
                    .as("[SQS] messageGroupId").isEqualTo(expectedGroupId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // S3 bucket content helpers (for direct use in diagnostic tests)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Returns all keys in {@code bucket}. */
    public List<String> listKeys(String bucket) {
        return s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build())
                .contents().stream().map(S3Object::key).collect(Collectors.toList());
    }

    /** Reads an S3 object as a UTF-8 string. */
    public String readS3(String bucket, String key) {
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build()).asUtf8String();
    }

    /** Returns true if {@code bucket} has at least one object. */
    public boolean bucketHasObjects(String bucket) {
        return !s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build())
                .contents().isEmpty();
    }

    /** Returns true if {@code bucket} is empty. */
    public boolean bucketIsEmpty(String bucket) {
        return !bucketHasObjects(bucket);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Date / prefix utilities
    // ═══════════════════════════════════════════════════════════════════════════

    /** {@code YYYY/MM/DD} — date path used inside all S3 key prefixes. */
    public static String todayDatePath() {
        LocalDate d = LocalDate.now();
        return String.format("%d/%02d/%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    /** {@code data/YYYY/MM/DD} — default data prefix (no dataDir). */
    public static String todayDefaultDataPrefix() {
        return PREFIX_DATA + "/" + todayDatePath();
    }

    /** {@code metadata/YYYY/MM/DD} — default metadata prefix (no metadataDir). */
    public static String todayDefaultMetadataPrefix() {
        return "metadata/" + todayDatePath();
    }

    /** {@code error/YYYY/MM/DD} — error prefix (never has dataDir applied). */
    public static String todayErrorPrefix() {
        return PREFIX_ERROR + "/" + todayDatePath();
    }

    /**
     * Builds the expected data key prefix from flow params.
     */
    public static String buildExpectedDataPrefix(FlowAssertionParams p, String datePath) {
        if (p.isErrorFlow) {
            return PREFIX_ERROR + "/" + datePath;
        }
        if (p.isHoldFlow) {
            String base = stripSlashes(p.dataDir);
            String tenantOrPort = p.tenantId != null ? p.tenantId
                    : (p.port > 0 ? String.valueOf(p.port) : null);
            if (tenantOrPort != null) {
                return base + "/" + HOLD_SEGMENT + "/" + tenantOrPort + "/" + datePath;
            }
            return base + "/" + HOLD_SEGMENT + "/" + datePath;
        }
        // Normal flow
        String base = (p.dataDir != null && !p.dataDir.isBlank())
                ? stripSlashes(p.dataDir) + "/" + PREFIX_DATA
                : PREFIX_DATA;
        if (p.tenantId != null) {
            return base + "/" + p.tenantId + "/" + datePath;
        }
        return base + "/" + datePath;
    }

    /**
     * Builds the expected metadata key prefix from flow params.
     */
    public static String buildExpectedMetaPrefix(FlowAssertionParams p, String datePath) {
        if (p.isErrorFlow) {
            return PREFIX_ERROR + "/" + datePath;
        }
        if (p.isHoldFlow) {
            String base = stripSlashes(p.metadataDir != null ? p.metadataDir : p.dataDir);
            String tenantOrPort = p.tenantId != null ? p.tenantId
                    : (p.port > 0 ? String.valueOf(p.port) : null);
            if (tenantOrPort != null) {
                return base + "/" + HOLD_META_SEGMENT + "/" + tenantOrPort + "/" + datePath;
            }
            return base + "/" + HOLD_META_SEGMENT + "/" + datePath;
        }
        // Normal flow
        String metaBase = (p.metadataDir != null && !p.metadataDir.isBlank())
                ? stripSlashes(p.metadataDir) + "/metadata"
                : "metadata";
        if (p.tenantId != null) {
            return metaBase + "/" + p.tenantId + "/" + datePath;
        }
        return metaBase + "/" + datePath;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private helpers — key selection
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Waits (with retries) until a payload key whose content matches
     * {@code expectedPayload} exists in {@code bucket}, then returns that key.
     *
     * <p>When {@code expectedPayload} is null the method waits for any payload
     * key to appear and returns the first one found — preserving existing
     * single-object behaviour for SOAP/TCP tests.
     *
     * <p>Critically, each retry re-lists the bucket and re-reads every candidate,
     * so the method blocks until the <em>specific</em> object for this assertion
     * call is durably written.  This is what makes two-message keep-alive sessions
     * work: the assertion for MSG2 keeps waiting past the point where only MSG1
     * is visible, rather than returning early because MSG1 satisfied a weaker
     * "any candidate" check.
     */
    private String waitForMatchingPayloadKey(String bucket, FlowAssertionParams params)
            throws InterruptedException {
        String expectedPayload = params.expectedPayload;

        for (int i = 0; i < 20; i++) {
            ListObjectsV2Response response = s3Client.listObjectsV2(
                    ListObjectsV2Request.builder().bucket(bucket).build());

            List<String> candidates = response.contents().stream()
                    .map(S3Object::key)
                    .filter(k -> !k.contains("_ack") && !k.contains("_metadata"))
                    .collect(Collectors.toList());

            if (expectedPayload == null) {
                // No content match needed — return first key as soon as one exists
                if (!candidates.isEmpty()) return candidates.get(0);
            } else {
                // Must find the key whose content matches THIS assertion's payload
                for (String key : candidates) {
                    try {
                        String actual = readS3(bucket, key);
                        if (params.normalizePayload(actual)
                                .equals(params.normalizePayload(expectedPayload))) {
                            return key;
                        }
                    } catch (Exception ignored) { /* object may still be in flight */ }
                }
            }
            Thread.sleep(1_000);
        }
        return null; // timed out — caller will produce a clear assertion failure
    }

    /**
     * Polls the metadata bucket (with retries) until a metadata JSON is found
     * whose embedded {@code s3DataObjectPath} equals {@code expectedDataS3Uri}.
     *
     * <p>This ensures that in multi-message sessions the metadata key returned
     * always belongs to the same ingestion event as the selected payload key.
     */
    private String waitForMatchingMetadataKey(String bucket, String expectedDataS3Uri)
            throws InterruptedException {
        for (int i = 0; i < 15; i++) {
            ListObjectsV2Response response = s3Client.listObjectsV2(
                    ListObjectsV2Request.builder().bucket(bucket).build());

            for (S3Object obj : response.contents()) {
                String key = obj.key();
                if (!key.contains("_metadata.json")) continue;
                try {
                    String content = readS3(bucket, key);
                    JsonNode meta  = MAPPER.readTree(content);
                    JsonNode jsonMeta = meta.get("json_metadata");
                    if (jsonMeta != null && jsonMeta.has("s3DataObjectPath")) {
                        String dataPath = jsonMeta.get("s3DataObjectPath").asText();
                        if (expectedDataS3Uri.equals(dataPath)) {
                            return key;
                        }
                    }
                } catch (Exception ignored) { /* metadata may still be in flight */ }
            }
            Thread.sleep(1_000);
        }
        return null;
    }

    /**
     * Finds an ACK key in the data bucket that corresponds to the given payload key.
     *
     * <p>The ACK key is expected to share the same timestamp/stem prefix as the
     * payload key and end with {@code _ack}.  Used only as a fallback when the
     * metadata JSON does not carry {@code fullS3AcknowledgementPath}.
     */
    private String findAckKeyForPayload(String bucket, String payloadKey) {
        // Extract the stem: everything after the last '/' and up to (not including)
        // any extension-like suffix that may be specific to the payload file name.
        // We try to match on timestamp prefix common to both payload and ack.
        String payloadName = payloadKey.contains("/")
                ? payloadKey.substring(payloadKey.lastIndexOf('/') + 1)
                : payloadKey;

        ListObjectsV2Response response = s3Client.listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucket).build());

        // First pass: exact stem match — "{payloadName}_ack"
        String exact = response.contents().stream()
                .map(S3Object::key)
                .filter(k -> k.contains("_ack") && k.endsWith(payloadName + "_ack"))
                .findFirst().orElse(null);
        if (exact != null) return exact;

        // Second pass: share same directory prefix and timestamp stem
        String dir = payloadKey.contains("/")
                ? payloadKey.substring(0, payloadKey.lastIndexOf('/') + 1)
                : "";
        // Extract leading timestamp (digits before first '_')
        String ts = payloadName.contains("_") ? payloadName.substring(0, payloadName.indexOf('_')) : payloadName;

        return response.contents().stream()
                .map(S3Object::key)
                .filter(k -> k.startsWith(dir) && k.contains("_ack"))
                .filter(k -> {
                    String name = k.contains("/") ? k.substring(k.lastIndexOf('/') + 1) : k;
                    return name.startsWith(ts + "_");
                })
                .findFirst().orElse(null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private helpers — SQS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Polls {@code queueUrl} (up to 15 × 2 s) until a message whose body contains
     * {@code s3DataObjectPath == expectedDataPath} is found.
     *
     * <p>Receiving messages does <em>not</em> delete them from the FIFO queue, so
     * both messages sent in a two-message session remain available for the second
     * {@code assertHoldFlow} call.
     */
    private Message waitForSqsMessageMatchingDataPath(String queueUrl, String expectedDataPath)
            throws InterruptedException {
        for (int i = 0; i < 15; i++) {
            // Receive up to 10 messages per poll — FIFO queues may hold both messages
            ReceiveMessageResponse resp = sqsClient.receiveMessage(
                    ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(2)
                            .build());

            for (Message msg : resp.messages()) {
                try {
                    JsonNode body = MAPPER.readTree(msg.body());
                    if (body.has("s3DataObjectPath")
                            && expectedDataPath.equals(body.get("s3DataObjectPath").asText())) {
                        return msg;
                    }
                } catch (Exception ignored) { /* malformed message — skip */ }
            }
            Thread.sleep(1_000);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private helpers — misc
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Builds the expected file stem used to verify key names.
     */
    private static String buildExpectedFileStem(
            String interactionId, String timestamp, String fileName, FlowAssertionParams p) {

        if (timestamp == null) return null;

        if (p.isHoldFlow) {
            return timestamp + "_";
        }
        if (interactionId != null) {
            return interactionId + "_" + timestamp;
        }
        return timestamp;
    }

    private static String stripSlashes(String path) {
        if (path == null) return "";
        return path.replaceAll("^/+", "").replaceAll("/+$", "");
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // FlowAssertionParams – builder-style configuration object
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Encapsulates all parameters needed to assert a single ingestion flow.
     */
    public static final class FlowAssertionParams {

        // ── Bucket config ──────────────────────────────────────────────────────
        public final String dataBucket;
        public final String metadataBucket;

        // ── Flow type flags ────────────────────────────────────────────────────
        public final boolean isHoldFlow;
        public final boolean isErrorFlow;

        // ── Port-config attributes ─────────────────────────────────────────────
        public final int port;
        public final String dataDir;
        public final String metadataDir;
        public final String tenantId;

        // ── SQS config ─────────────────────────────────────────────────────────
        public final String queueUrl;
        public final String expectedMessageGroupId;

        // ── Payload / ACK ──────────────────────────────────────────────────────
        public final String expectedPayload;
        public final boolean ackExpected;
        public final AckAssertion ackXPathAssertions;
        final PayloadNormalizer payloadNormalizer;

        private FlowAssertionParams(Builder b) {
            this.dataBucket              = b.dataBucket;
            this.metadataBucket          = b.metadataBucket;
            this.isHoldFlow              = b.isHoldFlow;
            this.isErrorFlow             = b.isErrorFlow;
            this.port                    = b.port;
            this.dataDir                 = b.dataDir;
            this.metadataDir             = b.metadataDir;
            this.tenantId                = b.tenantId;
            this.queueUrl                = b.queueUrl;
            this.expectedMessageGroupId  = b.expectedMessageGroupId;
            this.expectedPayload         = b.expectedPayload;
            this.ackExpected             = b.ackExpected;
            this.ackXPathAssertions      = b.ackXPathAssertions;
            this.payloadNormalizer        = b.payloadNormalizer != null
                                            ? b.payloadNormalizer
                                            : IngestionAssertionHelper::normalizeGeneric;
        }

        public String normalizePayload(String raw) {
            return payloadNormalizer.normalize(raw);
        }

        public static Builder builder() { return new Builder(); }

        public Builder toBuilder() {
            return new Builder()
                    .dataBucket(this.dataBucket)
                    .metadataBucket(this.metadataBucket)
                    .holdFlow(this.isHoldFlow)
                    .errorFlow(this.isErrorFlow)
                    .port(this.port)
                    .dataDir(this.dataDir)
                    .metadataDir(this.metadataDir)
                    .tenantId(this.tenantId)
                    .queueUrl(this.queueUrl)
                    .expectedMessageGroupId(this.expectedMessageGroupId)
                    .expectedPayload(this.expectedPayload)
                    .ackExpected(this.ackExpected)
                    .ackXPathAssertions(this.ackXPathAssertions)
                    .payloadNormalizer(this.payloadNormalizer);
        }

        public static final class Builder {
            private String dataBucket;
            private String metadataBucket;
            private boolean isHoldFlow;
            private boolean isErrorFlow;
            private int port;
            private String dataDir;
            private String metadataDir;
            private String tenantId;
            private String queueUrl;
            private String expectedMessageGroupId;
            private String expectedPayload;
            private boolean ackExpected = true;
            private AckAssertion ackXPathAssertions;
            private PayloadNormalizer payloadNormalizer;

            public Builder dataBucket(String v)             { dataBucket = v;             return this; }
            public Builder metadataBucket(String v)         { metadataBucket = v;         return this; }
            public Builder holdFlow(boolean v)              { isHoldFlow = v;             return this; }
            public Builder errorFlow(boolean v)             { isErrorFlow = v;            return this; }
            public Builder port(int v)                      { port = v;                   return this; }
            public Builder dataDir(String v)                { dataDir = v;                return this; }
            public Builder metadataDir(String v)            { metadataDir = v;            return this; }
            public Builder tenantId(String v)               { tenantId = v;               return this; }
            public Builder queueUrl(String v)               { queueUrl = v;               return this; }
            public Builder expectedMessageGroupId(String v) { expectedMessageGroupId = v; return this; }
            public Builder expectedPayload(String v)        { expectedPayload = v;        return this; }
            public Builder ackExpected(boolean v)           { ackExpected = v;            return this; }
            public Builder ackXPathAssertions(AckAssertion v) { ackXPathAssertions = v;  return this; }
            public Builder payloadNormalizer(PayloadNormalizer v) { payloadNormalizer = v; return this; }

            public FlowAssertionParams build() {
                if (dataBucket == null) throw new IllegalStateException("dataBucket is required");
                return new FlowAssertionParams(this);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Functional interfaces
    // ═══════════════════════════════════════════════════════════════════════════

    @FunctionalInterface
    public interface PayloadNormalizer {
        String normalize(String raw);
    }

    @FunctionalInterface
    public interface AckAssertion {
        void accept(String ackContent, SoftAssertions softly);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Built-in normalizers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Collapses all whitespace – works for both XML and HL7. */
    public static String normalizeGeneric(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    /** XML-aware: also collapses inter-element whitespace. */
    public static String normalizeXml(String xml) {
        return xml == null ? "" : xml.replaceAll(">\\s+<", "><").replaceAll("\\s+", " ").trim();
    }

    /** HL7 normalizer (alias of {@link #normalizeGeneric}). */
    public static String normalizeHl7(String hl7) {
        return normalizeGeneric(hl7);
    }
}