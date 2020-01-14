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

    private static List<JmsMessageDrivenEndpoint> jmsMessageDrivenEndpointList = new ArrayList<>();

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
        CachingConnectionFactory cachingConnectionFactory = null;
        ThreadPoolTaskExecutor threadPoolTaskExecutor = null;
        DefaultMessageListenerContainer defaultMessageListenerContainer = null;
        JmsMessageDrivenEndpoint jmsMessageDrivenEndpoint = null;
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
}
