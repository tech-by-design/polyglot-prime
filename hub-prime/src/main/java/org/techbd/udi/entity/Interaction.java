package org.techbd.udi.entity;

import java.sql.Timestamp;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
@Entity
@Immutable
@Table(name = "interaction", schema = "techbd_udi_ingress")
public class Interaction {
    @Id
    @Column(name = "hub_ingest_session_id")
    private String hubIngestSessionId;

    @Column(name = "hub_ingest_session_entry_id")
    private String hubIngestSessionEntryId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "interaction_id")
    private String interactionId;

    @Column(name = "provenance")
    private String provenance;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "request_method")
    private String requestMethod;

    @Column(name = "request_uri")
    private String requestUri;

    @Column(name = "request_params")
    private String requestParams;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_time_seconds")
    private Double responseTimeSeconds;

    @Column(name = "client_ip_address")
    private String clientIpAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "num_request_headers")
    private Integer numRequestHeaders;

    @Column(name = "request_encountered_at")
    private Timestamp requestEncounteredAt;

    @Column(name = "response_encountered_at")
    private Timestamp responseEncounteredAt;

    @Column(name = "response_time_microseconds")
    private Double responseTimeMicroseconds;

    public String getRequestParams() {
        return requestParams;
    }

    public void setRequestParams(String requestParams) {
        this.requestParams = requestParams;
    }

    public String getHubIngestSessionId() {
        return hubIngestSessionId;
    }

    public void setHubIngestSessionId(String hubIngestSessionId) {
        this.hubIngestSessionId = hubIngestSessionId;
    }

    public String getHubIngestSessionEntryId() {
        return hubIngestSessionEntryId;
    }

    public void setHubIngestSessionEntryId(String hubIngestSessionEntryId) {
        this.hubIngestSessionEntryId = hubIngestSessionEntryId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getInteractionId() {
        return interactionId;
    }

    public void setInteractionId(String interactionId) {
        this.interactionId = interactionId;
    }

    public String getProvenance() {
        return provenance;
    }

    public void setProvenance(String provenance) {
        this.provenance = provenance;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Double getResponseTimeSeconds() {
        return responseTimeSeconds;
    }

    public void setResponseTimeSeconds(Double responseTimeSeconds) {
        this.responseTimeSeconds = responseTimeSeconds;
    }

    public String getClientIpAddress() {
        return clientIpAddress;
    }

    public void setClientIpAddress(String clientIpAddress) {
        this.clientIpAddress = clientIpAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Integer getNumRequestHeaders() {
        return numRequestHeaders;
    }

    public void setNumRequestHeaders(Integer numRequestHeaders) {
        this.numRequestHeaders = numRequestHeaders;
    }

    public Timestamp getRequestEncounteredAt() {
        return requestEncounteredAt;
    }

    public void setRequestEncounteredAt(Timestamp requestEncounteredAt) {
        this.requestEncounteredAt = requestEncounteredAt;
    }

    public Timestamp getResponseEncounteredAt() {
        return responseEncounteredAt;
    }

    public void setResponseEncounteredAt(Timestamp responseEncounteredAt) {
        this.responseEncounteredAt = responseEncounteredAt;
    }

    public Double getResponseTimeMicroseconds() {
        return responseTimeMicroseconds;
    }

    public void setResponseTimeMicroseconds(Double responseTimeMicroseconds) {
        this.responseTimeMicroseconds = responseTimeMicroseconds;
    }

}
