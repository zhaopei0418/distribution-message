package com.github.distributionmessage.transformer;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.springframework.integration.support.DefaultMessageBuilderFactory;
import org.springframework.integration.transformer.Transformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.util.Assert;

import com.github.distributionmessage.constant.CommonConstant;
import com.github.distributionmessage.utils.DistributionUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * @author zhaopei
 */
@Slf4j
public class HGHeadUnWrapTransformer implements Transformer {

    @Override
    public Message<?> transform(Message<?> message) {
        try {
            Assert.notNull(message, "Message must not be null");
            Object payload = message.getPayload();
            Assert.notNull(payload, "Message payload must not be null");
            String strPayload = null;
            MessageHeaders messageHeaders = message.getHeaders();
            byte[] result = null;
            if (payload instanceof byte[]) {
                result = DistributionUtils.removeHGHead((byte[]) payload);
            } else if (payload instanceof File) {
                File pl = (File) payload;
                String fileName = messageHeaders.get(CommonConstant.HEADER_FILE_NAME, String.class);
                if (pl.exists()) {
                    result = DistributionUtils.removeHGHead(FileUtils.readFileToByteArray(pl));
                    if (!pl.delete()) {
                        log.info("file [{}] delete fail.", fileName);
                    }
                } else {
                    log.info("file [{}] message not exists. not handler.", fileName);
                    return null;
                }
            } else {
                strPayload = (String) payload;
                result = DistributionUtils.removeHGHead(strPayload);
            }

            if (null == result || 0 == result.length) {
                return null;
            }
            Message<?> transformedMessage = new DefaultMessageBuilderFactory().withPayload(result)
                    .copyHeaders(message.getHeaders())
                    .build();
//            log.info("transformed message [{}]", transformedMessage);
            return transformedMessage;
        }
        catch (Exception e) {
            throw new MessagingException(message, "failed to hg head unwrap Message", e);
        }
    }
}
