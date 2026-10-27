# courier-service — LogiTrack Atom

Componente propietario de repartidores, estado operativo y disponibilidad.
Responsable principal: [@JMatiasF](https://github.com/JMatiasF).

## Base del servicio

Java 25, Spring Boot 4.1.1, Maven Wrapper y MySQL 8.4. El modelo conserva los datos
del Sprint01: UUID `BINARY(16)`, nombre, tipo de vehículo, ACTIVO/INACTIVO y auditoría
UTC `DATETIME(6)`. `usuario_id` es opcional y único; no hay relaciones JPA ni FK a
security o shipment. La disponibilidad del modelo significa ACTIVO, sin reservas.

El esquema está en `docker/mysql/init/001-schema.sql`. Ejecutarlo únicamente sobre
una base vacía `courier_db`, usando administración externa. El usuario de aplicación
`courier_app` solo recibe permisos en esa base. No usar root para arrancar el servicio,
no regenerar UUID históricos y no usar `ddl-auto=create` sobre datos existentes.

La inicialización de infraestructura y las contraseñas se suministran fuera de Git.
El archivo `.env.example` documenta los nombres; Spring no carga un archivo .env
por sí solo. Exportar DB_URL, DB_USERNAME y DB_PASSWORD desde el entorno o configurarlos
en el IDE. Nunca registrar contraseñas reales en comandos versionados.

```bash
./mvnw spring-boot:run
# Puerto 8081; MySQL debe estar preparado y accesible desde DB_URL.
```

JPA utiliza `ddl-auto=validate` y `open-in-view=false`. El arranque falla si falta la
conexión o el esquema. `GET /actuator/health` publica salud agregada sin componentes
ni detalles; no se exponen env/configprops. La integración obligatoria de Config Client
y los perfiles remotos se completan en la issue #5.

## Verificación reproducible

Java 25 y Docker deben estar disponibles:

```bash
./mvnw -B -ntp verify javadoc:javadoc
```

Las pruebas crean un MySQL 8.4.11 efímero con el SQL real y usuario courier_app,
sin usar el volumen del equipo. Comprueban UUID/auditoría, restricciones, acceso
limitado a la base, arranque HTTP y lectura de datos importados por JPA antes y
después de iniciar un segundo contexto de aplicación. Los secretos de prueba son
aleatorios. El Javadoc se valida con doclint completo y advertencias como errores.

## Migración desde shipment

Importar con SQL explícito los UUID, datos y fechas existentes; no construir entidades
nuevas mediante POST/save para recrear los registros históricos. Las filas migradas
quedan con usuario_id NULL hasta su asociación administrativa explícita. Leer un
registro importado no debe escribir ni actualizar su auditoría.

El procedimiento de exportación, verificación y reversión está en
[shipment-service#22](https://github.com/LogiTrack-Atom/shipment-service/issues/22).
La migración se ensaya en copias descartables antes de retirar la tabla o FK de origen.

## Desarrollo

Ramas por issue desde `develop`, PR hacia `develop` y seguimiento en el Project común.
Las altas y consultas REST se implementan en #2; la edición, estado y disponibilidad
en #3; JWT, pertenencia y asociación de cuentas se incorporan en #4; Config Client en #5;
y la verificación del componente completo en #6. Docker y Compose del componente
ya cuentan con una base verificada.

El Compose general y la documentación de integración pertenecen a shipment-service.

## Docker y Compose del componente

El Dockerfile y Compose de la inicialización se integran conservando el commit
[90d70e4](https://github.com/LogiTrack-Atom/courier-service/commit/90d70e4493c49186ebb0678ae62d193ee6590f35)
y reconciliando su historial con develop. Se conserva una sola aplicación, el modelo,
el SQL y la verificación JPA ya integrados. Continúan Boot 4.1.1 y Java 25.

El contenedor compila con Maven Wrapper en una etapa JDK y ejecuta únicamente el
JAR y una sonda de salud Java en una JRE. Las imágenes oficiales están fijadas por
digest y el runtime usa UID/GID 10001; no necesita curl ni wget. El contexto permite
solo Maven Wrapper, pom y src/main y excluye pruebas, Git, entornos y claves.

Preparar un archivo de entorno **externo** con las variables MYSQL_*, APP_PORT
y JWT_PUBLIC_KEY_FILE de .env.example. La clave pública del emisor se monta como
archivo de solo lectura en /run/keys/public.pem; el proceso 10001 debe poder leerla.
No se monta la clave privada en courier. Las contraseñas son obligatorias y no tienen un valor de respaldo.
Este Compose selecciona explícitamente el modo local del componente; Config Client
y la comprobación dev/prod se completan en #5 y #6.

```bash
docker compose --env-file /ruta/externa/courier.env up --build -d
docker compose --env-file /ruta/externa/courier.env ps
docker compose --env-file /ruta/externa/courier.env logs courier-service
# Detener conservando el volumen de datos:
docker compose --env-file /ruta/externa/courier.env down
```

El puerto interno es 8081 y la publicación local usa APP_PORT, por defecto 8081.
MySQL se publica solo en loopback, por defecto 3308. La aplicación usa el DNS
courier-mysql:3306 y el mismo MYSQL_DATABASE/USER del contenedor, con JPA validate.
El healthcheck de MySQL exige conexión con el usuario de aplicación y la tabla
repartidores disponible; el de courier comprueba /actuator/health en 8081.

La red y los nombres de contenedores están aislados por el nombre del proyecto
Compose; no se exige crear una red externa y no se fijan nombres globales que
impidan dos instancias de ensayo. El Compose general del sprint y las imágenes de
entrega publicadas continúan en shipment-service; este archivo es para el componente.
No borrar el volumen del equipo para aplicar un init ni para demostrar persistencia.

## API de altas y consultas

- POST /api/v1/repartidores recibe solamente nombre y tipoVehiculo; devuelve 201,
  Location, UUID generado, estado ACTIVO y auditoría UTC con microsegundos.
- GET /api/v1/repartidores devuelve una lista (vacía como []) y admite el filtro
  opcional estado=ACTIVO o INACTIVO.
- GET /api/v1/repartidores/{id} devuelve el DTO o 404; UUID/filtro malformado y
  campos inválidos/no admitidos devuelven 400 con ApiError.

No se aceptan id, usuarioId, estado ni auditoría en el registro. La respuesta no
incluye asociaciones de usuario ni entidades JPA. Las altas no modifican otros
repartidores. El [contrato OpenAPI](docs/openapi.json) describe estas operaciones.
Estas operaciones requieren JWT y el acceso indicado en la matriz de permisos de este README.

```json
{"nombre":"Repartidor de ejemplo","tipoVehiculo":"Moto"}
```

Ejemplo local de alta y consulta con un JWT ADMIN válido emitido por security
y cargado en la variable de shell JWT_ADMIN (no guardar el token en archivos versionados):

```bash
curl -H "Authorization: Bearer ${JWT_ADMIN}" -i -X POST http://localhost:8081/api/v1/repartidores \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Repartidor de ejemplo","tipoVehiculo":"Moto"}'
curl -H "Authorization: Bearer ${JWT_ADMIN}" 'http://localhost:8081/api/v1/repartidores?estado=ACTIVO'
```

El alta devuelve `201 Created` y `Location: /api/v1/repartidores/{id}`.
El UUID y las fechas del siguiente ejemplo son ilustrativos:

```json
{
  "id": "bf9c4dd3-a149-452c-bf89-158f0889b149",
  "nombre": "Repartidor de ejemplo",
  "tipoVehiculo": "Moto",
  "estado": "ACTIVO",
  "createdAt": "2026-10-27T12:00:00.123456Z",
  "updatedAt": "2026-10-27T12:00:00.123456Z"
}
```

Una consulta individual sin coincidencias devuelve `404 Not Found` con este formato:

```json
{
  "timestamp": "2026-10-27T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "mensaje": "No se encontró el repartidor solicitado.",
  "detalles": []
}
```


## Edición, estado y disponibilidad

- `PUT /api/v1/repartidores/{id}` reemplaza nombre y tipoVehiculo con el mismo DTO
  descriptivo del alta. Ambos campos son obligatorios. Conserva UUID, usuario,
  estado y fecha de creación; devuelve 200 con RepartidorResponse.
- `PATCH /api/v1/repartidores/{id}/estado` recibe únicamente
  `{"estado":"ACTIVO"}` o `{"estado":"INACTIVO"}`. No acepta ordinales numéricos
  ni campos ajenos. Devuelve 200 con RepartidorResponse; repetir el estado vigente
  no modifica los datos ni la auditoría.
- `GET /api/v1/repartidores/{id}/disponibilidad` consulta solo la base de courier,
  sin escrituras ni llamadas a shipment. Su respuesta tiene exactamente
  repartidorId, estado y disponible.

```bash
# Reemplazar el UUID de ejemplo por el obtenido en el alta.
curl -H "Authorization: Bearer ${JWT_ADMIN}" -X PUT http://localhost:8081/api/v1/repartidores/bf9c4dd3-a149-452c-bf89-158f0889b149 \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Repartidor de ejemplo","tipoVehiculo":"Bicicleta"}'
curl -H "Authorization: Bearer ${JWT_ADMIN}" -X PATCH http://localhost:8081/api/v1/repartidores/bf9c4dd3-a149-452c-bf89-158f0889b149/estado \
  -H 'Content-Type: application/json' -d '{"estado":"INACTIVO"}'
curl -H "Authorization: Bearer ${JWT_ADMIN}" http://localhost:8081/api/v1/repartidores/bf9c4dd3-a149-452c-bf89-158f0889b149/disponibilidad
```

Un repartidor INACTIVO existe y responde **200**, con este cuerpo:

```json
{
  "repartidorId": "bf9c4dd3-a149-452c-bf89-158f0889b149",
  "estado": "INACTIVO",
  "disponible": false
}
```

ACTIVO devuelve disponible true. La disponibilidad no implica reserva, exclusividad
ni cálculo de carga. Un UUID inexistente devuelve 404; un UUID o cuerpo inválido,
400 con ApiError. Una edición inválida no modifica parcialmente el registro.
Los ejemplos para ambos estados están incluidos en [OpenAPI](docs/openapi.json).
Las restricciones ADMIN/REPARTIDOR y la asociación de cuentas se aplican según la matriz siguiente.

## JWT, permisos y asociación de cuentas

Courier valida RS256 con la clave pública **externa** del mismo emisor que utiliza
security-service. No firma tokens, no contiene una clave privada, no usa sesiones
ni cookies de autenticación y no consulta a security para verificar cada petición.
Una clave ausente, inválida o RSA menor de 2048 bits impide arrancar.

Para ejecutar desde el IDE o Maven, definir `JWT_PUBLIC_KEY_LOCATION=file:/ruta/externa/public.pem`.
Para Docker, definir `JWT_PUBLIC_KEY_FILE` con el archivo del host; Compose lo monta
como solo lectura. No copiar claves al código, recursos ni imagen. El contrato
exige issuer `logitrack-security`, audiencia `logitrack-api`, sub UUID canónico,
roles conocidos no vacíos e iat/exp obligatorios, con vida máxima de 15 minutos y
tolerancia de reloj máxima de 60 segundos. Config Client centralizará estas
propiedades en #5, conservando los mismos nombres.

| Operación | Acceso |
| --- | --- |
| POST /api/v1/repartidores | ADMIN |
| GET /api/v1/repartidores | ADMIN |
| GET /api/v1/repartidores/{id} | ADMIN o REPARTIDOR asociado |
| PUT /api/v1/repartidores/{id} | ADMIN |
| PATCH /api/v1/repartidores/{id}/estado | ADMIN |
| GET /api/v1/repartidores/{id}/disponibilidad | ADMIN o REPARTIDOR asociado |
| PUT /api/v1/repartidores/{id}/usuario | ADMIN |
| GET /actuator/health | Público, sin detalles internos |

CLIENTE no accede a la API de repartidores. Un REPARTIDOR sin asociación no puede
reclamar un registro por conocer su UUID ni por usar ese UUID como sub. Las rutas
no declaradas se deniegan. Bearer ausente, inválido o vencido devuelve 401 con
WWW-Authenticate: Bearer; rol o pertenencia insuficientes devuelven 403. Ambos
utilizan ApiError. Para un REPARTIDOR, las consultas ajenas o sin asociación se
denominan denegadas, sin revelar si existe el recurso. ADMIN conserva el 404 normal.

La vinculación se solicita con `{"usuarioId":"<UUID de cuenta>"}`:

```bash
curl -X PUT http://localhost:8081/api/v1/repartidores/bf9c4dd3-a149-452c-bf89-158f0889b149/usuario \
  -H "Authorization: Bearer ${JWT_ADMIN}" -H 'Content-Type: application/json' \
  -d '{"usuarioId":"8f3fd53c-83a4-4cdd-bf50-4b34dca59a81"}'
```

Antes de guardar, courier consulta GET /api/v1/usuarios/{id} en el origen
`SECURITY_SERVICE_URL`, reenviando exclusivamente el Bearer ADMIN ya validado.
Los tiempos iniciales son 2 segundos de conexión y 3 de lectura, configurables
por SECURITY_CONNECT_TIMEOUT y SECURITY_READ_TIMEOUT. No se siguen redirecciones
ni se agregan reintentos de aplicación. La lectura de una respuesta se limita a
64 KiB y se comprueban UUID y roles, sin conservar email ni auditoría de la cuenta.

La consulta remota termina antes de abrir la transacción local de escritura.
Las modificaciones de un mismo repartidor usan bloqueo de fila y la base impone
unicidad de usuario. Repetir una asociación idéntica devuelve 200 sin escritura ni
otra consulta; reemplazarla por otra cuenta devuelve 409. La respuesta sigue siendo
RepartidorResponse y no expone usuarioId.

| Resultado de la asociación | Respuesta de courier |
| --- | --- |
| Repartidor o cuenta inexistente | 404 |
| Cuenta sin rol REPARTIDOR | 422 |
| Cuenta ya vinculada o intento de reemplazo | 409 |
| Security caído, timeout, 429 o 5xx | 503, sin vinculación |
| Respuesta incoherente, JSON inválido, redirección o 401/403 remoto inesperado | 502, sin vinculación |

El Compose del componente no inicia security. Sus lecturas y operaciones locales
siguen funcionando con JWT válido aunque security no esté disponible; una nueva
asociación requiere acceso al proveedor. Para probarla, configurar
SECURITY_SERVICE_URL con un origen accesible **desde el contenedor**, no con su
propio localhost. En el Compose general se utilizará http://security-service:8082.

Las pruebas generan claves efímeras, verifican firmas y claims, ejecutan la matriz
completa por rol/pertenencia y usan un proveedor HTTP loopback para ensayar
asociaciones, fallos y concurrencia sin cuentas del equipo. La validación del
componente integrado con configuración remota continúa en #5 y #6.
