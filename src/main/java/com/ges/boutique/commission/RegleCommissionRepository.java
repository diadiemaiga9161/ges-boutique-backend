package com.ges.boutique.commission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegleCommissionRepository extends JpaRepository<RegleCommission, Long> {
    List<RegleCommission> findAllByOrderByDateDebutDescIdDesc();
}
