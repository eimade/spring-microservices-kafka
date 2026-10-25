package ma.uimad.ref.referentiel.messaging;

import java.time.Instant;

/** Message diffusé sur Kafka (topic reference-changes). Contrat partagé avec le service consumer. */
public record ReferenceEvent(
        String action,        // VALIDATED | DELETED
        String type,
        String code,
        String label,
        String description,
        int version,
        String actor,
        Instant occurredAt) {
}
