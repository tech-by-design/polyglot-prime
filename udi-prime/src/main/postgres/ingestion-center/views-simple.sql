-- only include simple (non-materialized) views here

DROP VIEW IF EXISTS techbd_udi_ingress.interaction_http CASCADE;
CREATE OR REPLACE VIEW techbd_udi_ingress.interaction_http AS 
WITH cte_interaction AS (
	 SELECT intpayload.sat_interaction_http_request_id,
	     	intpayload.hub_interaction_id,
	        intpayload.provenance,
	        intpayload.request_payload::jsonb ->> 'namespace'::text AS namespace,
	        intpayload.request_payload::jsonb ->> 'interactionId'::text AS interaction_id,
	        (intpayload.request_payload::jsonb -> 'tenant'::text) ->> 'tenantId'::text AS tenant_id,
	        (intpayload.request_payload::jsonb -> 'tenant'::text) ->> 'name'::text AS tenant_name,
	        (intpayload.request_payload::jsonb -> 'request'::text) ->> 'requestId'::text AS request_id,
	        (intpayload.request_payload::jsonb -> 'request'::text) ->> 'method'::text AS request_method,
	        (intpayload.request_payload::jsonb -> 'request'::text) ->> 'requestUri'::text AS request_uri,
	        (intpayload.request_payload::jsonb -> 'request'::text) ->> 'queryString'::text AS request_params,
	        (intpayload.request_payload::jsonb -> 'request'::text) ->> 'clientIpAddress'::text AS client_ip_address,
	        (intpayload.request_payload::jsonb -> 'request'::text) ->> 'userAgent'::text AS user_agent,
	        ((intpayload.request_payload::jsonb -> 'request'::text) ->> 'encounteredAt'::text)::double precision AS request_encountered_at_raw,
	        (intpayload.request_payload::jsonb -> 'response'::text) ->> 'responseId'::text AS response_id,
	        ((intpayload.request_payload::jsonb -> 'response'::text) ->> 'status'::text)::integer AS response_status,
	        ((intpayload.request_payload::jsonb -> 'response'::text) ->> 'encounteredAt'::text)::double precision AS response_encountered_at_raw,
	        jsonb_array_length((intpayload.request_payload::jsonb -> 'request'::text) -> 'headers'::text) AS num_request_headers
	       FROM techbd_udi_ingress.sat_interaction_http_request intpayload
	    )
	SELECT 
 	interaction.sat_interaction_http_request_id,
 	interaction.hub_interaction_id,
 	hubint."key" AS interaction_key,
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
    interaction.provenance
   FROM cte_interaction interaction
   LEFT OUTER JOIN techbd_udi_ingress.hub_interaction hubint
   ON hubint.hub_interaction_id = interaction.hub_interaction_id;
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
