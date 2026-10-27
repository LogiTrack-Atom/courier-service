package com.logitrack_atom.courier_service.service;

import com.logitrack_atom.courier_service.dto.request.RepartidorRequest;
import com.logitrack_atom.courier_service.dto.response.RepartidorResponse;
import com.logitrack_atom.courier_service.entity.Repartidor;
import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import com.logitrack_atom.courier_service.exception.RecursoNoEncontradoException;
import com.logitrack_atom.courier_service.mapper.RepartidorMapper;
import com.logitrack_atom.courier_service.repository.RepartidorRepository;
import com.logitrack_atom.courier_service.service.impl.RepartidorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifica decisiones de negocio y errores sin depender de infraestructura.
 *
 * @author MaxDz
 * @version 0.2.0
 * @see RepartidorServiceImpl
 */
@ExtendWith(MockitoExtension.class)
class RepartidorServiceTest {
    @Mock
    private RepartidorRepository repository;
    @Mock
    private RepartidorMapper mapper;
    private RepartidorService service;

    @BeforeEach
    void prepararServicio() {
        service = new RepartidorServiceImpl(repository, mapper);
    }

    /** El alta construye identidad y estado en el servidor, sin asociación de cuenta. */
    @Test
    void altaUtilizaLaEntidadPersistida() {
        Repartidor persisted = new Repartidor("Repartidor", "Moto");
        Instant instant = Instant.parse("2026-10-27T12:00:00Z");
        RepartidorResponse response = new RepartidorResponse(UUID.randomUUID(),
                "Repartidor", "Moto", EstadoRepartidor.ACTIVO, instant, instant);
        when(repository.saveAndFlush(any(Repartidor.class))).thenReturn(persisted);
        when(mapper.response(persisted)).thenReturn(response);

        assertThat(service.crear(new RepartidorRequest("Repartidor", "Moto"))).isSameAs(response);

        ArgumentCaptor<Repartidor> captured = ArgumentCaptor.forClass(Repartidor.class);
        verify(repository).saveAndFlush(captured.capture());
        Repartidor submitted = captured.getValue();
        assertThat(submitted.getNombre()).isEqualTo("Repartidor");
        assertThat(submitted.getTipoVehiculo()).isEqualTo("Moto");
        assertThat(submitted.getEstado()).isEqualTo(EstadoRepartidor.ACTIVO);
        assertThat(submitted.getId()).isNull();
        assertThat(submitted.getUsuarioId()).isNull();
    }

    /** La ausencia se informa como error de dominio, sin intentar mapear un recurso nulo. */
    @Test
    void consultaInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(id)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(mapper);
    }

    /** Un fallo de persistencia no se presenta como un alta exitosa. */
    @Test
    void falloDeAltaNoDevuelveRespuesta() {
        when(repository.saveAndFlush(any(Repartidor.class)))
                .thenThrow(new DataAccessResourceFailureException("Base no disponible"));

        assertThatThrownBy(() -> service.crear(new RepartidorRequest("Repartidor", "Moto")))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(mapper);
    }

    /** El filtro se aplica en la consulta propietaria, antes de construir respuestas. */
    @ParameterizedTest
    @EnumSource(EstadoRepartidor.class)
    void listadoFiltrado(EstadoRepartidor estado) {
        when(repository.findByEstado(estado)).thenReturn(List.of());

        assertThat(service.listar(estado)).isEmpty();
        verify(repository).findByEstado(estado);
        verify(repository, never()).findAll();
    }

    /** La ausencia de filtro consulta todos los registros y conserva el listado vacío. */
    @Test
    void listadoSinFiltro() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(service.listar(null)).isEmpty();
        verify(repository).findAll();
        verify(repository, never()).findByEstado(any());
    }
}
