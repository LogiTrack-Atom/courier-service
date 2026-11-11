package com.logitrack_atom.courier_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.logitrack_atom.courier_service.config.ConfigMarkerProperties;

/**
 * Inicia el componente propietario de los datos de repartidores.
 * <p>La persistencia valida un esquema versionado en su propia base MySQL.</p>
 * @author MaxDz
 * @version 0.2.0
 */
@SpringBootApplication(excludeName = "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
@EnableConfigurationProperties(ConfigMarkerProperties.class)
public class CourierServiceApplication {
    /** Crea el punto de configuración de Spring Boot. */
    public CourierServiceApplication() { }

    /**
     * Arranca el servicio con propiedades externas del ambiente.
     * @param args argumentos de arranque de Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(CourierServiceApplication.class, args);
    }
}
