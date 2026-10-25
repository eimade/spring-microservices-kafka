package ma.uimad.ref.referentiel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Test de bout en bout : API sécurisée + PostgreSQL (Flyway) + publication Kafka. */
@SpringBootTest(properties = "app.seed.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class ReferentielIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String login(String user, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", user, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    @Test
    void accesSansJetonRefuse() throws Exception {
        mvc.perform(get("/api/references")).andExpect(status().isUnauthorized());
    }

    @Test
    void mauvaisMotDePasseRefuse() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"editeur\",\"password\":\"faux\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void donneesInvalidesRetournent400() throws Exception {
        mvc.perform(post("/api/references").header("Authorization", login("editeur", "editeur123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PRODUIT\",\"code\":\"bad code!\",\"label\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.label").exists())
                .andExpect(jsonPath("$.errors.code").exists());
    }

    @Test
    void workflowCompletCreationValidationHistoriqueEtEvenementKafka() throws Exception {
        String code = "IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String editor = login("editeur", "editeur123");
        String validator = login("validateur", "validateur123");

        // Création en brouillon par l'éditeur
        String created = mvc.perform(post("/api/references").header("Authorization", editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", "PRODUIT", "code", code, "label", "Produit test"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        // Code déjà utilisé : conflit
        mvc.perform(post("/api/references").header("Authorization", editor).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", "PRODUIT", "code", code, "label", "Doublon"))))
                .andExpect(status().isConflict());

        // L'éditeur n'a pas le droit de valider
        mvc.perform(post("/api/references/" + id + "/validate").header("Authorization", editor))
                .andExpect(status().isForbidden());

        // Le validateur valide : événement Kafka attendu
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of("reference-changes"));
            consumer.poll(Duration.ofMillis(500));
            mvc.perform(post("/api/references/" + id + "/validate").header("Authorization", validator))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATED"));

            JsonNode event = awaitEvent(consumer, code);
            assertThat(event.get("action").asText()).isEqualTo("VALIDATED");
            assertThat(event.get("actor").asText()).isEqualTo("validateur");
            assertThat(event.get("version").asInt()).isEqualTo(1);
        }

        // Modification : repasse en brouillon, version 2
        mvc.perform(put("/api/references/" + id).header("Authorization", editor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Produit test v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(2));

        // Historique : création, validation, modification
        mvc.perform(get("/api/references/" + id + "/history").header("Authorization", validator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[1].action").value("VALIDATED"))
                .andExpect(jsonPath("$[2].action").value("UPDATED"));

        // Suppression
        mvc.perform(delete("/api/references/" + id).header("Authorization", editor)).andExpect(status().isNoContent());
        mvc.perform(get("/api/references/" + id).header("Authorization", editor)).andExpect(status().isNotFound());
    }

    private KafkaConsumer<String, String> newConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    private JsonNode awaitEvent(KafkaConsumer<String, String> consumer, String code) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (record.key().equals("PRODUIT:" + code)) {
                    return json.readTree(record.value());
                }
            }
        }
        throw new AssertionError("Aucun événement Kafka reçu pour " + code);
    }
}
