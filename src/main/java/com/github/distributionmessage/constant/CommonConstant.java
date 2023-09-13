package com.github.distributionmessage.constant;

import java.time.format.DateTimeFormatter;

/**
 * @author zhaopei
 */
public interface CommonConstant {

    String CHARSET = "UTF-8";

    String CACHE_MODE_CHANNEL = "CHANNEL";

    String CACHE_MODE_CONNECTION = "CONNECTION";

    String FILE_HEADERS_DIRECTORY = "directory";

    String MESSAGE_HEADER_PREFIX = "custom_";

    String SIGN_AND_WRAP_SERVICE_URL = MESSAGE_HEADER_PREFIX + "signAndWrapServiceUrl";

    String SIGN_AND_WRAP_IE_TYPE = MESSAGE_HEADER_PREFIX + "ieType";

    String SENDER_ID = MESSAGE_HEADER_PREFIX + "senderId";

    String RECEIVE_ID = MESSAGE_HEADER_PREFIX + "receiveId";

    DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    String IE_TYPE_IMPORT = "I";

    String IE_TYPE_EXPORT = "E";

    String RESULT_CODE = "code";

    String RESULT_MESSAGE = "message";

    String RESULT_DATA = "data";

    String RESULT_SUCCESS = "0";

    String RESULT_FAIL = "1";
}
