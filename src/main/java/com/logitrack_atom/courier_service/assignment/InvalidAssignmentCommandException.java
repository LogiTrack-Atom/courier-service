package com.logitrack_atom.courier_service.assignment;

/** Rechazo permanente de contrato con mensaje fijo, sin conservar el cuerpo recibido. */
public final class InvalidAssignmentCommandException extends RuntimeException {
    /** Crea un rechazo que no contiene credenciales ni datos del productor. */
    public InvalidAssignmentCommandException() { super("Comando de asignación inválido"); }
}
