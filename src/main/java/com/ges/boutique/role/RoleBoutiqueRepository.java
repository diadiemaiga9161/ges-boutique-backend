package com.ges.boutique.role;

import com.ges.boutique.utilisateur.RoleUtilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleBoutiqueRepository extends JpaRepository<RoleBoutique, Long> {
    Optional<RoleBoutique> findFirstBySystemeTrueAndRoleBase(RoleUtilisateur roleBase);
    List<RoleBoutique> findAllByOrderBySystemeDescNomAsc();
    boolean existsByNomIgnoreCase(String nom);
}
