package com.ges.boutique.benefice;

import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import com.ges.boutique.vente.Vente;
import com.ges.boutique.vente.VenteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BeneficeService {

    private final VenteRepository venteRepository;
    private final UtilisateurRepository utilisateurRepository;

    public Map<String, Object> beneficeJournalier(LocalDate date) {
        LocalDateTime debut = date.atStartOfDay();
        LocalDateTime fin   = date.atTime(LocalTime.MAX);

        double benefice = safe(venteRepository.getBeneficeTotalByDateRange(debut, fin));
        double ca       = safe(venteRepository.getCAByDateRange(debut, fin));
        long   nbVentes = safeL(venteRepository.countByDateRange(debut, fin));

        // Comparaison jour précédent
        LocalDate veille = date.minusDays(1);
        double beneficePrec = safe(venteRepository.getBeneficeTotalByDateRange(
                veille.atStartOfDay(), veille.atTime(LocalTime.MAX)));
        double evolution = pct(benefice, beneficePrec);

        // Lignes par heure (0-23)
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            LocalDateTime dh = date.atTime(h, 0, 0);
            LocalDateTime fh = date.atTime(h, 59, 59);
            double b = safe(venteRepository.getBeneficeTotalByDateRange(dh, fh));
            if (b > 0) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("label", String.format("%02dh", h));
                l.put("benefice", arrondi(b));
                l.put("nombreVentes", safeL(venteRepository.countByDateRange(dh, fh)));
                lignes.add(l);
            }
        }

        return buildResult("JOURNALIER", date.toString(), date.toString(),
                benefice, ca, nbVentes, evolution, lignes, debut, fin);
    }

    public Map<String, Object> beneficeHebdomadaire() {
        LocalDate lundi = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        LocalDate dimanche = lundi.plusDays(6);
        LocalDateTime debut = lundi.atStartOfDay();
        LocalDateTime fin   = dimanche.atTime(LocalTime.MAX);

        double benefice = safe(venteRepository.getBeneficeTotalByDateRange(debut, fin));
        double ca       = safe(venteRepository.getCAByDateRange(debut, fin));
        long   nbVentes = safeL(venteRepository.countByDateRange(debut, fin));

        // Comparaison semaine précédente
        LocalDate lundiPrec = lundi.minusWeeks(1);
        double beneficePrec = safe(venteRepository.getBeneficeTotalByDateRange(
                lundiPrec.atStartOfDay(), lundiPrec.plusDays(6).atTime(LocalTime.MAX)));
        double evolution = pct(benefice, beneficePrec);

        // Lignes par jour de la semaine
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate jour = lundi.plusDays(i);
            double b = safe(venteRepository.getBeneficeTotalByDateRange(
                    jour.atStartOfDay(), jour.atTime(LocalTime.MAX)));
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("label", jour.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.FRENCH));
            l.put("benefice", arrondi(b));
            l.put("nombreVentes", safeL(venteRepository.countByDateRange(jour.atStartOfDay(), jour.atTime(LocalTime.MAX))));
            lignes.add(l);
        }

        return buildResult("HEBDOMADAIRE", lundi.toString(), dimanche.toString(),
                benefice, ca, nbVentes, evolution, lignes, debut, fin);
    }

    public Map<String, Object> beneficeMensuel(int mois, int annee) {
        LocalDate premier = LocalDate.of(annee, mois, 1);
        LocalDate dernier = premier.withDayOfMonth(premier.lengthOfMonth());
        LocalDateTime debut = premier.atStartOfDay();
        LocalDateTime fin   = dernier.atTime(LocalTime.MAX);

        double benefice = safe(venteRepository.getBeneficeTotalByDateRange(debut, fin));
        double ca       = safe(venteRepository.getCAByDateRange(debut, fin));
        long   nbVentes = safeL(venteRepository.countByDateRange(debut, fin));

        // Comparaison mois précédent
        LocalDate moisPrec = premier.minusMonths(1);
        LocalDate dernierPrec = moisPrec.withDayOfMonth(moisPrec.lengthOfMonth());
        double beneficePrec = safe(venteRepository.getBeneficeTotalByDateRange(
                moisPrec.atStartOfDay(), dernierPrec.atTime(LocalTime.MAX)));
        double evolution = pct(benefice, beneficePrec);

        // Lignes par semaine du mois
        List<Map<String, Object>> lignes = new ArrayList<>();
        LocalDate cursor = premier;
        int semaine = 1;
        while (!cursor.isAfter(dernier)) {
            LocalDate finSem = cursor.plusDays(6);
            if (finSem.isAfter(dernier)) finSem = dernier;
            double b = safe(venteRepository.getBeneficeTotalByDateRange(
                    cursor.atStartOfDay(), finSem.atTime(LocalTime.MAX)));
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("label", "Sem " + semaine);
            l.put("benefice", arrondi(b));
            l.put("nombreVentes", safeL(venteRepository.countByDateRange(cursor.atStartOfDay(), finSem.atTime(LocalTime.MAX))));
            lignes.add(l);
            cursor = finSem.plusDays(1);
            semaine++;
        }

        return buildResult("MENSUEL", premier.toString(), dernier.toString(),
                benefice, ca, nbVentes, evolution, lignes, debut, fin);
    }

    public Map<String, Object> beneficeAnnuel(int annee) {
        LocalDate premier = LocalDate.of(annee, 1, 1);
        LocalDate dernier = LocalDate.of(annee, 12, 31);
        LocalDateTime debut = premier.atStartOfDay();
        LocalDateTime fin   = dernier.atTime(LocalTime.MAX);

        double benefice = safe(venteRepository.getBeneficeTotalByDateRange(debut, fin));
        double ca       = safe(venteRepository.getCAByDateRange(debut, fin));
        long   nbVentes = safeL(venteRepository.countByDateRange(debut, fin));

        // Comparaison année précédente
        double beneficePrec = safe(venteRepository.getBeneficeTotalByDateRange(
                LocalDate.of(annee - 1, 1, 1).atStartOfDay(),
                LocalDate.of(annee - 1, 12, 31).atTime(LocalTime.MAX)));
        double evolution = pct(benefice, beneficePrec);

        // Lignes par mois
        String[] moisNoms = {"Jan","Fév","Mar","Avr","Mai","Juin","Juil","Aoû","Sep","Oct","Nov","Déc"};
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            LocalDate deb = LocalDate.of(annee, m, 1);
            LocalDate end = deb.withDayOfMonth(deb.lengthOfMonth());
            double b = safe(venteRepository.getBeneficeTotalByDateRange(deb.atStartOfDay(), end.atTime(LocalTime.MAX)));
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("label", moisNoms[m - 1]);
            l.put("benefice", arrondi(b));
            l.put("nombreVentes", safeL(venteRepository.countByDateRange(deb.atStartOfDay(), end.atTime(LocalTime.MAX))));
            lignes.add(l);
        }

        return buildResult("ANNUEL", premier.toString(), dernier.toString(),
                benefice, ca, nbVentes, evolution, lignes, debut, fin);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildResult(String periode, String dateDebut, String dateFin,
                                             double benefice, double ca, long nbVentes,
                                             double evolution, List<Map<String, Object>> lignes,
                                             LocalDateTime debut, LocalDateTime fin) {
        double marge = ca > 0 ? (benefice / ca) * 100 : 0;

        // Traçabilité comptable : ventes annulées dans la période (bénéfice qu'elles
        // représentaient avant annulation + qui/quand/pourquoi), pour que le bénéfice
        // "disparu" d'une annulation reste visible quelque part au lieu de silencieusement
        // sortir des totaux (mêmes exigences que le journal des Opérations Caisse).
        List<Vente> ventesAnnuleesEntites = venteRepository.findVentesAnnuleesByDateAnnulationRange(debut, fin);
        List<Map<String, Object>> ventesAnnulees = new ArrayList<>();
        double beneficePerdu = 0.0;
        // Un seul accès par personne : la même personne annule souvent beaucoup de ventes.
        Map<Long, String> nomsAnnulation = new HashMap<>();
        for (Vente v : ventesAnnuleesEntites) {
            double b = v.getBeneficeTotal() != null ? v.getBeneficeTotal() : 0.0;
            beneficePerdu += b;
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("venteId", v.getId());
            a.put("numeroVente", v.getNumeroVente());
            a.put("dateVente", v.getDateVente());
            a.put("montantTotal", arrondi(v.getMontantTotal() != null ? v.getMontantTotal() : 0.0));
            a.put("beneficePerdu", arrondi(b));
            a.put("dateAnnulation", v.getDateAnnulation());
            a.put("motifAnnulation", v.getMotifAnnulation());
            a.put("annulePar", v.getUtilisateurAnnulation() == null ? null
                    : nomsAnnulation.computeIfAbsent(v.getUtilisateurAnnulation(), this::resoudreNomUtilisateur));
            ventesAnnulees.add(a);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("periode", periode);
        r.put("dateDebut", dateDebut);
        r.put("dateFin", dateFin);
        r.put("beneficeTotal", arrondi(benefice));
        r.put("chiffreAffaireTotal", arrondi(ca));
        r.put("nombreVentes", nbVentes);
        r.put("margeMoyenne", arrondi(marge));
        r.put("evolution", arrondi(evolution));
        r.put("lignes", lignes);
        r.put("ventesAnnulees", ventesAnnulees);
        r.put("beneficePerduAnnulations", arrondi(beneficePerdu));
        return r;
    }

    private String resoudreNomUtilisateur(Long utilisateurId) {
        if (utilisateurId == null) return null;
        return utilisateurRepository.findById(utilisateurId)
                .map(Utilisateur::getNomComplet)
                .orElse(null);
    }

    private double safe(Double v) { return v != null ? v : 0.0; }
    private long safeL(Long v)    { return v != null ? v : 0L; }
    private double arrondi(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
    private double pct(double actuel, double precedent) {
        if (precedent == 0) return actuel > 0 ? 100.0 : 0.0;
        return arrondi(((actuel - precedent) / precedent) * 100);
    }
}
