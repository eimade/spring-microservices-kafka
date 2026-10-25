package ma.uimad.ref.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Test de bout en bout : message Kafka JSON -> vue MongoDB -> API de lecture sécurisée. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired MockMvc mvc;
    @Autowired ReferenceViewRepository repository;
    @Value("${app.jwt.secret}") String secret;

    private String token() {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject("validateur").issuedAt(now).expiresAt(now.plusSeconds(600)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private void publish(String json) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>("reference-changes", "PRODUIT:IT-1", json)).get();
        }
    }

    private String event(String action, int version, String label) {
        return """
            {"action":"%s","type":"PRODUIT","code":"IT-1","label":"%s","description":"d","version":%d,
             "actor":"validateur","occurredAt":"2026-01-01T10:00:00Z"}""".formatted(action, label, version);
    }

    @Test
    void lectureSansJetonRefusee() throws Exception {
        mvc.perform(get("/api/read/references")).andExpect(status().isUnauthorized());
    }

    @Test
    void evenementKafkaAlimenteLaVueLecturePuisSuppression() throws Exception {
        publish(event("VALIDATED", 1, "Premier"));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(repository.findById("PRODUIT:IT-1")).isPresent());

        publish(event("VALIDATED", 2, "Second"));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(repository.findById("PRODUIT:IT-1").orElseThrow().version()).isEqualTo(2));

        mvc.perform(get("/api/read/references/produit/it-1").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Second"))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
        mvc.perform(get("/api/read/references?type=PRODUIT").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("IT-1"));

        publish(event("DELETED", 2, "Second"));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(repository.findById("PRODUIT:IT-1")).isEmpty());
        mvc.perform(get("/api/read/references/PRODUIT/IT-1").header("Authorization", token()))
                .andExpect(status().isNotFound());
    }
}
