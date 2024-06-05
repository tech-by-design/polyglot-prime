package org.techbd.udi;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.techbd.udi.entity.FhirValidationResultIssue;

public interface UdiPrimeRepository extends JpaRepository<FhirValidationResultIssue, String> {
  @Query(value = "SELECT * FROM public.fhir_validation_result_issue_with_qe", nativeQuery = true)
  List<FhirValidationResultIssue> findAll();
}
