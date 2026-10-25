package ma.uimad.ref.referentiel.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Bean
    NewTopic referenceChangesTopic(@Value("${app.kafka.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }
}
