package com.logitrack_atom.courier_service.controller;

import com.logitrack_atom.courier_service.dto.response.RepartidorIdentidadResponse;

import com.logitrack_atom.courier_service.dto.request.RepartidorRequest;
import com.logitrack_atom.courier_service.dto.request.EstadoRepartidorRequest;
import com.logitrack_atom.courier_service.dto.response.DisponibilidadRepartidorResponse;
import com.logitrack_atom.courier_service.dto.response.RepartidorResponse;
import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import com.logitrack_atom.courier_service.service.RepartidorService;
import com.logitrack_atom.courier_service.service.RepartidorUsuarioService;
import com.logitrack_atom.courier_service.dto.request.AsociacionUsuarioRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Expone el contrato REST de repartidores sin devolver entidades JPA.
 *
 * @author MaxDz
 * @version 0.2.0
 * @see RepartidorService
 */
@RestController
@RequestMapping("/api/v1/repartidores")
public class RepartidorController {
    private final RepartidorService service;
    private final RepartidorUsuarioService usuarios;
    /**
     * Inyecta el contrato de negocio.
     * @param service servicio de repartidores
     * @param usuarios coordinación de asociaciones administrativas
     */
    public RepartidorController(RepartidorService service, RepartidorUsuarioService usuarios) {
        this.service = service;
        this.usuarios = usuarios;
    }

    /**
     * Registra un repartidor activo.
     * @param request datos permitidos y validados
     * @return 201 con Location y DTO
     */
    @PostMapping
    public ResponseEntity<RepartidorResponse> crear(@Valid @RequestBody RepartidorRequest request) {
        RepartidorResponse result = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/repartidores/" + result.id())).body(result);
    }

    /**
     * Consulta un recurso existente.
     * @param id UUID válido
     * @return datos de dominio
     */
    @GetMapping("/{id}")
    public RepartidorResponse obtener(@PathVariable UUID id) {
        return service.obtener(id);
    }

    /**
     * Lista repartidores con filtro de estado opcional.
     * @param estado ACTIVO, INACTIVO o nulo
     * @return lista de DTOs
     */
    @GetMapping
    public List<RepartidorResponse> listar(@RequestParam(required = false) EstadoRepartidor estado) {
        return service.listar(estado);
    }

    /**
     * Reemplaza los datos descriptivos sin alterar el estado ni la identidad.
     * @param id UUID del repartidor
     * @param request nombre y vehículo validados
     * @return DTO actualizado
     */
    @PutMapping("/{id}")
    public RepartidorResponse actualizar(@PathVariable UUID id, @Valid @RequestBody RepartidorRequest request) {
        return service.actualizar(id, request);
    }

    /**
     * Establece explícitamente ACTIVO o INACTIVO.
     * @param id UUID del repartidor
     * @param request estado validado
     * @return DTO con el estado vigente
     */
    @PatchMapping("/{id}/estado")
    public RepartidorResponse cambiarEstado(@PathVariable UUID id,
                                           @Valid @RequestBody EstadoRepartidorRequest request) {
        return service.cambiarEstado(id, request);
    }

    /**
     * Consulta la disponibilidad operativa sin efectos secundarios.
     * @param id UUID del repartidor
     * @return disponibilidad coherente con el estado local
     */
    @GetMapping("/{id}/disponibilidad")
    public DisponibilidadRepartidorResponse disponibilidad(@PathVariable UUID id) {
        return service.disponibilidad(id);
    }

    /**
     * Vincula una cuenta existente mediante verificación administrativa explícita.
     * @param id UUID del repartidor
     * @param request identidad de la cuenta que se desea comprobar
     * @return DTO de dominio con la asociación persistida
     */
    @PutMapping("/{id}/usuario")
    public RepartidorResponse asociarUsuario(@PathVariable UUID id,
                                            @Valid @RequestBody AsociacionUsuarioRequest request) {
        return usuarios.asociar(id, request.usuarioId());
    }
    /**
     * Consulta exclusivamente como ADMIN la identidad vinculada al recurso.
     * @param id UUID del repartidor
     * @return referencia de usuario nullable, sin mutaciones ni datos personales
     */
    @GetMapping("/{id}/usuario")
    public RepartidorIdentidadResponse identidad(@PathVariable UUID id) {
        return service.identidad(id);
    }

}
