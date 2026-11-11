package com.logitrack_atom.courier_service.assignment;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Rechazos de contrato antes de SQL; no sustituye pruebas de broker o persistencia. */
class AvailabilityCommandTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsCanonicalCommand() {
        var body = AssignmentFixtures.body(UUID.randomUUID());
        var command = AvailabilityCommand.read(AssignmentFixtures.message(body, mapper), mapper);
        assertThat(command.messageId().toString()).isEqualTo(body.get("messageId"));
    }

    @Test
    void rejectsUnknownFieldsAndVersions() {
        var body = AssignmentFixtures.body(UUID.randomUUID());
        body.put("accessToken", "fixture-never-preserved");
        assertThatThrownBy(() -> AvailabilityCommand.read(AssignmentFixtures.message(body, mapper), mapper)).isInstanceOf(InvalidAssignmentCommandException.class);
        body.remove("accessToken");body.put("schemaVersion", 2);
        assertThatThrownBy(() -> AvailabilityCommand.read(AssignmentFixtures.message(body, mapper), mapper)).isInstanceOf(InvalidAssignmentCommandException.class);
    }

    @Test
    void rejectsInvalidUuidGenerationAndTime() {
        for (String field : new String[]{"repartidorId", "assignmentVersion", "expiresAt"}) {
            var body = AssignmentFixtures.body(UUID.randomUUID());
            body.put(field, field.equals("assignmentVersion") ? 1.5 : "invalid");
            assertThatThrownBy(() -> AvailabilityCommand.read(AssignmentFixtures.message(body, mapper), mapper)).isInstanceOf(InvalidAssignmentCommandException.class);
        }
    }

    @Test
    void rejectsMismatchedMetadata() {
        var message = AssignmentFixtures.message(AssignmentFixtures.body(UUID.randomUUID()), mapper);
        message.getMessageProperties().setCorrelationId(UUID.randomUUID().toString());
        assertThatThrownBy(() -> AvailabilityCommand.read(message, mapper)).isInstanceOf(InvalidAssignmentCommandException.class);
    }

    @Test
    void rejectsOversizedAndMalformedBodyWithoutEchoingInput() {
        var message = AssignmentFixtures.message(AssignmentFixtures.body(UUID.randomUUID()), mapper);
        var oversized = new org.springframework.amqp.core.Message(new byte[16385], message.getMessageProperties());
        assertThatThrownBy(() -> AvailabilityCommand.read(oversized, mapper)).isInstanceOf(InvalidAssignmentCommandException.class).hasMessage("Comando de asignación inválido");
        var malformed = new org.springframework.amqp.core.Message("not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8), message.getMessageProperties());
        assertThatThrownBy(() -> AvailabilityCommand.read(malformed, mapper)).isInstanceOf(InvalidAssignmentCommandException.class).hasMessage("Comando de asignación inválido");
    }
}
