package com.ges.boutique.vente;

import com.ges.boutique.caisse.CaisseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Règlement d'un crédit par POST /api/ventes/credits/{id}/reglement (page Ventes du site) :
 * le versement ne doit être compté qu'une fois. CaisseService.reglementCredit met déjà à jour
 * la vente ; le service ne doit pas l'ajouter une seconde fois.
 */
@ExtendWith(MockitoExtension.class)
class VenteServiceImplReglementCreditTest {

    @Mock private VenteRepository venteRepository;
    @Mock private CaisseService caisseService;

    @InjectMocks
    private VenteServiceImpl venteService;

    private Vente creditDe(double montant) {
        Vente v = new Vente();
        v.setId(10L);
        v.setEstCredit(true);
        v.setCreditRegle(false);
        v.setAnnulee(false);
        v.setMontantTotal(montant);
        v.setMontantApresRemise(montant);
        v.setMontantVerse(0.0);
        v.setMontantRestant(montant);
        return v;
    }

    /** Reproduit ce que fait réellement CaisseServiceImpl.reglementCredit sur la vente. */
    private void caisseMetAJourLaVente(Vente v) {
        doAnswer(inv -> {
            double m = inv.getArgument(1);
            v.setMontantVerse(v.getMontantVerse() + m);
            v.setMontantRestant(v.getMontantRestant() - m);
            if (v.getMontantRestant() <= 0) {
                v.setCreditRegle(true);
                v.setDateReglement(LocalDate.now());
            }
            return null;
        }).when(caisseService).reglementCredit(eq(10L), anyDouble(), any(), any(), any(), any(), any());
    }

    private ReglementCreditRequest reglement(double montant, LocalDate date) {
        ReglementCreditRequest r = new ReglementCreditRequest();
        r.setMontantRegle(montant);
        r.setModePaiement("ESPECES");
        r.setDateReglement(date);
        return r;
    }

    @Test
    void reglementPartiel_compteUneSeuleFois() {
        Vente v = creditDe(1000.0);
        when(venteRepository.findById(10L)).thenReturn(Optional.of(v));
        when(venteRepository.save(any(Vente.class))).thenAnswer(inv -> inv.getArgument(0));
        caisseMetAJourLaVente(v);

        Vente res = venteService.enregistrerReglementCredit(10L, reglement(300.0, null));

        assertEquals(300.0, res.getMontantVerse(), 0.001);
        assertEquals(700.0, res.getMontantRestant(), 0.001);
        assertFalse(Boolean.TRUE.equals(res.getCreditRegle()));
    }

    @Test
    void reglementTotal_soldeLeCreditAvecLaDateChoisie() {
        Vente v = creditDe(1000.0);
        when(venteRepository.findById(10L)).thenReturn(Optional.of(v));
        when(venteRepository.save(any(Vente.class))).thenAnswer(inv -> inv.getArgument(0));
        caisseMetAJourLaVente(v);
        LocalDate dateChoisie = LocalDate.of(2026, 10, 1);

        Vente res = venteService.enregistrerReglementCredit(10L, reglement(1000.0, dateChoisie));

        assertEquals(1000.0, res.getMontantVerse(), 0.001);
        assertEquals(0.0, res.getMontantRestant(), 0.001);
        assertTrue(res.getCreditRegle());
        assertEquals(dateChoisie, res.getDateReglement());
    }

    @Test
    void deuxReglementsPartiels_seCumulentSansDoublon() {
        Vente v = creditDe(1000.0);
        when(venteRepository.findById(10L)).thenReturn(Optional.of(v));
        when(venteRepository.save(any(Vente.class))).thenAnswer(inv -> inv.getArgument(0));
        caisseMetAJourLaVente(v);

        venteService.enregistrerReglementCredit(10L, reglement(300.0, null));
        Vente res = venteService.enregistrerReglementCredit(10L, reglement(200.0, null));

        assertEquals(500.0, res.getMontantVerse(), 0.001);
        assertEquals(500.0, res.getMontantRestant(), 0.001);
    }
}
