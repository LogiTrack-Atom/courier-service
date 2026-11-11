package com.logitrack_atom.courier_service.service.impl;

import com.logitrack_atom.courier_service.dto.response.RepartidorResponse;
import com.logitrack_atom.courier_service.exception.AsociacionConflictoException;
import com.logitrack_atom.courier_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.courier_service.mapper.RepartidorMapper;
import com.logitrack_atom.courier_service.repository.RepartidorRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Ejecuta exclusivamente la escritura local de una asociación ya verificada.
 *
 * <p>El bloqueo de fila evita reemplazos concurrentes. La restricción única de
 * MySQL resuelve la disputa de una misma cuenta entre distintos repartidores.
 * No se realizan llamadas HTTP dentro de esta transacción.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Service
public class RepartidorUsuarioWriter {
    private final RepartidorRepository repository;
    private final RepartidorMapper mapper;

    /**
     * Recibe los componentes de persistencia local.
     * @param repository repositorio propietario
     * @param mapper proyección de dominio
     */
    public RepartidorUsuarioWriter(RepartidorRepository repository, RepartidorMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Vincula una cuenta sin reemplazar otra asociación existente.
     * @param id UUID del repartidor
     * @param usuarioId UUID de cuenta previamente verificada
     * @return DTO del repartidor vinculado
     * @throws RecursoNoEncontradoException si el repartidor no existe
     * @throws AsociacionConflictoException si otra cuenta ya está asociada
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public RepartidorResponse asociar(UUID id, UUID usuarioId) {
        var row = repository.findByIdForUpdate(id).orElseThrow(RecursoNoEncontradoException::new);
        if (row.getUsuarioId() != null && !row.getUsuarioId().equals(usuarioId)) {
            throw new AsociacionConflictoException();
        }
        row.asociarUsuario(usuarioId);
        return mapper.response(repository.saveAndFlush(row));
    }
}
