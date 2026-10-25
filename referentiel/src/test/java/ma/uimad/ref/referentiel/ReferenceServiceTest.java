package ma.uimad.ref.referentiel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import ma.uimad.ref.referentiel.domain.*;
import ma.uimad.ref.referentiel.messaging.ReferenceEvent;
import ma.uimad.ref.referentiel.service.ConflictException;
import ma.uimad.ref.referentiel.service.NotFoundException;
import ma.uimad.ref.referentiel.service.ReferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ReferenceServiceTest {

    private ReferenceItemRepository items;
    private ReferenceVersionRepository versions;
    private ApplicationEventPublisher events;
    private ReferenceService service;

    @BeforeEach
    void setUp() {
        items = mock(ReferenceItemRepository.class);
        versions = mock(ReferenceVersionRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new ReferenceService(items, versions, events);
        when(items.save(any(ReferenceItem.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void creationNormaliseLeCodeEtEnregistreLHistorique() {
        ReferenceItem created = service.create(ReferenceType.PRODUIT, " prd-9 ", "Produit", null, "editeur");
        assertThat(created.getCode()).isEqualTo("PRD-9");
        verify(versions).save(any(ReferenceVersion.class));
        verifyNoInteractions(events);
    }

    @Test
    void creationAvecCodeExistantEstEnConflit() {
        when(items.existsByTypeAndCode(ReferenceType.PRODUIT, "PRD-9")).thenReturn(true);
        assertThatThrownBy(() -> service.create(ReferenceType.PRODUIT, "prd-9", "Produit", null, "editeur"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void validationPubliePuisUnEvenement() {
        ReferenceItem item = new ReferenceItem(ReferenceType.FOURNISSEUR, "FRN-1", "Fournisseur", null, "editeur");
        when(items.findById(1L)).thenReturn(Optional.of(item));

        service.validate(1L, "validateur");

        ArgumentCaptor<ReferenceEvent> captor = ArgumentCaptor.forClass(ReferenceEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("VALIDATED");
        assertThat(captor.getValue().code()).isEqualTo("FRN-1");
        assertThat(captor.getValue().actor()).isEqualTo("validateur");
    }

    @Test
    void validerUneReferenceDejaValideeEstEnConflit() {
        ReferenceItem item = new ReferenceItem(ReferenceType.FOURNISSEUR, "FRN-1", "Fournisseur", null, "editeur");
        item.validate("validateur");
        when(items.findById(1L)).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.validate(1L, "validateur")).isInstanceOf(ConflictException.class);
    }

    @Test
    void suppressionDUnBrouillonNePublieRien() {
        ReferenceItem item = new ReferenceItem(ReferenceType.PRODUIT, "PRD-1", "Produit", null, "editeur");
        when(items.findById(1L)).thenReturn(Optional.of(item));
        service.delete(1L, "editeur");
        verify(items).delete(item);
        verifyNoInteractions(events);
    }

    @Test
    void suppressionDUneReferencePublieeEmetUnEvenementDeSuppression() {
        ReferenceItem item = new ReferenceItem(ReferenceType.PRODUIT, "PRD-1", "Produit", null, "editeur");
        item.validate("validateur");
        when(items.findById(1L)).thenReturn(Optional.of(item));
        service.delete(1L, "editeur");
        ArgumentCaptor<ReferenceEvent> captor = ArgumentCaptor.forClass(ReferenceEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("DELETED");
    }

    @Test
    void referenceInconnueLeveNotFound() {
        when(items.findById(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(NotFoundException.class);
    }
}
