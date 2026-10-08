package com.ges.boutique.role;

import com.ges.boutique.utilisateur.RoleUtilisateur;
import lombok.Data;

import java.util.List;

@Data
public class RoleBoutiqueRequest {
    private String nom;
    private String description;
    private RoleUtilisateur roleBase;
    private List<String> permissions;
}
