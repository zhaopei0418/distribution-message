package com.github.distributionmessage.thread;

import com.ibm.mq.jms.MQQueue;
import lombok.Data;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;

import java.util.concurrent.ExecutorService;

@Data
public class SendMessageThread implements Runnable {

    private static final Log logger = LogFactory.getLog(SendMessageThread.class);

    private static ExecutorService executorService;

    private Object message;

    private MQQueue queue;

    private JmsTemplate jmsTemplate;

    private MessagePostProcessor messagePostProcessor;

    public SendMessageThread(JmsTemplate jmsTemplate, Object message, MQQueue queue, MessagePostProcessor messagePostProcessor) {
        this.jmsTemplate = jmsTemplate;
        this.message = message;
        this.queue = queue;
        this.messagePostProcessor = messagePostProcessor;
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        this.jmsTemplate.convertAndSend(this.queue, this.message, this.messagePostProcessor);
        logger.info("send message to queue[" + this.queue.getBaseQueueName() + "] use[" + (System.currentTimeMillis() - startTime) + "]ms");
    }

    public static ExecutorService getExecutorService() {
        return executorService;
    }

    public static void setExecutorService(ExecutorService executorService) {
        SendMessageThread.executorService = executorService;
    }
}
