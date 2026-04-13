package org.techbd.ingest.integrationtests.base;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * Abstract base class for all integration tests that need LocalStack (S3 +
 * SQS).
 *
 * <p>
 * <b>Lifecycle:</b>
 * <ul>
 * <li>The LocalStack container is started <em>once per JVM</em> via
 * {@code @Container static},
 * meaning all test classes that extend this share the same container
 * instance.</li>
 * <li>AWS clients (S3, SQS) and bucket/queue creation are also done
 * <em>once</em>
 * in {@link #setupLocalStackEnvironment()}.</li>
 * <li>Between each test method, {@link #cleanS3AndSqsState()} purges all data
 * objects and SQS messages so tests remain fully isolated.</li>
 * </ul>
 *
 * <p>
 * <b>Why per-test cleanup instead of per-test setup?</b>
 * Re-creating buckets and queues is expensive. Purging their contents is cheap
 * and sufficient.
 */
@NexusIntegrationTest
public abstract class BaseIntegrationTest {

        // ── Constants ──────────────────────────────────────────────────────────────

        protected static final String PORT_CONFIG_BUCKET = "local-pdr-txd-sbx-temp";
        protected static final String PORT_CONFIG_KEY = "port-config/list.json";
        protected static final String PORT_CONFIG_CLASSPATH = "org/techbd/ingest/portconfig/list.json";

        /** Data bucket used by the default (port 9000) flow. */
        protected static final String DEFAULT_DATA_BUCKET = "local-sbx-nexus-ingestion-s3-bucket";
        /** Metadata bucket used by the default (port 9000) flow. */
        protected static final String DEFAULT_METADATA_BUCKET = "local-sbx-nexus-ingestion-s3-metadata-bucket";
        /**
         * Single bucket used by the HOLD (port 5555) flow for both data and metadata.
         */
        protected static final String HOLD_BUCKET = "local-pdr-txd-sbx-hold";

        // ── SQS Queue Definitions ─────────────────────────────────────────────────

        protected static final List<String> QUEUE_NAMES = List.of(
                        "txd-sbx-main-queue.fifo",
                        "test.fifo",
                        "txd-sbx-ccd-queue.fifo",
                        "txd-sbx-hold-queue.fifo",
                        "txd-sbx-util-queue.fifo");

        protected static final Map<String, String> queueUrls = new HashMap<>();

        // ── LocalStack container ──────────────────────────────────────────────────

        /**
         * A single LocalStack container shared across the entire test suite.
         * {@code static} + {@code @Container} ensures Testcontainers starts it once
         * per JVM and tears it down only after all tests finish.
         */
        @Container
        static final LocalStackContainer localStack = new LocalStackContainer(
                        DockerImageName.parse("localstack/localstack:3.0"))
                        .withServices(LocalStackContainer.Service.S3,
                                        LocalStackContainer.Service.SQS)
                        .withStartupAttempts(3)
                        .waitingFor(Wait.forLogMessage(".*Ready.*", 1));

        // ── AWS clients ───────────────────────────────────────────────────────────

        protected static S3Client s3Client;
        protected static SqsClient sqsClient;

        /** Convenience alias for the main FIFO queue URL. */
        protected static String mainQueueUrl;

        // ── Spring property injection ─────────────────────────────────────────────

        @DynamicPropertySource
        static void overrideAwsProperties(DynamicPropertyRegistry registry) {
                registry.add("org.techbd.aws.s3.default-config.endpoint",
                                () -> localStack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
                registry.add("org.techbd.aws.s3.hold-config.endpoint",
                                () -> localStack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
                registry.add("org.techbd.aws.sqs.endpoint",
                                () -> localStack.getEndpointOverride(LocalStackContainer.Service.SQS).toString());

                registry.add("org.techbd.aws.region", localStack::getRegion);
                registry.add("org.techbd.aws.access-key", localStack::getAccessKey);
                registry.add("org.techbd.aws.secret-key", localStack::getSecretKey);

                registry.add("org.techbd.aws.sqs.fifo-queue-url", () -> mainQueueUrl);
                registry.add("org.techbd.aws.sqs.hold-queue-url",
                                () -> queueUrls.get("txd-sbx-hold-queue.fifo"));
                registry.add("org.techbd.aws.sqs.ccd-queue-url",
                                () -> queueUrls.get("txd-sbx-ccd-queue.fifo"));

                registry.add("PORT_CONFIG_S3_BUCKET", () -> PORT_CONFIG_BUCKET);
                registry.add("PORT_CONFIG_S3_KEY", () -> PORT_CONFIG_KEY);
                registry.add("AWS_REGION", localStack::getRegion);
                registry.add("SPRING_PROFILES_ACTIVE", () -> "test");

                System.setProperty("aws.accessKeyId", localStack.getAccessKey());
                System.setProperty("aws.secretAccessKey", localStack.getSecretKey());
        }

        // ── One-time setup ────────────────────────────────────────────────────────

        /**
         * Runs once before any test in the suite. Creates AWS clients, buckets,
         * queues, and uploads the port-config fixture.
         */
        @BeforeAll
        static void setupLocalStackEnvironment() throws Exception {

                // Guard: if clients are already built (another test class ran first),
                // skip re-initialisation — the container is already up and buckets exist.
                if (s3Client != null) {
                        return;
                }

                StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                                localStack.getAccessKey(),
                                                localStack.getSecretKey()));

                s3Client = S3Client.builder()
                                .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.S3))
                                .region(Region.of(localStack.getRegion()))
                                .credentialsProvider(credentials)
                                .forcePathStyle(true)
                                .build();

                sqsClient = SqsClient.builder()
                                .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.SQS))
                                .region(Region.of(localStack.getRegion()))
                                .credentialsProvider(credentials)
                                .build();

                createBuckets();
                createQueues();
                uploadPortConfig();
        }

        // ── Per-test cleanup ──────────────────────────────────────────────────────

        /**
         * Runs after every test method. Clears all S3 objects from the data/metadata
         * buckets and drains all SQS queues. This prevents cross-test contamination
         * without the cost of re-creating infrastructure.
         *
         * <p>
         * The port-config bucket is intentionally left untouched.
         */
        @AfterEach
        void cleanS3AndSqsState() {
                purgeS3Bucket(DEFAULT_DATA_BUCKET);
                purgeS3Bucket(DEFAULT_METADATA_BUCKET);
                purgeS3Bucket(HOLD_BUCKET);

                for (String queueUrl : queueUrls.values()) {
                        purgeSqsQueue(queueUrl);
                }
        }

        // ── Helpers ───────────────────────────────────────────────────────────────

        private static void createBuckets() {
                for (String bucket : new String[] {
                                DEFAULT_DATA_BUCKET,
                                DEFAULT_METADATA_BUCKET,
                                HOLD_BUCKET,
                                PORT_CONFIG_BUCKET
                }) {
                        s3Client.createBucket(b -> b.bucket(bucket));
                }
        }

        private static void createQueues() {
                Map<QueueAttributeName, String> attrs = Map.of(
                                QueueAttributeName.FIFO_QUEUE, "true",
                                QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true");

                for (String queueName : QUEUE_NAMES) {
                        String url = sqsClient.createQueue(b -> b
                                        .queueName(queueName)
                                        .attributes(attrs))
                                        .queueUrl();
                        queueUrls.put(queueName, url);
                }

                mainQueueUrl = queueUrls.get("txd-sbx-main-queue.fifo");
        }

        private static void uploadPortConfig() throws Exception {
                try (InputStream is = BaseIntegrationTest.class
                                .getClassLoader()
                                .getResourceAsStream(PORT_CONFIG_CLASSPATH)) {

                        if (is == null) {
                                throw new IllegalStateException(
                                                "list.json not found on classpath: " + PORT_CONFIG_CLASSPATH);
                        }

                        s3Client.putObject(
                                        PutObjectRequest.builder()
                                                        .bucket(PORT_CONFIG_BUCKET)
                                                        .key(PORT_CONFIG_KEY)
                                                        .contentType("application/json")
                                                        .build(),
                                        software.amazon.awssdk.core.sync.RequestBody.fromBytes(is.readAllBytes()));
                }
        }

        /**
         * Deletes all objects from an S3 bucket in a single batch request.
         * Safe to call on empty buckets.
         */
        private void purgeS3Bucket(String bucket) {
                ListObjectsV2Response listing = s3Client.listObjectsV2(
                                ListObjectsV2Request.builder().bucket(bucket).build());

                if (listing.contents().isEmpty()) {
                        return;
                }

                List<ObjectIdentifier> toDelete = listing.contents().stream()
                                .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                                .collect(Collectors.toList());

                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                                .bucket(bucket)
                                .delete(Delete.builder().objects(toDelete).quiet(true).build())
                                .build());
        }

        /**
         * Drains an SQS queue by receiving all available messages and deleting them.
         * Uses short-poll (waitTimeSeconds=0) to return quickly when the queue is
         * empty.
         */
        private void purgeSqsQueue(String queueUrl) {
                // PurgeQueue has a 60-second cooldown in real AWS, but LocalStack
                // supports it freely — use it for simplicity.
                try {
                        sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
                } catch (Exception ignored) {
                        // Fallback: drain manually
                        ReceiveMessageResponse resp;
                        do {
                                resp = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                                                .queueUrl(queueUrl)
                                                .maxNumberOfMessages(10)
                                                .waitTimeSeconds(0)
                                                .build());
                                for (var msg : resp.messages()) {
                                        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                                                        .queueUrl(queueUrl)
                                                        .receiptHandle(msg.receiptHandle())
                                                        .build());
                                }
                        } while (!resp.messages().isEmpty());
                }
        }

        protected static String getQueueUrl(String queueName) {
                return queueUrls.get(queueName);
        }
}