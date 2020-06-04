package com.github.distributionmessage.thread;

import com.github.distributionmessage.config.IntegrationConfiguration;
import lombok.Data;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.ExecutorService;

@Data
public class RabbitSendMessageThread implements Runnable {

    private static final Log logger = LogFactory.getLog(RabbitSendMessageThread.class);

    private static ExecutorService executorService;

    private Object message;

    private RabbitTemplate rabbitTemplate;

    private String queueName;

    public RabbitSendMessageThread(RabbitTemplate rabbitTemplate, Object message, String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.message = message;
        this.queueName = queueName;
    }

    @Override
    public void run() {
        long startTime = System.nanoTime();
        IntegrationConfiguration.CACHE_QUEUE.poll();
        this.rabbitTemplate.convertAndSend(queueName, message);
        logger.info("cache size [" + IntegrationConfiguration.CACHE_QUEUE.size() + "] send message to queue[" + this.queueName + "] use["
                + ((double)(System.nanoTime() - startTime) / 1000000.0) + "]ms");
    }

    public static ExecutorService getExecutorService() {
        return executorService;
    }

    public static void setExecutorService(ExecutorService executorService) {
        RabbitSendMessageThread.executorService = executorService;
    }
}
