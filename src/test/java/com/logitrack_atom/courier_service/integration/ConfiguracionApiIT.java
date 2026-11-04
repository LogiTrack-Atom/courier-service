package com.logitrack_atom.courier_service.integration;

import com.logitrack_atom.courier_service.support.TestRsaKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el diagnóstico no permite lectura anónima ni roles de negocio.
 *
 * @author MaxDz
 * @version 0.3.0
 */
class ConfiguracionApiIT extends MySqlIntegrationSupport {
    @Value("${local.server.port}")
    private int port;

    /** La frontera HTTP exige autenticación y un rol ADMIN explícito. */
    @Test
    void restringeDiagnostico() {
        RestClient client = RestClient.create("http://127.0.0.1:" + port);
        assertThat(client.get().uri("/api/v1/admin/configuracion")
                .<Integer>exchange((request, response) -> response.getStatusCode().value())).isEqualTo(401);
        for (String role : new String[]{"CLIENTE", "REPARTIDOR"}) {
            assertThat(client.get().uri("/api/v1/admin/configuracion")
                    .headers(headers -> headers.setBearerAuth(TestRsaKeys.token(UUID.randomUUID(), role)))
                    .<Integer>exchange((request, response) -> response.getStatusCode().value())).isEqualTo(403);
        }
        assertThat(client.get().uri("/api/v1/admin/configuracion")
                .headers(headers -> headers.setBearerAuth(TestRsaKeys.token(UUID.randomUUID(), "ADMIN")))
                .<Integer>exchange((request, response) -> response.getStatusCode().value())).isEqualTo(200);
    }
}
