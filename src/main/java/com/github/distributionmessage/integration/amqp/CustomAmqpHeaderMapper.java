package com.github.distributionmessage.integration.amqp;

import com.github.distributionmessage.constant.CommonConstant;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.integration.amqp.support.DefaultAmqpHeaderMapper;

import java.util.Map;

/**
 * @author zhaopei
 */
@Getter
@Setter
@Slf4j
public class CustomAmqpHeaderMapper extends DefaultAmqpHeaderMapper {


    private String senderId;

    private String receiverId;

    private String serviceUrl;

    private String ieType;

    private String startNode;

    private String endNode;

    public CustomAmqpHeaderMapper(String endNode, String startNode) {
        this(DefaultAmqpHeaderMapper.inboundRequestHeaders(), DefaultAmqpHeaderMapper.inboundReplyHeaders());
        this.endNode = endNode;
        this.startNode = startNode;
    }

    protected CustomAmqpHeaderMapper(String[] requestHeaderNames, String[] replyHeaderNames) {
        super(requestHeaderNames, replyHeaderNames);
    }

    protected CustomAmqpHeaderMapper(String senderId, String receiverId, String serviceUrl, String ieType) {
        this(DefaultAmqpHeaderMapper.inboundRequestHeaders(), DefaultAmqpHeaderMapper.inboundReplyHeaders());
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.serviceUrl = serviceUrl;
        this.ieType = ieType;
    }

    @Override
    public Map<String, Object> toHeadersFromRequest(MessageProperties source) {
        Map<String, Object> result = super.toHeadersFromRequest(source);
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
            if (StringUtils.isNotBlank(this.startNode)) {
                result.put(CommonConstant.START_NODE, this.startNode);
            }
            if (StringUtils.isNotBlank(this.endNode)) {
                result.put(CommonConstant.END_NODE, this.endNode);
            }
        }
        return result;
    }

    public static CustomAmqpHeaderMapper inboundWrapMapper(String senderId, String receiverId) {
        return new CustomAmqpHeaderMapper(senderId, receiverId, null, null);
    }

    public static CustomAmqpHeaderMapper inboundSvWrapMapper(String startNode, String endNode) {
        return new CustomAmqpHeaderMapper(startNode, endNode);
    }

    public static CustomAmqpHeaderMapper inboundSignAndWrapMapper(String serviceUrl, String ieType) {
        return new CustomAmqpHeaderMapper(null, null, serviceUrl, ieType);
    }
}
