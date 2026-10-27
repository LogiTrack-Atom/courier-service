package com.logitrack_atom.courier_service.exception;

import org.springframework.http.HttpStatus;

/**
 * Distingue indisponibilidad de una respuesta incompatible del servicio de cuentas.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public class SecurityServiceException extends RuntimeException {
    /** Estado público permitido para un fallo de integración. */
    private final HttpStatus status;

    /**
     * Crea un error remoto con descripción uniforme, sin cuerpo ni URL del proveedor.
     * @param status BAD_GATEWAY o SERVICE_UNAVAILABLE
     * @throws IllegalArgumentException si se solicita otro estado
     */
    public SecurityServiceException(HttpStatus status) {
        super("No se pudo verificar la cuenta de usuario en el servicio de seguridad.");
        if (status != HttpStatus.BAD_GATEWAY && status != HttpStatus.SERVICE_UNAVAILABLE) {
            throw new IllegalArgumentException("Estado remoto no admitido.");
        }
        this.status = status;
    }

    /**
     * Obtiene el estado público del fallo remoto.
     * @return 502 o 503
     */
    public HttpStatus getStatus() { return status; }
}
