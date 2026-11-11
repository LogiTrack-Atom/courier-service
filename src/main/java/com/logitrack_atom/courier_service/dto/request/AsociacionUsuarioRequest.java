package com.logitrack_atom.courier_service.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Solicita una asociación administrativa explícita con una cuenta existente.
 *
 * @param usuarioId UUID obligatorio de una cuenta REPARTIDOR
 * @author MaxDz
 * @version 0.2.0
 */
public record AsociacionUsuarioRequest(@NotNull UUID usuarioId) { }
