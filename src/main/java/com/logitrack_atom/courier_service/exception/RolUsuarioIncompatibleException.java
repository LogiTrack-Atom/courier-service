package com.logitrack_atom.courier_service.exception;

/**
 * Rechaza una cuenta existente sin el rol compatible con el dominio.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class RolUsuarioIncompatibleException extends RuntimeException {
    /** Construye un error de negocio sin datos personales ni credenciales. */
    public RolUsuarioIncompatibleException() {
        super("La cuenta debe tener el rol REPARTIDOR.");
    }
}
