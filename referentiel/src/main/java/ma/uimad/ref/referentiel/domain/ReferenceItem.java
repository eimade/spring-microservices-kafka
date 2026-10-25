package ma.uimad.ref.referentiel.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reference_item")
public class ReferenceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferenceType type;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferenceStatus status;

    @Column(nullable = false)
    private int version;

    /** Vrai si une version validée a déjà été diffusée aux consommateurs. */
    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    protected ReferenceItem() { }

    public ReferenceItem(ReferenceType type, String code, String label, String description, String user) {
        this.type = type;
        this.code = code;
        this.label = label;
        this.description = description;
        this.status = ReferenceStatus.DRAFT;
        this.version = 1;
        touch(user);
    }

    /** Modification : une référence validée repasse en brouillon avec une nouvelle version. */
    public void edit(String label, String description, String user) {
        this.label = label;
        this.description = description;
        if (status == ReferenceStatus.VALIDATED) {
            this.status = ReferenceStatus.DRAFT;
            this.version++;
        }
        touch(user);
    }

    public void validate(String user) {
        if (status == ReferenceStatus.VALIDATED) {
            throw new IllegalStateException("La référence est déjà validée");
        }
        this.status = ReferenceStatus.VALIDATED;
        this.published = true;
        touch(user);
    }

    private void touch(String user) {
        this.updatedAt = Instant.now();
        this.updatedBy = user;
    }

    public Long getId() { return id; }
    public ReferenceType getType() { return type; }
    public String getCode() { return code; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public ReferenceStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public boolean isPublished() { return published; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
