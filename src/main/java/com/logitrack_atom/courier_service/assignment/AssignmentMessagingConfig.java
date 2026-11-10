package com.logitrack_atom.courier_service.assignment;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa transporte solo con opt-in; no declara colas ni agrega salud AMQP al núcleo. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "logitrack.assignment", name = "enabled", havingValue = "true")
public class AssignmentMessagingConfig {
    /** Crea la configuración opcional sin abrir conexiones durante su construcción. */
    public AssignmentMessagingConfig() { }

    /**
     * Configura una conexión con el usuario restringido de Courier.
     * @param host DNS privado del broker
     * @param port puerto AMQP privado
     * @param vhost vhost de la ampliación
     * @param user usuario restringido de Courier
     * @param password credencial externa obligatoria al activar publicación
     * @return fábrica sin RabbitAdmin ni declaración automática de topología
     */
    @Bean(destroyMethod = "destroy")
    public CachingConnectionFactory assignmentConnectionFactory(
            @Value("${MQ_HOST:rabbitmq}") String host, @Value("${MQ_PORT:5672}") int port,
            @Value("${MQ_VHOST:logitrack-s04}") String vhost,
            @Value("${MQ_COURIER_USER:lt_courier}") String user,
            @Value("${MQ_COURIER_PASSWORD:}") String password) {
        if (password.isBlank()) throw new IllegalArgumentException("Configurar credencial externa de Courier para MQ");
        var factory = new CachingConnectionFactory(host, port);
        factory.setVirtualHost(vhost);
        factory.setUsername(user);
        factory.setPassword(password);
        factory.setConnectionTimeout(3000);
        factory.getRabbitConnectionFactory().setRequestedHeartbeat(15);
        factory.getRabbitConnectionFactory().setChannelRpcTimeout(5000);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    /**
     * Configura publicación confirmada y devolución de mensajes sin ruta.
     * @param assignmentConnectionFactory fábrica privada
     * @return publicador con mandatory activo
     */
    @Bean
    public RabbitTemplate assignmentRabbitTemplate(CachingConnectionFactory assignmentConnectionFactory) {
        var template = new RabbitTemplate(assignmentConnectionFactory);
        template.setMandatory(true);
        return template;
    }

    /**
     * Recibe acceso SQL y transacciones locales.
     * @param outbox mensajes locales
     * @param assignmentRabbitTemplate transporte confirmado
     * @return tarea desacoplada
     */
    @Bean
    @ConditionalOnProperty(prefix = "logitrack.assignment", name = "publisher-enabled", havingValue = "true")
    public AssignmentPublisher assignmentPublisher(OutboxStore outbox, RabbitTemplate assignmentRabbitTemplate) {
        outbox.ensureSchemaReady();
        return new AssignmentPublisher(outbox, assignmentRabbitTemplate);
    }

    /**
     * Construye el límite de consumo manual sin declarar topología.
     * @param service commit local idempotente
     * @param mapper parser JSON de la aplicación
     * @param assignmentRabbitTemplate publicación de retry confirmada
     * @return listener que confirma después del commit
     */
    @Bean
    public AssignmentListener assignmentListener(AssignmentConsumerService service,
            tools.jackson.databind.ObjectMapper mapper, RabbitTemplate assignmentRabbitTemplate) {
        return new AssignmentListener(service, mapper, assignmentRabbitTemplate);
    }

    /**
     * Activa un consumidor recuperable con ACK manual y prefetch acotado.
     * @param assignmentConnectionFactory usuario restringido del vhost
     * @param assignmentListener límite de procesamiento
     * @param outbox verificación del esquema requerido
     * @return contenedor que recupera conexión sin apagar el portal
     */
    @Bean
    @ConditionalOnProperty(prefix = "logitrack.assignment", name = "consumer-enabled", havingValue = "true")
    public org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer assignmentListenerContainer(
            CachingConnectionFactory assignmentConnectionFactory, AssignmentListener assignmentListener, OutboxStore outbox) {
        outbox.ensureSchemaReady();
        var container = new org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer(assignmentConnectionFactory);
        container.setQueueNames("lt.courier.assignment.v1");
        container.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        container.setPrefetchCount(10);
        container.setConcurrentConsumers(1);
        container.setAutoDeclare(false);
        container.setMissingQueuesFatal(false);
        container.setDefaultRequeueRejected(false);
        container.setRecoveryInterval(5000);
        container.setMessageListener(assignmentListener);
        return container;
    }

}
