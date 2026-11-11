package com.logitrack_atom.courier_service.integration;

import com.logitrack_atom.courier_service.entity.Repartidor;
import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import com.logitrack_atom.courier_service.support.SecurityAccountStub;
import com.logitrack_atom.courier_service.support.TestRsaKeys;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba JWT, matriz de acceso y asociaciones mediante HTTP real y MySQL aislado.
 *
 * @author MaxDz
 * @version 0.2.0
 */
class RepartidorSeguridadIT extends MySqlIntegrationSupport {
    private static final String[] ROUTES = {"POST|", "GET|", "GET|/{id}", "PUT|/{id}",
            "PATCH|/{id}/estado", "GET|/{id}/disponibilidad", "PUT|/{id}/usuario"};
    @Value("${local.server.port}")
    private int port;
    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void limpiarProveedor() { SecurityAccountStub.reset(); }

    static Stream<Arguments> matrizAcceso() {
        return Stream.of("ADMIN", "REPARTIDOR", "CLIENTE").flatMap(role ->
                Stream.of(ROUTES).map(route -> Arguments.of(role, route)));
    }

    /** Cada ruta aplica su rol; las consultas propias no usan el servicio remoto. */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("matrizAcceso")
    void matrizPorRol(String role, String route) throws Exception {
        UUID user = UUID.randomUUID();
        Repartidor row = crear(user);
        String[] parts = route.split("\\|", -1);
        String path = parts[1].replace("{id}", row.getId().toString());
        String body = switch (parts[0]) {
            case "POST" -> "{\"nombre\":\"Nuevo\",\"tipoVehiculo\":\"Moto\"}";
            case "PATCH" -> "{\"estado\":\"ACTIVO\"}";
            case "PUT" -> path.endsWith("/usuario") ? mapper.writeValueAsString(Map.of("usuarioId", user))
                    : "{\"nombre\":\"Editado\",\"tipoVehiculo\":\"Bicicleta\"}";
            default -> null;
        };
        var response = request(parts[0], path, body, TestRsaKeys.token(user, role));
        boolean permitida = role.equals("ADMIN") || (role.equals("REPARTIDOR")
                && parts[0].equals("GET") && !path.isEmpty());
        int expected = permitida ? (parts[0].equals("POST") ? 201 : 200) : 403;
        assertThat(response.statusCode()).isEqualTo(expected);
        if (!permitida) error(response, 403);
        assertThat(SecurityAccountStub.solicitudes()).isZero();
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    /** Las siete operaciones exigen Bearer antes de interpretar sus cuerpos. */
    @ParameterizedTest
    @ValueSource(strings = {"POST|", "GET|", "GET|/{id}", "PUT|/{id}",
            "PATCH|/{id}/estado", "GET|/{id}/disponibilidad", "PUT|/{id}/usuario"})
    void rutasSinToken(String route) throws Exception {
        String[] parts = route.split("\\|", -1);
        var response = request(parts[0], parts[1].replace("{id}", UUID.randomUUID().toString()), "{}", null);
        error(response, 401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(SecurityAccountStub.solicitudes()).isZero();
    }

    /** Conocer un UUID o usarlo como sub no crea una asociación ni habilita acceso ajeno. */
    @ParameterizedTest
    @ValueSource(strings = {"", "/disponibilidad"})
    void pertenenciaExplicita(String suffix) throws Exception {
        UUID owner = UUID.randomUUID();
        Repartidor own = crear(owner);
        Repartidor foreign = crear(UUID.randomUUID());
        Repartidor unlinked = crear(null);
        error(request("GET", "/" + foreign.getId() + suffix, null, TestRsaKeys.token(owner, "REPARTIDOR")), 403);
        error(request("GET", "/" + own.getId() + suffix, null, TestRsaKeys.token(UUID.randomUUID(), "REPARTIDOR")), 403);
        error(request("GET", "/" + unlinked.getId() + suffix, null,
                TestRsaKeys.token(unlinked.getId(), "REPARTIDOR")), 403);
        error(request("GET", "/" + UUID.randomUUID() + suffix, null, TestRsaKeys.token(owner, "REPARTIDOR")), 403);
        assertThat(request("GET", "/" + own.getId() + suffix, null,
                TestRsaKeys.token(owner, "REPARTIDOR")).statusCode()).isEqualTo(200);
        assertThat(SecurityAccountStub.solicitudes()).isZero();
    }

    /** Los filtros producen 401 ante tokens malformados, vencidos o con claims inválidos. */
    @Test
    void jwtInvalidoNoConsultaProveedor() throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet expired = new JWTClaimsSet.Builder().subject(UUID.randomUUID().toString())
                .issuer("logitrack-security").audience("logitrack-api").claim("roles", List.of("ADMIN"))
                .issueTime(Date.from(now.minusSeconds(1000))).expirationTime(Date.from(now.minusSeconds(100))).build();
        JWTClaimsSet wrongIssuer = new JWTClaimsSet.Builder(expired).issuer("ajeno")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(900))).build();
        JWTClaimsSet unknownRole = new JWTClaimsSet.Builder(wrongIssuer).issuer("logitrack-security")
                .claim("roles", List.of("SUPER_ADMIN")).build();
        var foreign = new com.nimbusds.jwt.SignedJWT(
                new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256),
                new JWTClaimsSet.Builder(unknownRole).claim("roles", List.of("ADMIN")).build());
        foreign.sign(new com.nimbusds.jose.crypto.RSASSASigner(TestRsaKeys.generar(2048).getPrivate()));
        for (String token : List.of("no-es-jwt", TestRsaKeys.firmar(expired),
                TestRsaKeys.firmar(wrongIssuer), TestRsaKeys.firmar(unknownRole), foreign.serialize())) {
            error(request("GET", "", null, token), 401);
        }
        assertThat(SecurityAccountStub.solicitudes()).isZero();
    }

    /** Solo la asociación inicial verifica cuenta y reenvía el Bearer ADMIN validado. */
    @Test
    void asociacionAdministrativaIdempotente() throws Exception {
        Repartidor row = crear(null);
        UUID account = UUID.randomUUID();
        SecurityAccountStub.cuenta(account, "REPARTIDOR");
        String token = TestRsaKeys.token(UUID.randomUUID(), "ADMIN");
        String body = mapper.writeValueAsString(Map.of("usuarioId", account));
        var response = request("PUT", "/" + row.getId() + "/usuario", body, token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).has("usuarioId")).isFalse();
        Repartidor linked = repository.findById(row.getId()).orElseThrow();
        assertThat(linked.getUsuarioId()).isEqualTo(account);
        assertThat(linked.getCreatedAt()).isEqualTo(row.getCreatedAt());
        assertThat(linked.getEstado()).isEqualTo(row.getEstado());
        assertThat(SecurityAccountStub.solicitudes()).isEqualTo(1);
        assertThat(SecurityAccountStub.authorization()).isEqualTo("Bearer " + token);
        var repeated = request("PUT", "/" + row.getId() + "/usuario", body, token);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(repository.findById(row.getId()).orElseThrow().getUpdatedAt()).isEqualTo(linked.getUpdatedAt());
        error(request("PUT", "/" + row.getId() + "/usuario",
                mapper.writeValueAsString(Map.of("usuarioId", UUID.randomUUID())), token), 409);
        assertThat(SecurityAccountStub.solicitudes()).isEqualTo(1);
        assertThat(request("GET", "/" + row.getId(), null, TestRsaKeys.token(account, "REPARTIDOR"))
                .statusCode()).isEqualTo(200);
    }

    /** Una cuenta no puede asociarse a dos repartidores; el conflicto revierte la escritura. */
    @Test
    void cuentaUnicaEntreRepartidores() throws Exception {
        UUID account = UUID.randomUUID();
        Repartidor first = crear(account);
        Repartidor second = crear(null);
        SecurityAccountStub.cuenta(account, "REPARTIDOR");
        error(request("PUT", "/" + second.getId() + "/usuario",
                mapper.writeValueAsString(Map.of("usuarioId", account)),
                TestRsaKeys.token(UUID.randomUUID(), "ADMIN")), 409);
        assertThat(repository.findById(first.getId()).orElseThrow().getUsuarioId()).isEqualTo(account);
        assertThat(repository.findById(second.getId()).orElseThrow().getUsuarioId()).isNull();
    }

    /** Dos altas simultáneas no reemplazan silenciosamente la asociación ganadora. */
    @Test
    void asociacionesConcurrentes() throws Exception {
        Repartidor row = crear(null);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SecurityAccountStub.cuenta(first, "REPARTIDOR");
        SecurityAccountStub.cuenta(second, "REPARTIDOR");
        SecurityAccountStub.sincronizarDosSolicitudes();
        String token = TestRsaKeys.token(UUID.randomUUID(), "ADMIN");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> request("PUT", "/" + row.getId() + "/usuario",
                    mapper.writeValueAsString(Map.of("usuarioId", first)), token));
            var b = executor.submit(() -> request("PUT", "/" + row.getId() + "/usuario",
                    mapper.writeValueAsString(Map.of("usuarioId", second)), token));
            var responseA = a.get();
            var responseB = b.get();
            assertThat(List.of(responseA.statusCode(), responseB.statusCode())).containsExactlyInAnyOrder(200, 409);
            UUID winner = responseA.statusCode() == 200 ? first : second;
            assertThat(repository.findById(row.getId()).orElseThrow().getUsuarioId()).isEqualTo(winner);
        }
    }

    static Stream<Arguments> proveedorInvalido() {
        return Stream.of(
                Arguments.of(404, "{}", 0, 404),
                Arguments.of(200, "{\"id\":\"{id}\",\"roles\":[\"CLIENTE\"]}", 0, 422),
                Arguments.of(500, "detalle-upstream-no-publicable", 0, 503),
                Arguments.of(429, "{}", 0, 503),
                Arguments.of(401, "{}", 0, 502), Arguments.of(403, "{}", 0, 502),
                Arguments.of(302, "{}", 0, 502), Arguments.of(200, "{", 0, 502),
                Arguments.of(200, "null", 0, 502),
                Arguments.of(200, "{\"id\":\"{id}\",\"roles\":null}", 0, 502),
                Arguments.of(200, "{\"id\":\"{id}\",\"roles\":[]}", 0, 502),
                Arguments.of(200, "{\"id\":\"{id}\",\"roles\":[null]}", 0, 502),
                Arguments.of(200, "{\"id\":\"{id}\",\"roles\":[\"SUPER_ADMIN\"]}", 0, 502),
                Arguments.of(200, "{\"id\":\"00000000-0000-0000-0000-000000000001\",\"roles\":[\"REPARTIDOR\"]}", 0, 502),
                Arguments.of(200, "x".repeat(65_537), 0, 502),
                Arguments.of(200, "{}", 2000, 503));
    }

    /** Los fallos del proveedor no alteran la asociación ni revelan cuerpos remotos. */
    @ParameterizedTest
    @MethodSource("proveedorInvalido")
    void falloRemotoSinCambios(int remoteStatus, String remoteBody, int delay, int expected) throws Exception {
        Repartidor row = crear(null);
        UUID account = UUID.randomUUID();
        SecurityAccountStub.respuesta(remoteStatus, remoteBody.replace("{id}", account.toString()), delay);
        var response = request("PUT", "/" + row.getId() + "/usuario",
                mapper.writeValueAsString(Map.of("usuarioId", account)), TestRsaKeys.token(UUID.randomUUID(), "ADMIN"));
        error(response, expected);
        Repartidor after = repository.findById(row.getId()).orElseThrow();
        assertThat(after.getUsuarioId()).isNull();
        assertThat(after.getUpdatedAt()).isEqualTo(row.getUpdatedAt());
        assertThat(SecurityAccountStub.solicitudes()).isEqualTo(1);
        assertThat(response.body()).doesNotContain("detalle-upstream-no-publicable", "SUPER_ADMIN");
    }

    /** No se llama al proveedor si el cuerpo o el repartidor son inválidos. */
    @Test
    void asociacionInvalidaAntesDeIntegrar() throws Exception {
        Repartidor row = crear(null);
        String token = TestRsaKeys.token(UUID.randomUUID(), "ADMIN");
        for (String body : List.of("{}", "{\"usuarioId\":null}", "{\"usuarioId\":\"invalido\"}")) {
            error(request("PUT", "/" + row.getId() + "/usuario", body, token), 400);
        }
        error(request("PUT", "/" + UUID.randomUUID() + "/usuario",
                mapper.writeValueAsString(Map.of("usuarioId", UUID.randomUUID())), token), 404);
        assertThat(SecurityAccountStub.solicitudes()).isZero();
    }

    private Repartidor crear(UUID user) {
        Repartidor row = new Repartidor("Repartidor", "Moto");
        row.cambiarEstado(EstadoRepartidor.INACTIVO);
        if (user != null) row.asociarUsuario(user);
        return repository.saveAndFlush(row);
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/repartidores" + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return HttpClient.newHttpClient().send(builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void error(HttpResponse<String> response, int expected) throws Exception {
        assertThat(response.statusCode()).isEqualTo(expected);
        var body = mapper.readTree(response.body());
        assertThat(body.path("status").asInt()).isEqualTo(expected);
        assertThat(body.path("mensaje").asString()).isNotBlank();
        assertThat(body.path("detalles").isArray()).isTrue();
        assertThat(response.body()).doesNotContain("Exception", "SELECT ", "stackTrace", "password");
    }
}
