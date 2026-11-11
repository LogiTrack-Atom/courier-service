package com.logitrack_atom.courier_service.integration;

import com.logitrack_atom.courier_service.entity.Repartidor;
import com.logitrack_atom.courier_service.support.TestRsaKeys;
import com.logitrack_atom.courier_service.support.SecurityAccountStub;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la consulta administrativa de asociaciones por HTTP y MySQL reales.
 * @author MaxDz
 * @version 0.2.0
 */
class RepartidorIdentidadIT extends MySqlIntegrationSupport {
    @Value("${local.server.port}") private int port;
    @Test
    void consultaNullableYAsociadaSinMutacionNiProveedor() throws Exception {
        SecurityAccountStub.reset();var row=repository.saveAndFlush(new Repartidor("Identidad","Moto"));
        var before=row.getUpdatedAt();var token=TestRsaKeys.token(UUID.randomUUID(),"ADMIN");
        var initial=consultar(row.getId(),token);assertThat(initial.statusCode()).isEqualTo(200);
        assertThat(JsonMapper.builder().build().readTree(initial.body()).path("usuarioId").isNull()).isTrue();
        var user=UUID.randomUUID();transaction(()->repository.findByIdForUpdate(row.getId()).orElseThrow().asociarUsuario(user));
        var current=repository.findById(row.getId()).orElseThrow();var result=consultar(row.getId(),token);
        assertThat(result.statusCode()).isEqualTo(200);
        var body=JsonMapper.builder().build().readTree(result.body());assertThat(body.size()).isEqualTo(2);
        assertThat(body.path("usuarioId").asString()).isEqualTo(user.toString());
        assertThat(body.path("repartidorId").asString()).isEqualTo(row.getId().toString());
        assertThat(repository.findById(row.getId()).orElseThrow().getUpdatedAt()).isEqualTo(current.getUpdatedAt());
        assertThat(SecurityAccountStub.solicitudes()).isZero();
        assertThat(consultar(UUID.randomUUID(),token).statusCode()).isEqualTo(404);
    }
    @ParameterizedTest
    @ValueSource(strings={"CLIENTE","REPARTIDOR","ANONIMO"})
    void rechazaLecturaNoAdministrativa(String role) throws Exception {
        var row=repository.saveAndFlush(new Repartidor("Privado","Moto"));
        var response=consultar(row.getId(),role.equals("ANONIMO")?null:TestRsaKeys.token(UUID.randomUUID(),role));
        int expected=role.equals("ANONIMO")?401:403;assertThat(response.statusCode()).isEqualTo(expected);
        assertThat(response.body()).contains("mensaje").doesNotContain(row.getId().toString(),"usuarioId");
    }
    private HttpResponse<String> consultar(UUID id,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/repartidores/"+id+"/usuario"));
        if(token!=null)request.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
}
