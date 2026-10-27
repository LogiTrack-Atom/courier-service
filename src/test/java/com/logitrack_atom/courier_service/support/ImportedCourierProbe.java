package com.logitrack_atom.courier_service.support;

import com.logitrack_atom.courier_service.CourierServiceApplication;
import com.logitrack_atom.courier_service.repository.RepartidorRepository;
import org.springframework.boot.builder.SpringApplicationBuilder;
import tools.jackson.databind.json.JsonMapper;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Comprueba desde JPA una exportación aplicada por el ensayo de shipment.
 *
 * <p>Es una utilidad de pruebas: no se incorpora al JAR del servicio. La conexión
 * llega por entorno y utiliza un lector del MySQL descartable. Inicia y cierra
 * la aplicación dos veces y no guarda entidades ni registra datos del artefacto.</p>
 *
 * @author MaxDz
 * @version 0.2.0
 */
public final class ImportedCourierProbe {
    private ImportedCourierProbe() { }

    /**
     * Lee los repartidores importados antes y después de reiniciar el contexto.
     * @param args ruta del source.json privado del ensayo
     * @throws Exception si la aplicación no arranca o la importación no coincide
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Se requiere el artefacto del ensayo.");
        var rows = JsonMapper.builder().build().readTree(Files.readString(Path.of(args[0])))
                .path("payload").path("tables").path("repartidores");
        if (!rows.isArray() || rows.isEmpty()) throw new IllegalArgumentException("El ensayo debe contener repartidores.");
        for (int restart = 0; restart < 2; restart++) {
            try (var context = new SpringApplicationBuilder(CourierServiceApplication.class).run("--server.port=0", "--spring.profiles.active=local", "--logitrack.security.public-key-location=" + TestRsaKeys.publicResource().getURI())) {
                var repository = context.getBean(RepartidorRepository.class);
                for (var expected : rows) {
                    var bytes = ByteBuffer.wrap(HexFormat.of().parseHex(expected.path("id").asString()));
                    var id = new UUID(bytes.getLong(), bytes.getLong());
                    var actual = repository.findById(id).orElseThrow(() -> new IllegalStateException("Falta una fila importada."));
                    if (!actual.getId().equals(id)
                            || !actual.getNombre().equals(expected.path("nombre").asString())
                            || !actual.getTipoVehiculo().equals(expected.path("tipo_vehiculo").asString())
                            || !actual.getEstado().name().equals(expected.path("estado").asString())
                            || !actual.getCreatedAt().equals(instant(expected.path("created_at").asString()))
                            || !actual.getUpdatedAt().equals(instant(expected.path("updated_at").asString()))
                            || actual.getUsuarioId() != null) {
                        throw new IllegalStateException("La lectura JPA no coincide con el lote importado.");
                    }
                }
            }
        }
        System.out.println("MIGRATION_JPA_VERIFIED=" + rows.size() + ";RESTARTS=2");
    }

    private static Instant instant(String sqlDatetime) {
        return Instant.parse(sqlDatetime.replace(' ', 'T') + "Z");
    }
}
