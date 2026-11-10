-- Ampliación MQ de Courier: aplicar una vez tras el esquema existente y sobre copia/backup.
-- DDL MySQL hace commits implícitos. No reemplazar tablas ni reejecutar a ciegas tras interrupción.
CREATE TABLE assignment_inbox (
    message_id BINARY(16) NOT NULL PRIMARY KEY,
    operation_id BINARY(16) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    result_message_id BINARY(16) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_assignment_inbox_operation (operation_id),
    UNIQUE KEY uk_assignment_inbox_result (result_message_id)
) ENGINE=InnoDB;

CREATE TABLE amqp_outbox (
    message_id BINARY(16) NOT NULL PRIMARY KEY,
    operation_id BINARY(16) NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    lease_token BINARY(16) NULL,
    lease_until DATETIME(6) NULL,
    published_at DATETIME(6) NULL,
    KEY idx_amqp_outbox_pending (published_at, next_attempt_at, lease_until)
) ENGINE=InnoDB;
