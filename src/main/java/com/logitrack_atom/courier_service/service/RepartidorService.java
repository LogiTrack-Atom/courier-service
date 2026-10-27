package com.logitrack_atom.courier_service.service;

import com.logitrack_atom.courier_service.dto.request.RepartidorRequest;
import com.logitrack_atom.courier_service.dto.request.EstadoRepartidorRequest;
import com.logitrack_atom.courier_service.dto.response.DisponibilidadRepartidorResponse;
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

    /**
     * Actualiza nombre y vehículo conservando identidad, usuario y estado.
     * @param id UUID del repartidor
     * @param request datos descriptivos completos y válidos
     * @return repartidor actualizado
     * @throws RecursoNoEncontradoException si no existe
     * @throws IllegalArgumentException si algún dato incumple el modelo
     */
    RepartidorResponse actualizar(UUID id, RepartidorRequest request);

    /**
     * Cambia únicamente el estado operativo; repetir el estado vigente es válido.
     * @param id UUID del repartidor
     * @param request estado explícito y obligatorio
     * @return repartidor con el estado solicitado
     * @throws RecursoNoEncontradoException si no existe
     * @throws IllegalArgumentException si el estado es nulo
     */
    RepartidorResponse cambiarEstado(UUID id, EstadoRepartidorRequest request);

    /**
     * Consulta disponibilidad sin mutar datos ni invocar otros servicios.
     * @param id UUID del repartidor
     * @return disponibilidad operativa; INACTIVO devuelve disponible falso
     * @throws RecursoNoEncontradoException si no existe
     */
    DisponibilidadRepartidorResponse disponibilidad(UUID id);
}
