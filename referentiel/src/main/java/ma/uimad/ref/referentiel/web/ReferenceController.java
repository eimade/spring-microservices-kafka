package ma.uimad.ref.referentiel.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import ma.uimad.ref.referentiel.domain.ReferenceType;
import ma.uimad.ref.referentiel.service.ReferenceService;
import ma.uimad.ref.referentiel.web.ReferenceDtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/references")
public class ReferenceController {

    private final ReferenceService service;

    public ReferenceController(ReferenceService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReferenceResponse> list(@RequestParam(required = false) ReferenceType type) {
        return service.list(type).stream().map(ReferenceResponse::of).toList();
    }

    @GetMapping("/{id}")
    public ReferenceResponse get(@PathVariable Long id) {
        return ReferenceResponse.of(service.get(id));
    }

    @GetMapping("/{id}/history")
    public List<VersionResponse> history(@PathVariable Long id) {
        return service.history(id).stream().map(VersionResponse::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ReferenceResponse> create(@Valid @RequestBody CreateRequest req, Authentication auth) {
        var item = service.create(req.type(), req.code(), req.label(), req.description(), auth.getName());
        return ResponseEntity.created(URI.create("/api/references/" + item.getId())).body(ReferenceResponse.of(item));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    public ReferenceResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest req, Authentication auth) {
        return ReferenceResponse.of(service.update(id, req.label(), req.description(), auth.getName()));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasRole('VALIDATOR')")
    public ReferenceResponse validate(@PathVariable Long id, Authentication auth) {
        return ReferenceResponse.of(service.validate(id, auth.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        service.delete(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
