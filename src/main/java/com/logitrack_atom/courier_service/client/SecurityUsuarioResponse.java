package com.logitrack_atom.courier_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.logitrack_atom.courier_service.enums.Rol;
import java.util.Set;
import java.util.UUID;

/**
 * Proyecta únicamente los datos de la cuenta necesarios para una asociación.
 *
 * <p>El proveedor publica también email y auditoría; esta proyección no los
 * conserva ni los expone en la API de repartidores.</p>
 *
 * @param id UUID de la cuenta consultada
 * @param roles roles conocidos informados por security
 * @author MaxDz
 * @version 0.2.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SecurityUsuarioResponse(UUID id, Set<Rol> roles) { }
