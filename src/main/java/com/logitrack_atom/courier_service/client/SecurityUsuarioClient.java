package com.logitrack_atom.courier_service.client;

import com.logitrack_atom.courier_service.config.SecurityClientProperties;
import com.logitrack_atom.courier_service.enums.Rol;
import com.logitrack_atom.courier_service.exception.CuentaNoEncontradaException;
import com.logitrack_atom.courier_service.exception.RolUsuarioIncompatibleException;
import com.logitrack_atom.courier_service.exception.SecurityServiceException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.UUID;

/**
 * Verifica cuentas exclusivamente durante una asociación administrativa explícita.
 *
 * <p>Reenvía el JWT validado al origen configurado, sin seguir redirecciones,
 * registrar tokens ni reintentar solicitudes. No se usa para autenticar peticiones.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component
@EnableConfigurationProperties(SecurityClientProperties.class)
public class SecurityUsuarioClient {
    private final RestClient client;
    private final ObjectMapper mapper;

    /**
     * Construye un cliente con esperas finitas y redirecciones desactivadas.
     * @param properties origen y timeouts
     * @param mapper lector JSON de la aplicación
     */
    public SecurityUsuarioClient(SecurityClientProperties properties, ObjectMapper mapper) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder().baseUrl(properties.baseUrl().toString()).requestFactory(factory).build();
        this.mapper = mapper;
    }

    /**
     * Comprueba identidad y rol de una cuenta utilizando el JWT ADMIN del contexto.
     * @param usuarioId cuenta que se desea vincular
     * @throws CuentaNoEncontradaException si el proveedor responde 404
     * @throws RolUsuarioIncompatibleException si la cuenta no tiene rol REPARTIDOR
     * @throws SecurityServiceException si está caído, demora o responde un contrato inválido
     * @throws AccessDeniedException si no existe una identidad ADMIN validada
     */
    public void validarRepartidor(UUID usuarioId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()
                || jwt.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Se requiere administración autenticada.");
        }
        try {
            client.get().uri("/api/v1/usuarios/{id}", usuarioId)
                    .headers(headers -> headers.setBearerAuth(jwt.getToken().getTokenValue()))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        int code = response.getStatusCode().value();
                        if (code == 404) throw new CuentaNoEncontradaException();
                        if (code == 429 || code >= 500) {
                            throw new SecurityServiceException(HttpStatus.SERVICE_UNAVAILABLE);
                        }
                        if (code != 200 || response.getHeaders().getContentType() == null
                                || !MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType())) {
                            throw new SecurityServiceException(HttpStatus.BAD_GATEWAY);
                        }
                        byte[] body = response.getBody().readNBytes(65_537);
                        if (body.length > 65_536) throw new SecurityServiceException(HttpStatus.BAD_GATEWAY);
                        SecurityUsuarioResponse account;
                        try {
                            account = mapper.readValue(body, SecurityUsuarioResponse.class);
                        } catch (JacksonException ex) {
                            throw new SecurityServiceException(HttpStatus.BAD_GATEWAY);
                        }
                        if (account == null || !usuarioId.equals(account.id()) || account.roles() == null
                                || account.roles().isEmpty() || account.roles().contains(null)) {
                            throw new SecurityServiceException(HttpStatus.BAD_GATEWAY);
                        }
                        if (!account.roles().contains(Rol.REPARTIDOR)) throw new RolUsuarioIncompatibleException();
                        return null;
                    });
        } catch (RestClientException ex) {
            throw new SecurityServiceException(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
