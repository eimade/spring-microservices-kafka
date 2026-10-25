package ma.uimad.ref.referentiel.service;

import java.time.Instant;
import java.util.List;
import ma.uimad.ref.referentiel.domain.*;
import ma.uimad.ref.referentiel.messaging.ReferenceEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReferenceService {

    private final ReferenceItemRepository items;
    private final ReferenceVersionRepository versions;
    private final ApplicationEventPublisher events;

    public ReferenceService(ReferenceItemRepository items, ReferenceVersionRepository versions,
                            ApplicationEventPublisher events) {
        this.items = items;
        this.versions = versions;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ReferenceItem> list(ReferenceType type) {
        return type == null ? items.findAllByOrderByTypeAscCodeAsc() : items.findByTypeOrderByCodeAsc(type);
    }

    @Transactional(readOnly = true)
    public ReferenceItem get(Long id) {
        return items.findById(id).orElseThrow(() -> new NotFoundException("Référence introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public List<ReferenceVersion> history(Long id) {
        get(id);
        return versions.findByReferenceIdOrderByIdAsc(id);
    }

    public ReferenceItem create(ReferenceType type, String code, String label, String description, String user) {
        String normalized = code.trim().toUpperCase();
        if (items.existsByTypeAndCode(type, normalized)) {
            throw new ConflictException("Le code " + normalized + " existe déjà pour le type " + type);
        }
        ReferenceItem item = items.save(new ReferenceItem(type, normalized, label.trim(), description, user));
        versions.save(new ReferenceVersion(item, "CREATED"));
        return item;
    }

    public ReferenceItem update(Long id, String label, String description, String user) {
        ReferenceItem item = get(id);
        item.edit(label.trim(), description, user);
        versions.save(new ReferenceVersion(item, "UPDATED"));
        return item;
    }

    public ReferenceItem validate(Long id, String user) {
        ReferenceItem item = get(id);
        try {
            item.validate(user);
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        versions.save(new ReferenceVersion(item, "VALIDATED"));
        events.publishEvent(event("VALIDATED", item, user));
        return item;
    }

    public void delete(Long id, String user) {
        ReferenceItem item = get(id);
        items.delete(item);
        versions.deleteByReferenceId(id);
        if (item.isPublished()) {
            events.publishEvent(event("DELETED", item, user));
        }
    }

    private ReferenceEvent event(String action, ReferenceItem i, String user) {
        return new ReferenceEvent(action, i.getType().name(), i.getCode(), i.getLabel(), i.getDescription(),
                i.getVersion(), user, Instant.now());
    }
}
