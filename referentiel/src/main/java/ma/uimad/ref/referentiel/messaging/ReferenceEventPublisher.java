package ma.uimad.ref.referentiel.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Publie sur Kafka uniquement après le commit de la transaction base de données. */
@Component
public class ReferenceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReferenceEventPublisher.class);

    private final KafkaTemplate<String, ReferenceEvent> kafka;
    private final String topic;

    public ReferenceEventPublisher(KafkaTemplate<String, ReferenceEvent> kafka,
                                   @Value("${app.kafka.topic}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvent(ReferenceEvent event) {
        kafka.send(topic, event.type() + ":" + event.code(), event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("Échec de publication Kafka pour {}:{}", event.type(), event.code(), ex);
                    } else {
                        log.info("Événement {} publié pour {}:{} v{}", event.action(), event.type(), event.code(), event.version());
                    }
                });
    }
}
