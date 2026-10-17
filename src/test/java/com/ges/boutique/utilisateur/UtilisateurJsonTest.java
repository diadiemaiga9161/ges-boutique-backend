package com.ges.boutique.utilisateur;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Le hash du mot de passe ne doit jamais sortir dans les réponses JSON
 * (/api/utilisateurs, /api/auth/profil…), mais doit rester lisible à la création d'un compte.
 */
class UtilisateurJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void motDePasse_absentDuJsonRenvoye() throws Exception {
        Utilisateur u = new Utilisateur();
        u.setId(7L);
        u.setUsername("vendeur1");
        u.setRole(RoleUtilisateur.VENDEUR);
        u.setPassword("$2a$10$hashDeTestQuiNeDoitPasSortir");

        String json = mapper.writeValueAsString(u);

        assertFalse(json.contains("password"), json);
        assertFalse(json.contains("hashDeTest"), json);
    }

    @Test
    void motDePasse_luALaCreationDuCompte() throws Exception {
        Utilisateur u = mapper.readValue("{\"username\":\"vendeur2\",\"password\":\"secret123\"}", Utilisateur.class);

        assertEquals("secret123", u.getPassword());
    }
}
