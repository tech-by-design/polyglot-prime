package org.techbd.ingest.commons;
/**
 * Constants class holds various constants used throughout the application.
 * <p>
 * It includes environment variable names, request header names, and default values
 * for tenant ID and user agent.
 * </p>
 */
public class Constants {
    public static final String SECRET_NAME = System.getenv("ORG_TECHBD_SERVICE_SECRET_NAME");
    public static final String SQS_BASE_URL = System.getenv("ORG_TECHBD_SERVICE_SQS_BASE_URL");
    public static final String AWS_REGION = System.getenv("ORG_TECHBD_SERVICE_AWS_REGION");

    public static final String REQ_HEADER_TENANT_ID = "X-TechBD-Tenant-ID";
    public static final String REQ_HEADER_USER_AGENT = "user-agent";
    public static final String REQ_HEADER_CONTENT_DISPOSITION = "content-disposition";

    public static final String DEFAULT_TENANT_ID = "unknown-tenant";
    public static final String DEFAULT_USER_AGENT = "unknown-user-agent";

    public static final String BUCKET_NAME = System.getenv("AWS_S3_BUCKET_NAME");
    public static final String FIFO_Q_URL = System.getenv("AWS_SQS_QUEUE_NAME");
    public static final String TENANT_ID = System.getenv("TENANT_ID");
}