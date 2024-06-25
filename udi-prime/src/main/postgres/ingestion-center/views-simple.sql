-- only include simple (non-materialized) views here

DROP VIEW IF EXISTS techbd_udi_ingress.interaction_http CASCADE;
CREATE OR REPLACE VIEW techbd_udi_ingress.interaction_http
AS
WITH cte_interaction AS (
         SELECT intpayload.sat_interaction_http_request_id,
            intpayload.hub_interaction_id,
            intpayload.provenance,
            intpayload.payload,
            intpayload.payload ->> 'namespace'::text AS namespace,
            intpayload.payload ->> 'interactionId'::text AS interaction_id,
            (intpayload.payload -> 'tenant'::text) ->> 'tenantId'::text AS tenant_id,
            (intpayload.payload -> 'tenant'::text) ->> 'name'::text AS tenant_name,
            (intpayload.payload -> 'request'::text) ->> 'requestId'::text AS request_id,
            (intpayload.payload -> 'request'::text) ->> 'method'::text AS request_method,
            (intpayload.payload -> 'request'::text) ->> 'requestUri'::text AS request_uri,
            (intpayload.payload -> 'request'::text) ->> 'queryString'::text AS request_params,
            (intpayload.payload -> 'request'::text) ->> 'clientIpAddress'::text AS client_ip_address,
            (intpayload.payload -> 'request'::text) ->> 'userAgent'::text AS user_agent,
            ((intpayload.payload -> 'request'::text) ->> 'encounteredAt'::text)::double precision AS request_encountered_at_raw,
            (intpayload.payload -> 'response'::text) ->> 'responseId'::text AS response_id,
            ((intpayload.payload -> 'response'::text) ->> 'status'::text)::integer AS response_status,
            ((intpayload.payload -> 'response'::text) ->> 'encounteredAt'::text)::double precision AS response_encountered_at_raw,
            jsonb_array_length((intpayload.payload -> 'request'::text) -> 'headers'::text) AS num_request_headers            
           	FROM techbd_udi_ingress.sat_interaction_http_request intpayload
        )
 SELECT interaction.sat_interaction_http_request_id,
    interaction.hub_interaction_id,
    hubint.key AS interaction_key,
    interaction.namespace,
    interaction.tenant_id,
    interaction.tenant_name,
    interaction.request_method,
    interaction.response_status,
    interaction.response_encountered_at_raw - interaction.request_encountered_at_raw AS response_time_seconds,
    interaction.client_ip_address,
    interaction.user_agent,
    interaction.num_request_headers,
    to_timestamp(interaction.request_encountered_at_raw) AS request_encountered_at,
    to_timestamp(interaction.response_encountered_at_raw) AS response_encountered_at,
    (interaction.response_encountered_at_raw - interaction.request_encountered_at_raw) * '1000000'::numeric::double precision AS response_time_microseconds,
    interaction.request_uri,
    interaction.request_params,
    interaction.request_id,
    interaction.interaction_id,
    interaction.provenance,
    interaction.payload     
   FROM cte_interaction interaction      
     LEFT JOIN techbd_udi_ingress.hub_interaction hubint 
     ON hubint.hub_interaction_id = interaction.hub_interaction_id
     WHERE interaction.hub_interaction_id = hubint.hub_interaction_id
     ; 
/*=============================================================================================================*/

DROP VIEW IF EXISTS techbd_udi_ingress.sat_operation_session_entry_session_issue_fhir CASCADE;
CREATE OR REPLACE VIEW techbd_udi_ingress.sat_operation_session_entry_session_issue_fhir AS 
WITH validation_results_object AS (
         SELECT tbl.hub_operation_session_entry_id,
            tbl.elaboration,
            tbl.created_at,
            tbl.created_by,
            tbl.provenance,
            jsonb_array_elements((tbl.validation_engine_payload -> 'OperationOutcome'::text) -> 'validationResults'::text) AS validation_result
           FROM techbd_udi_ingress.sat_operation_session_entry_session_issue tbl
          WHERE 1 = 1
        ), validation_results_issues_object AS (
         SELECT validation_results_object.hub_operation_session_entry_id,
            validation_results_object.elaboration,
            validation_results_object.created_at,
            validation_results_object.provenance,
            validation_results_object.validation_result ->> 'profileUrl'::text AS profile_url,
            validation_results_object.validation_result ->> 'initiatedAt'::text AS initiated_at,
            validation_results_object.validation_result ->> 'completedAt'::text AS completed_at,
            validation_results_object.validation_result ->> 'engine'::text AS engine,
            validation_results_object.validation_result ->> 'namespace'::text AS namespace,
            (validation_results_object.validation_result ->> 'valid'::text)::boolean AS valid,
            jsonb_array_elements(validation_results_object.validation_result -> 'issues'::text) AS issue
           FROM validation_results_object
        ), validation_results_flattened_issue AS (
         SELECT validation_results_issues_object.hub_operation_session_entry_id,
            validation_results_issues_object.elaboration,
            validation_results_issues_object.created_at,
            validation_results_issues_object.provenance,
            validation_results_issues_object.engine,
            validation_results_issues_object.profile_url,
            validation_results_issues_object.initiated_at,
            validation_results_issues_object.completed_at,
            validation_results_issues_object.namespace,
            validation_results_issues_object.valid,
            validation_results_issues_object.issue ->> 'level'::text AS level,
            validation_results_issues_object.issue ->> 'message_id'::text AS message_id,
            validation_results_issues_object.issue ->> 'ignorableError'::text AS ignorableerror,
            validation_results_issues_object.issue ->> 'invalid_value'::text AS invalid_value,
            validation_results_issues_object.issue ->> 'comment'::text AS comment,
            validation_results_issues_object.issue ->> 'display'::text AS display,
            validation_results_issues_object.issue ->> 'disposition'::text AS disposition,
            validation_results_issues_object.issue ->> 'remediation'::text AS remediation,
            validation_results_issues_object.issue ->> 'message'::text AS issue_message,
            validation_results_issues_object.issue ->> 'severity'::text AS issue_severity,
            (validation_results_issues_object.issue -> 'location'::text) ->> 'line'::text AS issue_location_line,
            (validation_results_issues_object.issue -> 'location'::text) ->> 'column'::text AS issue_location_column,
            (validation_results_issues_object.issue -> 'location'::text) ->> 'diagnostics'::text AS issue_diagnostics
           FROM validation_results_issues_object
        )         
 SELECT hubingestsession.key AS session_id,
    valresult.namespace,
    valresult.profile_url,
    valresult.engine,
    valresult.valid,
    valresult.issue_message,
    valresult.issue_severity,
    valresult.issue_location_line,
    valresult.issue_location_column,
    valresult.issue_diagnostics,
    valresult.initiated_at,
    valresult.completed_at,
    hubingestsession.hub_operation_session_id,
    hubingestsessionentry.hub_operation_session_entry_id,
    valresult.level,
    valresult.message_id,
    valresult.ignorableerror,
    valresult.invalid_value,
    valresult.comment,
    valresult.display,
    valresult.disposition,
    valresult.remediation,
    valresult.elaboration,
    valresult.created_at,
    valresult.provenance
   FROM validation_results_flattened_issue valresult
     LEFT JOIN techbd_udi_ingress.hub_operation_session_entry hubingestsessionentry ON hubingestsessionentry.hub_operation_session_entry_id = valresult.hub_operation_session_entry_id
     LEFT JOIN techbd_udi_ingress.link_session_entry linksessionentry ON linksessionentry.hub_operation_session_entry_id = hubingestsessionentry.hub_operation_session_entry_id
     LEFT JOIN techbd_udi_ingress.hub_operation_session hubingestsession 
     ON hubingestsession.hub_operation_session_id = linksessionentry.hub_operation_session_id
    ;
/*=============================================================================================================*/
DROP VIEW IF EXISTS techbd_udi_ingress.interaction_http_bundle CASCADE;
CREATE OR REPLACE VIEW techbd_udi_ingress.interaction_http_bundle AS
WITH cte_interaction AS (
    SELECT 
        intr_http.sat_interaction_http_request_id,
        intr_http.hub_interaction_id,
        lnk_int.hub_operation_session_id,
        lnk_ses.hub_operation_session_entry_id,        
        intr_http.interaction_key,
        intr_http."namespace",
        intr_http.tenant_id,
        intr_http.tenant_name,
        intr_http.request_method,
        intr_http.response_status,
        intr_http.response_time_seconds,
        intr_http.client_ip_address,
        intr_http.user_agent,
        intr_http.num_request_headers,
        intr_http.request_encountered_at,
        intr_http.response_encountered_at,
        intr_http.response_time_microseconds,
        intr_http.request_uri,
        intr_http.request_params,
        intr_http.request_id,
        intr_http.interaction_id,
        intr_http.provenance
    FROM techbd_udi_ingress.interaction_http intr_http
    LEFT OUTER JOIN techbd_udi_ingress.link_session_interaction lnk_int
        ON lnk_int.hub_interaction_id = intr_http.hub_interaction_id
    LEFT OUTER JOIN techbd_udi_ingress.hub_operation_session oper_ses
        ON oper_ses.hub_operation_session_id = lnk_int.hub_operation_session_id
    LEFT OUTER JOIN techbd_udi_ingress.link_session_entry lnk_ses
    	ON lnk_ses.hub_operation_session_id = oper_ses.hub_operation_session_id
    WHERE intr_http.request_uri LIKE '%Bundle'
), cte_payload AS (
    SELECT 
        sen_ayload.sat_operation_session_entry_payload_id,       
        sen_ayload.hub_operation_session_entry_id,
        lnk_sen.hub_operation_session_id,
        lnkint.hub_interaction_id,
        sen_ayload.ingest_payload,
        sen_ayload.created_at,
        sen_ayload.created_by,
        sen_ayload.provenance        
    FROM techbd_udi_ingress.sat_operation_session_entry_payload sen_ayload
    LEFT OUTER JOIN techbd_udi_ingress.hub_operation_session_entry opr_ses
        ON opr_ses.hub_operation_session_entry_id = sen_ayload.hub_operation_session_entry_id
    LEFT OUTER JOIN techbd_udi_ingress.link_session_entry lnk_sen
        ON lnk_sen.hub_operation_session_entry_id = opr_ses.hub_operation_session_entry_id      
    LEFT OUTER JOIN techbd_udi_ingress.link_session_interaction lnkint
        ON lnkint.hub_operation_session_id = lnk_sen.hub_operation_session_id 
    WHERE sen_ayload.ingest_payload ? 'status'
)
SELECT 
    ci.sat_interaction_http_request_id,
    ci.hub_operation_session_id,
    ci.hub_operation_session_entry_id,
    ci.hub_interaction_id,
    cp.sat_operation_session_entry_payload_id,
    ci.interaction_key,
    ci."namespace",
    ci.tenant_id,
    ci.tenant_name,
    ci.request_method,
    ci.response_status,
    ci.response_time_seconds,
    ci.client_ip_address,
    ci.user_agent,
    ci.num_request_headers,
    ci.request_encountered_at,
    ci.response_encountered_at,
    ci.response_time_microseconds,
    ci.request_uri,
    ci.request_params,
    ci.request_id,
    ci.interaction_id,    
    cp.ingest_payload AS shinny_response,
    cp.created_at,
    cp.created_by,
    cp.provenance AS provenance
FROM cte_interaction ci
LEFT JOIN cte_payload cp
    ON ci.hub_interaction_id = cp.hub_interaction_id
    AND ci.hub_operation_session_id = cp.hub_operation_session_id
    AND ci.hub_operation_session_entry_id = cp.hub_operation_session_entry_id
    WHERE 1 = 1 
    AND cp.ingest_payload IS NOT NULL
   ;    
/*=============================================================================================================*/