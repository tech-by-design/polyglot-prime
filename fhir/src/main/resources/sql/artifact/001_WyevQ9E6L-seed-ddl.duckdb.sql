INSTALL json;
LOAD json;

-- The artifact table is designed to store interactions or almost any kind of
-- text content, with the flexibility to include additional metadata.
-- 
-- This immutable, append-only, table provides a versatile storage solution for
-- various types of text content, including interactions and other data. The
-- flexible schema allows for the inclusion of metadata such as provenance, 
-- security boundaries, and custom data, enhancing the contextual information
-- available for each artifact.
--
-- While the content is currently stored as text, the table could be refactored
-- to store binary large objects (BLOBs) or character large objects (CLOBs) to
-- accommodate different types of content more efficiently.
--
-- This flexibility makes the artifact table suitable for a wide range of
-- applications, supporting improved data organization and retrieval.
--
-- Technical Summary:
-- * Stores each artifact with a unique identifier, typically a UUID.
-- * Categorizes the content using the namespace column, indicating the nature
--   or type of the content.
-- * Stores the actual content as text in the content column.
-- * Specifies the MIME type of the content using the content_type column.
-- * Optionally stores provenance information in the provenance JSON column,
--   providing details about the origin or history of the content.
-- * Optionally stores security or data boundaries in the boundary JSON column.
-- * Optionally stores custom data in the elaboration JSON column.
-- * Records the timestamp of when the artifact was created in the created_at
--   column, with a default value of the current timestamp.

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

-- The interaction view extracts and transforms JSON data stored in the content
-- column of the artifact table, providing a structured representation of 
-- HTTP request/response ("interaction") details.
--
-- This view provides a comprehensive and structured representation of
-- interactions captured in the artifact table. It enables easier reporting and
-- analysis of interaction details, facilitating better insights into client 
-- requests and server responses.
--
-- The view helps in monitoring and auditing interactions, supporting improved
-- operational transparency and performance analysis.

DROP VIEW IF EXISTS interaction;
CREATE VIEW interaction AS
    WITH extracted_data AS (
        SELECT
            artifact_id,
            namespace,
            provenance,
            content->>'$.interactionId' AS interaction_id,
            content->>'$.tenant.tenantId' AS tenant_id,
            content->>'$.request.requestId' AS request_id,
            content->>'$.request.method' AS request_method,
            content->>'$.request.requestUri' AS request_uri,
            content->>'$.request.queryString' AS request_params,
            content->>'$.request.clientIpAddress' AS client_ip_address,
            content->>'$.request.userAgent' AS user_agent,
            (content->>'$.request.encounteredAt')::DOUBLE AS request_encountered_at_raw,
            content->>'$.response.responseId' AS response_id,
            (content->>'$.response.status')::INTEGER AS response_status,
            (content->>'$.response.encounteredAt')::DOUBLE AS response_encountered_at_raw,
            json_array_length(content->'$.request.headers') AS num_request_headers,
            json_array_length(json_keys(content->'$.request.parameters')) AS num_query_params
        FROM artifact
    )
    SELECT
        artifact_id,
        namespace,
        provenance,
        tenant_id,
        request_method,
        request_uri,
        request_params,
        response_status,
        (response_encountered_at_raw - request_encountered_at_raw) AS response_time_seconds,
        client_ip_address,
        user_agent,
        num_request_headers,
        num_query_params,
        to_timestamp(request_encountered_at_raw) AS request_encountered_at,
        to_timestamp(response_encountered_at_raw) AS response_encountered_at,
        (response_encountered_at_raw - request_encountered_at_raw) * 1e6 AS response_time_microseconds
    FROM extracted_data;

-- The fhir_validation_result_issue view processes and flattens JSON data stored
-- in the content column of the artifact table.
--
-- This view provides a structured representation of validation results and issues
-- from the FHIR OperationOutcome, allowing for easier reporting and analysis of
-- data validation errors. It helps to identify and summarize issues encountered
-- during data validation processes, facilitating improved data quality and
-- compliance monitoring.
-- 
-- CTE strategy:
-- 1. Extracts the validationResults array from the JSON structure within the content column.
-- 2. Unnests the validationResults array into individual JSON objects.
-- 3. Extracts and unnests the issues array from each validationResult.
-- 4. Flattens the nested issues into individual rows with specific fields extracted, such as message, severity, location (line and column), and diagnostics.

DROP VIEW IF EXISTS fhir_validation_result_issue;
CREATE VIEW fhir_validation_result_issue AS
    WITH validation_results_object AS (
        SELECT
            artifact_id,
            namespace,
            unnest(from_json(
                json_extract(content, '$.response.responseBody.OperationOutcome.validationResults'),
                '["JSON"]'
            )) AS validation_result
        FROM artifact
    ), validation_results_issues_object AS (
        SELECT
            artifact_id,
            namespace,
            validation_result->>'profileUrl' AS profile_url,
            validation_result->>'engine' AS engine,
            (validation_result->>'valid')::BOOLEAN AS valid,
            unnest(from_json(validation_result->'issues', '["JSON"]')) AS issue
        FROM validation_results_object
    ), validation_results_flattened_issue AS (
        SELECT
            artifact_id,
            namespace,
            profile_url,
            engine,
            valid,
            issue->>'message' AS issue_message,
            issue->>'severity' AS issue_severity,
            issue->>'location'->>'line' AS issue_location_line,
            issue->>'location'->>'column' AS issue_location_column,
            issue->>'diagnostics' AS issue_diagnostics
        FROM validation_results_issues_object
    )
    SELECT * FROM validation_results_flattened_issue;