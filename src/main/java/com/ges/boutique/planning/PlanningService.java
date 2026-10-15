package com.ges.boutique.planning;

import com.ges.boutique.employe.Employe;
import com.ges.boutique.employe.EmployeRepository;
import com.ges.boutique.employe.StatutEmploye;
import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.FeatureToggleService;
import com.ges.boutique.utilisateur.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Planning de l'équipe : pour chaque employé et chaque jour, ses horaires ou son repos.
 * Quand la fonctionnalité est active, le pointage compte les retards à partir de l'heure
 * de début du planning, et ne compte pas d'absence un jour de repos.
 */
@Service
@RequiredArgsConstructor
public class PlanningService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Bamako");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final CreneauPlanningRepository repository;
    private final EmployeRepository employeRepository;
    private final FeatureToggleService featureToggleService;

    /** Lundi de la semaine qui contient ce jour. */
    public static LocalDate lundi(LocalDate jour) {
        return jour.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // ================================================================= pour le pointage

    /** Créneaux par employé et par jour sur une période (vide si la fonctionnalité est coupée). */
    public Map<Long, Map<LocalDate, CreneauPlanning>> creneaux(LocalDate debut, LocalDate fin) {
        if (!featureToggleService.estActive(CleFonctionnalite.PLANNING)) return Map.of();
        Map<Long, Map<LocalDate, CreneauPlanning>> res = new HashMap<>();
        for (CreneauPlanning c : repository.findByDateJourBetween(debut, fin)) {
            res.computeIfAbsent(c.getEmployeId(), k -> new HashMap<>()).put(c.getDateJour(), c);
        }
        return res;
    }

    public CreneauPlanning creneau(Long employeId, LocalDate jour) {
        if (!featureToggleService.estActive(CleFonctionnalite.PLANNING)) return null;
        return repository.findByEmployeIdAndDateJour(employeId, jour).orElse(null);
    }

    // ================================================================= gérant

    /** La semaine (lundi → dimanche) de tous les employés actifs. */
    public Map<String, Object> semaine(LocalDate jour) {
        LocalDate debut = lundi(jour);
        LocalDate fin = debut.plusDays(6);
        Map<Long, Map<LocalDate, CreneauPlanning>> parEmploye = new HashMap<>();
        for (CreneauPlanning c : repository.findByDateJourBetween(debut, fin)) {
            parEmploye.computeIfAbsent(c.getEmployeId(), k -> new HashMap<>()).put(c.getDateJour(), c);
        }
        List<Map<String, Object>> employes = new ArrayList<>();
        for (Employe e : employeRepository.findAllByOrderByNomAsc()) {
            Map<LocalDate, CreneauPlanning> siens = parEmploye.getOrDefault(e.getId(), Map.of());
            if (e.getStatut() != StatutEmploye.ACTIF && siens.isEmpty()) continue;
            employes.add(ligneEmploye(e, debut, siens));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("debut", debut);
        res.put("fin", fin);
        res.put("jours", jours(debut));
        res.put("employes", employes);
        return res;
    }

    /**
     * Enregistre des cases du planning. Une case sans horaire et sans repos est effacée.
     * Chaque case : employeId, date, heureDebut, heureFin, repos, note.
     */
    @Transactional
    public Map<String, Object> enregistrer(List<Map<String, Object>> cellules) {
        if (cellules == null || cellules.isEmpty()) throw new IllegalArgumentException("Rien à enregistrer.");
        if (cellules.size() > 500) throw new IllegalArgumentException("Trop de cases d'un coup.");
        int enregistrees = 0;
        int effacees = 0;
        LocalDate premier = null;
        for (Map<String, Object> cel : cellules) {
            Long employeId = lireLong(cel.get("employeId"));
            if (employeId == null || employeRepository.findById(employeId).isEmpty()) {
                throw new IllegalArgumentException("Employé introuvable.");
            }
            LocalDate date = lireDate(cel.get("date"));
            boolean repos = Boolean.parseBoolean(String.valueOf(cel.get("repos")));
            LocalTime debut = lireHeure(cel.get("heureDebut"));
            LocalTime fin = lireHeure(cel.get("heureFin"));
            Optional<CreneauPlanning> existant = repository.findByEmployeIdAndDateJour(employeId, date);

            if (!repos && debut == null && fin == null) {
                if (existant.isPresent()) { repository.delete(existant.get()); effacees++; }
                continue;
            }
            if (!repos) {
                if (debut == null || fin == null) throw new IllegalArgumentException("Indiquez l'heure de début et de fin (" + date + ").");
                if (!fin.isAfter(debut)) throw new IllegalArgumentException("L'heure de fin doit être après l'heure de début (" + date + ").");
            }
            CreneauPlanning c = existant.orElseGet(CreneauPlanning::new);
            c.setEmployeId(employeId);
            c.setDateJour(date);
            c.setRepos(repos);
            c.setHeureDebut(repos ? null : debut);
            c.setHeureFin(repos ? null : fin);
            Object note = cel.get("note");
            String n = note == null ? null : String.valueOf(note).trim();
            c.setNote(n == null || n.isEmpty() ? null : (n.length() > 120 ? n.substring(0, 120) : n));
            repository.save(c);
            enregistrees++;
            if (premier == null) premier = date;
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("enregistrees", enregistrees);
        res.put("effacees", effacees);
        return res;
    }

    /** Recopie une semaine sur une autre (sans toucher aux cases déjà remplies, sauf si écraser). */
    @Transactional
    public Map<String, Object> copierSemaine(LocalDate depuis, LocalDate vers, boolean ecraser) {
        LocalDate source = lundi(depuis);
        LocalDate cible = lundi(vers);
        if (source.equals(cible)) throw new IllegalArgumentException("Choisissez une autre semaine.");
        long decalage = Duration.between(source.atStartOfDay(), cible.atStartOfDay()).toDays();
        Set<Long> actifs = employeRepository.findByStatutOrderByNomAsc(StatutEmploye.ACTIF).stream()
                .map(Employe::getId).collect(Collectors.toSet());
        int copiees = 0;
        int gardees = 0;
        for (CreneauPlanning s : repository.findByDateJourBetween(source, source.plusDays(6))) {
            if (!actifs.contains(s.getEmployeId())) continue;
            LocalDate date = s.getDateJour().plusDays(decalage);
            Optional<CreneauPlanning> existant = repository.findByEmployeIdAndDateJour(s.getEmployeId(), date);
            if (existant.isPresent() && !ecraser) { gardees++; continue; }
            CreneauPlanning c = existant.orElseGet(CreneauPlanning::new);
            c.setEmployeId(s.getEmployeId());
            c.setDateJour(date);
            c.setRepos(s.getRepos());
            c.setHeureDebut(s.getHeureDebut());
            c.setHeureFin(s.getHeureFin());
            c.setNote(s.getNote());
            repository.save(c);
            copiees++;
        }
        if (copiees == 0 && gardees == 0) throw new IllegalArgumentException("La semaine à recopier est vide.");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("copiees", copiees);
        res.put("gardees", gardees);
        return res;
    }

    // ================================================================= employé

    public Map<String, Object> maSemaine(Utilisateur moi, LocalDate jour) {
        LocalDate debut = lundi(jour);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("debut", debut);
        res.put("fin", debut.plusDays(6));
        res.put("jours", jours(debut));
        if (moi.getEmployeId() == null || employeRepository.findById(moi.getEmployeId()).isEmpty()) {
            res.put("relie", false);
            return res;
        }
        Employe e = employeRepository.findById(moi.getEmployeId()).get();
        Map<LocalDate, CreneauPlanning> siens = repository
                .findByEmployeIdAndDateJourBetweenOrderByDateJourAsc(e.getId(), debut, debut.plusDays(6)).stream()
                .collect(Collectors.toMap(CreneauPlanning::getDateJour, c -> c));
        res.put("relie", true);
        res.put("moi", ligneEmploye(e, debut, siens));
        return res;
    }

    // ================================================================= outils

    private Map<String, Object> ligneEmploye(Employe e, LocalDate debut, Map<LocalDate, CreneauPlanning> siens) {
        List<Map<String, Object>> cases = new ArrayList<>();
        long minutes = 0;
        int joursTravail = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = debut.plusDays(i);
            CreneauPlanning c = siens.get(d);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d);
            m.put("repos", c != null && Boolean.TRUE.equals(c.getRepos()));
            m.put("heureDebut", c == null || c.getHeureDebut() == null ? null : c.getHeureDebut().format(HEURE));
            m.put("heureFin", c == null || c.getHeureFin() == null ? null : c.getHeureFin().format(HEURE));
            m.put("note", c == null ? null : c.getNote());
            m.put("rempli", c != null);
            if (c != null && !Boolean.TRUE.equals(c.getRepos()) && c.getHeureDebut() != null && c.getHeureFin() != null) {
                minutes += Duration.between(c.getHeureDebut(), c.getHeureFin()).toMinutes();
                joursTravail++;
            }
            cases.add(m);
        }
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("employeId", e.getId());
        l.put("nom", (e.getPrenom() == null || e.getPrenom().isBlank() ? "" : e.getPrenom().trim() + " ") + e.getNom());
        l.put("poste", e.getPoste());
        l.put("cases", cases);
        l.put("minutesSemaine", minutes);
        l.put("joursTravail", joursTravail);
        return l;
    }

    private static List<Map<String, Object>> jours(LocalDate debut) {
        String[] noms = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};
        LocalDate aujourdhui = LocalDate.now(ZONE);
        List<Map<String, Object>> res = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = debut.plusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d);
            m.put("nom", noms[i]);
            m.put("aujourdhui", d.equals(aujourdhui));
            res.add(m);
        }
        return res;
    }

    private static Long lireLong(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try { return (long) Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate lireDate(Object v) {
        try {
            return LocalDate.parse(String.valueOf(v).trim().substring(0, 10));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Date invalide.");
        }
    }

    private static LocalTime lireHeure(Object v) {
        if (v == null || String.valueOf(v).isBlank() || "null".equals(String.valueOf(v))) return null;
        try {
            return LocalTime.parse(String.valueOf(v).trim().substring(0, 5));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Heure invalide (format HH:MM).");
        }
    }
}
