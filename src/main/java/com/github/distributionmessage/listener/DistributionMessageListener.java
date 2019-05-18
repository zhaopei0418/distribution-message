package com.github.distributionmessage.listener;

import com.github.distributionmessage.config.DistributionProp;
import com.github.distributionmessage.constant.CommonConstant;
import com.github.distributionmessage.utils.CommonUtils;
import com.github.distributionmessage.utils.DistributionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import javax.jms.BytesMessage;
import javax.jms.Message;
import javax.jms.MessageListener;

@Component
public class DistributionMessageListener implements MessageListener {

    private final static Log logger = LogFactory.getLog(DistributionMessageListener.class);

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private DistributionProp distributionProp;

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof BytesMessage) {
                BytesMessage bytesMessage = (BytesMessage) message;
                byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
                bytesMessage.readBytes(bytes);
                String sm = new String(bytes, CommonConstant.CHARSET);
                String dxpid = DistributionUtils.getDxpIdByMessage(sm);
                String msgtype = DistributionUtils.getMessageType(sm);
                String queueName = DistributionUtils.getDestinationQueueName(this.distributionProp, dxpid, msgtype);
                logger.info("dxpId=[" + dxpid + "] messageType=["
                        + msgtype + "] distributionQueue=[" + queueName + "]");
                if (null == dxpid || null == msgtype || null == queueName) {
                    logger.error("message is not dxp message! message=[" + sm + "]");
                    return;
                }
                jmsTemplate.send(queueName, session -> {
                    BytesMessage bm = session.createBytesMessage();
                    bm.writeBytes(bytes);
                    return bm;
                });
            } else {
                logger.error("message not is bytes message! message=[" + message + "]");
            }
        } catch (Exception e) {
            CommonUtils.logError(logger, e);
        }
    }
}
