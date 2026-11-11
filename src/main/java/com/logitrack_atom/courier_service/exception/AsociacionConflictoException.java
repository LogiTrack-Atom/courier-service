package com.logitrack_atom.courier_service.exception;

/**
 * Indica un intento de reemplazar una asociación o utilizar una cuenta ya vinculada.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class AsociacionConflictoException extends RuntimeException {
    /** Construye un error de negocio sin datos personales ni credenciales. */
    public AsociacionConflictoException() {
        super("La asociación de usuario entra en conflicto con los datos existentes.");
    }
}
