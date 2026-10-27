package com.logitrack_atom.courier_service.repository;

import com.logitrack_atom.courier_service.entity.Repartidor;
import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persiste repartidores exclusivamente en la base propietaria de courier.
 * @author MaxDz
 * @version 0.2.0
 * @see Repartidor
 */
public interface RepartidorRepository extends JpaRepository<Repartidor, UUID> {
    /**
     * Filtra repartidores por su estado operativo.
     * @param estado filtro operativo obligatorio
     * @return repartidores del estado indicado
     */
    List<Repartidor> findByEstado(EstadoRepartidor estado);

    /**
     * Busca la asociación local de una identidad validada.
     * @param usuarioId identidad validada de security
     * @return repartidor asociado, si existe
     */
    Optional<Repartidor> findByUsuarioId(UUID usuarioId);

    /**
     * Serializa las escrituras sobre un repartidor dentro de una transacción local.
     * @param id UUID del registro que se desea modificar
     * @return entidad bloqueada, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Repartidor r where r.id = :id")
    Optional<Repartidor> findByIdForUpdate(@Param("id") UUID id);
}
