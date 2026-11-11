package com.logitrack_atom.courier_service.integration;

import static org.assertj.core.api.Assertions.*;
import com.logitrack_atom.courier_service.assignment.AssignmentConsumerService;
import com.logitrack_atom.courier_service.assignment.AssignmentFixtures;
import com.logitrack_atom.courier_service.assignment.AvailabilityCommand;
import com.logitrack_atom.courier_service.assignment.InvalidAssignmentCommandException;
import com.logitrack_atom.courier_service.entity.Repartidor;
import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/** Inbox/outbox reales en MySQL: observación puntual, deduplicación, carreras y rollback. */
class AssignmentInboxIT extends MySqlIntegrationSupport {
    @Autowired AssignmentConsumerService consumer;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void schemaAndCleanup() throws Exception {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='assignment_inbox'", Integer.class) == 0) {
            repository.saveAndFlush(new Repartidor("Fixture previa a migración", "Moto"));
            var previous = jdbc.queryForList("SELECT HEX(id),estado,created_at,updated_at,nombre,tipo_vehiculo FROM repartidores");
            for (String sql : Files.readString(Path.of("docker/mysql/mq/004-assignment-inbox-outbox.sql")).split(";"))
                if (!sql.isBlank()) jdbc.execute(sql);
            assertThat(jdbc.queryForList("SELECT HEX(id),estado,created_at,updated_at,nombre,tipo_vehiculo FROM repartidores")).isEqualTo(previous);
            repository.deleteAll();
        }
        jdbc.update("DELETE FROM amqp_outbox");
        jdbc.update("DELETE FROM assignment_inbox");
    }

    private void process(Message message) throws Exception {
        consumer.process(AvailabilityCommand.read(message, mapper), MessageDigest.getInstance("SHA-256").digest(message.getBody()));
    }

    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }

    @ParameterizedTest
    @ValueSource(strings = {"AVAILABLE", "INACTIVE", "NOT_FOUND"})
    void observesLocalStateWithoutReservingOrChangingRider(String availability) throws Exception {
        UUID id = UUID.randomUUID();
        if (!availability.equals("NOT_FOUND")) {
            var rider = new Repartidor("Fixture", "Moto");
            if (availability.equals("INACTIVE")) rider.cambiarEstado(EstadoRepartidor.INACTIVO);
            id = repository.saveAndFlush(rider).getId();
        }
        var before = jdbc.queryForList("SELECT HEX(id),estado,created_at,updated_at,nombre,tipo_vehiculo FROM repartidores");
        process(AssignmentFixtures.message(AssignmentFixtures.body(id), mapper));
        assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.availability')) FROM amqp_outbox", String.class)).isEqualTo(availability);
        assertThat(count("assignment_inbox")).isEqualTo(1);
        assertThat(count("amqp_outbox")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT HEX(id),estado,created_at,updated_at,nombre,tipo_vehiculo FROM repartidores")).isEqualTo(before);
    }

    @Test
    void replayKeepsOriginalResultEvenIfRiderChanged() throws Exception {
        var rider = repository.saveAndFlush(new Repartidor("Fixture", "Moto"));
        var message = AssignmentFixtures.message(AssignmentFixtures.body(rider.getId()), mapper);
        process(message);
        String payload = jdbc.queryForObject("SELECT payload FROM amqp_outbox", String.class);
        transaction(() -> { var current = repository.findById(rider.getId()).orElseThrow(); current.cambiarEstado(EstadoRepartidor.INACTIVO); });
        process(message);
        assertThat(jdbc.queryForObject("SELECT payload FROM amqp_outbox", String.class)).isEqualTo(payload);
        assertThat(count("assignment_inbox")).isEqualTo(1);
        assertThat(count("amqp_outbox")).isEqualTo(1);
    }

    @Test
    void sameMessageIdWithDifferentBodyIsPermanentRejection() throws Exception {
        var body = AssignmentFixtures.body(UUID.randomUUID());
        process(AssignmentFixtures.message(body, mapper));
        body.put("repartidorId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> process(AssignmentFixtures.message(body, mapper))).isInstanceOf(InvalidAssignmentCommandException.class);
        assertThat(count("amqp_outbox")).isEqualTo(1);
    }

    @Test
    void operationCannotAcquireASecondCommandIdentity() throws Exception {
        var body = AssignmentFixtures.body(UUID.randomUUID());
        process(AssignmentFixtures.message(body, mapper));
        body.put("messageId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> process(AssignmentFixtures.message(body, mapper))).isInstanceOf(InvalidAssignmentCommandException.class);
        assertThat(count("assignment_inbox")).isEqualTo(1);
    }

    @Test
    void concurrentRedeliveriesHaveOneInboxAndOneResult() throws Exception {
        var message = AssignmentFixtures.message(AssignmentFixtures.body(UUID.randomUUID()), mapper);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { process(message); return true; });
            var second = executor.submit(() -> { process(message); return true; });
            assertThat(first.get()).isTrue();assertThat(second.get()).isTrue();
        }
        assertThat(count("assignment_inbox")).isEqualTo(1);
        assertThat(count("amqp_outbox")).isEqualTo(1);
    }

    @Test
    void resultFailureRollsBackInboxAsWell() throws Exception {
        ddl("CREATE TRIGGER fallo_result_outbox BEFORE INSERT ON amqp_outbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture'");
        try {
            var message = AssignmentFixtures.message(AssignmentFixtures.body(UUID.randomUUID()), mapper);
            assertThatThrownBy(() -> process(message)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(count("assignment_inbox")).isZero();
            assertThat(count("amqp_outbox")).isZero();
        } finally { ddl("DROP TRIGGER fallo_result_outbox"); }
    }

    private void ddl(String sql) throws Exception {
        var result = MYSQL.execInContainer("env", "MYSQL_PWD=" + MYSQL.getPassword(), "mysql", "-uroot", MYSQL.getDatabaseName(), "-e", sql);
        assertThat(result.getExitCode()).as("DDL del laboratorio ejecutado").isZero();
    }
}
