package com.acme.reliable.mq;

import com.acme.reliable.command.CommandExecutor;
import com.acme.reliable.processor.ResponseRegistry;
import com.acme.reliable.mq.IbmMqFactoryProvider;
import com.acme.reliable.mq.Mappers;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Message;
import io.micronaut.messaging.annotation.MessageBody;

@Requires(beans = IbmMqFactoryProvider.class)
@Requires(property = "jms.consumers.enabled", value = "true", defaultValue = "false")
@JMSListener("mqConnectionFactory")
public class CommandConsumers {
    private final CommandExecutor exec;
    private final ResponseRegistry responses;

    public CommandConsumers(CommandExecutor e, ResponseRegistry r) {
        this.exec = e;
        this.responses = r;
    }

    @Queue("APP.CMD.CREATEUSER.Q")
    public void onCreateUser(@MessageBody String body, @Message jakarta.jms.Message m) throws jakarta.jms.JMSException {
        var env = Mappers.toEnvelope(body, m);
        exec.process(env);
    }

    @Queue("APP.CMD.REPLY.Q")
    public void onReply(@MessageBody String body, @Message jakarta.jms.Message m) throws jakarta.jms.JMSException {
        var commandId = m.getStringProperty("commandId");
        if (commandId != null) {
            responses.complete(java.util.UUID.fromString(commandId), body);
        }
    }
}
