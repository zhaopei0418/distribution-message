package com.github.distributionmessage.transformer;

import com.github.distributionmessage.constant.CommonConstant;
import com.github.distributionmessage.utils.DistributionUtils;
import com.github.distributionmessage.utils.MessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.integration.support.DefaultMessageBuilderFactory;
import org.springframework.integration.transformer.Transformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * @author zhaopei
 */
@Slf4j
public class SignAndWrapTransformer implements Transformer {

    @Override
    public Message<?> transform(Message<?> message) {
        try {
            Assert.notNull(message, "Message must not be null");
            Object payload = message.getPayload();
            Assert.notNull(payload, "Message payload must not be null");
            String strPayload = null;
            if (payload instanceof byte[]) {
                strPayload = new String((byte[]) payload, CommonConstant.CHARSET);
            } else if (payload instanceof File) {
                FileToStringTransformer fileToStringTransformer = new FileToStringTransformer();
                fileToStringTransformer.setCharset(CommonConstant.CHARSET);
                fileToStringTransformer.setDeleteFiles(true);
                message = fileToStringTransformer.transform(message);
                strPayload = (String) message.getPayload();
            } else {
                strPayload = (String) payload;
            }
            MessageHeaders messageHeaders = message.getHeaders();
            String serviceUrl = messageHeaders.get(CommonConstant.SIGN_AND_WRAP_SERVICE_URL, String.class);
            String ieType = messageHeaders.get(CommonConstant.SIGN_AND_WRAP_IE_TYPE, String.class);

            String result = DistributionUtils.signAndWrap(serviceUrl, strPayload, ieType);
            if (StringUtils.isBlank(result)) {
                MessageUtils.resendSignMessage(message);
                throw new MessagingException(message, "sign and wrap fail!");
            } else {
                MessageUtils.removeResendSignKey(strPayload.getBytes(StandardCharsets.UTF_8));
            }

            Message<?> transformedMessage = new DefaultMessageBuilderFactory().withPayload(result.getBytes(StandardCharsets.UTF_8))
                    .copyHeaders(message.getHeaders())
                    .build();
            return transformedMessage;
        } catch (Exception e) {
            throw new MessagingException(message, "failed to sign wrap Message", e);
        }
    }
}
