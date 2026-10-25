package ma.uimad.ref.referentiel;

import ma.uimad.ref.referentiel.domain.ReferenceItem;
import ma.uimad.ref.referentiel.domain.ReferenceItemRepository;
import ma.uimad.ref.referentiel.domain.ReferenceType;
import ma.uimad.ref.referentiel.service.ReferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Données de démonstration : passe par le service pour générer historique et événements Kafka. */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final ReferenceItemRepository repository;
    private final ReferenceService service;

    public DemoDataLoader(ReferenceItemRepository repository, ReferenceService service) {
        this.repository = repository;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        validated(ReferenceType.PRODUIT, "PRD-001", "Licence CRM Standard", "Licence annuelle par utilisateur");
        validated(ReferenceType.PRODUIT, "PRD-002", "Module Facturation", "Option de facturation électronique");
        validated(ReferenceType.FOURNISSEUR, "FRN-001", "Atlas Hébergement", "Hébergeur cloud principal");
        service.create(ReferenceType.PRODUIT, "PRD-003", "Pack Support Premium", "En attente de validation", "editeur");
        service.create(ReferenceType.FOURNISSEUR, "FRN-002", "Sahara Télécom", "Fournisseur de connectivité", "editeur");
        ReferenceItem prd1 = repository.findAllByOrderByTypeAscCodeAsc().stream()
                .filter(i -> i.getCode().equals("PRD-001")).findFirst().orElseThrow();
        service.update(prd1.getId(), "Licence CRM Standard 2026", "Tarif révisé, en attente de revalidation", "editeur");
        log.info("Données de démonstration chargées");
    }

    private void validated(ReferenceType type, String code, String label, String description) {
        ReferenceItem item = service.create(type, code, label, description, "editeur");
        service.validate(item.getId(), "validateur");
    }
}
