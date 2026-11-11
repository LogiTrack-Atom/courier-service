package com.logitrack_atom.courier_service.exception;

/**
 * Indica que una consulta no encontró el repartidor solicitado.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class RecursoNoEncontradoException extends RuntimeException {
    /** Construye un error sin incorporar identificadores ni credenciales. */
    public RecursoNoEncontradoException() { super("No se encontró el repartidor solicitado."); }
}
