package com.logitrack_atom.courier_service.service.impl;

import com.logitrack_atom.courier_service.dto.request.RepartidorRequest;
import com.logitrack_atom.courier_service.dto.request.EstadoRepartidorRequest;
import com.logitrack_atom.courier_service.dto.response.DisponibilidadRepartidorResponse;
import com.logitrack_atom.courier_service.dto.response.RepartidorResponse;
import com.logitrack_atom.courier_service.entity.Repartidor;
import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import com.logitrack_atom.courier_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.courier_service.mapper.RepartidorMapper;
import com.logitrack_atom.courier_service.repository.RepartidorRepository;
import com.logitrack_atom.courier_service.service.RepartidorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Ejecuta altas y lecturas exclusivamente en la base propietaria de courier.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Service
@Transactional(readOnly = true)
public class RepartidorServiceImpl implements RepartidorService {
    private final RepartidorRepository repository;
    private final RepartidorMapper mapper;
    /**
     * Recibe sus dependencias mediante constructor.
     * @param repository persistencia local
     * @param mapper proyección segura de respuesta
     */
    public RepartidorServiceImpl(RepartidorRepository repository, RepartidorMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RepartidorResponse crear(RepartidorRequest request) {
        Repartidor repartidor = new Repartidor(request.nombre(), request.tipoVehiculo());
        return mapper.response(repository.saveAndFlush(repartidor));
    }

    /** {@inheritDoc} */
    @Override
    public RepartidorResponse obtener(UUID id) {
        return mapper.response(repository.findById(id).orElseThrow(RecursoNoEncontradoException::new));
    }

    /** {@inheritDoc} */
    @Override
    public List<RepartidorResponse> listar(EstadoRepartidor estado) {
        List<Repartidor> repartidores = estado == null
                ? repository.findAll()
                : repository.findByEstado(estado);
        return repartidores.stream().map(mapper::response).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RepartidorResponse actualizar(UUID id, RepartidorRequest request) {
        Repartidor repartidor = buscarExistente(id);
        repartidor.actualizarDatos(request.nombre(), request.tipoVehiculo());
        return mapper.response(repository.saveAndFlush(repartidor));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RepartidorResponse cambiarEstado(UUID id, EstadoRepartidorRequest request) {
        Repartidor repartidor = buscarExistente(id);
        repartidor.cambiarEstado(request.estado());
        return mapper.response(repository.saveAndFlush(repartidor));
    }

    /** {@inheritDoc} */
    @Override
    public DisponibilidadRepartidorResponse disponibilidad(UUID id) {
        return mapper.disponibilidad(buscarExistente(id));
    }

    private Repartidor buscarExistente(UUID id) {
        return repository.findById(id).orElseThrow(RecursoNoEncontradoException::new);
    }
}
