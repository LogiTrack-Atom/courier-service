package com.logitrack_atom.courier_service.dto.request;

import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import jakarta.validation.constraints.NotNull;

/**
 * Solicita un cambio explícito del estado operativo de un repartidor.
 *
 * @param estado nuevo estado obligatorio; ACTIVO o INACTIVO
 * @author MaxDz
 * @version 0.2.0
 * @see EstadoRepartidor
 */
public record EstadoRepartidorRequest(@NotNull EstadoRepartidor estado) { }
