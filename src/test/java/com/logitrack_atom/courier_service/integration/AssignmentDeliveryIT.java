package com.logitrack_atom.courier_service.integration;

import static org.assertj.core.api.Assertions.*;

import com.logitrack_atom.courier_service.assignment.*;
import com.logitrack_atom.courier_service.entity.Repartidor;
import com.logitrack_atom.courier_service.repository.RepartidorRepository;
import com.logitrack_atom.courier_service.support.SecurityAccountStub;
import com.logitrack_atom.courier_service.support.TestRsaKeys;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.ObjectMapper;

/** Entrega real: corte de conexión antes del ACK, DLQ y reconexión automática con permisos Courier. */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "logitrack.assignment.enabled=true", "logitrack.assignment.publisher-enabled=false", "logitrack.assignment.consumer-enabled=false"})
class AssignmentDeliveryIT {
    private static final String VHOST = "logitrack-s04", QUEUE = "lt.courier.assignment.v1";
    private static final String OPERATOR_PASSWORD = UUID.randomUUID().toString(), COURIER_PASSWORD = UUID.randomUUID().toString();
    private static final int PORT = freePort();
    private static final GenericContainer<?> BROKER = new GenericContainer<>("rabbitmq:4.4.0-management@sha256:af6f50d38098d8bd52fef7f3fa85015dd8b2bf6def58e995bf584ae9ba0f6407")
            .withEnv("RABBITMQ_DEFAULT_USER", "lt_operator").withEnv("RABBITMQ_DEFAULT_PASS", OPERATOR_PASSWORD)
            .withEnv("RABBITMQ_DEFAULT_VHOST", VHOST).withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS", "+S 2:2")
            .withExposedPorts(5672).withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                    new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1", PORT), com.github.dockerjava.api.model.ExposedPort.tcp(5672))));
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("courier_assignment_test").withUsername("courier_test").withPassword(UUID.randomUUID().toString()).withUrlParam("serverTimezone", "UTC")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/mysql/init/001-schema.sql").toAbsolutePath()), "/docker-entrypoint-initdb.d/001-schema.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/mysql/mq/004-assignment-inbox-outbox.sql").toAbsolutePath()), "/docker-entrypoint-initdb.d/004-assignment.sql");
    static {
        BROKER.start(); MYSQL.start();
        try {
            assertThat(BROKER.execInContainer("rabbitmqctl", "add_user", "lt_courier", COURIER_PASSWORD).getExitCode()).isZero();
            assertThat(BROKER.execInContainer("rabbitmqctl", "set_permissions", "-p", VHOST, "lt_courier", "^$",
                    "^lt\\.assignment\\.(results|retry)\\.v1$", "^lt\\.courier\\.assignment\\.v1$").getExitCode()).isZero();
            try (var connection = operator(); var channel = connection.createChannel()) {
                for (String exchange : new String[]{"commands", "results", "retry", "dead"}) channel.exchangeDeclare("lt.assignment." + exchange + ".v1", "direct", true);
                channel.queueDeclare(QUEUE, true, false, false, Map.of("x-queue-type", "quorum", "x-dead-letter-exchange", "lt.assignment.dead.v1", "x-dead-letter-routing-key", "courier.dead"));
                channel.queueDeclare("lt.shipment.assignment.v1", true, false, false, Map.of("x-queue-type", "quorum"));
                channel.queueDeclare("lt.courier.assignment.dead.v1", true, false, false, Map.of("x-queue-type", "quorum"));
                channel.queueDeclare("lt.courier.assignment.retry.v1", true, false, false, Map.of("x-queue-type", "quorum", "x-message-ttl", 5000,
                        "x-dead-letter-exchange", "lt.assignment.commands.v1", "x-dead-letter-routing-key", "availability.request"));
                channel.queueBind(QUEUE, "lt.assignment.commands.v1", "availability.request");
                channel.queueBind("lt.shipment.assignment.v1", "lt.assignment.results.v1", "availability.result");
                channel.queueBind("lt.courier.assignment.retry.v1", "lt.assignment.retry.v1", "courier.retry");
                channel.queueBind("lt.courier.assignment.dead.v1", "lt.assignment.dead.v1", "courier.dead");
            }
        } catch (Exception error) { throw new IllegalStateException("No se pudo preparar el broker aislado de prueba"); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        TestRsaKeys.registrar(properties);
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("clients.security.base-url", SecurityAccountStub::baseUrl);
        properties.add("MQ_HOST", BROKER::getHost); properties.add("MQ_PORT", () -> PORT);
        properties.add("MQ_VHOST", () -> VHOST);properties.add("MQ_COURIER_USER", () -> "lt_courier");
        properties.add("MQ_COURIER_PASSWORD", () -> COURIER_PASSWORD);
    }

    @Autowired AssignmentListener listener;
    @Autowired AssignmentMessagingConfig config;
    @Autowired OutboxStore outbox;
    @Autowired CachingConnectionFactory assignmentConnectionFactory;
    @Autowired RabbitTemplate assignmentRabbitTemplate;
    @Autowired JdbcTemplate jdbc;
    @Autowired RepartidorRepository repository;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int httpPort;

    @Test
    void lostAckRedeliversWithoutDuplicateAndConsumerRecoversAfterRestart() throws Exception {
        UUID rider = repository.saveAndFlush(new Repartidor("Fixture", "Moto")).getId();
        var body = AssignmentFixtures.body(rider);
        publish(AssignmentFixtures.message(body, mapper));
        var cutConnection = operator();
        var cutChannel = cutConnection.createChannel();
        try {
            GetResponse delivered = receive(cutChannel, QUEUE, false);
            Channel cut = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[]{Channel.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("basicAck")) { cutConnection.abort(); throw new IOException("fixture: conexión cortada antes del ACK"); }
                try { return method.invoke(cutChannel, arguments); }
                catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
            });
            assertThatThrownBy(() -> listener.onMessage(message(delivered), cut)).isInstanceOf(IOException.class);
        } finally {
            if (cutChannel.isOpen()) cutChannel.close();
            if (cutConnection.isOpen()) cutConnection.close();
        }
        assertThat(count("assignment_inbox")).isEqualTo(1);
        String resultId = jdbc.queryForObject("SELECT HEX(result_message_id) FROM assignment_inbox", String.class);
        try (var connection = operator(); var channel = connection.createChannel()) {
            var redelivered = receive(channel, QUEUE, false);
            assertThat(redelivered.getEnvelope().isRedeliver()).isTrue();
            listener.onMessage(message(redelivered), channel);
        }
        assertThat(count("assignment_inbox")).isEqualTo(1);assertThat(count("amqp_outbox")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT HEX(result_message_id) FROM assignment_inbox", String.class)).isEqualTo(resultId);
        var invalid = AssignmentFixtures.body(rider);invalid.put("unexpected", true);
        publish(AssignmentFixtures.message(invalid, mapper));
        try (var connection = operator(); var channel = connection.createChannel()) {
            listener.onMessage(message(receive(channel, QUEUE, false)), channel);
            assertThat(receive(channel, "lt.courier.assignment.dead.v1", true).getProps().getHeaders()).containsKey("x-death");
        }
        assertThat(count("assignment_inbox")).isEqualTo(1);
        var container = config.assignmentListenerContainer(assignmentConnectionFactory, listener, outbox);
        container.afterPropertiesSet();container.start();
        try {
            BROKER.getDockerClient().stopContainerCmd(BROKER.getContainerId()).withTimeout(2).exec();
            var health = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + httpPort + "/actuator/health")).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            BROKER.getDockerClient().startContainerCmd(BROKER.getContainerId()).exec();
            waitForBroker();
            publish(AssignmentFixtures.message(AssignmentFixtures.body(rider), mapper));
            long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
            while (count("assignment_inbox") < 2 && System.nanoTime() < deadline) Thread.sleep(200);
            assertThat(count("assignment_inbox")).isEqualTo(2);
            new AssignmentPublisher(outbox, assignmentRabbitTemplate).publishPending();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM amqp_outbox WHERE published_at IS NULL", Integer.class)).isZero();
            try (var connection = operator(); var channel = connection.createChannel()) {
                for (int n = 0; n < 2; n++) {
                    var result = receive(channel, "lt.shipment.assignment.v1", true);
                    Map<?, ?> parsed = mapper.readValue(result.getBody(), Map.class);
                    assertThat(parsed.get("availability")).isEqualTo("AVAILABLE");
                    assertThat(parsed.get("type")).isEqualTo("assignment.availability.checked.v1");
                    assertThat(result.getProps().getCorrelationId()).isEqualTo(parsed.get("operationId"));
                }
            }
        } finally { container.stop(); }
    }

    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private static int freePort() { try (var socket = new java.net.ServerSocket(0)) { return socket.getLocalPort(); } catch (IOException error) { throw new IllegalStateException("Puerto de prueba no disponible"); } }
    private static com.rabbitmq.client.Connection operator() throws Exception {
        var factory = new com.rabbitmq.client.ConnectionFactory();factory.setHost(BROKER.getHost());factory.setPort(PORT);
        factory.setVirtualHost(VHOST);factory.setUsername("lt_operator");factory.setPassword(OPERATOR_PASSWORD);factory.setConnectionTimeout(2000);
        factory.setAutomaticRecoveryEnabled(false);return factory.newConnection();
    }
    private static void publish(Message message) throws Exception {
        try (var connection = operator(); var channel = connection.createChannel()) {
            channel.confirmSelect();
            var p = message.getMessageProperties();
            channel.basicPublish("lt.assignment.commands.v1", "availability.request", true,
                    new com.rabbitmq.client.AMQP.BasicProperties.Builder().contentType("application/json").deliveryMode(2).messageId(p.getMessageId()).correlationId(p.getCorrelationId()).build(), message.getBody());
            channel.waitForConfirmsOrDie(5000);
        }
    }
    private static GetResponse receive(Channel channel, String queue, boolean ack) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) { var response = channel.basicGet(queue, ack); if (response != null) return response;Thread.sleep(100); }
        throw new AssertionError("No llegó el mensaje de prueba dentro del límite");
    }
    private static Message message(GetResponse response) {
        return new Message(response.getBody(), new DefaultMessagePropertiesConverter().toMessageProperties(response.getProps(), response.getEnvelope(), "UTF-8"));
    }
    private static void waitForBroker() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) { try (var connection = operator()) { return; } catch (Exception unavailable) { Thread.sleep(1000); } }
        throw new AssertionError("Broker no recuperado dentro del límite");
    }
}
