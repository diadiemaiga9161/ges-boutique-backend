package com.ges.boutique.utilisateur;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByUsername(String username);
    Optional<Utilisateur> findByEmail(String email);
    Optional<Utilisateur> findByTelephone(String telephone);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    long countByRoleBoutiqueId(Long roleBoutiqueId);
    java.util.List<Utilisateur> findByRole(RoleUtilisateur role);
}