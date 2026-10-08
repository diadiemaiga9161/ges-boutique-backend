package com.ges.boutique.produit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Vente à la mesure : passer un produit au kilo (ou le remettre à l'unité) ne doit jamais
 * fausser son stock ni ses prix, et la vente "prix x quantité" doit rester juste.
 */
@ExtendWith(MockitoExtension.class)
class ModeMesureServiceTest {

    @Mock private ProduitRepository produitRepository;
    @Mock private UniteVenteRepository uniteVenteRepository;
    @InjectMocks private ModeMesureService service;

    private Produit riz() {
        Produit p = new Produit();
        p.setId(5L);
        p.setNom("Riz");
        p.setQuantite(50);          // 50 sacs... compris comme 50 kg
        p.setSeuilAlerte(5);
        p.setPrixVente(600.0);      // 600 F le kg
        p.setPrixAchat(450.0);
        return p;
    }

    @Test
    void passerAuKiloConvertitStockEtPrix() {
        Produit p = riz();
        when(produitRepository.findById(5L)).thenReturn(Optional.of(p));
        when(uniteVenteRepository.findByProduitIdOrderByOrdreAsc(5L)).thenReturn(List.of());
        when(produitRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Produit r = service.changer(5L, ModeMesure.KG);

        assertEquals(50_000, r.getQuantite());
        assertEquals(5_000, r.getSeuilAlerte());
        assertEquals(0.6, r.getPrixVente(), 1e-9);
        assertEquals(0.45, r.getPrixAchat(), 1e-9);
        assertEquals("g", r.getUniteBase());
        // 1,25 kg vendu = 1250 g x 0,6 F = 750 F : le calcul existant reste juste.
        assertEquals(750.0, 1250 * r.getPrixVente(), 1e-6);
        // Valeur du stock inchangée : 50 kg x 450 F = 22 500 F.
        assertEquals(22_500.0, r.getQuantite() * r.getPrixAchat(), 1e-6);
    }

    @Test
    void remettreAlUniteRetrouveLesValeursDeDepart() {
        Produit p = riz();
        when(produitRepository.findById(5L)).thenReturn(Optional.of(p));
        when(uniteVenteRepository.findByProduitIdOrderByOrdreAsc(5L)).thenReturn(List.of());
        when(produitRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.changer(5L, ModeMesure.KG);
        Produit r = service.changer(5L, null);

        assertNull(r.getModeMesure());
        assertEquals(50, r.getQuantite());
        assertEquals(5, r.getSeuilAlerte());
        assertEquals(600.0, r.getPrixVente(), 1e-9);
        assertEquals("Unité", r.getUniteBase());
    }

    @Test
    void unSacDe50KgDevientDesKilos() {
        // Sac de 50 kg : 10 sacs, acheté 27 500 F, vendu 30 000 F le sac.
        Produit p = riz();
        p.setQuantite(10);
        p.setSeuilAlerte(2);
        p.setPrixAchat(27_500.0);
        p.setPrixVente(30_000.0);
        when(produitRepository.findById(5L)).thenReturn(Optional.of(p));
        when(uniteVenteRepository.findByProduitIdOrderByOrdreAsc(5L)).thenReturn(List.of());
        when(produitRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Produit r = service.changer(5L, ModeMesure.KG, 50.0);

        assertEquals(500_000, r.getQuantite());              // 500 kg
        assertEquals(100_000, r.getSeuilAlerte());           // 100 kg
        assertEquals(0.55, r.getPrixAchat(), 1e-9);          // 550 F/kg
        assertEquals(0.6, r.getPrixVente(), 1e-9);           // 600 F/kg
        // Valeur du stock inchangée : 10 sacs x 27 500 F = 275 000 F.
        assertEquals(275_000.0, r.getQuantite() * r.getPrixAchat(), 1e-6);

        // Retour en sacs de 50 kg : on retrouve les valeurs de départ.
        Produit retour = service.changer(5L, null, 50.0);
        assertEquals(10, retour.getQuantite());
        assertEquals(30_000.0, retour.getPrixVente(), 1e-6);
    }

    @Test
    void refuseUneContenanceNulle() {
        assertThrows(IllegalArgumentException.class, () -> service.changer(5L, ModeMesure.KG, 0.0));
    }

    @Test
    void refuseUnProduitANiveaux() {
        Produit p = riz();
        p.getNiveaux().add(new ProduitNiveau());
        when(produitRepository.findById(5L)).thenReturn(Optional.of(p));
        assertThrows(IllegalStateException.class, () -> service.changer(5L, ModeMesure.KG));
    }

    @Test
    void lesDocumentsAffichentDesKilos() {
        Produit p = riz();
        p.setModeMesure(ModeMesure.KG);
        assertEquals("1,25 kg", ModeMesure.quantiteLisible(p, 1250));
        assertEquals(600.0, ModeMesure.prixLisible(p, 0.6), 1e-9);
        assertEquals("/kg", ModeMesure.suffixePrix(p));
        // Produit à l'unité : rien ne change.
        Produit normal = riz();
        assertEquals("3", ModeMesure.quantiteLisible(normal, 3));
        assertEquals("", ModeMesure.suffixePrix(normal));
    }
}
