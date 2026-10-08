package com.ges.boutique.produit;

import com.ges.boutique.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UniteVenteServiceImpl implements UniteVenteService {

    private final UniteVenteRepository uniteVenteRepository;
    private final ProduitRepository produitRepository;

    @Override
    public List<UniteVente> lister(Long produitId) {
        return uniteVenteRepository.findByProduitIdOrderByOrdreAsc(produitId);
    }

    @Override
    public UniteVente creer(Long produitId, UniteVenteRequest request) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé: " + produitId));
        if (produit.getModeMesure() != null) {
            throw new IllegalStateException("Ce produit est vendu au " + produit.getModeMesure().getUnite()
                    + " : il ne peut pas avoir d'unités de vente.");
        }

        if (request.getNom() == null || request.getNom().isBlank()) {
            throw new IllegalArgumentException("Le nom de l'unité est requis");
        }
        if (request.getPrixVente() == null || request.getPrixVente() < 0) {
            throw new IllegalArgumentException("Le prix de vente est requis");
        }

        UniteVente unite = new UniteVente();
        unite.setProduit(produit);
        unite.setNom(request.getNom().trim());
        unite.setFacteurBase(resoudreFacteurBase(request));
        unite.setPrixVente(request.getPrixVente());
        unite.setPrixAchat(request.getPrixAchat());
        unite.setOrdre(request.getOrdre() != null ? request.getOrdre() : lister(produitId).size());

        return uniteVenteRepository.save(unite);
    }

    @Override
    public UniteVente modifier(Long id, UniteVenteRequest request) {
        UniteVente unite = uniteVenteRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Unité de vente non trouvée: " + id));

        if (request.getNom() != null && !request.getNom().isBlank()) unite.setNom(request.getNom().trim());
        if (request.getPrixVente() != null) unite.setPrixVente(request.getPrixVente());
        if (request.getPrixAchat() != null) unite.setPrixAchat(request.getPrixAchat());
        if (request.getOrdre() != null) unite.setOrdre(request.getOrdre());
        if (request.getFacteurBase() != null || request.getUniteReferenceId() != null) {
            unite.setFacteurBase(resoudreFacteurBase(request));
        }

        return uniteVenteRepository.save(unite);
    }

    @Override
    public void supprimer(Long id) {
        if (!uniteVenteRepository.existsById(id)) {
            throw new RessourceIntrouvableException("Unité de vente non trouvée: " + id);
        }
        uniteVenteRepository.deleteById(id);
    }

    /**
     * Résout le facteur de conversion vers l'unité de base — soit fourni directement,
     * soit calculé relativement à une autre unité déjà enregistrée (ex: "1 Carton = 5
     * Cartouches", la Cartouche valant déjà 10 Pièces => 50 stocké pour le Carton).
     * L'admin entre les niveaux comme il les pense naturellement ; ce calcul ne se
     * fait qu'une fois ici, jamais au moment de la vente.
     */
    private Integer resoudreFacteurBase(UniteVenteRequest request) {
        if (request.getFacteurBase() != null && request.getFacteurBase() > 0) {
            return request.getFacteurBase();
        }
        if (request.getFacteurRelatif() == null || request.getFacteurRelatif() <= 0) {
            throw new IllegalArgumentException("Le facteur de conversion est requis");
        }
        if (request.getUniteReferenceId() == null) {
            return request.getFacteurRelatif();
        }
        UniteVente reference = uniteVenteRepository.findById(request.getUniteReferenceId())
                .orElseThrow(() -> new RessourceIntrouvableException("Unité de référence non trouvée: " + request.getUniteReferenceId()));
        return request.getFacteurRelatif() * reference.getFacteurBase();
    }
}
