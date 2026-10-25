package ma.uimad.ref.consumer;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Vue lecture dénormalisée : une référence validée. */
@Document("reference_view")
public record ReferenceView(@Id String id, @Indexed String type, String code, String label, String description,
                            int version, String status, String validatedBy, Instant validatedAt) {

    public static ReferenceView from(ReferenceEvent e) {
        return new ReferenceView(e.type() + ":" + e.code(), e.type(), e.code(), e.label(), e.description(),
                e.version(), "VALIDATED", e.actor(), e.occurredAt());
    }
}
