# Courier — verificación del componente e integración del Sprint 02

Fecha: 27 de octubre de 2026.

## Versión y alcance comprobados

La base funcional es el árbol de `477259f9f0a53bf948b89a8a5b9d3564821244ff`, integrado en develop
mediante [PR #13](https://github.com/LogiTrack-Atom/courier-service/pull/13).
Esta consolidación agrega documentación; no cambia producción, SQL ni contrato.

| Componente | Revisión utilizada |
| --- | --- |
| Courier | `477259f9f0a53bf948b89a8a5b9d3564821244ff` |
| Propiedades en GitHub | `477acba615cb97a8a6e6d8d83784c2c15ccb0e6d` |
| Merge de propiedades en develop | `bd7b29f82145b0372dbe4ff6da2883382defd9af` |
| Config Server del equipo | `60d9d8f291faedecf7c65e6dc682673f13ed3622` |

El merge de propiedades conserva el árbol que se probó. Los snapshots de pruebas
identifican su origen y SHA-256 en src/test/resources/config-repository/source.json.

La [CI del consumidor](https://github.com/LogiTrack-Atom/courier-service/actions/runs/40565098704)
aprobó **69 pruebas unitarias y 110 de integración**, 179 en total, sin fallos,
errores ni omitidas, además del Javadoc estricto. Se ejecutaron MySQL 8.4.11,
HTTP real, validación JWT y Config Server con Git temporal.

## Evidencia funcional

| Área | Resultado verificado |
| --- | --- |
| Alta y consultas | 201 con Location/UUID/ACTIVO; consulta 200/404; listado y filtros; DTOs sin entidades ni usuarioId |
| Edición | Conserva identidad, asociación, creación y estado; entradas inválidas sin cambios parciales |
| Estado | ACTIVO/INACTIVO explícitos; repetición idempotente sin alterar auditoría |
| Disponibilidad | Tres campos acordados; ACTIVO true, INACTIVO 200/false; consulta sin escritura ni llamadas externas |
| Persistencia | UUID BINARY(16), auditoría UTC DATETIME(6), importación histórica y lectura tras otro arranque |
| Identidad | sub identifica a la cuenta; pertenencia mediante asociación local; accesos ajenos o sin asociación denegados |
| Autorización | Matriz completa por ruta y rol; ausencia/fallo de JWT 401, permisos insuficientes 403; ApiError |
| Asociación | ADMIN verifica cuenta y rol; unicidad y bloqueo de fila; duplicados/reemplazos 409; carrera entre dos vinculaciones controlada |
| Integración de cuentas | Timeouts, conexión rechazada, JSON/identidad/roles inválidos y redirecciones; sin escritura ante fallo |
| Configuración | Beans efectivos de marcador/JWT/timeouts, puerto y base desde dev/prod; compatibilidad de Cloud activa |

El proveedor de cuentas de las pruebas de asociación es un fixture HTTP loopback
conforme al contrato de security. Esa prueba no acredita por sí sola el sistema
completo desplegado. La validación conjunta con shipment corresponde a sus issues
[23](https://github.com/LogiTrack-Atom/shipment-service/issues/23),
[24](https://github.com/LogiTrack-Atom/shipment-service/issues/24) y
[25](https://github.com/LogiTrack-Atom/shipment-service/issues/25).

## Imagen y propiedades remotas reales

Se construyó la imagen desde el Dockerfile del repositorio y se ejecutó contra el
JAR real de Config Server del equipo, con configuración obtenida de GitHub por el
commit indicado y un MySQL descartable con courier_app. Dev y prod alcanzaron
healthy; el bean consumió courier-service-dev/prod y registró el marcador al arrancar.

En ambos ambientes se verificaron salud 200, ausencia de token 401, CLIENTE 403,
ADMIN 200, alta 201, disponibilidad y rechazo de estado numérico/campos ajenos.
La API local siguió funcionando sin security disponible. Se rechazaron arranques
sin Config Server, sin pública o sin contraseña de base y con prod+local sin servidor.
Los placeholders permanecieron sin resolver en Config Server aunque su proceso
recibiera una variable DB_PASSWORD homónima.

| Artefacto inspeccionado | Valor |
| --- | --- |
| ID local de imagen | `sha256:710263404905854e6a1f7ca01a5e80a4397ad640912761e4a8ece31282448c8b` |
| SHA-256 del JAR extraído de esa imagen | `857ed7ac8e361bf5ac05a44e62f1e1018ef90b50f3ce62bcae0f0828c9e999e4` |
| Usuario de ejecución | 10001:10001 |
| Entrada | java -jar /app/app.jar |
| Puerto interno | 8081 |
| Healthcheck | Java, GET /actuator/health |
| Archivos en /app | app.jar y healthcheck |

Se auditó la imagen sin red ni credenciales. No contiene workspace de compilación,
Git, .env, caché Maven, claves, fixtures, perfil test ni Config Server. Sí contiene
Config Client y el modo local explícito. La clave pública se montó de solo lectura;
la privada no se entregó a courier. Los logs del ensayo no contuvieron secretos.
Solo se utilizaron y retiraron recursos temporales propios; no se alteraron datos
ni volúmenes del equipo.

El ID identifica una imagen **local verificada**. No acredita publicación en un
registro ni es una etiqueta de entrega de los cinco repositorios. La publicación
conjunta se registra en la [coordinación del Sprint](https://github.com/LogiTrack-Atom/shipment-service/issues/29).
La ejecución del JAR de Config Server tampoco acredita su Dockerfile propio,
pendiente en [configserver#4](https://github.com/LogiTrack-Atom/configserver/issues/4).

## Reproducción

Requiere Java 25, Docker y acceso a las imágenes/dependencias utilizadas:

```bash
./mvnw -B -ntp verify javadoc:javadoc
docker build --tag courier-service:local .
```

Las pruebas generan sus propios MySQL y claves efímeros. El perfil test importa
la configuración aislada y no requiere secretos del equipo ni un Config Server
externo. Las pruebas remotas arrancan uno real con Git temporal.

Para ejecutar el componente con Compose, preparar un archivo de entorno externo
según .env.example, con contraseñas propias y ruta pública legible por el proceso.
Usar un nombre de proyecto nuevo para mantener separados sus volúmenes:

```bash
docker compose --project-name courier-prueba --env-file /ruta/externa/courier.env up --build -d
docker compose --project-name courier-prueba --env-file /ruta/externa/courier.env ps
curl http://localhost:8081/actuator/health
# Detener conservando los datos de ese proyecto:
docker compose --project-name courier-prueba --env-file /ruta/externa/courier.env down
```

COURIER_PROFILE=local permite la ejecución aislada. Dev/prod requieren un Config
Server accesible desde el contenedor y CONFIG_LABEL explícito para reproducibilidad.
El Compose general, las credenciales de entrega y sus redes se preparan en shipment;
no se deben eliminar volúmenes existentes para aplicar un init o demostrar persistencia.

## Contrato para shipment

- Origen en la red general: http://courier-service:8081. El puerto se configura
  desde el entorno; no se consulta la base de courier desde otro servicio.
- Usar GET /api/v1/repartidores/{id}/disponibilidad con el Bearer validado del
  solicitante, hacia el origen configurado y con timeouts. ADMIN consulta cualquier
  registro; REPARTIDOR consulta únicamente su asociación local.
- La respuesta tiene repartidorId (UUID), estado y disponible. INACTIVO responde
  200 con false; inexistente responde 404 para ADMIN. La disponibilidad no reserva,
  no calcula carga y no depende de shipment ni de security para responder.
- Mantener referencias UUID sin relaciones JPA entre servicios. La migración
  conserva UUID y auditoría; usuarioId puede quedar nulo hasta su vinculación
  administrativa. No inferir pertenencia por conocer un UUID o un email.
- La validación local de permisos precede a la llamada remota. Los errores de
  courier conservan ApiError; su traducción en shipment sigue el
  [contrato compartido](https://github.com/LogiTrack-Atom/shipment-service/blob/491805d31044a363e9c3a5a2e7f979d8afaad314/docs/sprint02/Contratos_tecnicos.md).
- El [OpenAPI](../openapi.json) del servicio está en docs/openapi.json y el
  [README](../../README.md) incluye todas las rutas, roles, ejemplos y variables.

No se incorporan API Gateway, Eureka, mensajería, reservas distribuidas ni
transacciones entre bases como parte de esta entrega del componente.
