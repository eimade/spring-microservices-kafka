package ma.uimad.ref.consumer;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/read/references")
public class ReadController {

    private final ReferenceViewRepository repository;

    public ReadController(ReferenceViewRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ReferenceView> list(@RequestParam(required = false) String type) {
        return type == null ? repository.findAll(Sort.by("type", "code"))
                : repository.findByTypeOrderByCodeAsc(type.toUpperCase());
    }

    @GetMapping("/{type}/{code}")
    public ReferenceView get(@PathVariable String type, @PathVariable String code) {
        return repository.findById(type.toUpperCase() + ":" + code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Référence introuvable"));
    }
}
