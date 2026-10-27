package com.logitrack_atom.courier_service.dto.response;

import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import java.util.UUID;

/**
 * Publica la disponibilidad operativa sin reservar ni modificar al repartidor.
 *
 * <p>La disponibilidad significa únicamente que el estado es ACTIVO. No implica
 * exclusividad, capacidad ni consulta a otros servicios.</p>
 *
 * @param repartidorId UUID del repartidor consultado
 * @param estado estado operativo actual
 * @param disponible verdadero únicamente cuando el estado es ACTIVO
 * @author MaxDz
 * @version 0.2.0
 * @see EstadoRepartidor
 */
public record DisponibilidadRepartidorResponse(
        UUID repartidorId,
        EstadoRepartidor estado,
        boolean disponible) { }
