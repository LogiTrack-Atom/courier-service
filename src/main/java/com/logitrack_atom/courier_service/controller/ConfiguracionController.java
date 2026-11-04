package com.logitrack_atom.courier_service.controller;

import com.logitrack_atom.courier_service.config.ConfigMarkerProperties;
import com.logitrack_atom.courier_service.dto.response.ConfiguracionResponse;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Arrays;

/**
 * Permite demostrar el consumo de configuración sin exponer el Environment.
 *
 * @author MaxDz
 * @version 0.3.0
 */
@RestController
@RequestMapping("/api/v1/admin/configuracion")
public class ConfiguracionController {
    private final ConfigMarkerProperties properties;
    private final Environment environment;

    /**
     * Recibe el marcador ya enlazado por Spring y el entorno del cliente.
     * @param properties propiedades no sensibles consumidas
     * @param environment perfiles y nombre de aplicación efectivos
     */
    public ConfiguracionController(ConfigMarkerProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    /**
     * Devuelve una lista explícita de tres propiedades de diagnóstico.
     * @return identidad de configuración consumida por el proceso
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ConfiguracionResponse consultar() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        return new ConfiguracionResponse(environment.getRequiredProperty("spring.application.name"),
                Arrays.asList(profiles), properties.marker());
    }
}
