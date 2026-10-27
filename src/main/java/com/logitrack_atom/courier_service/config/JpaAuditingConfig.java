package com.logitrack_atom.courier_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Habilita las fechas de auditoría administradas por Spring Data JPA.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcMicrosecondClock")
public class JpaAuditingConfig {
    /** Crea la configuración de auditoría de persistencia. */
    public JpaAuditingConfig() { }

    /**
     * Genera fechas con la precisión que almacena DATETIME(6).
     * @return proveedor UTC con microsegundos, consistente entre POST y GET
     */
    @Bean
    public DateTimeProvider utcMicrosecondClock() {
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
