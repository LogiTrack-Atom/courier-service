package com.logitrack_atom.courier_service.exception;

/**
 * Indica que security no encontró la cuenta que se desea vincular.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class CuentaNoEncontradaException extends RuntimeException {
    /** Construye un error de negocio sin datos personales ni credenciales. */
    public CuentaNoEncontradaException() {
        super("No se encontró la cuenta de usuario solicitada.");
    }
}
