package com.logitrack_atom.courier_service.integration;

import com.logitrack_atom.courier_service.entity.Repartidor;
import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica actualizaciones y disponibilidad mediante HTTP y persistencia MySQL real.
 *
 * @author MaxDz
 * @version 0.2.0
 */
class RepartidorOperacionesIT extends MySqlIntegrationSupport {
    @Value("${local.server.port}")
    private int port;
    @Autowired
    private ObjectMapper mapper;

    /** Editar datos conserva identidad, asociación, creación y estado operativo. */
    @Test
    void actualizacionConservaIdentidadYEstado() throws Exception {
        Repartidor repartidor = crear(EstadoRepartidor.INACTIVO);
        Map<String, Object> before = snapshot(repartidor.getId());

        var response = request("PUT", "/" + repartidor.getId(),
                "{\"nombre\":\"Nuevo nombre\",\"tipoVehiculo\":\"Bicicleta\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode dto = mapper.readTree(response.body());
        assertThat(dto.size()).isEqualTo(6);
        assertThat(dto.path("id").asString()).isEqualTo(repartidor.getId().toString());
        assertThat(dto.path("nombre").asString()).isEqualTo("Nuevo nombre");
        assertThat(dto.path("tipoVehiculo").asString()).isEqualTo("Bicicleta");
        assertThat(dto.path("estado").asString()).isEqualTo("INACTIVO");
        assertThat(dto.has("usuarioId")).isFalse();
        Repartidor persisted = repository.findById(repartidor.getId()).orElseThrow();
        assertThat(persisted.getCreatedAt()).isEqualTo(repartidor.getCreatedAt());
        assertThat(persisted.getUpdatedAt()).isAfter(repartidor.getUpdatedAt());
        assertThat(persisted.getUsuarioId()).isEqualTo(repartidor.getUsuarioId());
        Map<String, Object> after = snapshot(repartidor.getId());
        assertThat(after).containsEntry("id", before.get("id"))
                .containsEntry("usuario_id", before.get("usuario_id"))
                .containsEntry("created_at", before.get("created_at"));
        assertThat(mapper.readTree(request("GET", "/" + repartidor.getId(), null).body())).isEqualTo(dto);
    }

    /** La disponibilidad devuelve ambos estados y no altera ninguna columna ni auditoría. */
    @ParameterizedTest
    @EnumSource(EstadoRepartidor.class)
    void disponibilidadSinEfectos(EstadoRepartidor estado) throws Exception {
        Repartidor repartidor = crear(estado);
        Map<String, Object> before = snapshot(repartidor.getId());

        for (int i = 0; i < 2; i++) {
            var response = request("GET", "/" + repartidor.getId() + "/disponibilidad", null);
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode dto = mapper.readTree(response.body());
            assertThat(dto.size()).isEqualTo(3);
            assertThat(dto.path("repartidorId").asString()).isEqualTo(repartidor.getId().toString());
            assertThat(dto.path("estado").asString()).isEqualTo(estado.name());
            assertThat(dto.path("disponible").asBoolean()).isEqualTo(estado == EstadoRepartidor.ACTIVO);
        }
        assertThat(snapshot(repartidor.getId())).isEqualTo(before);
    }

    /** Cambiar de estado es explícito y repetirlo no genera otra modificación de auditoría. */
    @ParameterizedTest
    @EnumSource(EstadoRepartidor.class)
    void cambioDeEstadoIdempotente(EstadoRepartidor target) throws Exception {
        EstadoRepartidor initial = target == EstadoRepartidor.ACTIVO
                ? EstadoRepartidor.INACTIVO : EstadoRepartidor.ACTIVO;
        Repartidor repartidor = crear(initial);
        Map<String, Object> before = snapshot(repartidor.getId());
        String body = mapper.writeValueAsString(Map.of("estado", target.name()));
        String path = "/" + repartidor.getId() + "/estado";

        var response = request("PATCH", path, body);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode dto = mapper.readTree(response.body());
        assertThat(dto.path("estado").asString()).isEqualTo(target.name());
        Map<String, Object> changed = snapshot(repartidor.getId());
        assertThat(changed).containsEntry("id", before.get("id"))
                .containsEntry("usuario_id", before.get("usuario_id"))
                .containsEntry("created_at", before.get("created_at"))
                .containsEntry("nombre", before.get("nombre"))
                .containsEntry("tipo_vehiculo", before.get("tipo_vehiculo"));
        assertThat(changed.get("updated_at")).isNotEqualTo(before.get("updated_at"));
        var repeat = request("PATCH", path, body);
        assertThat(repeat.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(repeat.body())).isEqualTo(dto);
        assertThat(snapshot(repartidor.getId())).isEqualTo(changed);
        JsonNode availability = mapper.readTree(request("GET",
                "/" + repartidor.getId() + "/disponibilidad", null).body());
        assertThat(availability.path("disponible").asBoolean()).isEqualTo(target == EstadoRepartidor.ACTIVO);
    }

    /** Rechaza estados desconocidos, ordinales, nulos o acompañados de otros campos. */
    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "{", "{\"estado\":null}", "{\"estado\":\"OTRO\"}",
            "{\"estado\":\"activo\"}", "{\"estado\":0}", "{\"estado\":1}",
            "{\"estado\":\"0\"}", "{\"estado\":\"\"}",
            "{\"estado\":\"ACTIVO\",\"nombre\":\"No permitido\"}"
    })
    void cambioInvalidoNoMuta(String body) throws Exception {
        Repartidor repartidor = crear(EstadoRepartidor.INACTIVO);
        Map<String, Object> before = snapshot(repartidor.getId());
        error(request("PATCH", "/" + repartidor.getId() + "/estado", body), 400);
        assertThat(snapshot(repartidor.getId())).isEqualTo(before);
    }

    /** La edición exige todos los datos descriptivos y rechaza campos de identidad o estado. */
    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "{\"nombre\":\"Nuevo\"}",
            "{\"nombre\":\"Nuevo\",\"tipoVehiculo\":\" \"}",
            "{\"nombre\":\"Nuevo\",\"tipoVehiculo\":\"Moto\",\"estado\":\"ACTIVO\"}",
            "{\"nombre\":\"Nuevo\",\"tipoVehiculo\":\"Moto\",\"id\":\"00000000-0000-0000-0000-000000000001\"}",
            "{\"nombre\":\"Nuevo\",\"tipoVehiculo\":\"Moto\",\"usuarioId\":\"00000000-0000-0000-0000-000000000001\"}",
            "{\"nombre\":\"Nuevo\",\"tipoVehiculo\":\"Moto\",\"createdAt\":\"2020-01-01T00:00:00Z\"}"
    })
    void edicionInvalidaNoMuta(String body) throws Exception {
        Repartidor repartidor = crear(EstadoRepartidor.INACTIVO);
        Map<String, Object> before = snapshot(repartidor.getId());
        error(request("PUT", "/" + repartidor.getId(), body), 400);
        assertThat(snapshot(repartidor.getId())).isEqualTo(before);
    }

    /** Los recursos inexistentes conservan 404 y los identificadores malformados, 400. */
    @Test
    void ausentesYParametrosInvalidos() throws Exception {
        for (String id : new String[]{UUID.randomUUID().toString(), "uuid-invalido"}) {
            int status = id.equals("uuid-invalido") ? 400 : 404;
            error(request("PUT", "/" + id, "{\"nombre\":\"Nombre\",\"tipoVehiculo\":\"Moto\"}"), status);
            error(request("PATCH", "/" + id + "/estado", "{\"estado\":\"ACTIVO\"}"), status);
            error(request("GET", "/" + id + "/disponibilidad", null), status);
        }
        assertThat(repository.count()).isZero();
    }

    private Repartidor crear(EstadoRepartidor estado) {
        Repartidor repartidor = new Repartidor("Repartidor", "Moto");
        repartidor.cambiarEstado(estado);
        repartidor.asociarUsuario(UUID.randomUUID());
        return repository.saveAndFlush(repartidor);
    }

    private Map<String, Object> snapshot(UUID id) {
        return jdbc.queryForMap("""
                SELECT HEX(id) AS id, HEX(usuario_id) AS usuario_id, nombre, tipo_vehiculo, estado,
                  DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created_at,
                  DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') AS updated_at
                FROM repartidores WHERE id = UNHEX(?)
                """, id.toString().replace("-", ""));
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/repartidores" + path));
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
        JsonNode error = mapper.readTree(response.body());
        assertThat(error.path("status").asInt()).isEqualTo(status);
        assertThat(error.path("mensaje").asString()).isNotBlank();
        assertThat(error.path("detalles").isArray()).isTrue();
        assertThat(response.body()).doesNotContain("Exception", "SELECT ", "stackTrace", "usuario_id");
    }
}
