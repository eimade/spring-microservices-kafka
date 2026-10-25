package ma.uimad.ref.referentiel.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Ligne d'historique : un instantané à chaque création, modification ou validation. */
@Entity
@Table(name = "reference_version")
public class ReferenceVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferenceStatus status;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ReferenceVersion() { }

    public ReferenceVersion(ReferenceItem item, String action) {
        this.referenceId = item.getId();
        this.version = item.getVersion();
        this.action = action;
        this.label = item.getLabel();
        this.description = item.getDescription();
        this.status = item.getStatus();
        this.changedBy = item.getUpdatedBy();
        this.changedAt = item.getUpdatedAt();
    }

    public Long getId() { return id; }
    public Long getReferenceId() { return referenceId; }
    public int getVersion() { return version; }
    public String getAction() { return action; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public ReferenceStatus getStatus() { return status; }
    public String getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
