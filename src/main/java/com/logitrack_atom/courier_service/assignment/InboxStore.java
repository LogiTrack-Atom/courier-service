package com.logitrack_atom.courier_service.assignment;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Deduplica comandos y confirma inbox/resultado en la misma transacción local de Courier. */
@Repository
public class InboxStore {
    private final JdbcTemplate jdbc;

    /**
     * Recibe acceso SQL de la base propietaria.
     * @param jdbc conexión unida al gestor de transacciones local
     */
    public InboxStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Busca la identidad de transporte ya procesada.
     * @param messageId UUID de comando
     * @return huella original inmutable, si existe
     */
    public Optional<byte[]> hash(UUID messageId) {
        return jdbc.query("SELECT payload_hash FROM assignment_inbox WHERE message_id=?", (r, n) -> r.getBytes(1), bytes(messageId)).stream().findFirst();
    }

    /**
     * Comprueba que una operación no venga de un segundo comando incompatible.
     * @param operationId identidad de operación
     * @return verdadero si hay un comando confirmado para ella
     */
    public boolean operationExists(UUID operationId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM assignment_inbox WHERE operation_id=?", Integer.class, bytes(operationId)) > 0;
    }

    /**
     * Consulta estado local sin HTTP, reserva ni cambio del repartidor.
     * @param rider UUID local
     * @return AVAILABLE, INACTIVE o NOT_FOUND
     */
    public String availability(UUID rider) {
        var states = jdbc.query("SELECT estado FROM repartidores WHERE id=?", (r, n) -> r.getString(1), bytes(rider));
        if (states.isEmpty()) return "NOT_FOUND";
        return switch (states.getFirst()) {
            case "ACTIVO" -> "AVAILABLE";
            case "INACTIVO" -> "INACTIVE";
            default -> throw new IllegalStateException("Estado de Courier no reconocido");
        };
    }

    /**
     * Registra un resultado único y su publicación futura, sin ACK ni espera AMQP.
     * @param command comando validado
     * @param hash huella exacta de bytes recibidos
     * @param resultId identidad estable del resultado
     * @param now observación UTC
     * @param payload JSON resultante sin información personal
     */
    public void record(AvailabilityCommand command, byte[] hash, UUID resultId, Instant now, String payload) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Inbox/outbox requieren una transacción local");
        jdbc.update("INSERT INTO assignment_inbox(message_id,operation_id,payload_hash,result_message_id,processed_at) VALUES (?,?,?,?,?)",
                bytes(command.messageId()), bytes(command.operationId()), hash, bytes(resultId), Timestamp.from(now));
        jdbc.update("INSERT INTO amqp_outbox(message_id,operation_id,routing_key,payload,created_at,next_attempt_at) VALUES (?,?,'availability.result',?,?,?)",
                bytes(resultId), bytes(command.operationId()), payload, Timestamp.from(now), Timestamp.from(now));
    }

    /**
     * Convierte un UUID a la representación local de persistencia.
     * @param value UUID no nulo
     * @return dieciséis bytes sin referencia a otras bases
     */
    static byte[] bytes(UUID value) { return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array(); }

    /**
     * Reconstruye el UUID de una columna binaria.
     * @param value dieciséis bytes
     * @return UUID local
     */
    static UUID uuid(byte[] value) { var b = ByteBuffer.wrap(value); return new UUID(b.getLong(), b.getLong()); }
}
