package ma.uimad.ref.consumer;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReferenceViewRepository extends MongoRepository<ReferenceView, String> {
    List<ReferenceView> findByTypeOrderByCodeAsc(String type);
}
