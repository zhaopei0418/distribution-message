package com.github.distributionmessage;

import com.github.distributionmessage.config.DistributionProp;
import com.github.distributionmessage.utils.CommonUtils;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.jms.connection.CachingConnectionFactory;

import javax.jms.ConnectionFactory;

@SpringBootApplication
@EnableIntegration
@Configuration
public class DistributionMessageApplication {

	@Autowired
	private DistributionProp distributionProp;

	public static void main(String[] args) {
		ApplicationContext applicationContext =  SpringApplication.run(DistributionMessageApplication.class, args);
		CommonUtils.setApplicationContext(applicationContext);
		CommonUtils.initListenerContainer();
	}

	@Bean
	@Primary
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

	@Bean(name = "secondConnectionFactory")
	public ConnectionFactory secondConnectionFactory(
			@Value("${distribution.second.sessionCacheSize}") int sessionCacheSize,
			@Value("${distribution.second.hostName}") String hostName,
			@Value("${distribution.second.port}") int port,
			@Value("${distribution.second.queueManager}") String queueManager,
			@Value("${distribution.second.channel}") String channel,
			@Value("${distribution.second.ccsid}") int ccsid
	) throws Exception {
		CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory();
		cachingConnectionFactory.setSessionCacheSize(sessionCacheSize);
		MQQueueConnectionFactory mqQueueConnectionFactory = new MQQueueConnectionFactory();
		mqQueueConnectionFactory.setHostName(hostName);
		mqQueueConnectionFactory.setPort(port);
		mqQueueConnectionFactory.setQueueManager(queueManager);
		mqQueueConnectionFactory.setChannel(channel);
		mqQueueConnectionFactory.setCCSID(ccsid);
		mqQueueConnectionFactory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
		cachingConnectionFactory.setTargetConnectionFactory(mqQueueConnectionFactory);
		return cachingConnectionFactory;
	}

	@Bean(name = "thirdConnectionFactory")
	public ConnectionFactory thirdConnectionFactory(
			@Value("${distribution.third.sessionCacheSize}") int sessionCacheSize,
			@Value("${distribution.third.hostName}") String hostName,
			@Value("${distribution.third.port}") int port,
			@Value("${distribution.third.queueManager}") String queueManager,
			@Value("${distribution.third.channel}") String channel,
			@Value("${distribution.third.ccsid}") int ccsid
	) throws Exception {
		CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory();
		cachingConnectionFactory.setSessionCacheSize(sessionCacheSize);
		MQQueueConnectionFactory mqQueueConnectionFactory = new MQQueueConnectionFactory();
		mqQueueConnectionFactory.setHostName(hostName);
		mqQueueConnectionFactory.setPort(port);
		mqQueueConnectionFactory.setQueueManager(queueManager);
		mqQueueConnectionFactory.setChannel(channel);
		mqQueueConnectionFactory.setCCSID(ccsid);
		mqQueueConnectionFactory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
		cachingConnectionFactory.setTargetConnectionFactory(mqQueueConnectionFactory);
		return cachingConnectionFactory;
	}
}
