package com.github.distributionmessage.utils;

import com.github.distributionmessage.config.DistributionProp;
import com.github.distributionmessage.constant.ChannelConstant;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.jms.ChannelPublishingJmsMessageListener;
import org.springframework.integration.jms.JmsMessageDrivenEndpoint;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

public class CommonUtils {

    private static List<JmsTemplate> jmsTemplateList = new ArrayList<>();

    private static List<Integer> ccsidList = new ArrayList<>();

    private static final Log logger = LogFactory.getLog(CommonUtils.class);

    private static ApplicationContext applicationContext;

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static void setApplicationContext(ApplicationContext applicationContext) {
        CommonUtils.applicationContext = applicationContext;
    }

    public static void initListenerContainer() {
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        DistributionProp distributionProp = defaultListableBeanFactory.getBean(DistributionProp.class);

        String[] queueNames = distributionProp.getQueueName().split(",");
        String suffix = "";
        Map<String, Object> propertyValueMap = null;
        Map<String, String> propertyReferenceMap = null;
        List<Object[]> constructorArgList = null;
        MQQueueConnectionFactory mqQueueConnectionFactory = null;
        String connectionFactoryBeanName = null;
        String taskExecutorBeanName = null;
        String listenerContainerBeanName = null;
        String jmsMessageDrivenEndpointBeanName = null;
        String queueName = null;
        if (queueNames.length > 1) {
            for (int i = 1; i < queueNames.length; i++) {
                queueName = queueNames[i].trim();
                suffix = queueName + "-" + i + "-";
                propertyValueMap = new HashMap<>();
                propertyReferenceMap = new HashMap<>();
                constructorArgList = new ArrayList<>();

                try {
                    connectionFactoryBeanName = "connectionFactory" + suffix;
                    taskExecutorBeanName = "taskExecutor" + suffix;
                    listenerContainerBeanName = "listenerContainer" + suffix;
                    jmsMessageDrivenEndpointBeanName = "messageDrivenEndpoint" + suffix;

                    propertyValueMap.put("sessionCacheSize", distributionProp.getMaxConcurrency() * 2);
                    mqQueueConnectionFactory = new MQQueueConnectionFactory();
                    mqQueueConnectionFactory.setHostName(distributionProp.getHostName());
                    mqQueueConnectionFactory.setPort(distributionProp.getPort());
                    mqQueueConnectionFactory.setQueueManager(distributionProp.getQueueManager());
                    mqQueueConnectionFactory.setChannel(distributionProp.getChannel());
                    mqQueueConnectionFactory.setCCSID(distributionProp.getCcsid());
                    mqQueueConnectionFactory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
                    propertyValueMap.put("targetConnectionFactory", mqQueueConnectionFactory);
                    createAndregisterBean(CachingConnectionFactory.class, connectionFactoryBeanName, propertyValueMap, null, null);

                    propertyValueMap.clear();
                    propertyValueMap.put("corePoolSize", distributionProp.getMinConcurrency());
                    propertyValueMap.put("maxPoolSize", distributionProp.getMaxConcurrency());
                    propertyValueMap.put("keepAliveSeconds", distributionProp.getKeepAliveSeconds());
                    propertyValueMap.put("queueCapacity", distributionProp.getQueueCapacity());
                    propertyValueMap.put("threadNamePrefix", distributionProp.getThreadNamePrefix() + suffix);
                    propertyValueMap.put("rejectedExecutionHandler", new ThreadPoolExecutor.CallerRunsPolicy());
                    createAndregisterBean(ThreadPoolTaskExecutor.class, taskExecutorBeanName, propertyValueMap, null, null);


                    propertyValueMap.clear();
                    propertyValueMap.put("concurrency", distributionProp.getMinConcurrency() + "-" + distributionProp.getMaxConcurrency());
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
        initOtherListenerContainer();
        initJmsTemplateList();
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

    public static Integer getCcsidByIndex(int index) {
        if (0 > index || ccsidList.size() <= index) {
            return null;
        }
        return ccsidList.get(index);
    }
}
