package com.acme.payments.infrastructure.messaging;

import com.acme.reliable.command.CommandHandlerRegistry;
import com.acme.reliable.mq.BaseCommandConsumer;
import com.acme.reliable.spi.CommandQueue;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Message;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.jms.JMSException;
import lombok.extern.slf4j.Slf4j;

/**
 * JMS Consumer for payment commands
 */
@Requires(notEnv = "test")
@Requires(property = "jms.consumers.enabled", value = "true", defaultValue = "false")
@JMSListener("mqConnectionFactory")
@Slf4j
public class PaymentCommandConsumer extends BaseCommandConsumer {
    private static final String REPLY_QUEUE = "APP.CMD.REPLY.Q";

    public PaymentCommandConsumer(CommandHandlerRegistry registry, CommandQueue commandQueue) {
        super(registry, commandQueue, REPLY_QUEUE);
    }

    @Queue("APP.CMD.PAYMENT.Q")
    public void onPaymentCommand(@MessageBody String body, @Message jakarta.jms.Message m) throws JMSException {
        String commandType = m.getStringProperty("commandType");
        processCommand(commandType != null ? commandType : "Payment", body, m);
    }

    @Queue("APP.CMD.CREATEACCOUNT.Q")
    public void onCreateAccountCommand(@MessageBody String body, @Message jakarta.jms.Message m) throws JMSException {
        processCommand("CreateAccount", body, m);
    }

    @Queue("APP.CMD.CREATETRANSACTION.Q")
    public void onCreateTransactionCommand(@MessageBody String body, @Message jakarta.jms.Message m) throws JMSException {
        processCommand("CreateTransaction", body, m);
    }

    @Queue("APP.CMD.BOOKLIMITS.Q")
    public void onBookLimitsCommand(@MessageBody String body, @Message jakarta.jms.Message m) throws JMSException {
        processCommand("BookLimits", body, m);
    }

    @Queue("APP.CMD.BOOKFX.Q")
    public void onBookFxCommand(@MessageBody String body, @Message jakarta.jms.Message m) throws JMSException {
        processCommand("BookFx", body, m);
    }

    @Queue("APP.CMD.CREATEPAYMENT.Q")
    public void onCreatePaymentCommand(@MessageBody String body, @Message jakarta.jms.Message m) throws JMSException {
        processCommand("CreatePayment", body, m);
    }
}
