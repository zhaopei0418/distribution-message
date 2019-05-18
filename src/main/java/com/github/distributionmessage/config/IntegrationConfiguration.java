package com.github.distributionmessage.config;

import com.github.distributionmessage.listener.DistributionMessageListener;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import javax.annotation.PostConstruct;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

@Configuration
public class IntegrationConfiguration {

    @Autowired
    private DistributionMessageListener distributionMessageListener;

    @Autowired
    private DistributionProp distributionProp;

    @Bean
    public DefaultMessageListenerContainer defaultMessageListenerContainer(ConnectionFactory connectionFactory) {
        DefaultMessageListenerContainer defaultMessageListenerContainer = new DefaultMessageListenerContainer();
        defaultMessageListenerContainer.setConnectionFactory(connectionFactory);
        defaultMessageListenerContainer.setConcurrency(this.distributionProp.getMinConcurrency() + "-"
            + this.distributionProp.getMaxConcurrency());
        defaultMessageListenerContainer.setMessageListener(this.distributionMessageListener);
        defaultMessageListenerContainer.setDestinationName(this.distributionProp.getQueueName());
        return defaultMessageListenerContainer;
    }

}
