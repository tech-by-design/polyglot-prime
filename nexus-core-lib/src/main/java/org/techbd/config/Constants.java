package org.techbd.config;

public interface Constants {

    public static final String PATIENT_ID = "PATIENT_ID";
    public static final String ENCOUNTER_ID = "ENCOUNTER_ID";
    public static final String ORGANIZATION_ID = "ORGRANIZATION_ID";
    public static final String INTERPERSONAL_SAFETY_GROUP_QUESTIONS = "INTERPERSONAL_SAFETY_GROUP_QUESTIONS";
    public static final String AHC_SCREENING_GROUP_QUESTIONS = "AHC_SCREENING_GROUP_QUESTIONS";
    public static final String SUPPLEMENTAL_GROUP_QUESTIONS = "SUPPLEMENTAL_GROUP_QUESTIONS";
    public static final String TOTAL_SCORE_QUESTIONS = "TOTAL_SCORE_QUESTIONS";
    public static final String MENTAL_HEALTH_SCORE_QUESTIONS = "MENTAL_HEALTH_SCORE_QUESTIONS";
    public static final String PHYSICAL_ACTIVITY_SCORE_QUESTIONS = "PHYSICAL_ACTIVITY_SCORE_QUESTIONS";

    // Constants for request paramteres
    public static final String INTERACTION_ID = "interactionId";
    public static final String REQUEST_URI = "requestUri";
    public static final String REQUEST_URL = "requestUrl";
    public static final String USER_AGENT = "User-Agent";
    public static final String TENANT_ID = "X-TechBD-Tenant-ID";
    public static final String ORIGIN = "ORIGIN";
    public static final String PREFIX = "X-TechBD-";
    public static final String HEALTH_CHECK = "X-TechBD-HealthCheck";
    public static final String SOURCE_TYPE = "source";
    public static final String CORRELATION_ID = "X-Correlation-ID";
    public static final String OVERRIDE_REQUEST_URI = "X-TechBD-Override-Request-URI";
    public static final String DELETE_SESSION = "delete-session-cookie";
    public static final String OBSERVABILITY_METRIC_INTERACTION_START_TIME = "X-Observability-Metric-Interaction-Start-Time";
    public static final String OBSERVABILITY_METRIC_INTERACTION_FINISH_TIME = "X-Observability-Metric-Interaction-Finish-Time";
    public static final String OBSERVABILITY_METRIC_INTERACTION_DURATION_NANOSECS = "X-Observability-Metric-Interaction-Duration-Nanosecs";
    public static final String OBSERVABILITY_METRIC_INTERACTION_DURATION_MILLISECS = "X-Observability-Metric-Interaction-Duration-Millisecs";
    public static final String FHIR_CONTENT_TYPE_HEADER_VALUE = "application/fhir+json";
    public static final String USER_NAME = "USER_NAME";
    public static final String USER_ID = "USER_ID";
    public static final String USER_SESSION = "USER_SESSION";
    public static final String USER_ROLE = "USER_ROLE";
    public static final String DELETE_USER_SESSION_COOKIE = "delete-session-cookie";
    public static final String PROVENANCE = "X-Provenance";
    public static final String DATA_LAKE_API_CONTENT_TYPE = "X-TechBD-DataLake-API-Content-Type";
    public static final String CUSTOM_DATA_LAKE_API = "X-TechBD-DataLake-API-URL";
    public static final String MTLS_STRATEGY = "mtls-strategy";
    public static final String GROUP_INTERACTION_ID = "groupInteractionId";
    public static final String MASTER_INTERACTION_ID = "masterInteractionId";
    public static final String PROTOCOL = "protocol";
    public static final String LOCAL_ADDRESS = "localAddress";
    public static final String REMOTE_ADDRESS = "remoteAddress";
    public static final String IMMEDIATE = "immediate";
    public static final String VALIDATION_SEVERITY_LEVEL = "X-TechBD-Validation-Severity-Level";
    public static final String FHIR_STRUCT_DEFN_PROFILE_URI = "X-TechBD-FHIR-Profile-URI";
    public static final String FHIR_VALIDATION_STRATEGY = "X-TechBD-FHIR-Validation-Strategy";
    public static final String DATALAKE_API_URL = "X-TechBD-DataLake-API-URL";
    public static final String DATALAKE_API_CONTENT_TYPE = "DataLake-API-Content-Type";
    public static final String HEALTH_CHECK_HEADER = "X-TechBD-HealthCheck";
    public static final String START_TIME_ATTRIBUTE = "startTime";
    public static final String BASE_FHIR_URL = "BASE_FHIR_URL";
    public static final String METRIC_COOKIE = "METRIC_COOKIE";
    public static final String HEADER = "X-Observability-Metric-Interaction-Duration-Millisecs";
    public static final String DEFAULT_USER_NAME = "API_USER";
    public static final String DEFAULT_USER_ID = "N/A";
    public static final String DEFAULT_USER_SESSION = "N/A";
    public static final String DEFAULT_USER_ROLE = "API_ROLE";
    public static final String KEY_ERROR = "error";
    public static final String KEY_INTERACTION_ID = "interaction_id";
    public static final String KEY_HUB_NEXUS_INTERACTION_ID = "hub_nexus_interaction_id";
    public static final String KEY_PAYLOAD = "payload";
}
