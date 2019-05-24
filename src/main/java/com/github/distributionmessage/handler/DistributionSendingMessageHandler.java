package com.github.distributionmessage.handler;

import com.github.distributionmessage.config.DistributionProp;
import com.github.distributionmessage.constant.CommonConstant;
import com.github.distributionmessage.utils.CommonUtils;
import com.github.distributionmessage.utils.DistributionUtils;
import com.ibm.mq.jms.MQQueue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.integration.jms.DefaultJmsHeaderMapper;
import org.springframework.integration.jms.JmsHeaderMapper;
import org.springframework.integration.jms.JmsSendingMessageHandler;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

@Data
@EqualsAndHashCode(callSuper=false)
public class DistributionSendingMessageHandler extends JmsSendingMessageHandler {

    private final JmsTemplate jmsTemplate;

    private JmsHeaderMapper headerMapper = new DefaultJmsHeaderMapper();

    private DistributionProp distributionProp;

    public DistributionSendingMessageHandler(JmsTemplate jmsTemplate) {
        super(jmsTemplate);
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    protected void handleMessageInternal(Message<?> message) {
        MessagePostProcessor messagePostProcessor = new HeaderMappingMessagePostProcessor(message, this.headerMapper);
        Assert.notNull(this.distributionProp, "distributionProp must not be null");
        Assert.notNull(message, "Message must not be null");
        Object playload = message.getPayload();
        Assert.notNull(playload, "Message playload must not be null");
        if (playload instanceof byte[]) {
            try {
                byte[] bytes = (byte[]) playload;
                MQQueue queue = new MQQueue();
                queue.setCCSID(this.distributionProp.getCcsid());
                String sm = new String(bytes, CommonConstant.CHARSET);
                String dxpid = DistributionUtils.getDxpIdByMessage(sm);
                String msgtype = DistributionUtils.getMessageType(sm);
                String queueName = DistributionUtils.getDestinationQueueName(this.distributionProp, dxpid, msgtype);
                logger.info("dxpId=[" + dxpid + "] messageType=["
                        + msgtype + "] distributionQueue=[" + queueName + "]");
                queue.setBaseQueueName(queueName);
                this.jmsTemplate.convertAndSend(queue, playload, messagePostProcessor);
            } catch (Exception e) {
                CommonUtils.logError(logger, e);
            }
        } else {
            logger.error("message not is bytes message! message=[" + message + "]");
        }
    }

    private static final class HeaderMappingMessagePostProcessor implements MessagePostProcessor {

        private final Message<?> integrationMessage;

        private final JmsHeaderMapper headerMapper;

        HeaderMappingMessagePostProcessor(Message<?> integrationMessage, JmsHeaderMapper headerMapper) {
            this.integrationMessage = integrationMessage;
            this.headerMapper = headerMapper;
        }

        @Override
        public javax.jms.Message postProcessMessage(javax.jms.Message jmsMessage) {
            this.headerMapper.fromHeaders(this.integrationMessage.getHeaders(), jmsMessage);
            return jmsMessage;
        }

    }
}
