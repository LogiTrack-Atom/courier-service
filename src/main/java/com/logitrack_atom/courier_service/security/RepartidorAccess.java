package com.logitrack_atom.courier_service.security;

import com.logitrack_atom.courier_service.repository.RepartidorRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Resuelve pertenencia mediante la asociación local, sin equiparar sub al UUID del dominio.
 *
 * @author MaxDz
 * @version 0.2.0
 */
@Component("repartidorAccess")
public class RepartidorAccess {
    private final RepartidorRepository repository;

    /**
     * Recibe el repositorio propietario de la asociación.
     * @param repository persistencia local
     */
    public RepartidorAccess(RepartidorRepository repository) {
        this.repository = repository;
    }

    /**
     * Permite consultar únicamente el registro vinculado al REPARTIDOR autenticado.
     * @param id UUID de dominio solicitado
     * @param authentication identidad previamente validada por el resource server
     * @return verdadero si existe asociación local con el sub validado
     */
    public boolean puedeConsultar(UUID id, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()
                || jwt.getAuthorities().stream().noneMatch(a -> "ROLE_REPARTIDOR".equals(a.getAuthority()))) {
            return false;
        }
        return repository.findByUsuarioId(UUID.fromString(jwt.getToken().getSubject()))
                .map(repartidor -> repartidor.getId().equals(id)).orElse(false);
    }
}
