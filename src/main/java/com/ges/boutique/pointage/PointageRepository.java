package com.ges.boutique.pointage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PointageRepository extends JpaRepository<Pointage, Long> {
    Optional<Pointage> findByEmployeIdAndDatePointage(Long employeId, LocalDate datePointage);
    List<Pointage> findByDatePointage(LocalDate datePointage);
    List<Pointage> findByDatePointageBetween(LocalDate debut, LocalDate fin);
    List<Pointage> findByEmployeIdAndDatePointageBetweenOrderByDatePointageDesc(Long employeId, LocalDate debut, LocalDate fin);
}
