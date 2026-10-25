package ma.uimad.ref.referentiel.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceItemRepository extends JpaRepository<ReferenceItem, Long> {
    boolean existsByTypeAndCode(ReferenceType type, String code);
    List<ReferenceItem> findByTypeOrderByCodeAsc(ReferenceType type);
    List<ReferenceItem> findAllByOrderByTypeAscCodeAsc();
}
