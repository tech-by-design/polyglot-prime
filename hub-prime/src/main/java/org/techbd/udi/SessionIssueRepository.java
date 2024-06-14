package org.techbd.udi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.techbd.udi.entity.SessionIssue;

import java.util.List;

public interface SessionIssueRepository extends JpaRepository<SessionIssue,String> {
    @Query(value = "SELECT * FROM techbd_udi_ingress.sat_ingest_session_entry_session_issue_fhir", nativeQuery = true)
    List<SessionIssue> findAllSessionIssue();
}
