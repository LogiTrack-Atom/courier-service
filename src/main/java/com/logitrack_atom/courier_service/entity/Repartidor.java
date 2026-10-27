package com.logitrack_atom.courier_service.entity;

import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa un repartidor con identidad y auditoría independientes de su cuenta.
 *
 * <p>usuarioId es una referencia opcional, única y local; no forma una relación JPA
 * con security. Los UUID y fechas históricos se importan mediante SQL, sin
 * volver a crear la entidad ni disparar la auditoría de una inserción JPA.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 * @see EstadoRepartidor
 */
@Entity
@Table(name = "repartidores")
public class Repartidor extends BaseEntity {
    @Column(nullable = false, length = 255)
    private String nombre;
    @Column(name = "tipo_vehiculo", nullable = false, length = 255)
    private String tipoVehiculo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRepartidor estado = EstadoRepartidor.ACTIVO;
    @Column(name = "usuario_id", unique = true)
    private UUID usuarioId;

    /** Constructor de hidratación requerido por JPA. */
    protected Repartidor() { }

    /**
     * Crea un repartidor activo sin asociación de cuenta.
     * @param nombre nombre no vacío, de hasta 255 caracteres
     * @param tipoVehiculo vehículo no vacío, de hasta 255 caracteres
     * @throws IllegalArgumentException si un dato está vacío o excede su longitud
     */
    public Repartidor(String nombre, String tipoVehiculo) {
        actualizarDatos(nombre, tipoVehiculo);
    }

    /**
     * Actualiza los datos descriptivos conservando identidad, estado y usuario.
     * @param nombre nuevo nombre válido
     * @param tipoVehiculo nuevo vehículo válido
     * @throws IllegalArgumentException si algún dato incumple sus límites
     */
    public void actualizarDatos(String nombre, String tipoVehiculo) {
        validarTexto(nombre);
        validarTexto(tipoVehiculo);
        this.nombre = nombre;
        this.tipoVehiculo = tipoVehiculo;
    }

    /**
     * Selecciona explícitamente el estado operativo; repetir el estado es válido.
     * @param estado nuevo estado, nunca nulo
     * @throws IllegalArgumentException si el estado es nulo
     */
    public void cambiarEstado(EstadoRepartidor estado) {
        if (estado == null) throw new IllegalArgumentException("El estado es obligatorio.");
        this.estado = estado;
    }

    /**
     * Asocia una cuenta previamente autorizada por el servicio de negocio.
     * @param usuarioId UUID de una cuenta REPARTIDOR existente
     * @throws IllegalArgumentException si es nulo o intenta reemplazar otra asociación
     */
    public void asociarUsuario(UUID usuarioId) {
        if (usuarioId == null || (this.usuarioId != null && !Objects.equals(this.usuarioId, usuarioId))) {
            throw new IllegalArgumentException("La asociación de usuario no es válida.");
        }
        this.usuarioId = usuarioId;
    }

    /**
     * Obtiene el nombre del repartidor.
     * @return nombre descriptivo del repartidor
     */
    public String getNombre() { return nombre; }
    /**
     * Obtiene el vehículo registrado.
     * @return tipo de vehículo
     */
    public String getTipoVehiculo() { return tipoVehiculo; }
    /**
     * Obtiene el estado operativo.
     * @return estado operativo actual
     */
    public EstadoRepartidor getEstado() { return estado; }
    /**
     * Obtiene la asociación local de identidad.
     * @return UUID de usuario asociado o nulo para registros sin vinculación
     */
    public UUID getUsuarioId() { return usuarioId; }
    /**
     * Consulta disponibilidad sin modificar el estado.
     * @return verdadero únicamente para ACTIVO; no representa reserva ni exclusividad
     */
    public boolean estaDisponible() { return estado == EstadoRepartidor.ACTIVO; }

    private static void validarTexto(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("Nombre y vehículo deben contener entre 1 y 255 caracteres.");
        }
    }
}
