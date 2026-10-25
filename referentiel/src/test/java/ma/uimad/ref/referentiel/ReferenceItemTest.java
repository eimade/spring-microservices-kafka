package ma.uimad.ref.referentiel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ma.uimad.ref.referentiel.domain.ReferenceItem;
import ma.uimad.ref.referentiel.domain.ReferenceStatus;
import ma.uimad.ref.referentiel.domain.ReferenceType;
import org.junit.jupiter.api.Test;

class ReferenceItemTest {

    private ReferenceItem draft() {
        return new ReferenceItem(ReferenceType.PRODUIT, "PRD-1", "Libellé", "desc", "editeur");
    }

    @Test
    void nouvelleReferenceEstUnBrouillonVersion1() {
        ReferenceItem item = draft();
        assertThat(item.getStatus()).isEqualTo(ReferenceStatus.DRAFT);
        assertThat(item.getVersion()).isEqualTo(1);
        assertThat(item.isPublished()).isFalse();
    }

    @Test
    void validationPasseEnValideEtMarquePubliee() {
        ReferenceItem item = draft();
        item.validate("validateur");
        assertThat(item.getStatus()).isEqualTo(ReferenceStatus.VALIDATED);
        assertThat(item.isPublished()).isTrue();
        assertThat(item.getUpdatedBy()).isEqualTo("validateur");
    }

    @Test
    void validerDeuxFoisEstRefuse() {
        ReferenceItem item = draft();
        item.validate("validateur");
        assertThatThrownBy(() -> item.validate("validateur")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void modifierUneReferenceValideeCreeUneNouvelleVersionEnBrouillon() {
        ReferenceItem item = draft();
        item.validate("validateur");
        item.edit("Nouveau", "autre", "editeur");
        assertThat(item.getStatus()).isEqualTo(ReferenceStatus.DRAFT);
        assertThat(item.getVersion()).isEqualTo(2);
        assertThat(item.getLabel()).isEqualTo("Nouveau");
    }

    @Test
    void modifierUnBrouillonGardeLaMemeVersion() {
        ReferenceItem item = draft();
        item.edit("Nouveau", "autre", "editeur");
        assertThat(item.getVersion()).isEqualTo(1);
    }
}
