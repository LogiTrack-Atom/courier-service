# Consumidor de disponibilidad — Courier #21

Consume `assignment.availability.requested.v1` y publica el resultado v1. La copia del
contrato y su procedencia inmutable están en `assignment-contract/source.json` (Shipment
#76/#78). Solo consulta `repartidores` en su base: AVAILABLE es observación puntual de
ACTIVO; INACTIVE y NOT_FOUND son resultados de negocio, no errores reintentables. No
reserva capacidad, no modifica al repartidor y no hace HTTP a Security/Shipment.

## Commit y ACK

El listener valida JSON UTF-8 de hasta 16 KiB, campos/version/tipo, UUID canónicos,
generación positiva representable, timestamps UTC y metadatos de correlación antes de
SQL. La transacción propia READ COMMITTED guarda inbox y resultado/outbox juntos. La
constraint de messageId y la huella SHA-256 detectan reentrega frente a identidad usada
con otro cuerpo; operationId es único para impedir un segundo comando de la misma
operación. La carrera se reconcilia después del rollback en otra transacción.

ACK se emite solo después de que el servicio retorna de su commit. Un corte entre commit
y ACK permite reentrega, que reconoce el inbox y conserva el resultado original, aunque
el repartidor haya cambiado después. El resultado/outbox no se elimina automáticamente:
limpiar inbox/outbox exige una política futura explícita para no perder deduplicación.
Publicación a `lt.assignment.results.v1` con mandatory y confirms, messageId persistente,
correlationId=operationId y causationId del comando. Se usan leases SQL cortas y no se
mantiene una transacción de base abierta esperando al broker.

Contrato inválido, metadatos incoherentes o reutilización incompatible se rechazan sin
requeue hacia DLQ. Logs con motivo fijo, UUID validado o `invalid` y hash; sin cuerpo,
JWT, correo, contraseña o excepción remota. Fallo SQL transitorio publica una copia
persistente a retry y espera confirm antes del ACK original; conserva cuerpo/messageId
y aumenta x-retry-count. Tras 3 reintentos rechaza a DLQ. Si retry no se confirma,
conserva el original con NACK/requeue y pausa 5 s fuera de SQL; la política quorum de
#77 limita a 10 redeliveries para evitar ciclo infinito. No se reinyecta DLQ automáticamente.

El vencimiento final y la vigencia de la operación se deciden en Shipment #79: una lectura
puntual de Courier no garantiza que el envío siga admitiendo ese resultado. No exigir
un JWT de usuario válido al procesar: la autorización se comprobó al aceptar y AMQP
usa una cuenta de servicio restringida, sin transportar Bearer.

## Instalación y configuración

SQL aditivo separado del núcleo: primero el esquema existente 001, después aplicar
una vez `docker/mysql/mq/004-assignment-inbox-outbox.sql` a Courier, con copia/backup
comprobado. No modifica `repartidores` ni agrega FK a otras bases. Registrar checksum y
resultado en el manifiesto MQ #80. DDL MySQL implica commits: ante interrupción revisar
el estado y reanudar explícitamente; no ocultar diferencias con IF NOT EXISTS ni borrar
volúmenes para migrar. Las pruebas instalan sobre la base existente y verifican una ficha
previa con identidad/estado/timestamps conservados; el fixture real también inicializa
una base nueva con 001+004.

El Compose vive en shipment-service: `compose.sprint04-assignment.yaml` con
ASSIGNMENT_ENABLED=false por defecto. Habilitar únicamente después de aplicar SQL y
completar #79/#80 con imágenes nuevas; las imágenes P0 permanecen intactas.
Variables privadas para Courier: MQ_HOST, MQ_PORT, MQ_VHOST, MQ_COURIER_USER (lt_courier)
y MQ_COURIER_PASSWORD. ASSIGNMENT_PUBLISHER_ENABLED/ASSIGNMENT_CONSUMER_ENABLED controlan
transporte para diagnóstico, por defecto true al habilitar MQ. Usuario sin configure,
lectura solo de `lt.courier.assignment.v1` y publicación solo results/retry según #77.

No hay RabbitAdmin ni declaración automática por la aplicación. Contenedor manual,
prefetch 10, un consumidor y recuperación cada 5 s; el broker no forma parte de la salud
requerida para consultas HTTP. Al activar transporte se verifica el esquema de inbox/
outbox; falta de migración falla explícitamente. Sin MQ no se crean conexiones AMQP.

## Verificación

```sh
./mvnw -B -ntp verify javadoc:javadoc
```

- 14 pruebas unitarias nuevas: contrato (5), decisiones de listener (5), publicador (4).
  Dobles controlados, no presentados como broker real.
- 8 casos MySQL: AVAILABLE/INACTIVE/NOT_FOUND sin mutación, replay estable, cuerpo distinto
  con mismo messageId, segundo comando de una operación, carrera y rollback inbox/outbox.
- 1 escenario con MySQL y RabbitMQ reales: corta la conexión nativa antes del ACK,
  observa redelivered y el mismo resultado único, envía inválido a DLQ; después ejecuta
  el consumidor con permisos restringidos, reinicia el broker, comprueba salud HTTP 200,
  recuperación automática y publicación real de ambos resultados. El fixture conserva
  un puerto local estable y las credenciales solo viven en el laboratorio descartable.

La topología del fixture es de prueba, declarada por el operador; no acredita por sí
sola todos los fallos DLX. #77 verifica el broker entregable y #80 conserva los ensayos
conjuntos de caídas, consumidores, outbox/inbox/DLQ y release con digests/migraciones.
Shipment #79 aún debe aplicar el resultado con sus guardas de concurrencia.
