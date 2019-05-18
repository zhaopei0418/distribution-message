package com.github.distributionmessage;

import com.github.distributionmessage.config.DistributionProp;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.jms.connection.CachingConnectionFactory;

import javax.jms.ConnectionFactory;

@SpringBootApplication
@EnableIntegration
public class DistributionMessageApplication {

	@Autowired
	private DistributionProp distributionProp;

	public static void main(String[] args) {
		SpringApplication.run(DistributionMessageApplication.class, args);
	}

	@Bean
	public ConnectionFactory connectionFactory() throws Exception {
		CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory();
		cachingConnectionFactory.setSessionCacheSize(this.distributionProp.getMaxConcurrency() * 2);
		MQQueueConnectionFactory mqQueueConnectionFactory = new MQQueueConnectionFactory();
		mqQueueConnectionFactory.setHostName(this.distributionProp.getHostName());
		mqQueueConnectionFactory.setPort(this.distributionProp.getPort());
		mqQueueConnectionFactory.setQueueManager(this.distributionProp.getQueueManager());
		mqQueueConnectionFactory.setChannel(this.distributionProp.getChannel());
		mqQueueConnectionFactory.setCCSID(this.distributionProp.getCcsid());
		mqQueueConnectionFactory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
		cachingConnectionFactory.setTargetConnectionFactory(mqQueueConnectionFactory);
		return cachingConnectionFactory;
	}
}
