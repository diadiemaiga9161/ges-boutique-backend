package com.ges.boutique.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreneauPlanningRepository extends JpaRepository<CreneauPlanning, Long> {
    List<CreneauPlanning> findByDateJourBetween(LocalDate debut, LocalDate fin);
    List<CreneauPlanning> findByEmployeIdAndDateJourBetweenOrderByDateJourAsc(Long employeId, LocalDate debut, LocalDate fin);
    Optional<CreneauPlanning> findByEmployeIdAndDateJour(Long employeId, LocalDate dateJour);
}
