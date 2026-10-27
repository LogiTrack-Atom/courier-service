package com.logitrack_atom.courier_service.service;

import com.logitrack_atom.courier_service.dto.request.RepartidorRequest;
import com.logitrack_atom.courier_service.dto.response.RepartidorResponse;
import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import com.logitrack_atom.courier_service.exception.RecursoNoEncontradoException;
import java.util.List;
import java.util.UUID;

/**
 * Define las operaciones de negocio mediante DTOs y transacciones locales.
 *
 * <p>Las altas generan una identidad nueva y las consultas trabajan con la base
 * propietaria de repartidores. No se exponen entidades de persistencia ni se
 * asignan cuentas de usuario desde estas operaciones.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 * @see RepartidorRequest
 * @see RepartidorResponse
 */
public interface RepartidorService {
    /**
     * Registra un repartidor ACTIVO, sin elegir UUID ni asociación de cuenta.
     * @param request datos descriptivos validados
     * @return repartidor persistido
     * @throws IllegalArgumentException si los datos incumplen el modelo
     */
    RepartidorResponse crear(RepartidorRequest request);

    /**
     * Busca un registro por su UUID.
     * @param id identificador requerido
     * @return respuesta de dominio
     * @throws RecursoNoEncontradoException si no existe
     */
    RepartidorResponse obtener(UUID id);

    /**
     * Lista los registros, aplicando un filtro operativo opcional.
     * @param estado estado o nulo para todos
     * @return lista, vacía si no hay coincidencias
     */
    List<RepartidorResponse> listar(EstadoRepartidor estado);
}
