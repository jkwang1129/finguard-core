package com.finguard.core.messaging.consumer.importjob;

import com.finguard.core.messaging.config.RabbitMessagingConfiguration;
import com.finguard.core.messaging.consumer.config.ImportConsumerConfiguration;
import com.finguard.core.messaging.outbox.message.JobRequestedMessage;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ImportJobMessageListener {

    private final ImportJobMessageHandler handler;

    public ImportJobMessageListener(ImportJobMessageHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(
            queues = RabbitMessagingConfiguration.IMPORT_QUEUE,
            containerFactory = ImportConsumerConfiguration.CONTAINER_FACTORY,
            autoStartup =
                    "${finguard.messaging.import-consumer.enabled:true}"
    )
    public void onMessage(
            JobRequestedMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        handler.handle(message);
        channel.basicAck(deliveryTag, false);
    }
}
