package com.logitrack_atom.courier_service.assignment;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Procesa disponibilidad con commit propio antes de que el listener pueda emitir ACK. */
@Service
public class AssignmentConsumerService {
    private final InboxStore inbox;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    /**
     * Construye el procesamiento local independiente del canal AMQP.
     * @param inbox persistencia deduplicada y consulta de Courier
     * @param mapper serializador JSON
     * @param manager transacciones de la base propietaria
     */
    public AssignmentConsumerService(InboxStore inbox, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.inbox = inbox;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * Guarda inbox y un resultado/outbox, o reconoce una reentrega ya confirmada.
     * @param command comando validado en el límite de transporte
     * @param hash SHA-256 del cuerpo original
     */
    public void process(AvailabilityCommand command, byte[] hash) {
        if (hash.length != 32) throw new InvalidAssignmentCommandException();
        try {
            transactions.executeWithoutResult(status -> {
                var previous = inbox.hash(command.messageId());
                if (previous.isPresent()) { sameHash(previous.get(), hash); return; }
                if (inbox.operationExists(command.operationId())) throw new InvalidAssignmentCommandException();
                String availability = inbox.availability(command.repartidorId());
                Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
                UUID resultId = UUID.randomUUID();
                var result = new LinkedHashMap<String, Object>();
                result.put("schemaVersion", 1);
                result.put("type", "assignment.availability.checked.v1");
                result.put("messageId", resultId);
                result.put("causationId", command.messageId());
                result.put("operationId", command.operationId());
                result.put("envioId", command.envioId());
                result.put("repartidorId", command.repartidorId());
                result.put("assignmentVersion", command.assignmentVersion());
                result.put("occurredAt", now.toString());
                result.put("expiresAt", command.expiresAt().toString());
                result.put("availability", availability);
                inbox.record(command, hash, resultId, now, mapper.writeValueAsString(result));
            });
        } catch (DuplicateKeyException race) {
            transactions.executeWithoutResult(status -> {
                var previous = inbox.hash(command.messageId());
                if (previous.isPresent()) sameHash(previous.get(), hash);
                else if (inbox.operationExists(command.operationId())) throw new InvalidAssignmentCommandException();
                else throw race;
            });
        }
    }

    private void sameHash(byte[] previous, byte[] received) {
        if (!MessageDigest.isEqual(previous, received)) throw new InvalidAssignmentCommandException();
    }
}
