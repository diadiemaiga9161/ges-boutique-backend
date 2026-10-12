package com.ges.boutique.pointage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParametrePointageRepository extends JpaRepository<ParametrePointage, Long> {
}
