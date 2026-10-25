package ma.uimad.ref.referentiel.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Comptes de démonstration. En production : annuaire ou fournisseur d'identité externe. */
@ConfigurationProperties(prefix = "app.security")
public record DemoUsersProperties(List<DemoUser> users) {
    public record DemoUser(String username, String password, List<String> roles) { }
}
