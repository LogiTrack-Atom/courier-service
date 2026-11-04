package com.logitrack_atom.courier_service.dto.response;

import java.util.List;

/**
 * Expone exclusivamente la identidad no sensible de la configuración consumida.
 *
 * @param aplicacion nombre lógico del servicio
 * @param perfiles perfiles efectivos del cliente
 * @param marcador valor enlazado desde la configuración remota
 * @author MaxDz
 * @version 0.3.0
 */
public record ConfiguracionResponse(String aplicacion, List<String> perfiles, String marcador) {
    /**
     * Conserva una copia inmutable de los perfiles publicados.
     * @param aplicacion nombre lógico
     * @param perfiles perfiles efectivos
     * @param marcador marcador no sensible
     */
    public ConfiguracionResponse {
        perfiles = List.copyOf(perfiles);
    }
}
