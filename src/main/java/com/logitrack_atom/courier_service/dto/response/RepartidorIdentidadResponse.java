package com.logitrack_atom.courier_service.dto.response;

import java.util.UUID;

/**
 * Consulta administrativa de una asociación inmutable, sin datos personales.
 * @param repartidorId identidad del recurso propietario
 * @param usuarioId cuenta asociada, o nulo si todavía no se vinculó
 * @author MaxDz
 * @version 0.2.0
 */
public record RepartidorIdentidadResponse(UUID repartidorId, UUID usuarioId) { }
