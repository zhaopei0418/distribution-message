package com.github.distributionmessage.config;

import com.github.distributionmessage.constant.ChannelConstant;
import com.github.distributionmessage.handler.DistributionSendingMessageHandler;
import com.github.distributionmessage.listener.DistributionMessageListener;
import com.ibm.mq.jms.MQQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.file.FileNameGenerator;
import org.springframework.integration.file.FileWritingMessageHandler;
import org.springframework.integration.jms.ChannelPublishingJmsMessageListener;
import org.springframework.integration.jms.JmsMessageDrivenEndpoint;
import org.springframework.integration.jms.JmsSendingMessageHandler;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.StringUtils;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;

@Configuration
public class IntegrationConfiguration {

    @Autowired
    private DistributionMessageListener distributionMessageListener;

    @Autowired
    private DistributionProp distributionProp;

    @Bean(name = ChannelConstant.IBMMQ_RECEIVE_CHANNEL)
    public MessageChannel ibmmqReceiveChannel() {
        return new PublishSubscribeChannel();
    }

    private static MessageHandler buildFileWriteMessageHandler(String dir, FileNameGenerator fileNameGenerator, boolean split) {
        FileWritingMessageHandler handler = null;
        if (split) {
            handler = new FileWritingMessageHandler(new SpelExpressionParser().parseExpression(
                    "@filePara.getTodayDir('" + dir + "')"));
        } else {
            handler = new FileWritingMessageHandler(new File(dir));
        }
        handler.setDeleteSourceFiles(true);
        handler.setExpectReply(false);
        handler.setFileNameGenerator(fileNameGenerator);
        handler.setAutoCreateDirectory(true);
        return handler;
    }

    @Bean
    public Object filePara() {
        return new Object() {

            public String getTodayDir(String dir) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
                return StringUtils.isEmpty(dir) ? "" : dir + File.separator +
                        simpleDateFormat.format(Calendar.getInstance().getTime());
            }
        };
    }

//    @Bean
//    @ServiceActivator(inputChannel = ChannelConstant.IBMMQ_RECEIVE_CHANNEL)
    public MessageHandler receiveMessageHandler() {
        return buildFileWriteMessageHandler("D:\\softs\\distribution-message\\mqmessage",
                new IbmmqFileNameGenerator("IBMMQ",
                        ".xml", true), false);
    }

    @Bean
    @ServiceActivator(inputChannel = ChannelConstant.IBMMQ_RECEIVE_CHANNEL)
    public JmsSendingMessageHandler jmsSendingMessageHandler(JmsTemplate jmsTemplate) {
        DistributionSendingMessageHandler distributionSendingMessageHandler = new DistributionSendingMessageHandler(jmsTemplate);
        distributionSendingMessageHandler.setDistributionProp(this.distributionProp);
        return distributionSendingMessageHandler;
    }

    @Bean
    public DefaultMessageListenerContainer defaultMessageListenerContainer(ConnectionFactory connectionFactory) {
        DefaultMessageListenerContainer defaultMessageListenerContainer = new DefaultMessageListenerContainer();
        defaultMessageListenerContainer.setConnectionFactory(connectionFactory);
        defaultMessageListenerContainer.setConcurrency(this.distributionProp.getMinConcurrency() + "-"
            + this.distributionProp.getMaxConcurrency());
//        defaultMessageListenerContainer.setMessageListener(this.distributionMessageListener);
        defaultMessageListenerContainer.setDestinationName(this.distributionProp.getQueueName());
        return defaultMessageListenerContainer;
    }

    @Bean
    public JmsMessageDrivenEndpoint jmsMessageDrivenEndpoint(DefaultMessageListenerContainer defaultMessageListenerContainer) {
        JmsMessageDrivenEndpoint jmsMessageDrivenEndpoint = new JmsMessageDrivenEndpoint(defaultMessageListenerContainer,
                new ChannelPublishingJmsMessageListener());
        jmsMessageDrivenEndpoint.setOutputChannelName(ChannelConstant.IBMMQ_RECEIVE_CHANNEL);
        return jmsMessageDrivenEndpoint;
    }

}
