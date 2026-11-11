package com.logitrack_atom.courier_service.dto.response;

import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import java.time.Instant;
import java.util.UUID;

/**
 * Publica datos de dominio sin exponer la entidad ni la asociación de usuario.
 * @param id UUID persistente
 * @param nombre nombre descriptivo
 * @param tipoVehiculo vehículo registrado
 * @param estado estado operativo
 * @param createdAt fecha de creación UTC
 * @param updatedAt fecha de modificación UTC
 * @author MaxDz
 * @version 0.2.0
 */
public record RepartidorResponse(
        UUID id,
        String nombre,
        String tipoVehiculo,
        EstadoRepartidor estado,
        Instant createdAt,
        Instant updatedAt) { }
