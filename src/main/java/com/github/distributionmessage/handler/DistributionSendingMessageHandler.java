package com.github.distributionmessage.handler;

import com.github.distributionmessage.config.DistributionProp;
import com.github.distributionmessage.config.IntegrationConfiguration;
import com.github.distributionmessage.constant.CommonConstant;
import com.github.distributionmessage.thread.SendMessageThread;
import com.github.distributionmessage.utils.CommonUtils;
import com.github.distributionmessage.utils.DistributionUtils;
import com.ibm.mq.jms.MQQueue;
import com.ibm.msg.client.wmq.WMQConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.integration.jms.DefaultJmsHeaderMapper;
import org.springframework.integration.jms.JmsHeaderMapper;
import org.springframework.integration.jms.JmsSendingMessageHandler;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

import java.util.concurrent.BlockingQueue;

@Data
@EqualsAndHashCode(callSuper=false)
public class DistributionSendingMessageHandler extends JmsSendingMessageHandler {


    private final JmsTemplate jmsTemplate;

    private final JmsTemplate secondJmsTemplate;

    private final JmsTemplate thirdJmsTemplate;

    private JmsHeaderMapper headerMapper = new DefaultJmsHeaderMapper();

    private DistributionProp distributionProp;

    public DistributionSendingMessageHandler(JmsTemplate jmsTemplate, JmsTemplate secondJmsTemplate, JmsTemplate thirdJmsTemplate) {
        super(jmsTemplate);
        this.jmsTemplate = jmsTemplate;
        this.secondJmsTemplate = secondJmsTemplate;
        this.thirdJmsTemplate = thirdJmsTemplate;
    }

    @Override
    protected void handleMessageInternal(Message<?> message) {
        long startTime = System.nanoTime();
        MessagePostProcessor messagePostProcessor = new HeaderMappingMessagePostProcessor(message, this.headerMapper);
        Assert.notNull(this.distributionProp, "distributionProp must not be null");
        Assert.notNull(message, "Message must not be null");
        Object playload = message.getPayload();
        Assert.notNull(playload, "Message playload must not be null");
        JmsTemplate useJmsTemplate = null;
        int useCcsid;
        if (playload instanceof byte[]) {
            try {
                byte[] bytes = (byte[]) playload;
                MQQueue queue = new MQQueue();
                queue.setTargetClient(WMQConstants.WMQ_CLIENT_NONJMS_MQ);
                String sm = new String(bytes, CommonConstant.CHARSET);
                if (DistributionUtils.isRemoveDxpMsgSvHead(sm)) {
                    sm = DistributionUtils.removeDxpMsgSvHead(sm);
                    playload = sm.getBytes(CommonConstant.CHARSET);
                }
                String dxpid = DistributionUtils.getDxpIdByMessage(sm);
                String msgtype = DistributionUtils.getMessageType(sm);
                String queueName = DistributionUtils.getDestinationQueueName(this.distributionProp, dxpid, msgtype);
                if (queueName.lastIndexOf("::") != -1) {
                    queueName = queueName.replaceAll("::", "");
                    useJmsTemplate = this.thirdJmsTemplate;
                    useCcsid = this.distributionProp.getThirdCcsid();
                } else if (queueName.lastIndexOf(":") != -1){
                    queueName = queueName.replaceAll(":", "");
                    useJmsTemplate = this.secondJmsTemplate;
                    useCcsid = this.distributionProp.getSecondCcsid();
                } else if (queueName.indexOf("|") != -1) {
                    String[] queueNameAndIndex = queueName.split("\\|");
                    queueName = queueNameAndIndex[0];
                    useJmsTemplate = CommonUtils.getJmsTemplateByIndex(Integer.valueOf(queueNameAndIndex[1]));
                    useCcsid = CommonUtils.getCcsidByIndex(Integer.valueOf(queueNameAndIndex[1]));
                } else {
                    useJmsTemplate = this.jmsTemplate;
                    useCcsid = this.distributionProp.getCcsid();
                }
                queue.setCCSID(useCcsid);
                queue.setBaseQueueName(queueName);
//                this.jmsTemplate.convertAndSend(queue, playload, messagePostProcessor);
                IntegrationConfiguration.CACHE_QUEUE.put(1);
                SendMessageThread.getExecutorService().execute(new SendMessageThread(useJmsTemplate, playload, queue, messagePostProcessor));
                logger.info("cache size [" + IntegrationConfiguration.CACHE_QUEUE.size() + "] dxpId=[" + dxpid + "] messageType=["
                        + msgtype + "] ccsid=[" + useCcsid + "] distributionQueue=[" + queueName + "] use["
                        + ((double)(System.nanoTime() - startTime) / 1000000.0) + "]ms");
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
