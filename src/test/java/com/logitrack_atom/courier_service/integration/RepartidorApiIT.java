package com.logitrack_atom.courier_service.integration;

import com.logitrack_atom.courier_service.entity.Repartidor;
import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el contrato DTO y sus transacciones mediante HTTP y MySQL reales.
 *
 * @author MaxDz
 * @version 0.2.0
 */
class RepartidorApiIT extends MySqlIntegrationSupport {
    @Value("${local.server.port}")
    private int port;
    @Autowired
    private ObjectMapper mapper;

    /** Un alta persiste ACTIVO, genera UUID y devuelve únicamente el DTO acordado. */
    @Test
    void altaValida() throws Exception {
        var response = request("POST", "/api/v1/repartidores",
                "{\"nombre\":\"Repartidor Álvarez\",\"tipoVehiculo\":\"Moto\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        var node = mapper.readTree(response.body());
        UUID id = UUID.fromString(node.path("id").asString());
        assertThat(response.headers().firstValue("Location")).contains("/api/v1/repartidores/" + id);
        assertThat(node.size()).isEqualTo(6);
        assertThat(node.path("estado").asString()).isEqualTo("ACTIVO");
        assertThat(node.path("createdAt").asString()).isNotBlank();
        assertThat(node.has("usuarioId")).isFalse();
        assertThat(repository.findById(id).orElseThrow().getUsuarioId()).isNull();

        var get = request("GET", "/api/v1/repartidores/" + id, null);
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(get.body())).isEqualTo(node);
    }

    /** El listado vacío es [] y sus filtros no mezclan estados. */
    @Test
    void listasYFiltros() throws Exception {
        var empty = request("GET", "/api/v1/repartidores", null);
        assertThat(empty.statusCode()).isEqualTo(200);
        assertThat(empty.body()).isEqualTo("[]");
        repository.saveAndFlush(new Repartidor("Uno", "Moto"));
        var inactive = new Repartidor("Dos", "Bici");
        inactive.cambiarEstado(EstadoRepartidor.INACTIVO);
        repository.saveAndFlush(inactive);
        assertThat(mapper.readTree(request("GET", "/api/v1/repartidores", null).body()).size()).isEqualTo(2);
        for (String state : new String[]{"ACTIVO", "INACTIVO"}) {
            var response = request("GET", "/api/v1/repartidores?estado=" + state, null);
            assertThat(response.statusCode()).isEqualTo(200);
            var values = mapper.readTree(response.body());
            assertThat(values.size()).isEqualTo(1);
            assertThat(values.get(0).path("estado").asString()).isEqualTo(state);
        }
    }

    /** No acepta estado, identidad, usuario ni auditoría elegidos desde el alta. */
    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "[]", "{",
            "{\"nombre\":\" \",\"tipoVehiculo\":\"Moto\"}",
            "{\"nombre\":\"Nombre\",\"tipoVehiculo\":\"\"}",
            "{\"nombre\":\"Nombre\",\"tipoVehiculo\":\"Moto\",\"estado\":\"INACTIVO\"}",
            "{\"nombre\":\"Nombre\",\"tipoVehiculo\":\"Moto\",\"usuarioId\":\"00000000-0000-0000-0000-000000000001\"}",
            "{\"nombre\":\"Nombre\",\"tipoVehiculo\":\"Moto\",\"id\":\"00000000-0000-0000-0000-000000000001\"}",
            "{\"nombre\":\"Nombre\",\"tipoVehiculo\":\"Moto\",\"createdAt\":\"2020-01-01T00:00:00Z\"}",
            "{\"nombre\":\"Nombre\",\"tipoVehiculo\":\"Moto\",\"updatedAt\":\"2020-01-01T00:00:00Z\"}"
    })
    void rechazaCuerposInvalidos(String body) throws Exception {
        error(request("POST", "/api/v1/repartidores", body), 400);
        assertThat(repository.count()).isZero();
    }

    /** Rechaza longitudes incompatibles con el esquema antes de insertar. */
    @ParameterizedTest
    @ValueSource(strings = {"nombre", "tipoVehiculo"})
    void longitudInvalida(String field) throws Exception {
        Map<String, String> values = field.equals("nombre")
                ? Map.of("nombre", "n".repeat(256), "tipoVehiculo", "Moto")
                : Map.of("nombre", "Nombre", "tipoVehiculo", "v".repeat(256));
        error(request("POST", "/api/v1/repartidores", mapper.writeValueAsString(values)), 400);
        assertThat(repository.count()).isZero();
    }

    /** Distingue recurso inexistente de parámetros inválidos, manteniendo ApiError. */
    @Test
    void erroresDeConsulta() throws Exception {
        error(request("GET", "/api/v1/repartidores/" + UUID.randomUUID(), null), 404);
        error(request("GET", "/api/v1/repartidores/no-es-uuid", null), 400);
        error(request("GET", "/api/v1/repartidores?estado=OTRO", null), 400);
        error(request("GET", "/api/v1/repartidores?estado=activo", null), 400);
    }

    /** El error 405 conserva Allow además de la estructura común. */
    @Test
    void metodoNoAdmitido() throws Exception {
        var response = request("DELETE", "/api/v1/repartidores", null);
        error(response, 405);
        assertThat(response.headers().firstValue("Allow")).isPresent();
    }

    /** Un cuerpo de texto no se interpreta como una solicitud JSON válida. */
    @Test
    void contenidoNoAdmitido() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/repartidores"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("Nombre;Moto"))
                .build();
        error(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()), 415);
        assertThat(repository.count()).isZero();
    }

    /** Conserva 406 si el cliente solicita un formato de respuesta no disponible. */
    @Test
    void respuestaNoAceptable() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/repartidores"))
                .header("Accept", "text/plain")
                .GET()
                .build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(406);
        assertThat(response.body()).isEmpty();
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return HttpClient.newHttpClient().send(builder.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void error(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        var error = mapper.readTree(response.body());
        assertThat(error.path("status").asInt()).isEqualTo(status);
        assertThat(error.path("timestamp").asString()).isNotBlank();
        assertThat(error.path("mensaje").asString()).isNotBlank();
        assertThat(error.path("detalles").isArray()).isTrue();
        assertThat(response.body()).doesNotContain("SELECT ", "Exception", "stackTrace", "password");
    }
}
