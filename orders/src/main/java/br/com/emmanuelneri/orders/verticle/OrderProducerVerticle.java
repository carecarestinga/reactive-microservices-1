package br.com.emmanuelneri.orders.verticle;

import br.com.emmanuelneri.orders.infra.Configuration;
import br.com.emmanuelneri.orders.infra.Topic;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static br.com.emmanuelneri.orders.infra.EventBusAddress.RECEIVED_ORDER;

public class OrderProducerVerticle extends AbstractVerticle {

    private final Logger LOGGER = LoggerFactory.getLogger(OrderProducerVerticle.class);
    private static final String NEW_ORDER_TOPIC = Topic.ORDER.getTopic();

    private final Configuration configuration;

    public OrderProducerVerticle(final Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void start(final Future<Void> startFuture) {
        final Map<String, String> config = createKafkaConfig();
        final KafkaProducer<String, String> producer = KafkaProducer.create(vertx, config);

        vertx.eventBus().localConsumer(RECEIVED_ORDER.getAddress(), message -> {
            final String key = UUID.randomUUID().toString();
            final KafkaProducerRecord<String, String> kafkaProducerRecord = KafkaProducerRecord.create(NEW_ORDER_TOPIC, key, (String) message.body());
            producer.send(kafkaProducerRecord);

            LOGGER.info("message produced {0}", kafkaProducerRecord);
        });
    }

    private Map<String, String> createKafkaConfig() {
        final Map<String, String> config = new HashMap<>();
        config.put("bootstrap.servers", configuration.getKafkaBootstrapServers());
        config.put("key.serializer", configuration.getKafkaKeySerializer());
        config.put("value.serializer", configuration.getKafkaValueSerializer());
        return config;
    }
}
