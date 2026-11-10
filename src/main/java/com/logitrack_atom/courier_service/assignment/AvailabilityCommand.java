package com.logitrack_atom.courier_service.assignment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import tools.jackson.databind.ObjectMapper;

/**
 * Comando v1 autorizado por el usuario AMQP de Shipment; no transporta JWT ni reserva capacidad.
 * @param messageId identidad estable de transporte
 * @param operationId correlación de negocio
 * @param envioId UUID de agregado remoto
 * @param repartidorId UUID local que se consultará
 * @param assignmentVersion generación que Shipment revalidará al recibir el resultado
 * @param occurredAt aceptación original UTC
 * @param expiresAt vencimiento de la operación que decide Shipment
 */
public record AvailabilityCommand(UUID messageId, UUID operationId, UUID envioId, UUID repartidorId,
        long assignmentVersion, Instant occurredAt, Instant expiresAt) {
    private static final Set<String> FIELDS = Set.of("schemaVersion", "messageId", "operationId", "envioId",
            "repartidorId", "assignmentVersion", "occurredAt", "expiresAt", "type");

    /**
     * Valida tamaño, esquema y correlación antes de comenzar una transacción SQL.
     * @param message cuerpo y propiedades recibidas del broker
     * @param mapper parser JSON estricto de la aplicación
     * @return comando válido sin campos adicionales
     */
    public static AvailabilityCommand read(Message message, ObjectMapper mapper) {
        try {
            if (message.getBody().length == 0 || message.getBody().length > 16384) throw invalid();
            Map<?, ?> body = mapper.readValue(message.getBody(), Map.class);
            if (!body.keySet().equals(FIELDS) || integer(body.get("schemaVersion")) != 1
                    || !"assignment.availability.requested.v1".equals(body.get("type"))) throw invalid();
            var command = new AvailabilityCommand(uuid(body.get("messageId")), uuid(body.get("operationId")),
                    uuid(body.get("envioId")), uuid(body.get("repartidorId")), integer(body.get("assignmentVersion")),
                    instant(body.get("occurredAt")), instant(body.get("expiresAt")));
            if (!command.expiresAt().isAfter(command.occurredAt())) throw invalid();
            var properties = message.getMessageProperties();
            var mode = properties.getReceivedDeliveryMode() != null ? properties.getReceivedDeliveryMode() : properties.getDeliveryMode();
            if (mode != org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT
                    || !"application/json".equals(properties.getContentType())
                    || !command.messageId().toString().equals(properties.getMessageId())
                    || !command.operationId().toString().equals(properties.getCorrelationId())) throw invalid();
            return command;
        } catch (RuntimeException error) {
            throw invalid();
        }
    }

    private static long integer(Object value) {
        if (!(value instanceof Number)) throw invalid();
        long result = new BigDecimal(value.toString()).longValueExact();
        if (result < 1 || result > 9007199254740991L) throw invalid();
        return result;
    }

    private static UUID uuid(Object value) {
        if (!(value instanceof String text)) throw invalid();
        UUID result = UUID.fromString(text);
        if (!result.toString().equals(text)) throw invalid();
        return result;
    }

    private static Instant instant(Object value) {
        if (!(value instanceof String text) || !text.endsWith("Z")) throw invalid();
        return Instant.parse(text);
    }

    private static InvalidAssignmentCommandException invalid() { return new InvalidAssignmentCommandException(); }
}
