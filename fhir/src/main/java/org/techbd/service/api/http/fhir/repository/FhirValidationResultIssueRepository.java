package org.techbd.service.api.http.fhir.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.techbd.service.api.http.fhir.entity.FhirValidationResultIssue;

import java.util.List;

public interface FhirValidationResultIssueRepository extends JpaRepository<FhirValidationResultIssue, String> {
  @Query(value = "SELECT * FROM public.fhir_validation_result_issue", nativeQuery = true)
  List<FhirValidationResultIssue> findAll();
}
