package com.github.distributionmessage.config;

import com.alibaba.fastjson.JSON;
import com.github.distributionmessage.constant.ChannelConstant;
import com.github.distributionmessage.constant.CommonConstant;
import com.github.distributionmessage.handler.DistributionSendingMessageHandler;
import com.github.distributionmessage.listener.DistributionMessageListener;
import com.github.distributionmessage.thread.RabbitSendMessageThread;
import com.github.distributionmessage.thread.SendMessageThread;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.file.FileNameGenerator;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.FileWritingMessageHandler;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;
import org.springframework.integration.file.transformer.FileToByteArrayTransformer;
import org.springframework.integration.handler.AbstractMessageHandler;
import org.springframework.integration.jms.ChannelPublishingJmsMessageListener;
import org.springframework.integration.jms.JmsMessageDrivenEndpoint;
import org.springframework.integration.jms.JmsSendingMessageHandler;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.jms.ConnectionFactory;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class IntegrationConfiguration {

    public static BlockingQueue<Integer> CACHE_QUEUE;

    private static final Log logger = LogFactory.getLog(IntegrationConfiguration.class);

    @Autowired
    private DistributionMessageListener distributionMessageListener;

    @Autowired
    private DistributionProp distributionProp;

    @PostConstruct
    public void initialization() {
        CACHE_QUEUE = new LinkedBlockingQueue<Integer>(this.distributionProp.getCacheSize());
        SendMessageThread.setExecutorService(Executors.newFixedThreadPool(this.distributionProp.getPoolSize()));
        RabbitSendMessageThread.setExecutorService(Executors.newFixedThreadPool(this.distributionProp.getPoolSize()));
    }

    @Bean(name = ChannelConstant.IBMMQ_RECEIVE_CHANNEL)
    public MessageChannel ibmmqReceiveChannel() {
        return new PublishSubscribeChannel();
    }

    @Bean(name = ChannelConstant.RABBIT_RECEIVE_CHANNEL)
    public MessageChannel rabbitReceiveChannel() {
        return new PublishSubscribeChannel();
    }

    @Bean(name = ChannelConstant.FILE_RECEIVE_CHANNEL)
    public MessageChannel fileReceiveChannel() {
        return new PublishSubscribeChannel();
    }

    @Bean(name = ChannelConstant.BYTES_RECEIVE_CHANNEL)
    public MessageChannel bytesReceiveChannel() {
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

    @Bean
    @ServiceActivator(inputChannel = ChannelConstant.WRITE_TO_FILE_CHANNEL)
    public MessageHandler fileWritingMessageHandler() {
        Expression directoryExpression = new SpelExpressionParser().parseExpression("headers.directory");
        FileWritingMessageHandler handler = new FileWritingMessageHandler(directoryExpression);
        handler.setExpectReply(false);
        handler.setFileNameGenerator(new IbmmqFileNameGenerator(this.distributionProp.getFilePrefix(),
                this.distributionProp.getFileSuffix(), true));
        handler.setAutoCreateDirectory(true);
        return handler;
    }

    @Bean
    @ServiceActivator(inputChannel = ChannelConstant.IBMMQ_RECEIVE_CHANNEL)
    public JmsSendingMessageHandler jmsSendingMessageHandler(JmsTemplate jmsTemplate,
                                                             @Qualifier("secondJmsTemplate") JmsTemplate secondJmsTemplate,
                                                             @Qualifier("thirdJmsTemplate") JmsTemplate thirdJmsTemplate) {
        DistributionSendingMessageHandler distributionSendingMessageHandler =
                new DistributionSendingMessageHandler(jmsTemplate, secondJmsTemplate, thirdJmsTemplate);
        distributionSendingMessageHandler.setDistributionProp(this.distributionProp);
        return distributionSendingMessageHandler;
    }

    @Bean
    @Primary
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
        return jmsTemplate;
    }

    @Bean(name = "secondJmsTemplate")
    public JmsTemplate secondJmsTemplate(@Qualifier("secondConnectionFactory") ConnectionFactory connectionFactory) {
        JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
        return jmsTemplate;
    }

    @Bean(name = "thirdJmsTemplate")
    public JmsTemplate thirdJmsTemplate(@Qualifier("thirdConnectionFactory") ConnectionFactory connectionFactory) {
        JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
        return jmsTemplate;
    }

    @Bean
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(this.distributionProp.getMinConcurrency());
        threadPoolTaskExecutor.setMaxPoolSize(this.distributionProp.getMaxConcurrency());
        threadPoolTaskExecutor.setKeepAliveSeconds(this.distributionProp.getKeepAliveSeconds());
        threadPoolTaskExecutor.setQueueCapacity(this.distributionProp.getQueueCapacity());
        threadPoolTaskExecutor.setThreadNamePrefix(this.distributionProp.getThreadNamePrefix() + this.distributionProp.getQueueName().split(",")[0].trim() + "-0-");
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return threadPoolTaskExecutor;
    }

    @Bean
    @Primary
    public DefaultMessageListenerContainer defaultMessageListenerContainer(ConnectionFactory connectionFactory) {
        DefaultMessageListenerContainer defaultMessageListenerContainer = new DefaultMessageListenerContainer();
        defaultMessageListenerContainer.setConnectionFactory(connectionFactory);
        defaultMessageListenerContainer.setConcurrency(this.distributionProp.getMinConcurrency() + "-"
            + this.distributionProp.getMaxConcurrency());
//        defaultMessageListenerContainer.setMessageListener(this.distributionMessageListener);
        defaultMessageListenerContainer.setDestinationName(this.distributionProp.getQueueName().split(",")[0].trim());
        defaultMessageListenerContainer.setTaskExecutor(threadPoolTaskExecutor());
        defaultMessageListenerContainer.setCacheLevel(DefaultMessageListenerContainer.CACHE_CONSUMER);
        return defaultMessageListenerContainer;
    }

    @Bean
    @Primary
    public JmsMessageDrivenEndpoint jmsMessageDrivenEndpoint(DefaultMessageListenerContainer defaultMessageListenerContainer) {
        JmsMessageDrivenEndpoint jmsMessageDrivenEndpoint = new JmsMessageDrivenEndpoint(defaultMessageListenerContainer,
                new ChannelPublishingJmsMessageListener());
        jmsMessageDrivenEndpoint.setOutputChannelName(ChannelConstant.IBMMQ_RECEIVE_CHANNEL);
        return jmsMessageDrivenEndpoint;
    }

    @Bean
    @ServiceActivator(inputChannel = ChannelConstant.FILE_RECEIVE_CHANNEL, outputChannel = ChannelConstant.IBMMQ_RECEIVE_CHANNEL)
    public FileToByteArrayTransformer fileToByteArrayTransformer() {
        FileToByteArrayTransformer fileToByteArrayTransformer = new FileToByteArrayTransformer();
        fileToByteArrayTransformer.setDeleteFiles(true);
        return fileToByteArrayTransformer;
    }

    @ServiceActivator(inputChannel = ChannelConstant.BYTES_RECEIVE_CHANNEL)
    public void handleBytesMessage(byte[] message) {
        try {
            logger.info("message=[" + new String(message, CommonConstant.CHARSET) + "]");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Bean
    @ServiceActivator(inputChannel = ChannelConstant.RABBIT_RECEIVE_CHANNEL)
    public MessageHandler rabbitMessageHandler() {
        return new AbstractMessageHandler() {

            @Override
            protected void handleMessageInternal(Message<?> message) throws Exception {
                logger.info(JSON.toJSONString(message.getHeaders()));
                String payloadType = null;
                if (message.getPayload() instanceof byte[]) {
                    payloadType = "byte[]";
                } else if (message.getPayload() instanceof String) {
                    payloadType = "String";
                }
                logger.info(String.format("payloadType = [%s]", payloadType));
            }
        };
    }

    @MessagingGateway(defaultRequestChannel = ChannelConstant.WRITE_TO_FILE_CHANNEL)
    public interface DistributionMessageGateway {

        void writeToFile(@Header(CommonConstant.FILE_HEADERS_DIRECTORY) File directory, Object data);

    }
}
