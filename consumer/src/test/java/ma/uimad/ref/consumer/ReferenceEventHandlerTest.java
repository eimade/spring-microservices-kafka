package ma.uimad.ref.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReferenceEventHandlerTest {

    private ReferenceViewRepository repository;
    private ReferenceEventHandler handler;

    @BeforeEach
    void setUp() {
        repository = mock(ReferenceViewRepository.class);
        handler = new ReferenceEventHandler(repository);
    }

    private ReferenceEvent event(String action, int version) {
        return new ReferenceEvent(action, "PRODUIT", "PRD-1", "Libellé", "desc", version, "validateur", Instant.now());
    }

    @Test
    void validationCreeLaVue() {
        when(repository.findById("PRODUIT:PRD-1")).thenReturn(Optional.empty());
        handler.apply(event("VALIDATED", 1));
        verify(repository).save(any(ReferenceView.class));
    }

    @Test
    void versionPerimeeEstIgnoree() {
        ReferenceView current = ReferenceView.from(event("VALIDATED", 3));
        when(repository.findById("PRODUIT:PRD-1")).thenReturn(Optional.of(current));
        handler.apply(event("VALIDATED", 2));
        verify(repository, never()).save(any());
    }

    @Test
    void memeVersionRejoueeEstIdempotente() {
        ReferenceView current = ReferenceView.from(event("VALIDATED", 2));
        when(repository.findById("PRODUIT:PRD-1")).thenReturn(Optional.of(current));
        handler.apply(event("VALIDATED", 2));
        verify(repository, times(1)).save(any(ReferenceView.class));
    }

    @Test
    void suppressionRetireLaVue() {
        handler.apply(event("DELETED", 1));
        verify(repository).deleteById("PRODUIT:PRD-1");
    }

    @Test
    void actionInconnueEstIgnoree() {
        handler.apply(event("AUTRE", 1));
        verifyNoInteractions(repository);
    }
}
