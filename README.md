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
en #3; autenticación, pertenencia y asociación de cuentas en #4; Config Client en #5;
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

Preparar un archivo de entorno **externo** con las variables MYSQL_* y APP_PORT de
.env.example. Las contraseñas son obligatorias y no tienen un valor de respaldo.
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
JWT y las restricciones por rol/pertenencia se completan en #4 antes de la entrega.

```json
{"nombre":"Repartidor de ejemplo","tipoVehiculo":"Moto"}
```

Ejemplo local de alta y consulta:

```bash
curl -i -X POST http://localhost:8081/api/v1/repartidores \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Repartidor de ejemplo","tipoVehiculo":"Moto"}'
curl 'http://localhost:8081/api/v1/repartidores?estado=ACTIVO'
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
