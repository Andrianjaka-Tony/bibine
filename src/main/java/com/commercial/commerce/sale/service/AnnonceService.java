package com.commercial.commerce.sale.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.naming.CommunicationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;
import com.commercial.commerce.UserAuth.Models.User;
import com.commercial.commerce.UserAuth.Service.AuthService;
import com.commercial.commerce.chat.model.JsonResponse;
import com.commercial.commerce.chat.service.FileHelper;
import com.commercial.commerce.sale.entity.AnnonceEntity;
import com.commercial.commerce.sale.entity.CategoryEntity;
import com.commercial.commerce.sale.entity.CouleurEntity;
import com.commercial.commerce.sale.entity.MakeEntity;
import com.commercial.commerce.sale.entity.ModelEntity;
import com.commercial.commerce.sale.entity.TypeEntity;
import com.commercial.commerce.sale.repository.AnnonceRepository;
import com.commercial.commerce.sale.repository.AnnonceRepositoryImpl;
import com.commercial.commerce.sale.utils.CommissionSummary;
import com.commercial.commerce.sale.utils.Parameter;
import com.commercial.commerce.sale.utils.Statistique;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnnonceService {
    @Autowired
    private AnnonceRepository annonceRepository;
    @Autowired
    private AuthService authService;
    @Autowired
    private AnnonceRepositoryImpl annonceRepositoryImpl;

    @Autowired
    private MongoTemplate mongoTemplate;

    public List<AnnonceEntity> recherchePleinTexte(String termeRecherche) {
        Criteria criteria = new Criteria();
        criteria.orOperator(
                Criteria.where("modele.brand.nom").regex(termeRecherche, "i"),
                Criteria.where("modele.nom").regex(termeRecherche, "i"),
                Criteria.where("maintenance.nom").regex(termeRecherche, "i"),
                Criteria.where("modele.type.nom").regex(termeRecherche, "i"),
                Criteria.where("description").regex(termeRecherche, "i"));

        Query query = new Query(criteria).with(Sort.by(Sort.Order.desc("score")));
        return mongoTemplate.find(query, AnnonceEntity.class);
    }

    public List<AnnonceEntity> getAllEntity() {
        List<AnnonceEntity> annonces = annonceRepository.findAllByState(1);
        User user = null;
        for (AnnonceEntity annonceEntity : annonces) {
            user = authService.findById(annonceEntity.getVendeur().getIdvendeur()).get();
            annonceEntity.getVendeur().setNom(user.getName());
            annonceEntity.getVendeur().setProfile(user.getProfile());
        }
        return annonces;
    }

    public List<AnnonceEntity> getAllEntityPaginer(int offset, int limit) {
        List<AnnonceEntity> annonces = annonceRepository.findAllByState(1, PageRequest.of(offset, limit)).getContent();
        User user = null;
        for (AnnonceEntity annonceEntity : annonces) {
            user = authService.findById(annonceEntity.getVendeur().getIdvendeur()).get();
            annonceEntity.getVendeur().setNom(user.getName());
            annonceEntity.getVendeur().setProfile(user.getProfile());
        }
        return annonces;
    }

    public List<AnnonceEntity> getAllWithPagination(int offset, int limit) {
        PageRequest pageRequest = PageRequest.of(offset, limit);
        Page<AnnonceEntity> annonces = annonceRepository.findAll(pageRequest);
        User user = null;
        for (AnnonceEntity annonceEntity : annonces) {
            user = authService.findById(annonceEntity.getVendeur().getIdvendeur()).get();
            annonceEntity.getVendeur().setNom(user.getName());
            annonceEntity.getVendeur().setProfile(user.getProfile());
        }
        return annonces.getContent();
    }

    public List<AnnonceEntity> getAllWithPaginationNo(int offset, int limit) {
        PageRequest pageRequest = PageRequest.of(offset, limit);
        Page<AnnonceEntity> annonces = annonceRepository.findAllNo(pageRequest);
        User user = null;
        for (AnnonceEntity annonceEntity : annonces) {
            user = authService.findById(annonceEntity.getVendeur().getIdvendeur()).get();
            annonceEntity.getVendeur().setNom(user.getName());
            annonceEntity.getVendeur().setProfile(user.getProfile());
        }
        return annonces.getContent();
    }

    public Long countSoldCarsByModelId(ModelEntity model) {
        return annonceRepository.countSoldCarsByModelId(model.getId());

    }

    public AnnonceEntity insert(AnnonceEntity annonce) throws Exception {
        FileHelper file = new FileHelper();

        List<String> pictures = new ArrayList<String>();
        if (annonce.getPictures() == null || annonce.getPictures().isEmpty()) {
            throw new Exception("Announcement needs pictures");

        }
        for (String fileBase64 : annonce.getPictures()) {
            JsonResponse json = file.uploadOnline(fileBase64);
            pictures.add(json.getData().getUrl());
        }
        annonce.setPictures(pictures);
        return annonceRepository.save(annonce);
    }

    public AnnonceEntity addFavoris(Long id, String idannonce) {

        AnnonceEntity annonce = annonceRepository.findById(idannonce).orElse(null);
        if (annonce != null) {
            annonce.getFavoris().add(id);
            Set<Long> uniqueFavoris = new HashSet<>(annonce.getFavoris());
            annonce.getFavoris().clear();
            annonce.getFavoris().addAll(uniqueFavoris);
            annonceRepository.save(annonce);
        }
        return annonce;
    }

    public Optional<AnnonceEntity> validate(String id, int com) {

        this.updateAnnonceState(id, 1);
        this.updateAnnonceCommission(id, com);
        return annonceRepository.findById(id);
    }

    public Optional<AnnonceEntity> validate(String id) {

        this.updateAnnonceValidity(id, 0);
        return annonceRepository.findById(id);
    }

    public Optional<AnnonceEntity> unvalidate(String id) {

        this.updateAnnonceValidity(id, 1);
        return annonceRepository.findById(id);
    }

    public long pagination(String type, int limit) {
        long number = annonceRepository.countByModeleTypeId(type);
        return (number + limit - 1) / limit;
    }

    public AnnonceEntity removeFavoris(Long user, String annonceId) {
        AnnonceEntity annonce = annonceRepository.findById(annonceId).orElse(null);

        if (annonce != null) {
            annonce.getFavoris().remove(user);
            annonceRepository.save(annonce);

        }
        return annonce;
    }

    public List<AnnonceEntity> getAnnoncesByFavoris(Long user) {
        List<AnnonceEntity> annonce = annonceRepository.findByFavorisAndState(user, 1);
        return annonce;
    }

    public List<AnnonceEntity> getAnnoncesByFavoris(Long user, int offset, int limit) {
        List<AnnonceEntity> annonce = annonceRepository.findByFavorisAndState(user, 1, PageRequest.of(offset, limit))
                .getContent();
        return annonce;
    }

    public Long getAnnoncesByFavorisCount(Long user) {

        return annonceRepository.countByFavoris(user);
    }

    public List<AnnonceEntity> getAnnoncesByVendeur(Long user, int offset, int limit) {
        List<AnnonceEntity> annonce = annonceRepository
                .findByVendeurIdvendeurAndState(user, 1, PageRequest.of(offset, limit)).getContent();
        User userEntity = null;
        for (AnnonceEntity annonceEntity : annonce) {
            userEntity = authService.findById(annonceEntity.getVendeur().getIdvendeur()).get();
            annonceEntity.getVendeur().setNom(userEntity.getName());
            annonceEntity.getVendeur().setProfile(userEntity.getProfile());
        }
        return annonce;
    }

    public List<AnnonceEntity> getAnnoncesByVendeur(Long user) {
        List<AnnonceEntity> annonce = annonceRepository
                .findByVendeurIdvendeurAndState(user, 1);
        User userEntity = null;
        for (AnnonceEntity annonceEntity : annonce) {
            userEntity = authService.findById(annonceEntity.getVendeur().getIdvendeur()).get();
            annonceEntity.getVendeur().setNom(userEntity.getName());
            annonceEntity.getVendeur().setProfile(userEntity.getProfile());
        }
        return annonce;
    }

    public List<AnnonceEntity> getAnnoncesByVendeurPaginer(Long idVendeur, int state, int offset, int limit) {
        return annonceRepository.findAllByVendeur_IdvendeurAndState(idVendeur, state, PageRequest.of(offset, limit))
                .getContent();
    }

    public Long getAnnoncesByVendeurPage(Long iduser, int limit) {
        Long count = annonceRepository.countByVendeur_Idvendeur(iduser);
        return (count + limit - 1) / limit;
    }

    public Long getFavorisPage(Long iduser, int limit) {
        Long count = annonceRepository.countByFavoris(iduser);
        return (count + limit - 1) / limit;
    }

    public Long getAnnoncesByVendeurPage(Long iduser, int state, int limit) {
        Long count = annonceRepository.countByVendeur_IdvendeurAndState(iduser, state);
        return (count + limit - 1) / limit;
    }

    public List<AnnonceEntity> getByType(String type, int offset, int limit) {
        List<AnnonceEntity> annonce = annonceRepository
                .findByModeleTypeIdAndState(type, 1, PageRequest.of(offset, limit)).getContent();
        return annonce;
    }

    public List<AnnonceEntity> getRecentAnnonces(int offset, int limit) {
        int status = 1;
        Pageable pageable = PageRequest.of(offset, limit, Sort.by(Sort.Direction.DESC, "date"));
        List<AnnonceEntity> annonce = annonceRepository.findAllByState(status, pageable).getContent();
        User userEntity = null;
        for (AnnonceEntity annonceEntity : annonce) {
            userEntity = authService.findById(annonceEntity.getVendeur().getIdvendeur()).get();
            annonceEntity.getVendeur().setNom(userEntity.getName());
            annonceEntity.getVendeur().setProfile(userEntity.getProfile());
        }
        return annonce;
    }

    public AnnonceEntity getById(String id) {
        AnnonceEntity annonceEntity = annonceRepository.findById(id).get();
        User user = null;
        user = authService.findById(annonceEntity.getVendeur().getIdvendeur()).get();
        annonceEntity.getVendeur().setNom(user.getName());
        annonceEntity.getVendeur().setProfile(user.getProfile());
        return annonceEntity;

    }

    public List<AnnonceEntity> getBetween(double inf, double sup) {
        return annonceRepository.findByPrixBetween(inf, sup);
    }

    public List<AnnonceEntity> getInf(double inf) {
        return annonceRepository.findByPrixLessThanEqual(inf);
    }

    public List<AnnonceEntity> getSup(double sup) {
        return annonceRepository.findByPrixGreaterThanEqual(sup);
    }

    public List<AnnonceEntity> getEtatBetween(double inf, double sup) {
        return annonceRepository.findByEtatBetween(inf, sup);
    }

    public List<AnnonceEntity> getEtatInf(double inf) {
        return annonceRepository.findByEtatLessThanEqual(inf);
    }

    public List<AnnonceEntity> getEtatSup(double sup) {
        return annonceRepository.findByEtatGreaterThanEqual(sup);
    }

    public List<AnnonceEntity> getBrand(List<MakeEntity> brand) {
        String[] idbrand = new String[brand.size()];
        for (int i = 0; i < brand.size(); i++) {
            idbrand[i] = brand.get(i).getId();
        }
        return annonceRepository.findAllByBrand_IdIn(idbrand);
    }

    public List<AnnonceEntity> getType(List<TypeEntity> brand) {
        String[] idbrand = new String[brand.size()];
        for (int i = 0; i < brand.size(); i++) {
            idbrand[i] = brand.get(i).getId();
        }
        return annonceRepository.findAllByModele_Type_IdIn(idbrand);
    }

    public List<AnnonceEntity> getCouleur(List<CouleurEntity> brand) {
        String[] idbrand = new String[brand.size()];
        for (int i = 0; i < brand.size(); i++) {
            idbrand[i] = brand.get(i).getId();
        }
        return annonceRepository.findAllByCouleur_IdIn(idbrand);
    }

    public List<AnnonceEntity> getModel(List<ModelEntity> brand) {
        String[] idbrand = new String[brand.size()];
        for (int i = 0; i < brand.size(); i++) {
            idbrand[i] = brand.get(i).getId();
        }
        return annonceRepository.findAllByModele_IdIn(idbrand);
    }

    public List<AnnonceEntity> getBetweenDate(LocalDateTime inf, LocalDateTime sup) {
        return annonceRepository.findByDateBetween(inf, sup);
    }

    public List<AnnonceEntity> getInfDate(LocalDateTime inf) {
        return annonceRepository.findByDateLessThanEqual(inf);
    }

    public List<AnnonceEntity> getSupDate(LocalDateTime sup) {
        return annonceRepository.findByDateGreaterThanEqual(sup);
    }

    public List<AnnonceEntity> research(Parameter parametre) {
        List<AnnonceEntity> entity0 = annonceRepository.findAll();

        List<AnnonceEntity> entity1 = null;
        List<AnnonceEntity> entity = null;
        if (parametre.getDescription() != null && parametre.getDescription() != "") {
            // entity = annonceRepository
            // .findByDescriptionContainingIgnoreCase(parametre.getDescription());
            entity = this.recherchePleinTexte(parametre.getDescription());

        }
        if (parametre.getDateInf() != null && parametre.getDateSup() != null) {
            entity1 = this.getBetweenDate(parametre.getDateInf(), parametre.getDateSup());
        }
        List<AnnonceEntity> entity11 = null;
        if (parametre.getDateInf() != null && parametre.getDateSup() == null) {
            entity11 = this.getSupDate(parametre.getDateInf());
        }
        List<AnnonceEntity> entity12 = null;
        if (parametre.getDateInf() == null && parametre.getDateSup() != null) {
            entity12 = this.getInfDate(parametre.getDateSup());
        }
        List<AnnonceEntity> entity2 = null;
        if (parametre.getInfMontant() != null && parametre.getSupMontant() != null) {
            entity2 = this.getBetween(parametre.getInfMontant(), parametre.getSupMontant());
        }
        List<AnnonceEntity> entity21 = null;
        if (parametre.getInfMontant() != null && parametre.getSupMontant() == null) {
            entity21 = this.getSup(parametre.getInfMontant());
        }
        List<AnnonceEntity> entity22 = null;
        if (parametre.getInfMontant() == null && parametre.getSupMontant() != null) {
            entity22 = this.getInf(parametre.getSupMontant());
        }
        List<AnnonceEntity> entity3 = null;
        if (parametre.getBrands() != null && parametre.getBrands().size() > 0) {
            entity3 = this.getBrand(parametre.getBrands());
        }
        List<AnnonceEntity> entity4 = null;
        if (parametre.getModeles() != null && parametre.getModeles().size() > 0) {
            entity4 = this.getModel(parametre.getModeles());
        }
        List<AnnonceEntity> entity5 = null;
        if (parametre.getInfEtat() != null && parametre.getSupEtat() != null) {
            entity1 = this.getEtatBetween(parametre.getInfEtat(), parametre.getSupEtat());
        }
        List<AnnonceEntity> entity51 = null;
        if (parametre.getInfEtat() != null && parametre.getSupEtat() == null) {
            entity11 = this.getEtatSup(parametre.getInfEtat());
        }
        List<AnnonceEntity> entity52 = null;
        if (parametre.getInfEtat() == null && parametre.getSupEtat() != null) {
            entity12 = this.getEtatInf(parametre.getSupEtat());
        }
        List<AnnonceEntity> entity6 = null;
        if (parametre.getCouleurs() != null && parametre.getCouleurs().size() > 0) {
            entity4 = this.getCouleur(parametre.getCouleurs());
        }
        List<AnnonceEntity> entity7 = null;
        if (parametre.getTypes() != null && parametre.getTypes().size() > 0) {
            entity7 = this.getType(parametre.getTypes());
        }
        List<AnnonceEntity> list = this.intersect(entity1, entity2);
        list = this.intersect(entity0, list);
        list = this.intersect(entity11, list);
        list = this.intersect(entity12, list);
        list = this.intersect(entity21, list);
        list = this.intersect(entity22, list);
        list = this.intersect(entity3, list);
        list = this.intersect(list, entity4);
        list = this.intersect(entity5, list);
        list = this.intersect(entity51, list);
        list = this.intersect(entity52, list);
        list = this.intersect(entity6, list);
        list = this.intersect(entity7, list);
        list = this.intersect(entity, list);
        return list;
    }

    public Optional<AnnonceEntity> updateAnnonceState(String id, int newState) {
        Optional<AnnonceEntity> existingAnnonce = annonceRepository.findById(id);

        if (existingAnnonce.isPresent()) {
            AnnonceEntity updatedAnnonce = existingAnnonce.get();
            updatedAnnonce.setState(newState);

            return Optional.of(annonceRepository.save(updatedAnnonce));
        } else {
            return Optional.empty();
        }
    }

    public Optional<AnnonceEntity> updateAnnonceValidity(String id, int newState) {
        Optional<AnnonceEntity> existingAnnonce = annonceRepository.findById(id);

        if (existingAnnonce.isPresent()) {
            AnnonceEntity updatedAnnonce = existingAnnonce.get();
            updatedAnnonce.setValidity(newState);

            return Optional.of(annonceRepository.save(updatedAnnonce));
        } else {
            return Optional.empty();
        }
    }

    public Optional<AnnonceEntity> updateAnnonceCommission(String id, int newState) {
        Optional<AnnonceEntity> existingAnnonce = annonceRepository.findById(id);

        if (existingAnnonce.isPresent()) {
            AnnonceEntity updatedAnnonce = existingAnnonce.get();
            updatedAnnonce.setCommission(newState);

            return Optional.of(annonceRepository.save(updatedAnnonce));
        } else {
            return Optional.empty();
        }
    }

    public List<AnnonceEntity> getAnnoncesByState(int state) {
        List<AnnonceEntity> annonces = annonceRepository.findAllByState(state);
        User user = null;
        for (AnnonceEntity annonceEntity : annonces) {
            user = authService.findById(annonceEntity.getVendeur().getIdvendeur()).get();
            annonceEntity.getVendeur().setNom(user.getName());
            annonceEntity.getVendeur().setProfile(user.getProfile());
        }
        return annonces;
    }

    public Page<AnnonceEntity> getAnnoncesByStatePaginer(Long id, int state, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AnnonceEntity> annonces = annonceRepository.findAllByVendeur_IdvendeurAndState(id, state, pageable);
        User user = null;
        for (AnnonceEntity annonceEntity : annonces) {
            user = authService.findById(annonceEntity.getVendeur().getIdvendeur()).get();
            annonceEntity.getVendeur().setNom(user.getName());
            annonceEntity.getVendeur().setProfile(user.getProfile());
        }
        return annonces;
    }

    public Long getAnnoncesByVendeurCount(Long idVendeur, int state) {
        return annonceRepository.countByVendeur_IdvendeurAndState(idVendeur, state);
    }

    public Long getAnnoncesByVendeurCount(Long idVendeur) {
        return annonceRepository.countByVendeur_Idvendeur(idVendeur);
    }

    public List<AnnonceEntity> intersect(List<AnnonceEntity> list1, List<AnnonceEntity> list2) {

        List<AnnonceEntity> list = new ArrayList<>();
        if (list1 != null && list2 != null) {
            for (int i = 0; i < list1.size(); i++) {
                for (int u = 0; u < list2.size(); u++) {
                    if (list1.get(i).getId().compareToIgnoreCase(list2.get(u).getId()) == 0) {
                        list.add(list1.get(i));
                        break;
                    }
                }
            }
            return list;
        }
        if (list1 != null && list2 == null) {
            return list1;
        }
        if (list2 != null && list1 == null) {
            return list2;
        }
        return null;
    }

    public long pagination(int limit) {
        long number = annonceRepository.count();
        return (number + limit - 1) / limit;
    }

    public long paginationValid(int limit) {
        long number = annonceRepository.countByState(1);
        return (number + limit - 1) / limit;
    }

    public List<Statistique> countAllByModele() {
        return annonceRepositoryImpl.countAllByModele();
    }

    public List<Statistique> countAllByType() {
        return annonceRepositoryImpl.countAllByType();
    }

    public List<Statistique> getBestVenteType() {
        return annonceRepositoryImpl.getBestVenteType();
    }

    public List<Statistique> countAllByBrand() {
        return annonceRepositoryImpl.countAllByBrand();
    }

    public List<Statistique> getBestVenteBrand() {
        return annonceRepositoryImpl.getBestVenteBrand();
    }

    public List<Statistique> getBestVenteModel() {
        return annonceRepositoryImpl.getBestVenteModel();
    }

    public double getCommission() {
        return annonceRepositoryImpl.sumOfCommissions();
    }

    public double getSolde(Long id) {
        User user = authService.findById(id).get();
        if (user.getCompte() == null) {
            return 0;
        }
        return user.getCompte();
    }
}
