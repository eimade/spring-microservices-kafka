package ma.uimad.ref.referentiel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReferentielApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReferentielApplication.class, args);
    }
}
