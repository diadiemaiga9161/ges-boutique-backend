package com.ges.boutique.commission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaiementCommissionRepository extends JpaRepository<PaiementCommission, Long> {
    List<PaiementCommission> findAllByOrderByDatePaiementDesc();
    List<PaiementCommission> findByVendeurIdOrderByDatePaiementDesc(Long vendeurId);
}
