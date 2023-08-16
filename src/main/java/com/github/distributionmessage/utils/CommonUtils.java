package com.github.distributionmessage.utils;

import com.alibaba.fastjson.JSON;
import com.github.distributionmessage.config.DistributionProp;
import com.github.distributionmessage.config.IntegrationConfiguration;
import com.github.distributionmessage.constant.ChannelConstant;
import com.github.distributionmessage.constant.CommonConstant;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;
import org.springframework.integration.jms.ChannelPublishingJmsMessageListener;
import org.springframework.integration.jms.JmsMessageDrivenEndpoint;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.support.PeriodicTrigger;

import javax.jms.JMSException;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author zhaopei
 */

@Slf4j
public class CommonUtils {

    private static List<JmsTemplate> jmsTemplateList = new ArrayList<>();

    private static List<RabbitTemplate> rabbitTemplateList = new ArrayList<>();

    private static List<Integer> ccsidList = new ArrayList<>();

    private static final Log logger = LogFactory.getLog(CommonUtils.class);

    private static ApplicationContext applicationContext;

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static void setApplicationContext(ApplicationContext applicationContext) {
        CommonUtils.applicationContext = applicationContext;
    }

    public static void initParams() {
        initJmsTemplateList();
        initRabbitTemplateList();
        try {
            Thread.sleep(2000);
        } catch (Exception e) {
            log.error("sleep 2 second. error", e);
        }
        initOtherListenerContainer();
        initRabbitContainer();
        initDirectoryInboundAdapter();
    }

    private static void initOtherListenerContainer() {
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        DistributionProp distributionProp = defaultListableBeanFactory.getBean(DistributionProp.class);

        if (null == distributionProp.getOtherInputQueue() || distributionProp.getOtherInputQueue().isEmpty()) {
            logger.error("otherInputQueue error");
            return;
        }
        String[] queueNames = null;
        String[] queueInfos = null;
        String key = "";
        String suffix = "";
        Map<String, Object> propertyValueMap = null;
        Map<String, String> propertyReferenceMap = null;
        List<Object[]> constructorArgList = null;
        MQQueueConnectionFactory mqQueueConnectionFactory = null;
        String connectionFactoryBeanName = null;
        String taskExecutorBeanName = null;
        String listenerContainerBeanName = null;
        String jmsMessageDrivenEndpointBeanName = null;
        String tmpQueueName = null;
        String hostName = null;
        Integer port = null;
        String queueManager = null;
        String channel = null;
        Integer ccsid = null;
        String queueName = null;
        Integer minConcurrency = null;
        Integer maxConcurrency = null;
        Integer keepAliveSeconds = null;
        Integer queueCapacity = null;
        String threadNamePrefix = null;

        for (String inputQueueInfo : distributionProp.getOtherInputQueue()) {
            queueInfos = inputQueueInfo.split("\\|");
            for (int i = 0; i < queueInfos.length; i++) {
                logger.info("queueInfo=[" + i + "]=[" + queueInfos[i] + "]");
            }
            if (queueInfos.length < 11) {
                continue;
            }
            try {
                hostName = queueInfos[0].trim();
                port = Integer.valueOf(queueInfos[1].trim());
                queueManager = queueInfos[2].trim();
                channel = queueInfos[3].trim();
                ccsid = Integer.valueOf(queueInfos[4].trim());
                queueName = queueInfos[5].trim();
                minConcurrency = Integer.valueOf(queueInfos[6].trim());
                maxConcurrency = Integer.valueOf(queueInfos[7].trim());
                keepAliveSeconds = Integer.valueOf(queueInfos[8].trim());
                queueCapacity = Integer.valueOf(queueInfos[9].trim());
                threadNamePrefix = queueInfos[10].trim();
                key = hostName + "-" + port + "-";
                queueNames = queueName.split(",");
                if (queueNames.length > 0) {
                    for (int i = 0; i < queueNames.length; i++) {
                        tmpQueueName = queueNames[i].trim();
                        suffix = tmpQueueName + "-" + i + "-";
                        propertyValueMap = new HashMap<>();
                        propertyReferenceMap = new HashMap<>();
                        constructorArgList = new ArrayList<>();

                        try {
                            connectionFactoryBeanName = key + "connectionFactory" + suffix;
                            taskExecutorBeanName = key + "taskExecutor" + suffix;
                            listenerContainerBeanName = key + "listenerContainer" + suffix;
                            jmsMessageDrivenEndpointBeanName = key + "messageDrivenEndpoint" + suffix;

                            propertyValueMap.put("sessionCacheSize", maxConcurrency * 2);
                            mqQueueConnectionFactory = new MQQueueConnectionFactory();
                            mqQueueConnectionFactory.setHostName(hostName);
                            mqQueueConnectionFactory.setPort(port);
                            mqQueueConnectionFactory.setQueueManager(queueManager);
                            mqQueueConnectionFactory.setChannel(channel);
                            mqQueueConnectionFactory.setCCSID(ccsid);
                            mqQueueConnectionFactory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
                            propertyValueMap.put("targetConnectionFactory", mqQueueConnectionFactory);
                            createAndregisterBean(CachingConnectionFactory.class, connectionFactoryBeanName, propertyValueMap, null, null);

                            propertyValueMap.clear();
                            propertyValueMap.put("corePoolSize", minConcurrency);
                            propertyValueMap.put("maxPoolSize", maxConcurrency);
                            propertyValueMap.put("keepAliveSeconds", keepAliveSeconds);
                            propertyValueMap.put("queueCapacity", queueCapacity);
                            propertyValueMap.put("threadNamePrefix", threadNamePrefix + suffix);
                            propertyValueMap.put("rejectedExecutionHandler", new ThreadPoolExecutor.CallerRunsPolicy());
                            createAndregisterBean(ThreadPoolTaskExecutor.class, taskExecutorBeanName, propertyValueMap, null, null);


                            propertyValueMap.clear();
                            propertyValueMap.put("concurrency", minConcurrency + "-" + maxConcurrency);
                            propertyValueMap.put("destinationName", queueNames[i].trim());
                            propertyValueMap.put("cacheLevel", DefaultMessageListenerContainer.CACHE_CONSUMER);
                            propertyReferenceMap.put("connectionFactory", connectionFactoryBeanName);
                            propertyReferenceMap.put("taskExecutor", taskExecutorBeanName);
                            createAndregisterBean(DefaultMessageListenerContainer.class, listenerContainerBeanName, propertyValueMap, propertyReferenceMap, null);

                            constructorArgList.clear();
                            constructorArgList.add(new Object[] {true, listenerContainerBeanName});
                            constructorArgList.add(new Object[] {false, new ChannelPublishingJmsMessageListener()});
                            propertyValueMap.clear();
                            propertyValueMap.put("outputChannelName", ChannelConstant.IBMMQ_RECEIVE_CHANNEL);
                            createAndregisterBean(JmsMessageDrivenEndpoint.class, jmsMessageDrivenEndpointBeanName, propertyValueMap, null, constructorArgList);
                            ((DefaultMessageListenerContainer) defaultListableBeanFactory.getBean(listenerContainerBeanName)).start();
                            ((JmsMessageDrivenEndpoint) defaultListableBeanFactory.getBean(jmsMessageDrivenEndpointBeanName)).start();

                        } catch (JMSException e) {
                            logError(logger, e);
                        }
                    }
                }
            } catch (Exception e) {
                logError(logger, e);
            }
        }

    }

    private static void initJmsTemplateList() {
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        DistributionProp distributionProp = defaultListableBeanFactory.getBean(DistributionProp.class);

        if (null == distributionProp.getOtherOutputQueue() || distributionProp.getOtherOutputQueue().isEmpty()) {
            logger.error("otherOutputQueue error");
            return;
        }
        String[] queueInfos = null;
        String key = "";
        String suffix = "";
        Map<String, Object> propertyValueMap = null;
        Map<String, String> propertyReferenceMap = null;
        List<Object[]> constructorArgList = null;
        MQQueueConnectionFactory mqQueueConnectionFactory = null;
        String connectionFactoryBeanName = null;
        String jmsTemplateBeanName = null;
        String hostName = null;
        Integer port = null;
        String queueManager = null;
        String channel = null;
        Integer ccsid = null;
        Integer sessionCacheSize = null;
        for (String outQueueInfo : distributionProp.getOtherOutputQueue()) {
            queueInfos = outQueueInfo.split("\\|");
            propertyValueMap = new HashMap<>();
            propertyReferenceMap = new HashMap<>();
            constructorArgList = new ArrayList<>();
            for (int i = 0; i < queueInfos.length; i++) {
                logger.info("outputQueueInfo=[" + i + "]=[" + queueInfos[i] + "]");
            }
            if (queueInfos.length < 6) {
                continue;
            }
            try {
                hostName = queueInfos[0].trim();
                port = Integer.valueOf(queueInfos[1].trim());
                queueManager = queueInfos[2].trim();
                channel = queueInfos[3].trim();
                ccsid = Integer.valueOf(queueInfos[4].trim());
                sessionCacheSize = Integer.valueOf(queueInfos[5].trim());
                key = hostName + "-" + port + "-";
                suffix = "-" + queueManager;
                connectionFactoryBeanName = key + "connectionFactory" + suffix;
                jmsTemplateBeanName = key + "jmsTemplateBeanName" + suffix;

                propertyValueMap.put("sessionCacheSize", sessionCacheSize);
                mqQueueConnectionFactory = new MQQueueConnectionFactory();
                mqQueueConnectionFactory.setHostName(hostName);
                mqQueueConnectionFactory.setPort(port);
                mqQueueConnectionFactory.setQueueManager(queueManager);
                mqQueueConnectionFactory.setChannel(channel);
                mqQueueConnectionFactory.setCCSID(ccsid);
                mqQueueConnectionFactory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
                propertyValueMap.put("targetConnectionFactory", mqQueueConnectionFactory);
                createAndregisterBean(CachingConnectionFactory.class, connectionFactoryBeanName, propertyValueMap, null, null);

                constructorArgList.clear();
                constructorArgList.add(new Object[] {true, connectionFactoryBeanName});
                createAndregisterBean(JmsTemplate.class, jmsTemplateBeanName, null, null, constructorArgList);

                jmsTemplateList.add((JmsTemplate) defaultListableBeanFactory.getBean(jmsTemplateBeanName));
                ccsidList.add(ccsid);
            } catch (Exception e) {
                logError(logger, e);
            }
        }
    }

    private static void initRabbitContainer() {
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        DistributionProp distributionProp = defaultListableBeanFactory.getBean(DistributionProp.class);
        if (null == distributionProp.getOtherRabbitInputQueue() || distributionProp.getOtherRabbitInputQueue().isEmpty()) {
            logger.error("otherRabbitInputQueue error");
            return;
        }
        String[] queueInfos = null;
        String key = "";
        String suffix = "";
        String rabbitConnectionFactoryBeanName = "";
        String rabbitTaskExecutorBeanName = null;
        String rabbitMessageListenerContainerBeanName = "";
        String rabbitAmqpInboundChannelAdapterBeanName = "";
        Map<String, Object> propertyValueMap = null;
        Map<String, String> propertyReferenceMap = null;
        List<Object[]> constructorArgList = null;
        String host = null;
        Integer port = null;
        String username = null;
        String password = null;
        String virtualHost = null;
        String cacheMode = null;
        Integer channelCacheSize = null;
        Integer connectionCacheSize = null;
        Integer connectionLimit = null;
        Integer minConcurrency = null;
        Integer maxConcurrency = null;
        Integer prefetchCount = null;
        Integer keepAliveSeconds = null;
        Integer queueCapacity = null;
        String threadNamePrefix = null;
        String[] queueNames = null;
        for (String inQueueInfo : distributionProp.getOtherRabbitInputQueue()) {
            queueInfos = inQueueInfo.split("\\|");
            propertyValueMap = new HashMap<>();
            propertyReferenceMap = new HashMap<>();
            constructorArgList = new ArrayList<>();
            for (int i = 0; i < queueInfos.length; i++) {
                logger.info("rabbitInQueueInfo=[" + i + "]=[" + queueInfos[i] + "]");
            }

            if (queueInfos.length < 16) {
                continue;
            }

            try {
                host = queueInfos[0].trim();
                port = Integer.valueOf(queueInfos[1].trim());
                username = queueInfos[2].trim();
                password = queueInfos[3].trim();
                virtualHost = queueInfos[4].trim();
                cacheMode = queueInfos[5].trim();
                channelCacheSize = Integer.valueOf(queueInfos[6].trim());
                connectionCacheSize = Integer.valueOf(queueInfos[7].trim());
                connectionLimit = Integer.valueOf(queueInfos[8].trim());
                minConcurrency = Integer.valueOf(queueInfos[9].trim());
                maxConcurrency = Integer.valueOf(queueInfos[10].trim());
                prefetchCount = Integer.valueOf(queueInfos[11].trim());
                keepAliveSeconds = Integer.valueOf(queueInfos[12].trim());
                queueCapacity = Integer.valueOf(queueInfos[13].trim());
                threadNamePrefix = queueInfos[14].trim();
                queueNames = queueInfos[15].trim().split(",");
                key = host + "-" + port + "-";
                suffix = queueInfos[15].trim() + "-";

                rabbitConnectionFactoryBeanName = key + "rabbitInputConnectionFactory" + suffix;
                rabbitTaskExecutorBeanName = key + "rabbitTaskExecutor" + suffix;
                rabbitMessageListenerContainerBeanName = key + "rabbitMessageListenerContainer" + suffix;
                rabbitAmqpInboundChannelAdapterBeanName = key + "rabbitAmqpInboundChannelAdapter" + suffix;

                propertyValueMap.put("host", host);
                propertyValueMap.put("port", port);
                propertyValueMap.put("username", username);
                propertyValueMap.put("password", password);
                propertyValueMap.put("virtualHost", virtualHost);
                propertyValueMap.put("cacheMode", CommonConstant.CACHE_MODE_CONNECTION.equals(cacheMode) ?
                        org.springframework.amqp.rabbit.connection.CachingConnectionFactory.CacheMode.CONNECTION :
                        org.springframework.amqp.rabbit.connection.CachingConnectionFactory.CacheMode.CHANNEL);
                propertyValueMap.put("publisherConfirms", true);
                propertyValueMap.put("publisherReturns", true);
                propertyValueMap.put("channelCacheSize", channelCacheSize);
                propertyValueMap.put("connectionCacheSize", connectionCacheSize);
                propertyValueMap.put("connectionLimit", connectionLimit);
                createAndregisterBean(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.class, rabbitConnectionFactoryBeanName, propertyValueMap, null, null);
                logger.info("init rabbit cachingConnectionFactory finished.");

                propertyValueMap.clear();
                propertyValueMap.put("corePoolSize", maxConcurrency * 2);
                propertyValueMap.put("maxPoolSize", maxConcurrency * 3);
                propertyValueMap.put("keepAliveSeconds", keepAliveSeconds);
                propertyValueMap.put("queueCapacity", queueCapacity);
                propertyValueMap.put("threadNamePrefix", threadNamePrefix + suffix);
                propertyValueMap.put("rejectedExecutionHandler", new ThreadPoolExecutor.CallerRunsPolicy());
                createAndregisterBean(ThreadPoolTaskExecutor.class, rabbitTaskExecutorBeanName, propertyValueMap, null, null);
                logger.info("init rabbit taskExecutor finished.");

                propertyValueMap.clear();
                propertyValueMap.put("queueNames", queueNames);
                propertyValueMap.put("concurrentConsumers", minConcurrency);
                propertyValueMap.put("maxConcurrentConsumers", maxConcurrency);
                propertyValueMap.put("defaultRequeueRejected", false);
                propertyValueMap.put("acknowledgeMode", AcknowledgeMode.AUTO);
                propertyValueMap.put("prefetchCount", prefetchCount);
//                propertyValueMap.put("consumerStartTimeout", 10 * 60 * 1000); //default 10minutes
//                propertyValueMap.put("messageListener", (ChannelAwareMessageListener) (message, channel) -> {
//                    logger.info("channel=[" + JSON.toJSONString(channel) + "] message=[" + JSON.toJSONString(message) + "]");
//                });
                propertyReferenceMap.clear();
                propertyReferenceMap.put("taskExecutor", rabbitTaskExecutorBeanName);
                constructorArgList.clear();
                constructorArgList.add(new Object[] {true, rabbitConnectionFactoryBeanName});
                createAndregisterBean(SimpleMessageListenerContainer.class, rabbitMessageListenerContainerBeanName, propertyValueMap, propertyReferenceMap, constructorArgList);
                logger.info("init rabbit messageListenerContainer finished.");

                constructorArgList.clear();
                constructorArgList.add(new Object[] {true, rabbitMessageListenerContainerBeanName});
                propertyValueMap.clear();
//                propertyValueMap.put("outputChannelName", ChannelConstant.RABBIT_RECEIVE_CHANNEL);
                propertyValueMap.put("outputChannelName", ChannelConstant.IBMMQ_RECEIVE_CHANNEL);
                createAndregisterBean(AmqpInboundChannelAdapter.class, rabbitAmqpInboundChannelAdapterBeanName, propertyValueMap, null, constructorArgList);
                logger.info("init rabbit amqpInboundChannelAdapter finished.");

                ((AmqpInboundChannelAdapter) defaultListableBeanFactory.getBean(rabbitAmqpInboundChannelAdapterBeanName)).start();
                ((SimpleMessageListenerContainer) defaultListableBeanFactory.getBean(rabbitMessageListenerContainerBeanName)).start();
            } catch (Exception e) {
                logError(logger, e);
            }
        }
    }

    private static void initRabbitTemplateList() {
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        DistributionProp distributionProp = defaultListableBeanFactory.getBean(DistributionProp.class);

        if (null == distributionProp.getRabbitOtherOutputQueue() || distributionProp.getRabbitOtherOutputQueue().isEmpty()) {
            logger.error("rabbitOtherOutputQueue error");
            return;
        }

        org.springframework.amqp.rabbit.connection.CachingConnectionFactory cachingConnectionFactory =
                (org.springframework.amqp.rabbit.connection.CachingConnectionFactory) defaultListableBeanFactory.getBean("rabbitConnectionFactory");

        String[] queueInfos = null;
        String key = "";
        String suffix = "";
        String rabbitTemplateBeanName = "";
        String rabbitConnectionFactoryBeanName = "rabbitConnectionFactory";
        Map<String, Object> propertyValueMap = null;
        List<Object[]> constructorArgList = null;
        String host = null;
        Integer port = null;
        String username = null;
        String password = null;
        String virtualHost = null;
        String cacheMode = null;
        Integer channelCacheSize = null;
        Integer connectionCacheSize = null;
        Integer connectionLimit = null;

        for (String outQueueInfo : distributionProp.getRabbitOtherOutputQueue()) {
            queueInfos = outQueueInfo.split("\\|");
            propertyValueMap = new HashMap<>();
            constructorArgList = new ArrayList<>();
            for (int i = 0; i < queueInfos.length; i++) {
                logger.info("rabbitOutputQueueInfo=[" + i + "]=[" + queueInfos[i] + "]");
            }

            if (queueInfos.length < 9) {
                continue;
            }

            try {
                host = queueInfos[0].trim();
                port = Integer.valueOf(queueInfos[1].trim());
                username = queueInfos[2].trim();
                password = queueInfos[3].trim();
                virtualHost = queueInfos[4].trim();
                cacheMode = queueInfos[5].trim();
                channelCacheSize = Integer.valueOf(queueInfos[6].trim());
                connectionCacheSize = Integer.valueOf(queueInfos[7].trim());
                connectionLimit = Integer.valueOf(queueInfos[8].trim());
                key = host + "-" + port + "-";
                suffix = "-" + virtualHost;
                rabbitConnectionFactoryBeanName = key + "rabbitConnectionFactory" + suffix;
                rabbitTemplateBeanName = key + "rabbitTemplateBeanName" + suffix;

                propertyValueMap.put("host", host);
                propertyValueMap.put("port", port);
                propertyValueMap.put("username", username);
                propertyValueMap.put("password", password);
                propertyValueMap.put("virtualHost", virtualHost);
                propertyValueMap.put("cacheMode", CommonConstant.CACHE_MODE_CONNECTION.equals(cacheMode) ?
                        org.springframework.amqp.rabbit.connection.CachingConnectionFactory.CacheMode.CONNECTION :
                        org.springframework.amqp.rabbit.connection.CachingConnectionFactory.CacheMode.CHANNEL);
                propertyValueMap.put("publisherConfirms", true);
                propertyValueMap.put("publisherReturns", true);
                propertyValueMap.put("channelCacheSize", channelCacheSize);
                propertyValueMap.put("connectionCacheSize", connectionCacheSize);
                propertyValueMap.put("connectionLimit", connectionLimit);
                createAndregisterBean(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.class, rabbitConnectionFactoryBeanName,
                        propertyValueMap, null, null);

                constructorArgList.clear();
                constructorArgList.add(new Object[] {true, rabbitConnectionFactoryBeanName});
                createAndregisterBean(RabbitTemplate.class, rabbitTemplateBeanName, null, null, constructorArgList);
                rabbitTemplateList.add((RabbitTemplate) defaultListableBeanFactory.getBean(rabbitTemplateBeanName));

            } catch (Exception e) {
                logError(logger, e);
            }
        }
    }

    private static void initDirectoryInboundAdapter() {
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        DistributionProp distributionProp = defaultListableBeanFactory.getBean(DistributionProp.class);
        if (null == distributionProp.getOtherDirectorInput() || distributionProp.getOtherDirectorInput().isEmpty()) {
            logger.error("otherDirectoryInput error");
            return;
        }

        Map<String, Object> propertyValueMap = null;
        String[] directoryInfos = null;
        String key = "";
        String suffix = "";
        String directory = null;
        String dir = null;
        String[] directories = null;
        String fileFilter = null;
        Integer periodic = null;
        Integer maxMessagesPrePoll = null;
        Integer minConcurrency = null;
        Integer maxConcurrency = null;
        Integer keepAliveSeconds = null;
        Integer queueCapacity = null;
        String threadNamePrefix = null;
        String pollingChannelAdapterBeanName = null;
        FileReadingMessageSource fileReadingMessageSource = null;
        ThreadPoolTaskExecutor threadPoolTaskExecutor = null;

        for (String inDirInfo : distributionProp.getOtherDirectorInput()) {
            directoryInfos = inDirInfo.split("\\|");
            if (directoryInfos.length < 9) {
                continue;
            }

            propertyValueMap = new HashMap<>();
            directory = directoryInfos[0].trim();
            fileFilter = directoryInfos[1].trim();
            periodic = Integer.valueOf(directoryInfos[2].trim());
            maxMessagesPrePoll = Integer.valueOf(directoryInfos[3].trim());
            minConcurrency = Integer.valueOf(directoryInfos[4].trim());
            maxConcurrency = Integer.valueOf(directoryInfos[5].trim());
            keepAliveSeconds = Integer.valueOf(directoryInfos[6].trim());
            queueCapacity = Integer.valueOf(directoryInfos[7].trim());
            threadNamePrefix = directoryInfos[8];
            directories = directory.split(",");
            key = "directory-";
            for (int i = 0; i < directories.length; i++) {
                dir = directories[i];
                suffix = dir + "-" + i + "-";
                pollingChannelAdapterBeanName = key + "SourcePollingChannelAdapter" + suffix;
                fileReadingMessageSource = new FileReadingMessageSource();
                fileReadingMessageSource.setDirectory(new File(dir));
                fileReadingMessageSource.setFilter(new SimplePatternFileListFilter(fileFilter));
                threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
                threadPoolTaskExecutor.initialize();
                threadPoolTaskExecutor.setCorePoolSize(minConcurrency);
                threadPoolTaskExecutor.setMaxPoolSize(maxConcurrency);
                threadPoolTaskExecutor.setKeepAliveSeconds(keepAliveSeconds);
                threadPoolTaskExecutor.setQueueCapacity(queueCapacity);
                threadPoolTaskExecutor.setThreadNamePrefix(threadNamePrefix + suffix);
                threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

                propertyValueMap.put("source", fileReadingMessageSource);
                propertyValueMap.put("outputChannelName", ChannelConstant.FILE_RECEIVE_CHANNEL);
                propertyValueMap.put("trigger", new PeriodicTrigger(periodic));
                propertyValueMap.put("maxMessagesPerPoll", maxMessagesPrePoll);
                propertyValueMap.put("taskExecutor", threadPoolTaskExecutor);
                createAndregisterBean(SourcePollingChannelAdapter.class, pollingChannelAdapterBeanName, propertyValueMap, null, null);

                ((SourcePollingChannelAdapter) defaultListableBeanFactory.getBean(pollingChannelAdapterBeanName)).start();
            }
        }
    }

    public static void createAndregisterBean(Class clzz, String beanName, Map<String, Object> propertyValueMap,
                                                                  Map<String, String> propertyReferenceMap,
                                             List<Object[]> constructorArgList) {
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();

        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clzz);
        if (null != propertyReferenceMap && !propertyReferenceMap.isEmpty()) {
            for (Map.Entry<String, String> entry : propertyReferenceMap.entrySet()) {
                beanDefinitionBuilder.addPropertyReference(entry.getKey(), entry.getValue());
            }
        }

        if (null != propertyValueMap && !propertyValueMap.isEmpty()) {
            for (Map.Entry<String, Object> entry : propertyValueMap.entrySet()) {
                beanDefinitionBuilder.addPropertyValue(entry.getKey(), entry.getValue());
            }
        }

        if (null != constructorArgList && !constructorArgList.isEmpty()) {
            for (Object[] arg : constructorArgList) {
                if (arg.length >= 2) {
                    if ((Boolean) arg[0]) {
                        beanDefinitionBuilder.addConstructorArgReference((String) arg[1]);
                    } else {
                        beanDefinitionBuilder.addConstructorArgValue(arg[1]);
                    }
                }
            }
        }

        defaultListableBeanFactory.registerBeanDefinition(beanName, beanDefinitionBuilder.getBeanDefinition());

    }

    public static void logError(Log log, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        log.error(sw.toString());
    }

    public static JmsTemplate getJmsTemplateByIndex(int index) {
        if (0 > index || jmsTemplateList.size() <= index) {
            return null;
        }
        return jmsTemplateList.get(index);
    }

    public static RabbitTemplate getRabbitTelmpateByIndex(int index) {
        if (0 > index || rabbitTemplateList.size() <= index) {
            return null;
        }
        return rabbitTemplateList.get(index);
    }

    public static Integer getCcsidByIndex(int index) {
        if (0 > index || ccsidList.size() <= index) {
            return null;
        }
        return ccsidList.get(index);
    }

    public static IntegrationConfiguration.DistributionMessageGateway getDistributionMessageGateway() {
        return applicationContext.getBean(IntegrationConfiguration.DistributionMessageGateway.class);
    }
}
