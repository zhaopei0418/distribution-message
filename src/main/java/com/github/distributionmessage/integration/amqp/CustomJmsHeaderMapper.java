package com.github.distributionmessage.integration.amqp;

import com.github.distributionmessage.constant.CommonConstant;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.integration.jms.DefaultJmsHeaderMapper;

import javax.jms.Message;
import java.util.Map;

/**
 * @author zhaopei
 */
@Getter
@Setter
@Slf4j
public class CustomJmsHeaderMapper extends DefaultJmsHeaderMapper {

    private String senderId;

    private String receiverId;

    private String serviceUrl;

    private String ieType;

    public CustomJmsHeaderMapper(String senderId, String receiverId, String serviceUrl, String ieType) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.serviceUrl = serviceUrl;
        this.ieType = ieType;
    }

    @Override
    public Map<String, Object> toHeaders(Message jmsMessage) {
        Map<String, Object> result = super.toHeaders(jmsMessage);
        if (null != result) {
            if (StringUtils.isNotBlank(this.senderId)) {
                result.put(CommonConstant.SENDER_ID, this.senderId);
            }
            if (StringUtils.isNotBlank(this.receiverId)) {
                result.put(CommonConstant.RECEIVE_ID, this.receiverId);
            }
            if (StringUtils.isNotBlank(this.serviceUrl)) {
                result.put(CommonConstant.SIGN_AND_WRAP_SERVICE_URL, this.serviceUrl);
            }
            if (StringUtils.isNotBlank(this.ieType)) {
                result.put(CommonConstant.SIGN_AND_WRAP_IE_TYPE, this.ieType);
            }
        }

        return result;
    }

    public static CustomJmsHeaderMapper createWrapHeaderMapper(String senderId, String receiverId) {
        return new CustomJmsHeaderMapper(senderId, receiverId, null, null);
    }

    public static CustomJmsHeaderMapper createSignAndWrapHeaderMapper(String serviceUrl, String ieType) {
        return new CustomJmsHeaderMapper(null, null, serviceUrl, ieType);
    }
}
