package com.logitrack_atom.courier_service.entity;

import com.logitrack_atom.courier_service.enums.EstadoRepartidor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/**
 * Comprueba invariantes del modelo sin reemplazar la prueba de persistencia MySQL.
 * @author MaxDz
 * @version 0.2.0
 */
class RepartidorTest {
    /** Verifica estado inicial e independencia de identidad respecto del usuario. */
    @Test void altaActivaSinIdentidadAsignadaPorElSolicitante() {
        var repartidor = new Repartidor("Repartidor", "Moto");
        assertThat(repartidor.getId()).isNull();
        assertThat(repartidor.getUsuarioId()).isNull();
        assertThat(repartidor.getEstado()).isEqualTo(EstadoRepartidor.ACTIVO);
        assertThat(repartidor.estaDisponible()).isTrue();
    }

    /** Rechaza textos inválidos antes de mutar el modelo. */
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t"})
    void rechazaNombreInvalido(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Repartidor(value,"Moto"));
    }

    /** Aplica los mismos límites al tipo de vehículo. */
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t"})
    void rechazaVehiculoInvalido(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Repartidor("Nombre",value));
    }

    /** Los límites coinciden con los VARCHAR(255) del esquema versionado. */
    @Test void limitaLongitudesSinTruncar() {
        assertThat(new Repartidor("a".repeat(255), "v".repeat(255)).getNombre()).hasSize(255);
        assertThatIllegalArgumentException().isThrownBy(() -> new Repartidor("a".repeat(256), "Moto"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Repartidor("Nombre", "v".repeat(256)));
    }

    /** Una validación fallida no actualiza parcialmente nombre o vehículo. */
    @Test void actualizacionAtomicaDelModelo() {
        var repartidor = new Repartidor("Anterior", "Moto");
        assertThatIllegalArgumentException().isThrownBy(() -> repartidor.actualizarDatos("Nuevo", ""));
        assertThat(repartidor.getNombre()).isEqualTo("Anterior");
        assertThat(repartidor.getTipoVehiculo()).isEqualTo("Moto");
    }

    /** La disponibilidad depende solo del estado y las consultas no lo cambian. */
    @Test void estadoExplicitoIdempotenteYDisponibilidadSinEfectos() {
        var repartidor = new Repartidor("Nombre", "Moto");
        repartidor.cambiarEstado(EstadoRepartidor.INACTIVO);
        repartidor.cambiarEstado(EstadoRepartidor.INACTIVO);
        assertThat(repartidor.estaDisponible()).isFalse();
        assertThat(repartidor.getEstado()).isEqualTo(EstadoRepartidor.INACTIVO);
        assertThatIllegalArgumentException().isThrownBy(() -> repartidor.cambiarEstado(null));
        assertThat(repartidor.getEstado()).isEqualTo(EstadoRepartidor.INACTIVO);
    }

    /** No reemplaza asociaciones existentes y la actualización descriptiva las conserva. */
    @Test void asociacionExplicitaNoReemplazable() {
        var repartidor = new Repartidor("Nombre", "Moto");
        UUID usuario = UUID.randomUUID();
        repartidor.asociarUsuario(usuario);
        repartidor.asociarUsuario(usuario);
        assertThatIllegalArgumentException().isThrownBy(() -> repartidor.asociarUsuario(UUID.randomUUID()));
        assertThatIllegalArgumentException().isThrownBy(() -> repartidor.asociarUsuario(null));
        repartidor.actualizarDatos("Otro nombre", "Bicicleta");
        assertThat(repartidor.getUsuarioId()).isEqualTo(usuario);
    }
}
