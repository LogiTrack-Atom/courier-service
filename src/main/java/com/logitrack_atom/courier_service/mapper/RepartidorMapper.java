package com.logitrack_atom.courier_service.mapper;

import com.logitrack_atom.courier_service.dto.response.RepartidorResponse;
import com.logitrack_atom.courier_service.dto.response.DisponibilidadRepartidorResponse;
import com.logitrack_atom.courier_service.entity.Repartidor;
import org.springframework.stereotype.Component;

/**
 * Traduce el modelo propietario a un DTO sin asociaciones de identidad.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
public class RepartidorMapper {
    /** Crea el mapper sin estado. */
    public RepartidorMapper() { }

    /**
     * Obtiene una respuesta independiente de la persistencia.
     * @param row entidad existente
     * @return DTO de dominio
     */
    public RepartidorResponse response(Repartidor row) {
        return new RepartidorResponse(row.getId(), row.getNombre(), row.getTipoVehiculo(),
                row.getEstado(), row.getCreatedAt(), row.getUpdatedAt());
    }

    /**
     * Proyecta la disponibilidad definida por el modelo, sin efectuar escrituras.
     * @param row repartidor existente
     * @return identidad, estado y disponibilidad coherentes
     */
    public DisponibilidadRepartidorResponse disponibilidad(Repartidor row) {
        return new DisponibilidadRepartidorResponse(row.getId(), row.getEstado(), row.estaDisponible());
    }
}
