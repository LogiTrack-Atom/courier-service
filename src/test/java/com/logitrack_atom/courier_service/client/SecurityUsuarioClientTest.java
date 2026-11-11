package com.logitrack_atom.courier_service.client;

import com.logitrack_atom.courier_service.config.SecurityClientProperties;
import com.logitrack_atom.courier_service.exception.SecurityServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica el rechazo local de credenciales ausentes y de conexiones no disponibles.
 *
 * @author MaxDz
 * @version 0.2.0
 */
class SecurityUsuarioClientTest {
    @AfterEach
    void limpiarIdentidad() { SecurityContextHolder.clearContext(); }

    /** El cliente no intenta usar credenciales recibidas fuera de un contexto ADMIN. */
    @Test
    void requiereContextoAdmin() {
        var client = crearCliente(8082);
        assertThatThrownBy(() -> client.validarRepartidor(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }

    /** Una conexión rechazada produce 503 uniforme sin registrar el token. */
    @Test
    void conexionRechazada() throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        var jwt = Jwt.withTokenValue("fixture-token-no-publicable").header("alg", "RS256")
                .subject(UUID.randomUUID().toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThatThrownBy(() -> crearCliente(port).validarRepartidor(UUID.randomUUID()))
                .isInstanceOfSatisfying(SecurityServiceException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasMessageNotContaining("fixture-token-no-publicable");
    }

    private SecurityUsuarioClient crearCliente(int port) {
        return new SecurityUsuarioClient(new SecurityClientProperties(URI.create("http://127.0.0.1:" + port),
                Duration.ofMillis(200), Duration.ofMillis(200)), JsonMapper.builder().build());
    }
}
