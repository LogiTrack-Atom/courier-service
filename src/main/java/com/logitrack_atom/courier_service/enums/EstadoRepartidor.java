package com.logitrack_atom.courier_service.enums;

/**
 * Define el estado operativo; ACTIVO significa disponible en este sprint.
 * @author MaxDz
 * @version 0.2.0
 */
public enum EstadoRepartidor {
    /** Admite asignaciones según la regla de disponibilidad acordada. */
    ACTIVO,
    /** Conserva el registro y sus referencias históricas, sin nuevas asignaciones. */
    INACTIVO
}
