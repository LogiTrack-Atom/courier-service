package com.logitrack_atom.courier_service.service;

import com.logitrack_atom.courier_service.client.SecurityUsuarioClient;
import com.logitrack_atom.courier_service.dto.response.RepartidorResponse;
import com.logitrack_atom.courier_service.exception.AsociacionConflictoException;
import com.logitrack_atom.courier_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.courier_service.mapper.RepartidorMapper;
import com.logitrack_atom.courier_service.repository.RepartidorRepository;
import com.logitrack_atom.courier_service.service.impl.RepartidorUsuarioWriter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import java.util.UUID;

/**
 * Coordina la verificación remota y la vinculación local sin transacción distribuida.
 *
 * <p>La consulta a security termina antes de abrir la transacción de escritura.
 * Repetir una asociación idéntica es idempotente y no consulta al proveedor.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Service
public class RepartidorUsuarioService {
    private final RepartidorRepository repository;
    private final RepartidorMapper mapper;
    private final SecurityUsuarioClient client;
    private final RepartidorUsuarioWriter writer;

    /**
     * Recibe las dependencias de consulta, verificación y escritura.
     * @param repository consulta local
     * @param mapper proyección de dominio
     * @param client verificación administrativa de cuentas
     * @param writer transacción local con bloqueo
     */
    public RepartidorUsuarioService(RepartidorRepository repository, RepartidorMapper mapper,
                                   SecurityUsuarioClient client, RepartidorUsuarioWriter writer) {
        this.repository = repository;
        this.mapper = mapper;
        this.client = client;
        this.writer = writer;
    }

    /**
     * Asocia explícitamente una cuenta compatible a un repartidor existente.
     * @param id UUID de dominio
     * @param usuarioId UUID de cuenta REPARTIDOR
     * @return DTO de dominio sin exponer la asociación
     * @throws RecursoNoEncontradoException si no existe el repartidor
     * @throws AsociacionConflictoException si ya tiene otra cuenta
     */
    @PreAuthorize("hasRole('ADMIN')")
    public RepartidorResponse asociar(UUID id, UUID usuarioId) {
        var row = repository.findById(id).orElseThrow(RecursoNoEncontradoException::new);
        if (row.getUsuarioId() != null) {
            if (!row.getUsuarioId().equals(usuarioId)) throw new AsociacionConflictoException();
            return mapper.response(row);
        }
        client.validarRepartidor(usuarioId);
        return writer.asociar(id, usuarioId);
    }
}
