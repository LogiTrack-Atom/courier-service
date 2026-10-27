package com.logitrack_atom.courier_service.integration;

import com.logitrack_atom.courier_service.CourierServiceApplication;
import com.logitrack_atom.courier_service.support.TestRsaKeys;
import com.logitrack_atom.courier_service.entity.Repartidor;
import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import com.logitrack_atom.courier_service.repository.RepartidorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/**
 * Verifica esquema, permisos, auditoría e importación histórica con JPA y MySQL reales.
 * @author MaxDz
 * @version 0.2.0
 */
class RepartidorPersistenceIT extends MySqlIntegrationSupport {
    @Value("${local.server.port}") private int port;

    /** La creación JPA genera UUID y auditoría, sin una identidad de usuario implícita. */
    @Test void generaIdentidadYAuditoria() {
        var saved = repository.saveAndFlush(new Repartidor("Repartidor", "Moto"));
        var loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isAfterOrEqualTo(loaded.getCreatedAt());
        assertThat(loaded.getUsuarioId()).isNull();
        assertThat(jdbc.queryForObject("SELECT HEX(id) FROM repartidores",String.class))
            .isEqualTo(loaded.getId().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT));
    }

    /** La lectura JPA conserva bytes y microsegundos importados, incluso tras otro arranque. */
    @Test void importaUuidYFechasSinRegenerarAlReiniciar() {
        UUID id = UUID.fromString("b4a52c50-b363-4b95-b365-58302f12cd91");
        Instant created = Instant.parse("2026-10-19T12:13:14.123456Z");
        Instant updated = Instant.parse("2026-10-20T13:14:15.654321Z");
        jdbc.update("INSERT INTO repartidores(id,created_at,updated_at,nombre,tipo_vehiculo,estado,usuario_id) VALUES (UNHEX(?),?,?,?,?,?,NULL)",
            id.toString().replace("-", ""), Timestamp.from(created), Timestamp.from(updated), "O'Hara 🛵", "Moto eléctrica", "INACTIVO");
        comprobarImportado(repository.findById(id).orElseThrow(),id,created,updated);
        try (var restarted = new SpringApplicationBuilder(CourierServiceApplication.class)
                .run("--server.port=0", "--logitrack.security.public-key-location=" + java.nio.file.Path.of(TestRsaKeys.publicResource().getPath()).toUri(), "--spring.datasource.url=" + MYSQL.getJdbcUrl(),
                    "--spring.datasource.username=" + MYSQL.getUsername(), "--spring.datasource.password=" + MYSQL.getPassword())) {
            comprobarImportado(restarted.getBean(RepartidorRepository.class).findById(id).orElseThrow(),id,created,updated);
        }
        assertThat(repository.count()).isEqualTo(1);
    }

    private static void comprobarImportado(Repartidor row, UUID id, Instant created, Instant updated) {
        assertThat(row.getId()).isEqualTo(id);
        assertThat(row.getCreatedAt()).isEqualTo(created);
        assertThat(row.getUpdatedAt()).isEqualTo(updated);
        assertThat(row.getNombre()).isEqualTo("O'Hara 🛵");
        assertThat(row.getTipoVehiculo()).isEqualTo("Moto eléctrica");
        assertThat(row.getEstado()).isEqualTo(EstadoRepartidor.INACTIVO);
        assertThat(row.getUsuarioId()).isNull();
    }

    /** MySQL permite múltiples NULL y rechaza dos repartidores para el mismo usuario. */
    @Test void imponeAsociacionUnicaOpcional() {
        var first = repository.saveAndFlush(new Repartidor("Uno", "Moto"));
        var second = repository.saveAndFlush(new Repartidor("Dos", "Bici"));
        UUID user = UUID.randomUUID();
        transaction(() -> repository.findById(first.getId()).orElseThrow().asociarUsuario(user));
        assertThatThrownBy(() -> transaction(() -> {
            repository.findById(second.getId()).orElseThrow().asociarUsuario(user);
            repository.flush();
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findByUsuarioId(user).orElseThrow().getId()).isEqualTo(first.getId());
        assertThat(repository.findById(second.getId()).orElseThrow().getUsuarioId()).isNull();
    }

    /** La actualización conserva la creación y modifica la auditoría únicamente al escribir. */
    @Test void actualizaSinCambiarIdentidadNiCreacion() {
        var first = repository.saveAndFlush(new Repartidor("Uno", "Moto"));
        var before = repository.findById(first.getId()).orElseThrow();
        transaction(() -> {
            var row = repository.findById(first.getId()).orElseThrow();
            row.actualizarDatos("Nuevo", "Camioneta");
            row.cambiarEstado(EstadoRepartidor.INACTIVO);
        });
        var after = repository.findById(first.getId()).orElseThrow();
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getUpdatedAt()).isAfterOrEqualTo(before.getUpdatedAt());
        assertThat(after.getNombre()).isEqualTo("Nuevo");
        assertThat(repository.findByEstado(EstadoRepartidor.ACTIVO)).isEmpty();
        assertThat(repository.findByEstado(EstadoRepartidor.INACTIVO)).hasSize(1);
    }

    /** El usuario operativo no tiene permisos sobre tablas administrativas de MySQL. */
    @Test void limitaUsuarioALaBasePropietaria() {
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()",String.class)).startsWith("courier_app@");
        assertThatThrownBy(() -> jdbc.queryForList("SELECT * FROM mysql.user")).isInstanceOf(DataAccessException.class);
    }

    /** Las referencias a usuarios son UUID locales y el esquema mantiene la precisión histórica. */
    @Test void validaContratoDeAlmacenamientoSinFkRemotas() {
        assertThat(jdbc.queryForObject("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='repartidores' AND COLUMN_NAME='id'",String.class)).isEqualTo("binary(16)");
        assertThat(jdbc.queryForObject("SELECT DATETIME_PRECISION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='repartidores' AND COLUMN_NAME='created_at'",Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()",Integer.class)).isZero();
    }

    /** El servidor HTTP arranca con JPA validate y solo publica salud agregada. */
    @Test void arranqueRealYSaludSinDatosInternos() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP").doesNotContain("components","details","password");
    }
}
