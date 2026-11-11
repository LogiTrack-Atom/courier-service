package com.logitrack_atom.courier_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Define los únicos datos admitidos para registrar o describir un repartidor.
 * @param nombre nombre obligatorio, hasta 255 caracteres
 * @param tipoVehiculo vehículo obligatorio, hasta 255 caracteres
 * @author MaxDz
 * @version 0.2.0
 */
public record RepartidorRequest(
        @NotBlank @Size(max = 255) String nombre,
        @NotBlank @Size(max = 255) String tipoVehiculo) { }
