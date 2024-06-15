package org.techbd.udi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.sql.Timestamp;

@Entity
@Immutable
@Table(name = "sat_ingest_session_entry_session_issue_fhir", schema = "techbd_udi_ingress")
public class SessionIssue {
    @Id
    @Column(name = "session_entry_id")
    private String sessionEntryId;
    @Column(name = "issue_type")
    private String issueType;
    @Column(name = "issue_message")
    private String issueMessage;
    @Column(name = "level")
    private String level;
    @Column(name = "issue_column")
    private String issueColumn;
    @Column(name = "issue_row")
    private String issueRow;
    @Column(name = "message_id")
    private String messageId;
    @Column(name = "ignorableerror")
    private String ignorableError;
    @Column(name = "invalid_value")
    private String invalidValue;

    public String getSessionEntryId() {
        return sessionEntryId;
    }

    public void setSessionEntryId(String sessionEntryId) {
        this.sessionEntryId = sessionEntryId;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getIssueMessage() {
        return issueMessage;
    }

    public void setIssueMessage(String issueMessage) {
        this.issueMessage = issueMessage;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getIssueColumn() {
        return issueColumn;
    }

    public void setIssueColumn(String issueColumn) {
        this.issueColumn = issueColumn;
    }

    public String getIssueRow() {
        return issueRow;
    }

    public void setIssueRow(String issueRow) {
        this.issueRow = issueRow;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getIgnorableError() {
        return ignorableError;
    }

    public void setIgnorableError(String ignorableError) {
        this.ignorableError = ignorableError;
    }

    public String getInvalidValue() {
        return invalidValue;
    }

    public void setInvalidValue(String invalidValue) {
        this.invalidValue = invalidValue;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String disposition) {
        this.disposition = disposition;
    }

    public String getRemediation() {
        return remediation;
    }

    public void setRemediation(String remediation) {
        this.remediation = remediation;
    }

    public String getElaboration() {
        return elaboration;
    }

    public void setElaboration(String elaboration) {
        this.elaboration = elaboration;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getProvenance() {
        return provenance;
    }

    public void setProvenance(String provenance) {
        this.provenance = provenance;
    }

    @Column(name = "comment")
    private String comment;
    @Column(name = "display")
    private String display;
    @Column(name = "disposition")
    private String disposition;
    @Column(name = "remediation")
    private String remediation;
    @Column(name = "elaboration")
    private String elaboration;
    @Column(name = "created_at")
    private Timestamp createdAt;
    @Column(name = "provenance")
    private String provenance;
}
