INSTALL json;
LOAD json;

CREATE TABLE IF NOT EXISTS "artifact" (
    "artifact_id" TEXT PRIMARY KEY, -- usually UUID
    "namespace" TEXT NOT NULL,      -- "nature" or "type" of content
    "content" TEXT NOT NULL,        -- actual content
    "content_type" TEXT NOT NULL,   -- MIME type
    "provenance" JSON,              -- optional content provenance
    "boundary" JSON,                -- optional security or data boundaries
    "elaboration" JSON,             -- optional custom data
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_namespace ON "artifact"("namespace");
CREATE INDEX IF NOT EXISTS idx_content_type ON "artifact"("content_type");
CREATE INDEX IF NOT EXISTS idx_created_at ON "artifact"("created_at");

DROP VIEW IF EXISTS interaction;

CREATE VIEW interaction AS
    SELECT
        artifact_id,
        namespace,
        json_extract_string(content, '$.interactionId') AS interaction_id,
        json_extract_string(content, '$.tenant.tenantId') AS tenant_id,
        json_extract_string(content, '$.request.requestId') AS request_id,
        json_extract_string(content, '$.request.method') AS request_method,
        json_extract_string(content, '$.request.uri') AS request_uri,
        json_extract_string(content, '$.request.clientIpAddress') AS client_ip_address,
        json_extract_string(content, '$.request.userAgent') AS user_agent,
        to_timestamp(json_extract(content, '$.request.encounteredAt')::DOUBLE) AS request_encountered_at,
        json_extract_string(content, '$.response.responseId') AS response_id,
        json_extract(content, '$.response.status')::INTEGER AS response_status,
        to_timestamp(json_extract(content, '$.response.encounteredAt')::DOUBLE) AS response_encountered_at
    FROM artifact;
