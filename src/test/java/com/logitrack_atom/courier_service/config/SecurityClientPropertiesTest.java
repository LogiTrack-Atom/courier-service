package com.logitrack_atom.courier_service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba destinos sin credenciales y límites finitos para el cliente administrativo.
 *
 * @author MaxDz
 * @version 0.2.0
 */
class SecurityClientPropertiesTest {
    /** No se admiten esquemas alternativos, credenciales ni componentes variables del destino. */
    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com", "http://demo:valor@example.com", "http://example.com?x=1",
            "https://example.com#fragmento", "http://example.com/api", "origen-relativo"})
    void rechazaOrigenInvalido(String value) {
        assertThatThrownBy(() -> new SecurityClientProperties(URI.create(value), Duration.ofSeconds(2), Duration.ofSeconds(3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** No se admiten esperas ilimitadas, negativas o superiores al contrato. */
    @Test
    void rechazaTiemposInvalidos() {
        URI origin = URI.create("http://localhost:8082");
        for (Duration value : new Duration[]{null, Duration.ZERO, Duration.ofMillis(-1), Duration.ofSeconds(11)}) {
            assertThatThrownBy(() -> new SecurityClientProperties(origin, value, Duration.ofSeconds(3)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (Duration value : new Duration[]{null, Duration.ZERO, Duration.ofMillis(-1), Duration.ofSeconds(31)}) {
            assertThatThrownBy(() -> new SecurityClientProperties(origin, Duration.ofSeconds(2), value))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
