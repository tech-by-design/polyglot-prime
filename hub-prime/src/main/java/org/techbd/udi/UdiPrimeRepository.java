package org.techbd.udi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.techbd.udi.entity.FhirValidationResultIssue;

import java.util.List;

public interface UdiPrimeRepository extends JpaRepository<FhirValidationResultIssue, String> {
  @Query(value = "SELECT * FROM public.fhir_validation_result_issue", nativeQuery = true)
  List<FhirValidationResultIssue> findAll();
}
