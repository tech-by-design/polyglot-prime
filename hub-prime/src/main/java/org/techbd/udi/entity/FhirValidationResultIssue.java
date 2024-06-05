package org.techbd.udi.entity;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "fhir_validation_result_issue_with_qe")
public class FhirValidationResultIssue {
  @Id
  @Column(name = "artifact_id")
  private String artifactId;
  @Column(name = "namespace")
  private String namespace;
  @Column(name = "profile_url")
  private String profileUrl;
  @Column(name = "engine")
  private String engine;
  @Column(name = "valid")
  private Boolean valid;
  @Column(name = "issue_message")
  private String issueMessage;
  @Column(name = "issue_severity")
  private String issueSeverity;
  @Column(name = "issue_location_line")
  private String issueLocationLine;
  @Column(name = "issue_location_column")
  private String issueLocationColumn;
  @Column(name = "issue_diagnostics")
  private String issueDiagnostics;
  @Column(name = "qe")
  private String qe;
  @Column(name = "initiated_at")
  private String initiatedAt;

  public String getArtifactId() {
    return artifactId;
  }

  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getProfileUrl() {
    return profileUrl;
  }

  public void setProfileUrl(String profileUrl) {
    this.profileUrl = profileUrl;
  }

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }

  public Boolean getValid() {
    return valid;
  }

  public void setValid(Boolean valid) {
    this.valid = valid;
  }

  public String getIssueMessage() {
    return issueMessage;
  }

  public void setIssueMessage(String issueMessage) {
    this.issueMessage = issueMessage;
  }

  public String getIssueSeverity() {
    return issueSeverity;
  }

  public void setIssueSeverity(String issueSeverity) {
    this.issueSeverity = issueSeverity;
  }

  public String getIssueLocationLine() {
    return issueLocationLine;
  }

  public void setIssueLocationLine(String issueLocationLine) {
    this.issueLocationLine = issueLocationLine;
  }

  public String getIssueLocationColumn() {
    return issueLocationColumn;
  }

  public void setIssueLocationColumn(String issueLocationColumn) {
    this.issueLocationColumn = issueLocationColumn;
  }

  public String getIssueDiagnostics() {
    return issueDiagnostics;
  }

  public void setIssueDiagnostics(String issueDiagnostics) {
    this.issueDiagnostics = issueDiagnostics;
  }
  
  public String getQe() {
    return qe;
  }

  public void setQe(String qe) {
    this.qe = qe;
  }

  public String getInitiatedAt() {
    return initiatedAt;
  }

  public void setInitiatedAt(String initiatedAt) {
    this.initiatedAt = initiatedAt;
  }
}
