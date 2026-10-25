package ma.uimad.ref.referentiel.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceVersionRepository extends JpaRepository<ReferenceVersion, Long> {
    List<ReferenceVersion> findByReferenceIdOrderByIdAsc(Long referenceId);
    void deleteByReferenceId(Long referenceId);
}
