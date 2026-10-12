package com.ges.boutique.pointage;

import com.ges.boutique.employe.Employe;
import com.ges.boutique.employe.EmployeRepository;
import com.ges.boutique.employe.StatutEmploye;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pointage des employés.
 *
 * Le code QR affiché dans la boutique change toutes les 30 secondes : une photo du code
 * envoyée à quelqu'un qui n'est pas sur place ne marche plus une minute après. Chaque code
 * est signé avec la clé secrète de la boutique (HMAC), il ne peut donc pas être fabriqué.
 */
@Service
@RequiredArgsConstructor
public class PointageService {

    static final ZoneId ZONE = ZoneId.of("Africa/Bamako");
    private static final long DUREE_CODE_SECONDES = 30;
    private static final String PREFIXE_CODE = "GLP1";
    /** Deux scans à moins de 5 minutes d'écart : le second est ignoré (double scan). */
    private static final Duration ECART_MINIMUM = Duration.ofMinutes(5);
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final PointageRepository pointageRepository;
    private final ParametrePointageRepository parametreRepository;
    private final EmployeRepository employeRepository;
    private final UtilisateurRepository utilisateurRepository;

    // ================================================================= réglages

    @Transactional
    public ParametrePointage reglage() {
        return parametreRepository.findById(1L).orElseGet(() -> {
            ParametrePointage p = new ParametrePointage();
            byte[] cle = new byte[32];
            new SecureRandom().nextBytes(cle);
            p.setCleSecrete(Base64.getUrlEncoder().withoutPadding().encodeToString(cle));
            return parametreRepository.save(p);
        });
    }

    public Map<String, Object> parametres() {
        ParametrePointage p = reglage();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("heureArriveePrevue", p.getHeureArriveePrevue().format(HEURE));
        m.put("toleranceMinutes", p.getToleranceMinutes());
        m.put("jourRepos", p.getJourRepos());
        return m;
    }

    @Transactional
    public Map<String, Object> modifierParametres(Map<String, Object> corps) {
        ParametrePointage p = reglage();
        if (corps.get("heureArriveePrevue") != null) {
            p.setHeureArriveePrevue(lireHeure(String.valueOf(corps.get("heureArriveePrevue")), "Heure d'arrivée prévue"));
        }
        if (corps.get("toleranceMinutes") != null) {
            int t;
            try {
                t = Integer.parseInt(String.valueOf(corps.get("toleranceMinutes")).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("La tolérance doit être un nombre de minutes.");
            }
            if (t < 0 || t > 180) throw new IllegalArgumentException("La tolérance doit être entre 0 et 180 minutes.");
            p.setToleranceMinutes(t);
        }
        if (corps.get("jourRepos") != null) {
            String j = String.valueOf(corps.get("jourRepos")).trim().toUpperCase();
            if (!j.equals("AUCUN")) {
                try { DayOfWeek.valueOf(j); } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Jour de repos inconnu.");
                }
            }
            p.setJourRepos(j);
        }
        parametreRepository.save(p);
        return parametres();
    }

    // ================================================================= code QR

    /** Code à afficher maintenant, et nombre de secondes avant qu'il change. */
    public Map<String, Object> codeActuel() {
        long maintenant = Instant.now().getEpochSecond();
        long fenetre = maintenant / DUREE_CODE_SECONDES;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", PREFIXE_CODE + "." + fenetre + "." + signature(fenetre));
        m.put("changeDans", DUREE_CODE_SECONDES - (maintenant % DUREE_CODE_SECONDES));
        m.put("dureeSecondes", DUREE_CODE_SECONDES);
        return m;
    }

    /** Accepte le code en cours et le précédent (entre 30 et 60 secondes pour scanner). */
    void verifierCode(String code) {
        String c = code == null ? "" : code.trim();
        // Le QR peut contenir une adresse ".../pointage?c=CODE" : on garde le code seul.
        int i = c.indexOf("c=");
        if (i >= 0) c = c.substring(i + 2).split("[&#]")[0];
        String[] parts = c.split("\\.");
        if (parts.length != 3 || !PREFIXE_CODE.equals(parts[0])) {
            throw new IllegalArgumentException("Ce n'est pas le code de pointage de la boutique.");
        }
        long fenetre;
        try {
            fenetre = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ce n'est pas le code de pointage de la boutique.");
        }
        long actuelle = Instant.now().getEpochSecond() / DUREE_CODE_SECONDES;
        boolean signatureOk = MessageDigest.isEqual(
                signature(fenetre).getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII));
        if (!signatureOk) {
            throw new IllegalArgumentException("Ce n'est pas le code de pointage de cette boutique.");
        }
        if (fenetre != actuelle && fenetre != actuelle - 1) {
            throw new IllegalArgumentException("Ce code a expiré. Scannez le code affiché en ce moment dans la boutique.");
        }
    }

    private String signature(long fenetre) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getUrlDecoder().decode(reglage().getCleSecrete()), "HmacSHA256"));
            byte[] h = mac.doFinal(("pointage:" + fenetre).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(h, 16));
        } catch (Exception e) {
            throw new IllegalStateException("Signature du code impossible", e);
        }
    }

    // ================================================================= employé

    /** Scan du code par l'employé connecté : arrivée au premier scan, départ ensuite. */
    @Transactional
    public Map<String, Object> scanner(Utilisateur moi, String code) {
        verifierCode(code);
        Employe e = employeDe(moi);
        LocalDateTime maintenant = LocalDateTime.now(ZONE);
        LocalDate jour = maintenant.toLocalDate();
        LocalTime heure = maintenant.toLocalTime().withSecond(0).withNano(0);

        Optional<Pointage> existant = pointageRepository.findByEmployeIdAndDatePointage(e.getId(), jour);
        Map<String, Object> res = new LinkedHashMap<>();
        if (existant.isEmpty()) {
            Pointage p = new Pointage();
            p.setEmployeId(e.getId());
            p.setDatePointage(jour);
            p.setHeureArrivee(heure);
            p.setMethodeArrivee("QR");
            p.setDateModification(maintenant);
            try {
                pointageRepository.saveAndFlush(p);
            } catch (DataIntegrityViolationException doublon) {
                throw new IllegalStateException("Arrivée déjà enregistrée. Réessayez dans un instant.");
            }
            int retard = retardMinutes(heure, reglage());
            res.put("action", "ARRIVEE");
            res.put("message", "Arrivée enregistrée à " + heure.format(HEURE)
                    + (retard > 0 ? " (" + dureeLisible(retard) + " de retard)" : "") + ".");
        } else {
            Pointage p = existant.get();
            LocalTime derniere = p.getHeureDepart() != null ? p.getHeureDepart() : p.getHeureArrivee();
            if (Duration.between(derniere, heure).compareTo(ECART_MINIMUM) < 0) {
                res.put("action", "DEJA_FAIT");
                res.put("message", (p.getHeureDepart() != null ? "Départ" : "Arrivée")
                        + " déjà enregistré(e) à " + derniere.format(HEURE) + ".");
            } else {
                boolean miseAJour = p.getHeureDepart() != null;
                p.setHeureDepart(heure);
                p.setMethodeDepart("QR");
                p.setDateModification(maintenant);
                pointageRepository.save(p);
                res.put("action", "DEPART");
                res.put("message", (miseAJour ? "Départ mis à jour : " : "Départ enregistré à ") + heure.format(HEURE) + ".");
            }
        }
        res.put("aujourdhui", ligne(e, pointageRepository.findByEmployeIdAndDatePointage(e.getId(), jour).orElse(null), reglage(), true));
        return res;
    }

    /** Mes pointages du mois (page « Mon pointage »). */
    public Map<String, Object> mesPointages(Utilisateur moi, YearMonth mois) {
        Map<String, Object> res = new LinkedHashMap<>();
        if (moi.getEmployeId() == null || employeRepository.findById(moi.getEmployeId()).isEmpty()) {
            res.put("relie", false);
            return res;
        }
        Employe e = employeRepository.findById(moi.getEmployeId()).get();
        ParametrePointage reglage = reglage();
        LocalDate aujourdhui = LocalDate.now(ZONE);
        res.put("relie", true);
        res.put("employe", nomComplet(e));
        res.put("aujourdhui", ligne(e, pointageRepository.findByEmployeIdAndDatePointage(e.getId(), aujourdhui).orElse(null), reglage, true));
        res.put("mois", mois.toString());
        res.put("jours", pointageRepository
                .findByEmployeIdAndDatePointageBetweenOrderByDatePointageDesc(e.getId(), mois.atDay(1), mois.atEndOfMonth())
                .stream().map(p -> ligne(e, p, reglage, true)).toList());
        res.put("resume", resumeEmploye(e, mois, reglage,
                pointageRepository.findByEmployeIdAndDatePointageBetweenOrderByDatePointageDesc(e.getId(), mois.atDay(1), mois.atEndOfMonth())));
        return res;
    }

    private Employe employeDe(Utilisateur moi) {
        if (moi.getEmployeId() == null) {
            throw new IllegalStateException("Votre compte n'est pas relié à une fiche employé. "
                    + "Demandez au gérant de cocher « Ce vendeur est aussi employé » sur votre compte.");
        }
        Employe e = employeRepository.findById(moi.getEmployeId())
                .orElseThrow(() -> new IllegalStateException("Votre fiche employé est introuvable. Prévenez le gérant."));
        if (e.getStatut() != StatutEmploye.ACTIF) {
            throw new IllegalStateException("Votre fiche employé est désactivée. Prévenez le gérant.");
        }
        return e;
    }

    // ================================================================= gérant

    /** Tous les employés actifs pour un jour, présents ou non. */
    public Map<String, Object> jour(LocalDate date) {
        ParametrePointage reglage = reglage();
        Map<Long, Pointage> parEmploye = pointageRepository.findByDatePointage(date).stream()
                .collect(Collectors.toMap(Pointage::getEmployeId, Function.identity(), (a, b) -> a));
        Set<Long> avecCompte = employesAvecCompte();
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Employe e : employeRepository.findAllByOrderByNomAsc()) {
            Pointage p = parEmploye.get(e.getId());
            if (p == null && (e.getStatut() != StatutEmploye.ACTIF || avantEmbauche(e, date))) continue;
            Map<String, Object> l = ligne(e, p, reglage, date.equals(LocalDate.now(ZONE)));
            l.put("aCompte", avecCompte.contains(e.getId()));
            lignes.add(l);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("date", date);
        res.put("estJourRepos", estJourRepos(date, reglage));
        res.put("parametres", parametres());
        res.put("employes", lignes);
        res.put("presents", lignes.stream().filter(l -> l.get("pointageId") != null).count());
        res.put("retards", lignes.stream().filter(l -> ((Integer) l.get("retardMinutes")) > 0).count());
        return res;
    }

    /** Saisie ou correction à la main par le gérant (employé sans téléphone, oubli…). */
    @Transactional
    public Map<String, Object> enregistrerManuel(Map<String, Object> corps, Utilisateur gerant) {
        Object idBrut = corps.get("employeId");
        if (idBrut == null) throw new IllegalArgumentException("Choisissez l'employé.");
        Long employeId = Long.valueOf(String.valueOf(idBrut));
        Employe e = employeRepository.findById(employeId)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable."));
        LocalDate date;
        try {
            date = corps.get("date") == null ? LocalDate.now(ZONE) : LocalDate.parse(String.valueOf(corps.get("date")));
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Date invalide.");
        }
        if (date.isAfter(LocalDate.now(ZONE))) throw new IllegalArgumentException("On ne peut pas pointer un jour à venir.");
        LocalTime arrivee = lireHeure(corps.get("heureArrivee") == null ? null : String.valueOf(corps.get("heureArrivee")), "Heure d'arrivée");
        Object departBrut = corps.get("heureDepart");
        LocalTime depart = departBrut == null || String.valueOf(departBrut).isBlank() ? null : lireHeure(String.valueOf(departBrut), "Heure de départ");
        if (depart != null && !depart.isAfter(arrivee)) {
            throw new IllegalArgumentException("L'heure de départ doit être après l'heure d'arrivée.");
        }
        Object obs = corps.get("observation");
        String observation = obs == null ? null : String.valueOf(obs).trim();
        if (observation != null && observation.length() > 255) observation = observation.substring(0, 255);

        Pointage p = pointageRepository.findByEmployeIdAndDatePointage(employeId, date).orElseGet(Pointage::new);
        boolean nouveau = p.getId() == null;
        if (nouveau || !arrivee.equals(p.getHeureArrivee())) p.setMethodeArrivee("MANUEL");
        if (depart != null && !depart.equals(p.getHeureDepart())) p.setMethodeDepart("MANUEL");
        if (depart == null) p.setMethodeDepart(null);
        p.setEmployeId(employeId);
        p.setDatePointage(date);
        p.setHeureArrivee(arrivee);
        p.setHeureDepart(depart);
        p.setObservation(observation == null || observation.isEmpty() ? null : observation);
        p.setSaisiPar(gerant.getNomComplet());
        p.setDateModification(LocalDateTime.now(ZONE));
        pointageRepository.save(p);
        return ligne(e, p, reglage(), date.equals(LocalDate.now(ZONE)));
    }

    @Transactional
    public void supprimer(Long id) {
        if (!pointageRepository.existsById(id)) throw new IllegalArgumentException("Pointage introuvable.");
        pointageRepository.deleteById(id);
    }

    /** Résumé du mois par employé : jours de présence, retards, absences, heures. */
    public Map<String, Object> resumeMois(YearMonth mois) {
        ParametrePointage reglage = reglage();
        Map<Long, List<Pointage>> parEmploye = pointageRepository
                .findByDatePointageBetween(mois.atDay(1), mois.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(Pointage::getEmployeId));
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Employe e : employeRepository.findAllByOrderByNomAsc()) {
            List<Pointage> liste = parEmploye.getOrDefault(e.getId(), List.of());
            if (liste.isEmpty() && e.getStatut() != StatutEmploye.ACTIF) continue;
            lignes.add(resumeEmploye(e, mois, reglage, liste));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("mois", mois.toString());
        res.put("parametres", parametres());
        res.put("employes", lignes);
        return res;
    }

    // ================================================================= calculs

    private Map<String, Object> resumeEmploye(Employe e, YearMonth mois, ParametrePointage reglage, List<Pointage> liste) {
        int retards = 0;
        long minutesRetard = 0;
        long minutesTravail = 0;
        for (Pointage p : liste) {
            int r = retardMinutes(p.getHeureArrivee(), reglage);
            if (r > 0) { retards++; minutesRetard += r; }
            if (p.getHeureDepart() != null) minutesTravail += Duration.between(p.getHeureArrivee(), p.getHeureDepart()).toMinutes();
        }
        Set<LocalDate> joursPointes = liste.stream().map(Pointage::getDatePointage).collect(Collectors.toSet());
        LocalDate debut = mois.atDay(1);
        if (e.getDateEmbauche() != null && e.getDateEmbauche().isAfter(debut)) debut = e.getDateEmbauche();
        LocalDate aujourdhui = LocalDate.now(ZONE);
        // Aujourd'hui n'est compté absent qu'une fois la journée finie.
        LocalDate fin = mois.atEndOfMonth().isBefore(aujourdhui) ? mois.atEndOfMonth() : aujourdhui.minusDays(1);
        int absences = 0;
        if (e.getStatut() == StatutEmploye.ACTIF) {
            for (LocalDate d = debut; !d.isAfter(fin); d = d.plusDays(1)) {
                if (!estJourRepos(d, reglage) && !joursPointes.contains(d)) absences++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeId", e.getId());
        m.put("nom", nomComplet(e));
        m.put("poste", e.getPoste());
        m.put("joursPresence", joursPointes.size());
        m.put("absences", absences);
        m.put("retards", retards);
        m.put("minutesRetard", minutesRetard);
        m.put("minutesTravail", minutesTravail);
        return m;
    }

    private Map<String, Object> ligne(Employe e, Pointage p, ParametrePointage reglage, boolean estAujourdhui) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeId", e.getId());
        m.put("nom", nomComplet(e));
        m.put("poste", e.getPoste());
        m.put("pointageId", p == null ? null : p.getId());
        m.put("date", p == null ? null : p.getDatePointage());
        m.put("heureArrivee", p == null ? null : p.getHeureArrivee().format(HEURE));
        m.put("heureDepart", p == null || p.getHeureDepart() == null ? null : p.getHeureDepart().format(HEURE));
        m.put("methodeArrivee", p == null ? null : p.getMethodeArrivee());
        m.put("methodeDepart", p == null ? null : p.getMethodeDepart());
        m.put("saisiPar", p == null ? null : p.getSaisiPar());
        m.put("observation", p == null ? null : p.getObservation());
        m.put("retardMinutes", p == null ? 0 : retardMinutes(p.getHeureArrivee(), reglage));
        m.put("minutesTravail", p == null || p.getHeureDepart() == null ? null
                : Duration.between(p.getHeureArrivee(), p.getHeureDepart()).toMinutes());
        String statut;
        if (p == null) statut = estAujourdhui ? "PAS_ENCORE" : "ABSENT";
        else if (p.getHeureDepart() != null) statut = "PARTI";
        else statut = "PRESENT";
        m.put("statut", statut);
        return m;
    }

    static int retardMinutes(LocalTime arrivee, ParametrePointage reglage) {
        LocalTime prevue = reglage.getHeureArriveePrevue();
        long minutes = Duration.between(prevue, arrivee).toMinutes();
        return minutes > reglage.getToleranceMinutes() ? (int) minutes : 0;
    }

    private static boolean estJourRepos(LocalDate d, ParametrePointage reglage) {
        return !"AUCUN".equals(reglage.getJourRepos()) && d.getDayOfWeek().name().equals(reglage.getJourRepos());
    }

    private static boolean avantEmbauche(Employe e, LocalDate d) {
        return e.getDateEmbauche() != null && d.isBefore(e.getDateEmbauche());
    }

    private Set<Long> employesAvecCompte() {
        return utilisateurRepository.findAll().stream()
                .filter(u -> u.isActif() && u.getEmployeId() != null)
                .map(Utilisateur::getEmployeId)
                .collect(Collectors.toSet());
    }

    private static String dureeLisible(int minutes) {
        return minutes >= 60 ? (minutes / 60) + " h " + String.format("%02d", minutes % 60) : minutes + " min";
    }

    private static String nomComplet(Employe e) {
        return (e.getPrenom() == null || e.getPrenom().isBlank() ? "" : e.getPrenom().trim() + " ") + e.getNom();
    }

    private static LocalTime lireHeure(String valeur, String champ) {
        if (valeur == null || valeur.isBlank()) throw new IllegalArgumentException(champ + " obligatoire.");
        try {
            return LocalTime.parse(valeur.trim().length() == 5 ? valeur.trim() : valeur.trim().substring(0, 5)).withSecond(0);
        } catch (DateTimeException | StringIndexOutOfBoundsException ex) {
            throw new IllegalArgumentException(champ + " invalide (format HH:MM).");
        }
    }
}
