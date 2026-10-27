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
Las capas REST/DTO/service y su contrato se completan en #2 y #3; autenticación,
pertenencia y asociación de cuentas en #4; Config Client en #5; Docker/OpenAPI y
verificación del componente completo en #6. No se presentan como entregadas antes
de implementarlas y probarlas.

El Compose general y la documentación de integración pertenecen a shipment-service.
