package com.logitrack_atom.courier_service.support;

import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * Simula el contrato administrativo de security mediante HTTP loopback real.
 *
 * @author MaxDz
 * @version 0.2.0
 */
public final class SecurityAccountStub {
    private static final HttpServer SERVER;
    private static final Map<UUID, String> ACCOUNTS = new ConcurrentHashMap<>();
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static volatile int status;
    private static volatile String body;
    private static volatile int delay;
    private static volatile String authorization;
    private static volatile CyclicBarrier barrier;
    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            SERVER.setExecutor(executor);
            SERVER.createContext("/api/v1/usuarios/", exchange -> {
                REQUESTS.incrementAndGet();
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                int responseStatus = status;
                String responseBody = body;
                int responseDelay = delay;
                CyclicBarrier responseBarrier = barrier;
                if (responseStatus == 0) {
                    UUID id = UUID.fromString(exchange.getRequestURI().getPath().substring("/api/v1/usuarios/".length()));
                    responseBody = ACCOUNTS.get(id);
                    responseStatus = responseBody == null ? 404 : 200;
                    if (responseBody == null) responseBody = "{}";
                }
                try {
                    if (responseBarrier != null) responseBarrier.await(3, TimeUnit.SECONDS);
                    if (responseDelay > 0) Thread.sleep(responseDelay);
                    byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Location", baseUrl() + "/destino-no-autorizado");
                    exchange.sendResponseHeaders(responseStatus, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.BrokenBarrierException | java.util.concurrent.TimeoutException ex) {
                    throw new java.io.IOException("No se alcanzó la barrera del ensayo concurrente.");
                } finally {
                    exchange.close();
                }
            });
            SERVER.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                SERVER.stop(0);
                executor.close();
            }, "limpieza-security-fixture"));
        } catch (java.io.IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private SecurityAccountStub() { }

    /** @return origen loopback exclusivo del proceso de pruebas */
    public static String baseUrl() { return "http://127.0.0.1:" + SERVER.getAddress().getPort(); }

    /** Restaura respuestas y contador sin modificar datos fuera del fixture. */
    public static void reset() {
        ACCOUNTS.clear(); REQUESTS.set(0); status = 0; body = null; delay = 0; authorization = null; barrier = null;
    }

    /**
     * Registra una cuenta sintética con el formato real del proveedor.
     * @param id identidad de cuenta
     * @param roles roles declarados
     */
    public static void cuenta(UUID id, String... roles) {
        ACCOUNTS.put(id, JsonMapper.builder().build().writeValueAsString(Map.of(
                "id", id, "roles", roles, "email", "fixture@example.com",
                "createdAt", "2026-10-27T12:00:00Z", "updatedAt", "2026-10-27T12:00:00Z")));
    }

    /**
     * Fuerza un resultado HTTP sin depender de cuentas reales.
     * @param code estado HTTP
     * @param response cuerpo de respuesta
     * @param delayMs demora antes de responder
     */
    public static void respuesta(int code, String response, int delayMs) {
        status = code; body = response; delay = delayMs;
    }

    /** @return solicitudes recibidas desde el último reset */
    public static int solicitudes() { return REQUESTS.get(); }

    /** @return cabecera de autenticación recibida, únicamente de tokens efímeros */
    public static String authorization() { return authorization; }

    /** Sincroniza dos verificaciones remotas antes de competir por la asociación local. */
    public static void sincronizarDosSolicitudes() { barrier = new CyclicBarrier(2); }
}
