package ma.uimad.ref.referentiel.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import ma.uimad.ref.referentiel.domain.*;

public final class ReferenceDtos {
    private ReferenceDtos() { }

    public record CreateRequest(
            @NotNull ReferenceType type,
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "lettres, chiffres, - et _ uniquement") String code,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 1000) String description) { }

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String label,
            @Size(max = 1000) String description) { }

    public record ReferenceResponse(Long id, ReferenceType type, String code, String label, String description,
                                    ReferenceStatus status, int version, Instant updatedAt, String updatedBy) {
        public static ReferenceResponse of(ReferenceItem i) {
            return new ReferenceResponse(i.getId(), i.getType(), i.getCode(), i.getLabel(), i.getDescription(),
                    i.getStatus(), i.getVersion(), i.getUpdatedAt(), i.getUpdatedBy());
        }
    }

    public record VersionResponse(int version, String action, String label, String description,
                                  ReferenceStatus status, String changedBy, Instant changedAt) {
        public static VersionResponse of(ReferenceVersion v) {
            return new VersionResponse(v.getVersion(), v.getAction(), v.getLabel(), v.getDescription(),
                    v.getStatus(), v.getChangedBy(), v.getChangedAt());
        }
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }

    public record TokenResponse(String token, String username, List<String> roles, long expiresInSeconds) { }
}
