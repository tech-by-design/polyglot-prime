package org.techbd.udi;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.techbd.udi.entity.Interaction;

public interface InteractionRepository extends JpaRepository<Interaction,String> {
    @Query(value = "SELECT * FROM techbd_udi_ingress.interaction", nativeQuery = true)
    List<Interaction> findAllInteractions();
}
