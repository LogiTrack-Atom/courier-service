package com.logitrack_atom.courier_service.integration;

import com.logitrack_atom.courier_service.repository.RepartidorRepository;
import com.logitrack_atom.courier_service.support.TestRsaKeys;
import com.logitrack_atom.courier_service.support.SecurityAccountStub;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import java.nio.file.Path;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proporciona un MySQL exclusivo, con el SQL real y commits de transacción efectivos.
 * @author MaxDz
 * @version 0.2.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class MySqlIntegrationSupport {
    /** Contenedor efímero que Testcontainers retira al terminar el proceso. */
    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
        .withDatabaseName("courier_db").withUsername("courier_app")
        .withPassword(UUID.randomUUID().toString()).withUrlParam("serverTimezone", "UTC")
        .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/mysql/init/001-schema.sql").toAbsolutePath()),
                "/docker-entrypoint-initdb.d/001-schema.sql");
    static { MYSQL.start(); }

    /** Sustituye únicamente la conexión del contexto de prueba. */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestRsaKeys.registrar(registry);
        registry.add("clients.security.base-url", SecurityAccountStub::baseUrl);
        registry.add("clients.security.connect-timeout", () -> "200ms");
        registry.add("clients.security.read-timeout", () -> "1s");
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    /** Repositorio real de repartidores. */
    @Autowired protected RepartidorRepository repository;
    /** Conexión de aplicación limitada a courier_db. */
    @Autowired protected JdbcTemplate jdbc;
    /** Coordinación de commits locales en cada operación del ensayo. */
    @Autowired protected PlatformTransactionManager transactions;

    /** Elimina solo las filas de la base temporal previamente identificada. */
    @BeforeEach void limpiar() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("courier_db");
        jdbc.update("DELETE FROM repartidores");
    }

    /**
     * Ejecuta una unidad de trabajo con commit real, sin transacción global del test.
     * @param work trabajo sobre la persistencia
     */
    protected void transaction(Runnable work) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> work.run());
    }
}
