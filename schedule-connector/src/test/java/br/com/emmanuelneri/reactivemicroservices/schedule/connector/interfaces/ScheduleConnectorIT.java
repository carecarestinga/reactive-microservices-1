package br.com.emmanuelneri.reactivemicroservices.schedule.connector.interfaces;

import br.com.emmanuelneri.reactivemicroservices.config.KafkaConsumerConfiguration;
import br.com.emmanuelneri.reactivemicroservices.config.KafkaProducerConfiguration;
import br.com.emmanuelneri.reactivemicroservices.mapper.JsonConfiguration;
import br.com.emmanuelneri.reactivemicroservices.schedule.schema.CustomerScheduleSchema;
import br.com.emmanuelneri.reactivemicroservices.schedule.schema.ScheduleEndpointSchema;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.KafkaContainer;

import java.time.LocalDateTime;
import java.util.Map;

@RunWith(VertxUnitRunner.class)
public class ScheduleConnectorIT {

    private static final int PORT = 8888;
    private static final String HOST = "localhost";
    private static final String URI = "/schedules";

    private Vertx vertx;

    @Rule
    public KafkaContainer kafka = new KafkaContainer("5.2.1");
    private JsonObject configuration;

    @Before
    public void before() {
        configuration = new JsonObject()
                .put("kafka.bootstrap.servers", kafka.getBootstrapServers())
                .put("kafka.key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
                .put("kafka.value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
                .put("kafka.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
                .put("kafka.value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
                .put("kafka.offset.reset", "earliest");

        this.vertx = Vertx.vertx();
        JsonConfiguration.setUpDefault();
    }

    @After
    public void after() {
        this.vertx.close();
    }

    @Test
    public void shouldProcessSchedule(final TestContext context) {
        final CustomerScheduleSchema customerSchema = new CustomerScheduleSchema();
        customerSchema.setDocumentNumber("948948393849");
        customerSchema.setName("Customer 1");
        customerSchema.setPhone("4499099493");

        final ScheduleEndpointSchema schedule = new ScheduleEndpointSchema();
        schedule.setCustomer(customerSchema);
        schedule.setDateTime(LocalDateTime.now().plusDays(1));
        schedule.setDescription("Complete Test");

        final KafkaProducerConfiguration kafkaProducerConfiguration = new KafkaProducerConfiguration(configuration);
        final Router router = Router.router(vertx);

        this.vertx.deployVerticle(new ScheduleProcessor());
        this.vertx.deployVerticle(new ScheduleProducer(kafkaProducerConfiguration));
        this.vertx.deployVerticle(new ScheduleEndpoint((router)));

        final Map<String, String> kafkaConsumerConfiguration = new KafkaConsumerConfiguration(configuration).createConfig("test-schedule-consumer");
        final KafkaConsumer<String, String> kafkaConsumer = KafkaConsumer.create(this.vertx, kafkaConsumerConfiguration);
        kafkaConsumer.subscribe(ScheduleProducer.SCHEDULE_REQUEST_TOPIC);

        final WebClient client = WebClient.create(this.vertx);
        final HttpServer httpServer = this.vertx.createHttpServer();

        final Async async = context.async();
        httpServer.requestHandler(router)
                .listen(PORT, serverAsyncResult -> {
                    if (serverAsyncResult.failed()) {
                        context.fail(serverAsyncResult.cause());
                    }

                    client.post(PORT, HOST, URI)
                            .sendJson(schedule, clientAsyncResult -> {
                                if (clientAsyncResult.failed()) {
                                    context.fail(clientAsyncResult.cause());
                                }

                                final HttpResponse<Buffer> result = clientAsyncResult.result();
                                context.assertEquals(201, result.statusCode());

                                kafkaConsumer.handler(consumerRecord -> {
                                    context.assertNotNull(consumerRecord.key());
                                    context.assertEquals(Json.encode(schedule), consumerRecord.value());

                                    httpServer.close();
                                    async.complete();
                                });
                            });
                });
    }
}