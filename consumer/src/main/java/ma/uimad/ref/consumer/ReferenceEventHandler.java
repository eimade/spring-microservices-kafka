package ma.uimad.ref.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Applique les événements Kafka à la vue lecture MongoDB (idempotent, ignore les versions périmées). */
@Component
public class ReferenceEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ReferenceEventHandler.class);

    private final ReferenceViewRepository repository;

    public ReferenceEventHandler(ReferenceViewRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "${app.kafka.topic}")
    public void onMessage(ReferenceEvent event) {
        apply(event);
    }

    void apply(ReferenceEvent event) {
        ReferenceView incoming = ReferenceView.from(event);
        if ("DELETED".equals(event.action())) {
            repository.deleteById(incoming.id());
            log.info("Vue supprimée : {}", incoming.id());
            return;
        }
        if (!"VALIDATED".equals(event.action())) {
            log.warn("Action inconnue ignorée : {}", event.action());
            return;
        }
        boolean stale = repository.findById(incoming.id())
                .map(current -> current.version() > event.version()).orElse(false);
        if (stale) {
            log.info("Version périmée ignorée : {} v{}", incoming.id(), event.version());
            return;
        }
        repository.save(incoming);
        log.info("Vue mise à jour : {} v{}", incoming.id(), event.version());
    }
}
