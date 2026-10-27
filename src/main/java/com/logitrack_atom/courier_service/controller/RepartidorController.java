package com.logitrack_atom.courier_service.controller;

import com.logitrack_atom.courier_service.dto.request.RepartidorRequest;
import com.logitrack_atom.courier_service.dto.response.RepartidorResponse;
import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import com.logitrack_atom.courier_service.service.RepartidorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    /**
     * Inyecta el contrato de negocio.
     * @param service servicio de repartidores
     */
    public RepartidorController(RepartidorService service) {
        this.service = service;
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
}
