package ma.uimad.ref.consumer;

import java.time.Instant;

/** Contrat du message publié par le service referentiel sur le topic reference-changes. */
public record ReferenceEvent(String action, String type, String code, String label, String description,
                             int version, String actor, Instant occurredAt) { }
