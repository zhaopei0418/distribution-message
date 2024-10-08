package com.github.distributionmessage.utils;

import com.github.distributionmessage.config.DistributionProp;
import com.github.distributionmessage.config.IntegrationConfiguration;
import com.github.distributionmessage.constant.ChannelConstant;
import com.github.distributionmessage.constant.CommonConstant;
import com.github.distributionmessage.domain.SignWrapParam;
import com.github.distributionmessage.domain.SvWrapParam;
import com.github.distributionmessage.domain.WrapParam;
import com.github.distributionmessage.integration.amqp.CustomAmqpHeaderMapper;
import com.github.distributionmessage.integration.amqp.CustomJmsHeaderMapper;
import com.github.distributionmessage.integration.file.CustomFileReadingMessageSource;
import com.github.distributionmessage.integration.file.FileExtensionFilter;
import com.github.distributionmessage.thrift.SignService;
import com.github.distributionmessage.thrift.factory.TSignClientFactory;
import com.github.distributionmessage.thrift.factory.TSocketPoolFactory;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.thrift.transport.TSocket;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.jms.ChannelPublishingJmsMessageListener;
import org.springframework.integration.jms.JmsMessageDrivenEndpoint;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.util.CollectionUtils;

import javax.jms.JMSException;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author zhaopei
 */

@Slf4j
public class CommonUtils {

    private static List<JmsTemplate> jmsTemplateList = new ArrayList<>();

    private static List<RabbitTemplate> rabbitTemplateList = new ArrayList<>();

    private static List<Integer> ccsidList = new ArrayList<>();

    private static Map<Integer, WrapParam> ibmWrapParamMap = new HashMap<>();
    private static Map<Integer, WrapParam> rabbitmqWrapParamMap = new HashMap<>();
    private static Map<Integer, WrapParam> dirWrapParamMap = new HashMap<>();

    private static Map<Integer, SvWrapParam> ibmSvWrapParamMap = new HashMap<>();
    private static Map<Integer, SvWrapParam> rabbitmqSvWrapParamMap = new HashMap<>();
    private static Map<Integer, SvWrapParam> dirSvWrapParamMap = new HashMap<>();

    private static Map<Integer, SignWrapParam> ibmSignWrapParamMap = new HashMap<>();
    private static Map<Integer, SignWrapParam> rabbitmqSignWrapParamMap = new HashMap<>();
    private static Map<Integer, SignWrapParam> dirSignWrapParamMap = new HashMap<>();

    private static Map<Integer, Boolean> ibmHGSendWrapParamMap = new HashMap<>();
    private static Map<Integer, Boolean> rabbitmqHGSendWrapParamMap = new HashMap<>();
    private static Map<Integer, Boolean> dirHGSendWrapParamMap = new HashMap<>();

    private static Map<Integer, Boolean> ibmHGHeadUnWrapParamMap = new HashMap<>();
    private static Map<Integer, Boolean> rabbitmqHGHeadUnWrapParamMap = new HashMap<>();
    private static Map<Integer, Boolean> dirHGHeadUnWrapParamMap = new HashMap<>();

    private static Map<String, GenericObjectPool<TSocket>> thriftSocketPoolMap = new ConcurrentHashMap<>();
    private static Map<String, GenericObjectPool<SignService.Client>> thriftSignClientPoolMap = new ConcurrentHashMap<>();
    private static Map<Integer, SignWrapParam> thriftIbmSignWrapParamMap = new HashMap<>();
    private static Map<Integer, SignWrapParam> thriftRabbitmqSignWrapParamMap = new HashMap<>();
    private static Map<Integer, SignWrapParam> thriftDirSignWrapParamMap = new HashMap<>();

    private static final Log logger = LogFactory.getLog(CommonUtils.class);

    private static DefaultListableBeanFactory defaultListableBeanFactory;

    private static DistributionProp distributionProp;

    private static ApplicationContext applicationContext;

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static void setApplicationContext(ApplicationContext applicationContext) {
        CommonUtils.applicationContext = applicationContext;
        defaultListableBeanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        distributionProp = defaultListableBeanFactory.getBean(DistributionProp.class);
    }

    public static void initParams() {
        initWrapParamMap();
        initSvWrapParamMap();
        initSignWrapParamMap();
        initThriftSignWrapParamMap();
        
        initHGSendWrap();
        initHGHeadUnWrap();

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

//    public static void main(String[] args) throws Exception {
//        TTransport transport = new TSocket("localhost", 9090);
//        TFramedTransport framedTransport = new TFramedTransport(transport);
//        SignService.Client client = new SignService.Client(new TBinaryProtocol(framedTransport));
//        transport.open();
//        log.info("connect opened...");
//        String result = client.signAndWrap("111", "E");
//        log.info("result=[{}]", result);
//        transport.close();
//    }

    public static void initWrapParamMap() {
        if (CollectionUtils.isEmpty(distributionProp.getWrapChain())) {
            logger.error("wrapChain error");
            return;
        }

        String wrapChain = null;
        String[] chainInfos = null;
        String senderId = null;
        String receiverId = null;
        String type = null;
        int index = 0;
        WrapParam wrapParam = null;

        for (int i = 0 ; i < distributionProp.getWrapChain().size(); i++) {
            wrapChain = distributionProp.getWrapChain().get(i);

            chainInfos = wrapChain.split("\\|");
            for (int j = 0; j < chainInfos.length; j++) {
                logger.info("chainInfos=[" + j + "]=[" + chainInfos[j] + "]");
            }
            if (chainInfos.length < 4) {
                log.error(String.format("chainInfos=[%s] error!", wrapChain));
                continue;
            }

            try {
                senderId = chainInfos[0].trim();
                receiverId = chainInfos[1].trim();
                type = chainInfos[2].trim();
                index = Integer.parseInt(chainInfos[3].trim());

                wrapParam = new WrapParam(senderId, receiverId);

                if ("i".equalsIgnoreCase(type)) {
                    ibmWrapParamMap.put(index, wrapParam);
                } else if ("r".equalsIgnoreCase(type)) {
                    rabbitmqWrapParamMap.put(index, wrapParam);
                } else {
                    dirWrapParamMap.put(index, wrapParam);
                }

            } catch (Exception e) {
                logError(logger, e);
            }

        }
    }

    public static void initSvWrapParamMap() {
        if (CollectionUtils.isEmpty(distributionProp.getSvWrapChain())) {
            logger.error("svWrapChain error");
            return;
        }

        String svWrapChain = null;
        String[] chainInfos = null;
        String startNode = null;
        String endNode = null;
        String type = null;
        int index = 0;
        SvWrapParam svWrapParam = null;

        for (int i = 0 ; i < distributionProp.getSvWrapChain().size(); i++) {
            svWrapChain = distributionProp.getSvWrapChain().get(i);

            chainInfos = svWrapChain.split("\\|");
            for (int j = 0; j < chainInfos.length; j++) {
                logger.info("chainInfos=[" + j + "]=[" + chainInfos[j] + "]");
            }
            if (chainInfos.length < 4) {
                log.error(String.format("chainInfos=[%s] error!", svWrapChain));
                continue;
            }

            try {
                startNode = chainInfos[0].trim();
                endNode = chainInfos[1].trim();
                type = chainInfos[2].trim();
                index = Integer.parseInt(chainInfos[3].trim());

                svWrapParam = new SvWrapParam(startNode, endNode);

                if ("i".equalsIgnoreCase(type)) {
                    ibmSvWrapParamMap.put(index, svWrapParam);
                } else if ("r".equalsIgnoreCase(type)) {
                    rabbitmqSvWrapParamMap.put(index, svWrapParam);
                } else {
                    dirSvWrapParamMap.put(index, svWrapParam);
                }

            } catch (Exception e) {
                logError(logger, e);
            }

        }
    }

    private static void initThriftSignWrapParamMap() {
        if (CollectionUtils.isEmpty(distributionProp.getThriftSignAndWrapChain())) {
            logger.error("thriftWrapChain error");
            return;
        }

        String chain = null;
        String[] chainInfos = null;
        String ip = null;
        int port = 0;
        int timeout = 0;
        int minIdle = 0;
        int maxIdle = 0;
        int maxTotal = 0;
        String ieType = null;
        String type = null;
        int index = 0;

        String key = null;
        SignWrapParam signWrapParam = null;

        TSocketPoolFactory socketPoolFactory = null;
        GenericObjectPoolConfig<TSocket> objectPoolConfig = null;
        GenericObjectPool<TSocket> socketPool = null;

        TSignClientFactory signClientFactory = null;
        GenericObjectPoolConfig<SignService.Client> signClientPoolConfig = null;
        GenericObjectPool<SignService.Client> signClientPool = null;

        for (int i = 0 ; i < distributionProp.getThriftSignAndWrapChain().size(); i++) {
            chain = distributionProp.getThriftSignAndWrapChain().get(i);

            chainInfos = chain.split("\\|");
            for (int j = 0; j < chainInfos.length; j++) {
                logger.info("thrift sign and wrap chainInfos=[" + j + "]=[" + chainInfos[j] + "]");
            }
            if (chainInfos.length < 9) {
                log.error(String.format("thrift sign and wrap chainInfos=[%s] error!", chain));
                continue;
            }

            try {
                ip = chainInfos[0].trim();
                port = Integer.parseInt(chainInfos[1].trim());
                timeout = Integer.parseInt(chainInfos[2].trim());
                minIdle = Integer.parseInt(chainInfos[3].trim());
                maxIdle = Integer.parseInt(chainInfos[4].trim());
                maxTotal = Integer.parseInt(chainInfos[5].trim());
                ieType = chainInfos[6].trim();
                type = chainInfos[7].trim();
                index = Integer.parseInt(chainInfos[8].trim());

                key = String.format("%s-%d-%s", ip, port, ieType);
                signWrapParam = new SignWrapParam(key, ieType);

                if ("i".equalsIgnoreCase(type)) {
                    thriftIbmSignWrapParamMap.put(index, signWrapParam);
                } else if ("r".equalsIgnoreCase(type)) {
                    thriftRabbitmqSignWrapParamMap.put(index, signWrapParam);
                } else {
                    thriftDirSignWrapParamMap.put(index, signWrapParam);
                }

                if (!thriftSocketPoolMap.containsKey(key)) {
                    socketPoolFactory = new TSocketPoolFactory(ip, port, timeout);

                    objectPoolConfig = new GenericObjectPoolConfig<>();
                    objectPoolConfig.setMinIdle(minIdle);
                    objectPoolConfig.setMaxIdle(maxIdle);
                    objectPoolConfig.setMaxTotal(maxTotal);

                    socketPool = new GenericObjectPool<>(socketPoolFactory, objectPoolConfig);
                    thriftSocketPoolMap.put(key, socketPool);
                }

                if (!thriftSignClientPoolMap.containsKey(key)) {
                    signClientFactory = new TSignClientFactory(ip, port, timeout);

                    signClientPoolConfig = new GenericObjectPoolConfig<>();
                    signClientPoolConfig.setMinIdle(minIdle);
                    signClientPoolConfig.setMaxIdle(maxIdle);
                    signClientPoolConfig.setMaxTotal(maxTotal);

                    signClientPool = new GenericObjectPool<>(signClientFactory, signClientPoolConfig);
                    thriftSignClientPoolMap.put(key, signClientPool);
                }

            } catch (Exception e) {
                logError(logger, e);
            }

        }

    }

    public static void initSignWrapParamMap() {
        if (CollectionUtils.isEmpty(distributionProp.getSignAndWrapChain())) {
            logger.error("signWrapChain error");
            return;
        }

        String signWrapChain = null;
        String[] chainInfos = null;
        String url = null;
        String ieType = null;
        String type = null;
        int index = 0;
        SignWrapParam signWrapParam = null;

        for (int i = 0 ; i < distributionProp.getSignAndWrapChain().size(); i++) {
            signWrapChain = distributionProp.getSignAndWrapChain().get(i);

            chainInfos = signWrapChain.split("\\|");
            for (int j = 0; j < chainInfos.length; j++) {
                logger.info("sign and wrap chainInfos=[" + j + "]=[" + chainInfos[j] + "]");
            }
            if (chainInfos.length < 4) {
                log.error(String.format("sign and wrap chainInfos=[%s] error!", signWrapChain));
                continue;
            }

            try {
                url = chainInfos[0].trim();
                ieType = chainInfos[1].trim();
                type = chainInfos[2].trim();
                index = Integer.parseInt(chainInfos[3].trim());

                signWrapParam = new SignWrapParam(url, ieType);

                if ("i".equalsIgnoreCase(type)) {
                    ibmSignWrapParamMap.put(index, signWrapParam);
                } else if ("r".equalsIgnoreCase(type)) {
                    rabbitmqSignWrapParamMap.put(index, signWrapParam);
                } else {
                    dirSignWrapParamMap.put(index, signWrapParam);
                }

            } catch (Exception e) {
                logError(logger, e);
            }

        }
    }

    public static void initHGSendWrap() {
        if (CollectionUtils.isEmpty(distributionProp.getHgSendWrapChain())) {
            logger.error("wrapChain error");
            return;
        }

        String hgSendWrapChain = null;
        String[] chainInfos = null;
        String type = null;
        int index = 0;

        for (int i = 0 ; i < distributionProp.getHgSendWrapChain().size(); i++) {
            hgSendWrapChain = distributionProp.getHgSendWrapChain().get(i);

            chainInfos = hgSendWrapChain.split("\\|");
            for (int j = 0; j < chainInfos.length; j++) {
                logger.info("chainInfos=[" + j + "]=[" + chainInfos[j] + "]");
            }
            if (chainInfos.length < 2) {
                log.error(String.format("chainInfos=[%s] error!", hgSendWrapChain));
                continue;
            }

            try {
                type = chainInfos[0].trim();
                index = Integer.parseInt(chainInfos[1].trim());

                if ("i".equalsIgnoreCase(type)) {
                    ibmHGSendWrapParamMap.put(index, true);
                } else if ("r".equalsIgnoreCase(type)) {
                    rabbitmqHGSendWrapParamMap.put(index, true);
                } else {
                    dirHGSendWrapParamMap.put(index, true);
                }

            } catch (Exception e) {
                logError(logger, e);
            }

        }
    }

    public static void initHGHeadUnWrap() {
        if (CollectionUtils.isEmpty(distributionProp.getHgHeadUnWrapChain())) {
            logger.error("wrapChain error");
            return;
        }

        String hgHeadUnWrapParam = null;
        String[] chainInfos = null;
        String type = null;
        int index = 0;

        for (int i = 0 ; i < distributionProp.getHgHeadUnWrapChain().size(); i++) {
            hgHeadUnWrapParam = distributionProp.getHgHeadUnWrapChain().get(i);

            chainInfos = hgHeadUnWrapParam.split("\\|");
            for (int j = 0; j < chainInfos.length; j++) {
                logger.info("chainInfos=[" + j + "]=[" + chainInfos[j] + "]");
            }
            if (chainInfos.length < 2) {
                log.error(String.format("chainInfos=[%s] error!", hgHeadUnWrapParam));
                continue;
            }

            try {
                type = chainInfos[0].trim();
                index = Integer.parseInt(chainInfos[1].trim());

                if ("i".equalsIgnoreCase(type)) {
                    ibmHGHeadUnWrapParamMap.put(index, true);
                } else if ("r".equalsIgnoreCase(type)) {
                    rabbitmqHGHeadUnWrapParamMap.put(index, true);
                } else {
                    dirHGHeadUnWrapParamMap.put(index, true);
                }

            } catch (Exception e) {
                logError(logger, e);
            }

        }
    }

    private static void initOtherListenerContainer() {
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
        String inputQueueInfo = null;
        String outputChannelName = null;

        for (int k = 0; k < distributionProp.getOtherInputQueue().size(); k++) {
            inputQueueInfo = distributionProp.getOtherInputQueue().get(k);
//        for (String inputQueueInfo : distributionProp.getOtherInputQueue()) {
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
                            ChannelPublishingJmsMessageListener channelPublishingJmsMessageListener = new ChannelPublishingJmsMessageListener();
                            if (ibmSignWrapParamMap.containsKey(k)) {
                                SignWrapParam signWrapParam = ibmSignWrapParamMap.get(k);
                                channelPublishingJmsMessageListener.setHeaderMapper(CustomJmsHeaderMapper.createSignAndWrapHeaderMapper(signWrapParam.getServiceUrl(), signWrapParam.getIeType()));
                                outputChannelName = ChannelConstant.SIGN_WRAP_CHANNEL;
                            } else if (ibmWrapParamMap.containsKey(k)) {
                                WrapParam wrapParam = ibmWrapParamMap.get(k);
                                channelPublishingJmsMessageListener.setHeaderMapper(CustomJmsHeaderMapper.createWrapHeaderMapper(wrapParam.getSenderId(), wrapParam.getReceiverId()));
                                outputChannelName = ChannelConstant.WRAP_CHANNEL;
                            } else if (ibmSvWrapParamMap.containsKey(k)) {
                                SvWrapParam svWrapParam = ibmSvWrapParamMap.get(k);
                                channelPublishingJmsMessageListener.setHeaderMapper(CustomJmsHeaderMapper.createSvWrapHeaderMapper(svWrapParam.getStartNode(), svWrapParam.getEndNode()));
                                outputChannelName = ChannelConstant.SV_WRAP_CHANNEL;
                            } else if (thriftIbmSignWrapParamMap.containsKey(k)) {
                                SignWrapParam signWrapParam = thriftIbmSignWrapParamMap.get(k);
                                channelPublishingJmsMessageListener.setHeaderMapper(CustomJmsHeaderMapper.createSignAndWrapHeaderMapper(signWrapParam.getServiceUrl(), signWrapParam.getIeType()));
                                outputChannelName = ChannelConstant.THRIFT_SIGN_WRAP_CHANNEL;
                            } else if (ibmHGSendWrapParamMap.containsKey(k)) {
                                outputChannelName = ChannelConstant.HG_SEND_WRAP_CHANNEL;
                            } else if (ibmHGHeadUnWrapParamMap.containsKey(k)) {
                                outputChannelName = ChannelConstant.HG_HEAD_UNWRAP_CHANNEL;
                            } else {
                                outputChannelName = ChannelConstant.IBMMQ_RECEIVE_CHANNEL;
                            }
                            constructorArgList.add(new Object[] {false, new ChannelPublishingJmsMessageListener()});
                            propertyValueMap.clear();
//                            propertyValueMap.put("outputChannelName", ChannelConstant.IBMMQ_RECEIVE_CHANNEL);
                            propertyValueMap.put("outputChannelName", outputChannelName);
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
        String inQueueInfo = null;

        for (int k = 0; k < distributionProp.getOtherRabbitInputQueue().size(); k++) {
            inQueueInfo = distributionProp.getOtherRabbitInputQueue().get(k);
//        for (String inQueueInfo : distributionProp.getOtherRabbitInputQueue()) {
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
//                propertyValueMap.put("outputChannelName", ChannelConstant.IBMMQ_RECEIVE_CHANNEL);
                if (rabbitmqSignWrapParamMap.containsKey(k)) {
                    propertyValueMap.put("outputChannelName", ChannelConstant.SIGN_WRAP_CHANNEL);
                } else if (rabbitmqWrapParamMap.containsKey(k)) {
                    propertyValueMap.put("outputChannelName", ChannelConstant.WRAP_CHANNEL);
                } else if (rabbitmqSvWrapParamMap.containsKey(k)) {
                    propertyValueMap.put("outputChannelName", ChannelConstant.SV_WRAP_CHANNEL);
                } else if (thriftRabbitmqSignWrapParamMap.containsKey(k)) {
                    propertyValueMap.put("outputChannelName", ChannelConstant.THRIFT_SIGN_WRAP_CHANNEL);
                } else if (ibmHGSendWrapParamMap.containsKey(k)) {
                    propertyValueMap.put("outputChannelName", ChannelConstant.HG_SEND_WRAP_CHANNEL);
                } else if (ibmHGHeadUnWrapParamMap.containsKey(k)) {
                    propertyValueMap.put("outputChannelName", ChannelConstant.HG_HEAD_UNWRAP_CHANNEL);
                } else {
                    propertyValueMap.put("outputChannelName", ChannelConstant.IBMMQ_RECEIVE_CHANNEL);
                }
                createAndregisterBean(AmqpInboundChannelAdapter.class, rabbitAmqpInboundChannelAdapterBeanName, propertyValueMap, null, constructorArgList);
                logger.info("init rabbit amqpInboundChannelAdapter finished.");

                AmqpInboundChannelAdapter amqpInboundChannelAdapter = (AmqpInboundChannelAdapter) defaultListableBeanFactory.getBean(rabbitAmqpInboundChannelAdapterBeanName);
                if (rabbitmqSignWrapParamMap.containsKey(k)) {
                    SignWrapParam signWrapParam = rabbitmqSignWrapParamMap.get(k);
                    amqpInboundChannelAdapter.setHeaderMapper(CustomAmqpHeaderMapper.inboundSignAndWrapMapper(signWrapParam.getServiceUrl(), signWrapParam.getIeType()));
                } else if (rabbitmqWrapParamMap.containsKey(k)) {
                    WrapParam wrapParam = rabbitmqWrapParamMap.get(k);
                    amqpInboundChannelAdapter.setHeaderMapper(CustomAmqpHeaderMapper.inboundWrapMapper(wrapParam.getSenderId(), wrapParam.getReceiverId()));
                } else if (rabbitmqSvWrapParamMap.containsKey(k)) {
                    SvWrapParam svWrapParam = rabbitmqSvWrapParamMap.get(k);
                    amqpInboundChannelAdapter.setHeaderMapper(CustomAmqpHeaderMapper.inboundSvWrapMapper(svWrapParam.getStartNode(), svWrapParam.getEndNode()));
                } else if (thriftRabbitmqSignWrapParamMap.containsKey(k)) {
                    SignWrapParam signWrapParam = thriftRabbitmqSignWrapParamMap.get(k);
                    amqpInboundChannelAdapter.setHeaderMapper(CustomAmqpHeaderMapper.inboundSignAndWrapMapper(signWrapParam.getServiceUrl(), signWrapParam.getIeType()));
                }
                amqpInboundChannelAdapter.start();

//                ((AmqpInboundChannelAdapter) defaultListableBeanFactory.getBean(rabbitAmqpInboundChannelAdapterBeanName)).start();
                ((SimpleMessageListenerContainer) defaultListableBeanFactory.getBean(rabbitMessageListenerContainerBeanName)).start();
            } catch (Exception e) {
                logError(logger, e);
            }
        }
    }

    private static void initRabbitTemplateList() {
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
//        String fileFilter = null;
        String fileExtension = null;
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
        String inDirInfo = null;
        String outputChannelName = null;

        for (int k = 0; k < distributionProp.getOtherDirectorInput().size(); k++) {
            inDirInfo = distributionProp.getOtherDirectorInput().get(k);
//        for (String inDirInfo : distributionProp.getOtherDirectorInput()) {
            directoryInfos = inDirInfo.split("\\|");
            if (directoryInfos.length < 9) {
                continue;
            }

            propertyValueMap = new HashMap<>();
            directory = directoryInfos[0].trim();
//            fileFilter = directoryInfos[1].trim();
            fileExtension = directoryInfos[1].trim();
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
//                fileReadingMessageSource = new FileReadingMessageSource();
                if (dirSignWrapParamMap.containsKey(k)) {
                    SignWrapParam signWrapParam = dirSignWrapParamMap.get(k);
                    fileReadingMessageSource = CustomFileReadingMessageSource.signAndWrapMessageSource(signWrapParam.getServiceUrl(), signWrapParam.getIeType());
                    outputChannelName = ChannelConstant.SIGN_WRAP_CHANNEL;
                } else if (dirWrapParamMap.containsKey(k)) {
                    WrapParam wrapParam = dirWrapParamMap.get(k);
                    fileReadingMessageSource = CustomFileReadingMessageSource.wrapMessageSource(wrapParam.getSenderId(), wrapParam.getReceiverId());
                    outputChannelName = ChannelConstant.WRAP_CHANNEL;
                } else if (dirSvWrapParamMap.containsKey(k)) {
                    SvWrapParam svWrapParam = dirSvWrapParamMap.get(k);
                    fileReadingMessageSource = CustomFileReadingMessageSource.svWrapMessageSource(svWrapParam.getStartNode(), svWrapParam.getEndNode());
                    outputChannelName = ChannelConstant.SV_WRAP_CHANNEL;
                } else if (thriftDirSignWrapParamMap.containsKey(k)) {
                    SignWrapParam signWrapParam = thriftDirSignWrapParamMap.get(k);
                    fileReadingMessageSource = CustomFileReadingMessageSource.signAndWrapMessageSource(signWrapParam.getServiceUrl(), signWrapParam.getIeType());
                    outputChannelName = ChannelConstant.THRIFT_SIGN_WRAP_CHANNEL;
                } else if (ibmHGSendWrapParamMap.containsKey(k)) {
                    fileReadingMessageSource = new FileReadingMessageSource();
                    outputChannelName = ChannelConstant.HG_SEND_WRAP_CHANNEL;
                } else if (ibmHGHeadUnWrapParamMap.containsKey(k)) {
                    fileReadingMessageSource = new FileReadingMessageSource();
                    outputChannelName = ChannelConstant.HG_HEAD_UNWRAP_CHANNEL;
                } else {
                    fileReadingMessageSource = new FileReadingMessageSource();
                    outputChannelName = ChannelConstant.FILE_RECEIVE_CHANNEL;
                }
                fileReadingMessageSource.setDirectory(new File(dir));

//                fileReadingMessageSource.setFilter(new SimplePatternFileListFilter(fileFilter));
//                fileReadingMessageSource.setFilter(new FileExtensionFilter(fileExtension));
                fileReadingMessageSource.getScanner().setFilter(new FileExtensionFilter(fileExtension, maxMessagesPrePoll));
                threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
                threadPoolTaskExecutor.initialize();
                threadPoolTaskExecutor.setCorePoolSize(minConcurrency);
                threadPoolTaskExecutor.setMaxPoolSize(maxConcurrency);
                threadPoolTaskExecutor.setKeepAliveSeconds(keepAliveSeconds);
                threadPoolTaskExecutor.setQueueCapacity(queueCapacity);
                threadPoolTaskExecutor.setThreadNamePrefix(threadNamePrefix + suffix);
                threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

                propertyValueMap.put("source", fileReadingMessageSource);
//                propertyValueMap.put("outputChannelName", ChannelConstant.FILE_RECEIVE_CHANNEL);
                propertyValueMap.put("outputChannelName", outputChannelName);
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

    public static String generateSeqNo(int length) {
        Random random = new Random();
        return String.format("%0" + length + "d", random.nextInt(new BigInteger("10000000000").intValue()));
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

    public static GenericObjectPool<TSocket> getGenericObjectPool(String key) {
        return thriftSocketPoolMap.get(key);
    }

    public static GenericObjectPool<SignService.Client> getSignClientPool(String key) {
        return thriftSignClientPoolMap.get(key);
    }
}
