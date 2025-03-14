package com.github.distributionmessage.handler;

import com.github.distributionmessage.config.DistributionProp;
import com.github.distributionmessage.config.IntegrationConfiguration;
import com.github.distributionmessage.constant.CommonConstant;
import com.github.distributionmessage.thread.RabbitSendMessageThread;
import com.github.distributionmessage.thread.SendMessageThread;
import com.github.distributionmessage.utils.CommonUtils;
import com.github.distributionmessage.utils.DistributionUtils;
import com.ibm.mq.jms.MQQueue;
import com.ibm.msg.client.wmq.WMQConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.integration.handler.AbstractMessageHandler;
import org.springframework.integration.jms.DefaultJmsHeaderMapper;
import org.springframework.integration.jms.JmsHeaderMapper;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

import java.io.File;

/**
 * @author zhaopei
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DistributionSendingMessageHandler extends AbstractMessageHandler {

    private JmsHeaderMapper headerMapper = new DefaultJmsHeaderMapper();

    private DistributionProp distributionProp;

    @Override
    protected void handleMessageInternal(Message<?> message) {
        long startTime = System.nanoTime();
        MessagePostProcessor messagePostProcessor = new HeaderMappingMessagePostProcessor(message, this.headerMapper);
//        logger.info("message header=[" + JSON.toJSONString(message.getHeaders()) + "]");
        Assert.notNull(this.distributionProp, "distributionProp must not be null");
        Assert.notNull(message, "Message must not be null");
        Object playload = message.getPayload();
        Assert.notNull(playload, "Message playload must not be null");
        JmsTemplate useJmsTemplate = null;
        RabbitTemplate userRabbitmqTemplate = null;
        int useCcsid = 819;
        IntegrationConfiguration.DistributionMessageGateway distributionMessageGateway = CommonUtils.getDistributionMessageGateway();
        if (playload instanceof byte[] || playload instanceof String) {
            try {
                byte[] bytes = null;
                String sm = null;
                if (playload instanceof byte[]) {
                    bytes = (byte[]) playload;
                    sm = new String(bytes, CommonConstant.CHARSET);
                } else {
                    sm = (String) playload;
                    playload = ((String) playload).getBytes(CommonConstant.CHARSET);
                    messagePostProcessor = null;
                }
                MQQueue queue = new MQQueue();
                queue.setTargetClient(WMQConstants.WMQ_CLIENT_NONJMS_MQ);
                sm = DistributionUtils.removeTagPrefix(sm);
                if (this.distributionProp.getRemoveSvHead() && DistributionUtils.isRemoveDxpMsgSvHead(sm)) {
                    sm = DistributionUtils.removeDxpMsgSvHead(sm);
                    playload = sm.getBytes(CommonConstant.CHARSET);
                }
                String dxpid = DistributionUtils.getDxpIdByMessage(sm);
                String senderId = DistributionUtils.getSenderIdByMessage(sm);
                String msgtype = DistributionUtils.getMessageType(sm);
                String originQueueName = DistributionUtils.getDestinationQueueName(this.distributionProp, dxpid, msgtype, senderId);
                String[] queueNames = null;
                logger.info("search queueName is [" + originQueueName + "]");
                if (originQueueName.indexOf("&") != -1) {
                    queueNames = originQueueName.split("&");
                } else {
                    queueNames = new String[] {originQueueName};
                }

                for (String queueName : queueNames) {
                    if (queueName.indexOf("|||") != -1) {
                        String dir = queueName.replaceAll("\\|\\|\\|", "");
                        if (this.distributionProp.getUnWrap()) {
                            playload = DistributionUtils.unWrap(sm);
                        }
                        distributionMessageGateway.writeToFile(new File(dir), playload);
                        logger.info("senderId=[" + senderId + "] dxpId=[" + dxpid + "] messageType=[" + msgtype + "] write to dir=[" + dir + "] use["
                                + ((double) (System.nanoTime() - startTime) / 1000000.0) + "]ms");
                        continue;
                    } else if (queueName.indexOf("||") != -1) {
                        String[] queueNameAndIndex = queueName.split("\\|\\|");
                        queueName = queueNameAndIndex[0];
                        userRabbitmqTemplate = CommonUtils.getRabbitTelmpateByIndex(Integer.valueOf(queueNameAndIndex[1]));
                    } else if (queueName.indexOf("|") != -1) {
                        String[] queueNameAndIndex = queueName.split("\\|");
                        queueName = queueNameAndIndex[0];
                        useJmsTemplate = CommonUtils.getJmsTemplateByIndex(Integer.valueOf(queueNameAndIndex[1]));
                        useCcsid = CommonUtils.getCcsidByIndex(Integer.valueOf(queueNameAndIndex[1]));
                    } else {
                        logger.error("无法找到对应的输出,消息无法处理!!!");
                        continue;
                    }
                    queue.setCCSID(useCcsid);
                    queue.setBaseQueueName(queueName);
                    IntegrationConfiguration.CACHE_QUEUE.put(1);
                    if (null != useJmsTemplate) {
                        SendMessageThread.getExecutorService().execute(
                            new SendMessageThread(useJmsTemplate, playload, queue, messagePostProcessor));
                    } else {
                        RabbitSendMessageThread.getExecutorService().execute(
                            new RabbitSendMessageThread(userRabbitmqTemplate, sm, queueName));
                    }
//                    SendMessageThread.getExecutorService().execute(
//                            null != useJmsTemplate ? new SendMessageThread(useJmsTemplate, playload, queue, messagePostProcessor)
//                                    : new RabbitSendMessageThread(userRabbitmqTemplate, sm, queueName));
                    logger.info("cache size [" + IntegrationConfiguration.CACHE_QUEUE.size() + "] senderId=[" + senderId + "] dxpId=[" + dxpid + "] messageType=["
                            + msgtype + "] ccsid=[" + useCcsid + "] distributionQueue=[" + queueName + "] use["
                            + ((double) (System.nanoTime() - startTime) / 1000000.0) + "]ms");
                }
            } catch (Exception e) {
                CommonUtils.logError(logger, e);
            }
        } else {
            logger.error("message not is bytes message or string message! message=[" + message + "]");
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
