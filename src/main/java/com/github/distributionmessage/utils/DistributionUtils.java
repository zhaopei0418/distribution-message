package com.github.distributionmessage.utils;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.distributionmessage.config.DistributionProp;
import com.github.distributionmessage.config.HttpClientProp;
import com.github.distributionmessage.constant.CommonConstant;
import com.github.distributionmessage.thrift.SignService;


public class DistributionUtils {

    private final static Log logger = LogFactory.getLog(DistributionUtils.class);

    private static HttpClientProp httpClientProp;

    public static String getDestinationQueueName(DistributionProp distributionProp, String dxpid, String msgtype, String senderId) {
        String result = null;

        if (distributionProp.getConditionMutualExclusion()) {
            if (null != distributionProp.getDxpidDistribution() && !distributionProp.getDxpidDistribution().isEmpty()) {
                logger.debug("dxpid distribution");
                result = distributionProp.getDxpidDistribution().get(dxpid);
            } else if (null != distributionProp.getMsgtypeDistribution() && !distributionProp.getMsgtypeDistribution().isEmpty()) {
                logger.debug("msgtype distribution");
                result = distributionProp.getMsgtypeDistribution().get(msgtype);
            } else if (null != distributionProp.getSenderIdDistribution() && !distributionProp.getSenderIdDistribution().isEmpty()) {
                logger.debug("sender distribution");
                result = distributionProp.getSenderIdDistribution().get(senderId);
            } else if (null != distributionProp.getPercentageDistribution() && !distributionProp.getPercentageDistribution().isEmpty()) {
                logger.debug("percentage distribution");
                if (distributionProp.isUpdate()) {
                    distributionProp.setQueuePercentage(new HashMap<String, Integer[]>());
                    int total = distributionProp.getPercentageDistribution().entrySet().stream().mapToInt(Map.Entry::getValue).sum();
                    distributionProp.setPercentageTotal(total);
                    int tmptotal = 0;
                    for (Map.Entry<String, Integer> entry : distributionProp.getPercentageDistribution().entrySet()) {
                        distributionProp.getQueuePercentage().put(entry.getKey(), new Integer[]{tmptotal, tmptotal + entry.getValue()});
                        tmptotal += entry.getValue();
                    }
                    distributionProp.setUpdate(false);
                }

                if (null != distributionProp.getQueuePercentage() && !distributionProp.getQueuePercentage().isEmpty()) {
                    double randomResult = getRandomRange(distributionProp.getPercentageTotal());
                    logger.debug("queuePercentage=[" + JSON.toJSONString(distributionProp.getQueuePercentage()) + "]");
                    logger.debug("randomResult=[" + randomResult + "] total=[" + distributionProp.getPercentageTotal() + "]");
                    for (Map.Entry<String, Integer[]> entry : distributionProp.getQueuePercentage().entrySet()) {
                        if (randomResult >= entry.getValue()[0] && randomResult < entry.getValue()[1]) {
                            result = entry.getKey();
                            break;
                        }
                    }
                }
            } else if (null != distributionProp.getRandomDistribution() && !distributionProp.getRandomDistribution().isEmpty()) {
                logger.debug("randomdistribution=[" + JSON.toJSONString(distributionProp.getRandomDistribution()) + "] size=[" + distributionProp.getRandomDistribution().size() + "]");
                logger.debug("random distribution");
                int randomIndex = getRandomIndex(distributionProp.getRandomDistribution().size());
                logger.debug("randomIndex=[" + randomIndex + "]");
                result = distributionProp.getRandomDistribution().get(randomIndex);
            }
        } else {
            if (null != distributionProp.getDxpidDistribution() && !distributionProp.getDxpidDistribution().isEmpty()) {
                logger.debug("dxpid distribution");
                result = distributionProp.getDxpidDistribution().get(dxpid);
            }

            if (null == result && null != distributionProp.getMsgtypeDistribution() && !distributionProp.getMsgtypeDistribution().isEmpty()) {
                logger.debug("msgtype distribution");
                result = distributionProp.getMsgtypeDistribution().get(msgtype);
            }

            if (null == result && null != distributionProp.getSenderIdDistribution() && !distributionProp.getSenderIdDistribution().isEmpty()) {
                logger.debug("senderId distribution");
                result = distributionProp.getSenderIdDistribution().get(senderId);
            }

            if (null == result && null != distributionProp.getPercentageDistribution() && !distributionProp.getPercentageDistribution().isEmpty()) {
                logger.debug("percentage distribution");
                if (distributionProp.isUpdate()) {
                    distributionProp.setQueuePercentage(new HashMap<String, Integer[]>());
                    int total = distributionProp.getPercentageDistribution().entrySet().stream().mapToInt(Map.Entry::getValue).sum();
                    distributionProp.setPercentageTotal(total);
                    int tmptotal = 0;
                    for (Map.Entry<String, Integer> entry : distributionProp.getPercentageDistribution().entrySet()) {
                        distributionProp.getQueuePercentage().put(entry.getKey(), new Integer[]{tmptotal, tmptotal + entry.getValue()});
                        tmptotal += entry.getValue();
                    }
                    distributionProp.setUpdate(false);
                }

                if (null != distributionProp.getQueuePercentage() && !distributionProp.getQueuePercentage().isEmpty()) {
                    double randomResult = getRandomRange(distributionProp.getPercentageTotal());
                    logger.debug("queuePercentage=[" + JSON.toJSONString(distributionProp.getQueuePercentage()) + "]");
                    logger.debug("randomResult=[" + randomResult + "] total=[" + distributionProp.getPercentageTotal() + "]");
                    for (Map.Entry<String, Integer[]> entry : distributionProp.getQueuePercentage().entrySet()) {
                        if (randomResult >= entry.getValue()[0] && randomResult < entry.getValue()[1]) {
                            result = entry.getKey();
                            break;
                        }
                    }
                }
            }

            if (null == result && null != distributionProp.getRandomDistribution() && !distributionProp.getRandomDistribution().isEmpty()) {
                logger.debug("randomdistribution=[" + JSON.toJSONString(distributionProp.getRandomDistribution()) + "] size=[" + distributionProp.getRandomDistribution().size() + "]");
                logger.debug("random distribution");
                int randomIndex = getRandomIndex(distributionProp.getRandomDistribution().size());
                logger.debug("randomIndex=[" + randomIndex + "]");
                result = distributionProp.getRandomDistribution().get(randomIndex);
            }
        }
        if (null == result) {
            result = distributionProp.getDefaultQueue();
        }
        return getRandomQueueName(result);
    }

    private static int getRandomIndex(int length) {
        return (int) (Math.random() * length);
    }

    private static double getRandomRange(int num) {
        return Math.random() * num;
    }

    private static String getRandomQueueName(String queueNames) {
        if (StringUtils.isEmpty(queueNames)) {
            return null;
        }

        String[] queueNameArr = queueNames.split(",");
        if (queueNameArr.length == 1) {
            return queueNameArr[0];
        }

        return queueNameArr[getRandomIndex(queueNameArr.length)];
    }

    public static String removeTagPrefix(String message) {
        String result = message;
        if (-1 != message.indexOf("<dxp:DxpMsg")) {
            result = message.replaceAll("dxp:", "");
        }
        return result;
    }

    public static boolean isRemoveDxpMsgSvHead(String message) {
        if (null == message) {
            return false;
        }

        int dxpMsgSvStartIndex = message.indexOf("<DxpMsgSv");
        if (dxpMsgSvStartIndex == -1) {
            return false;
        }

        return true;
    }

    public static String removeDxpMsgSvHead(String message) {
//        Pattern pattern = Pattern.compile("<DxpMsg\\s+xmlns.+>(.|\\s)+</DxpMsg>");
//        Matcher matcher = pattern.matcher(message);
//        if (matcher.find()) {
//            return matcher.group();
//        }
//
//        return message;

        int dxpMsgStartIndex = message.indexOf("<DxpMsg ");
        if (dxpMsgStartIndex == -1) {
            dxpMsgStartIndex = message.indexOf("<DxpMsg>");
        }
        int dxpMsgEndIndex = message.lastIndexOf("</DxpMsg>");

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + message.substring(dxpMsgStartIndex, dxpMsgEndIndex + 9);
    }

    public static String getDxpIdByMessage(String message) {
        if (null == message) {
            return null;
        }
        Pattern pattern = Pattern.compile("<ReceiverId>(.+)</ReceiverId>");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String getSenderIdByMessage(String message) {
        if (null == message) {
            return null;
        }
        Pattern pattern = Pattern.compile("<SenderId>(.+)</SenderId>");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String getMessageType(String message) {
        if (null == message) {
            return null;
        }
        Pattern pattern = Pattern.compile("<MsgType>(.+)</MsgType>");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        Document document = stringTransformDocument(message);
        if (null != document) {
            return document.getDocumentElement().getTagName();
        }
        return null;
    }

    public static String getCopMsgId(String message) {
        if (null == message) {
            return null;
        }
        Pattern pattern = Pattern.compile("<CopMsgId>(.+)</CopMsgId>");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String getCreatTime(String message) {
        if (null == message) {
            return null;
        }
        Pattern pattern = Pattern.compile("<CreatTime>(.+)</CreatTime>");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static Document stringTransformDocument(String data) {
        if (StringUtils.isEmpty(data)) {
            return null;
        }
        Document document = null;
        try {
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            document = documentBuilder.parse(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            logger.error("string transform document error!", e);
        }

        return document;
    }

    public static byte[] unWrap(String dxp) {
        if (null == dxp) {
            return null;
        }

        Pattern pattern = Pattern.compile("<Data>(.+)</Data>");
        Matcher matcher = pattern.matcher(dxp);
        if (matcher.find()) {
            return Base64.decodeBase64(matcher.group(1).getBytes(StandardCharsets.UTF_8));
        }
        return dxp.getBytes(StandardCharsets.UTF_8);
    }

    public static String wrap(byte[] data, String senderId, String receiverId) {
        if (null == data || data.length == 0) {
            return null;
        }

        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        stringBuffer.append("<DxpMsg xmlns=\"http://www.chinaport.gov.cn/dxp\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ver=\"1.0\">\n");
        stringBuffer.append("    <TransInfo>\n");
        stringBuffer.append(String.format("        <CopMsgId>%s</CopMsgId>\n", UUID.randomUUID()));
        stringBuffer.append(String.format("        <SenderId>%s</SenderId>\n", senderId));
        stringBuffer.append("        <ReceiverIds>\n");
        stringBuffer.append(String.format("            <ReceiverId>%s</ReceiverId>\n", receiverId));
        stringBuffer.append("        </ReceiverIds>\n");
        stringBuffer.append(String.format("        <CreatTime>%s</CreatTime>\n", CommonConstant.LOCAL_DATE_TIME.format(LocalDateTime.now())));
        stringBuffer.append(String.format("        <MsgType>%s</MsgType>\n", getMessageTypeByCebMessage(new String(data, StandardCharsets.UTF_8))));
        stringBuffer.append("    </TransInfo>\n");
        stringBuffer.append(String.format("    <Data>%s</Data>\n", Base64.encodeBase64String(data)));
        stringBuffer.append("</DxpMsg>");

        return stringBuffer.toString();
    }

    public static String svWrap(byte[] data, String startNode, String endNode) {
        if (null == data || data.length == 0) {
            return null;
        }

        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        stringBuffer.append("<DxpMsgSv xmlns=\"http://www.chinaport.gov.cn/dxp\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ver=\"1.0\">\n");
        stringBuffer.append("    <SvNo Ver=\"1.0\">\n");
//        stringBuffer.append(String.format("        <MsgId>%s</MsgId>\n", UUID.randomUUID().toString().replaceAll("-", "")));
        stringBuffer.append(String.format("        <MsgId>%s</MsgId>\n", buildSvMsgId(startNode)));
        stringBuffer.append("         <SubMsgId>1</SubMsgId>\n");
        stringBuffer.append(String.format("         <SendTime>%s</SendTime>\n", CommonConstant.LOCAL_DATE_TIME.format(LocalDateTime.now())));
        stringBuffer.append(String.format("         <EndNode>%s</EndNode>\n", startNode));
        stringBuffer.append(String.format("         <StaNode>%s</StaNode>\n", endNode));
        stringBuffer.append("    </SvNo>\n    ");
        stringBuffer.append(new String(data, StandardCharsets.UTF_8).replaceAll("<\\?xml.*?\\?>", ""));
        stringBuffer.append("\n</DxpMsgSv>");

        return stringBuffer.toString();
    }

    public static String svWrap(String data, String startNode, String endNode) {
        if (org.apache.commons.lang3.StringUtils.isBlank(data)) {
            return null;
        }

        return svWrap(data.getBytes(StandardCharsets.UTF_8), startNode, endNode);
    }

    public static String wrap(String ceb, String senderId, String receiverId) {
        if (StringUtils.isEmpty(ceb)) {
            return null;
        }

        return wrap(ceb.getBytes(StandardCharsets.UTF_8), senderId, receiverId);
    }

    public static byte[] hgSendWrap(String data) {
        if (StringUtils.isEmpty(data)) {
            return null;
        }

        return hgSendWrap(data.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] hgSendWrap(byte[] data) {
        if (null == data || 0 == data.length) {
            return null;
        }

        byte[] rs = new byte[data.length + 1];
        System.arraycopy(data, 0, rs, 1, data.length);
        rs[0] = 0;
        return rs;
    }

    public static byte[] removeHGHead(String data) {
        if (StringUtils.isEmpty(data)) {
            return null;
        }

        return removeHGHead(data.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] removeHGHead(byte[] data) {
        if (null == data || 0 == data.length) {
            return null;
        }

        byte[] rs = new byte[data.length - 1];
        System.arraycopy(data, 1, rs, 0, rs.length);
        return rs;
    }

    public static String getMessageTypeByCebMessage(String message) {
        String msgType = "UNKNOWN";
        if (message.toLowerCase().indexOf("<ceb") != -1) {
            if (message.indexOf("<ceb:") != -1) {
                message = message.replaceAll("<ceb:", "<");
            }

            if (message.indexOf("<CEB") != -1) {
                try {
                    message = message.substring(message.indexOf("<CEB"));
                    message = message.substring(0, message.indexOf(" "));
                    message = message.substring(message.indexOf("<") + 1);
                    msgType = message;
                } catch (Exception e) {
                    logger.error("message msgtype unknown!", e);
                }
            }
        }

        return msgType;
    }

    public static String signAndWrap(String url, String xml, String ieType) {
        String result = null;

        for (int i = 0; i < httpClientProp.getRetryTimes(); i++) {
            try {
                Map<String, String> param = new HashMap<>();
                param.put("xml", xml);
                param.put("ieType", ieType);
                long startTime = System.currentTimeMillis();
                String requestResult = HttpClientUtils.post(new URI(url), null, param);
//                logger.info(String.format("request [%s] param [%s] result [%s] cost time [%d]ms.", url,
//                        JSON.toJSONString(param), requestResult, (System.currentTimeMillis() - startTime)));
//                logger.info(String.format("request [%s] param [%s] cost time [%d]ms.", url,
//                        JSON.toJSONString(param), (System.currentTimeMillis() - startTime)));
                String printInfo = null;
                if (org.apache.commons.lang.StringUtils.isNotBlank(requestResult)) {
                    JSONObject jsonResult =  JSON.parseObject(requestResult);
                    if (CommonConstant.RESULT_SUCCESS.equals(jsonResult.getString(CommonConstant.RESULT_CODE))) {
                        result = jsonResult.getString(CommonConstant.RESULT_DATA);
                        printInfo = String.format("CopMsgId: [%s], SenderId: [%s], ReceiverId: [%s], CreatTime: [%s], MsgType: [%s]", getCopMsgId(result),
                                getSenderIdByMessage(result), getDxpIdByMessage(result), getCreatTime(result), getMessageType(result));
                    } else {
                        printInfo = String.format("fail, cause:[%s]", jsonResult.getString(CommonConstant.RESULT_MESSAGE));
                    }
                }
                logger.info(String.format("request [%s] param {ieType: [%s], MsgType: [%s]} result [%s] cost time [%d]ms.", url,
                        ieType, getMessageType(xml), printInfo, (System.currentTimeMillis() - startTime)));
                if (org.apache.commons.lang.StringUtils.isNotBlank(result)) {
                    break;
                } else {
                    Thread.sleep(httpClientProp.getRetryInterval());
                }
            } catch (Exception e) {
                logger.error(String.format("request [%s] sign and wrap error!", url), e);
            }
        }

        return result;
    }

    public static String thriftSignAndWrap(String key, String playload, String ieType) {
        GenericObjectPool<SignService.Client> signClientPool = CommonUtils.getSignClientPool(key);
        if (null == signClientPool) {
            return null;
        }
        SignService.Client client = null;
        String result = null;
        for (int i = 0; i < httpClientProp.getRetryTimes(); i++) {
            try {
                long startTime = System.currentTimeMillis();
                client = signClientPool.borrowObject();

                result =  client.signAndWrap(playload, ieType);
                String printInfo = String.format("CopMsgId: [%s], SenderId: [%s], ReceiverId: [%s], CreatTime: [%s], MsgType: [%s]", getCopMsgId(result),
                        getSenderIdByMessage(result), getDxpIdByMessage(result), getCreatTime(result), getMessageType(result));
                logger.info(String.format("request thrift sign wrap param {ieType: [%s]} result [%s] cost time [%d]ms.", ieType, printInfo, (System.currentTimeMillis() - startTime)));

                if (!StringUtils.isEmpty(result)) {
                    break;
                } else {
                    Thread.sleep(httpClientProp.getRetryInterval());
                }
            } catch (Exception e) {
                logger.error(String.format("thrift sign and wrap error key:[%s], ieType:[%s]", key, ieType), e);
            } finally {
                if (null != client) {
                    signClientPool.returnObject(client);
                }
            }
        }

        return result;
    }

    public static String buildSvMsgId(String startNode) {
        if (org.apache.commons.lang3.StringUtils.isBlank(startNode)) {
            return null;
        }

        return String.format("%s%s%s", startNode, CommonConstant.DATE_TIME_FORMATTER.format(LocalDateTime.now()),
            CommonUtils.generateSeqNo(10));
    }

    public static void setHttpClientProp(HttpClientProp httpClientProp) {
        DistributionUtils.httpClientProp = httpClientProp;
    }

    public static void main(String[] args) {
//        String queueNames = "DXP_TO_GGFW,ENT_TO_GGFW:,DXP_TO_GGFW::";
//        for (int i = 0; i < 20; i++) {
//            System.out.println(getRandomQueueName(queueNames));
//        }

//        String a = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
//                "<dxp:DxpMsg\n" +
//                "    xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"\n" +
//                "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
//                "    xmlns:dxp=\"http://www.chinaport.gov.cn/dxp\" ver=\"1.0\">\n" +
//                "    <dxp:TransInfo>\n" +
//                "        <dxp:CopMsgId>8bcf1fc0cd2f417c9c2bfa7218b76a60</dxp:CopMsgId>\n" +
//                "        <dxp:SenderId>DXPEDCCEB0000002</dxp:SenderId>\n" +
//                "        <dxp:ReceiverIds>";
//
//        logger.info("string a = " + a);
//        logger.info("string b = " + removeTagPrefix(a));
    }

//    public static void main(String[] args) {
//        String message = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
//                "<DxpMsgSv xmlns=\"http://www.chinaport.gov.cn/dxp\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ver=\"1.0\">\n" +
//                "\t<SvNo Ver=\"1.0\">\n" +
//                "\t\t<MsgId>00000002201906281054520057087669</MsgId>\n" +
//                "\t\t<SubMsgId>1</SubMsgId>\n" +
//                "\t\t<SendTime>2019-06-28T10:54:52Z</SendTime>\n" +
//                "\t\t<EndNode>46000002</EndNode>\n" +
//                "\t\t<StaNode>00000002</StaNode>\n" +
//                "\t</SvNo>" +
//                "<DxpMsg xmlns=\"http://www.chinaport.gov.cn/dxp\" xmlns:ns2=\"http://www.w3.org/2000/09/xmldsig#\" ver=\"1.0\">\n" +
//                "    <TransInfo>\n" +
//                "        <CopMsgId>BDF401B3-36D7-4A73-BCAD-292E9F053C6D</CopMsgId>\n" +
//                "        <SenderId>DXPEDCCEB0000002</SenderId>\n" +
//                "        <ReceiverIds>\n" +
//                "            <ReceiverId>DXPENT0000011951</ReceiverId>\n" +
//                "        </ReceiverIds>\n" +
//                "        <CreatTime>2017-01-02T23:19:32</CreatTime>\n" +
//                "        <MsgType>CEB312Message</MsgType>\n" +
//                "    </TransInfo>\n" +
//                "    <Data>PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pgo8Q0VCMzEyTWVzc2FnZSB4bWxucz0iaHR0cDovL3d3dy5jaGluYXBvcnQuZ292LmNuL2NlYiIgZ3VpZD0iQkRGNDAxQjMtMzZENy00QTczLUJDQUQtMjkyRTlGMDUzQzZEIiB2ZXJzaW9uPSIxLjAiPgogICAgPE9yZGVyUmV0dXJuPgogICAgICAgIDxndWlkPmZmYjYxN2M2LTczNDktNGQ5Mi1hOWMwLTJjOTE0YzE2NzhkODwvZ3VpZD4KICAgICAgICA8ZWJwQ29kZT40MjAxOTZUMDAxPC9lYnBDb2RlPgogICAgICAgIDxlYmNDb2RlPjQyMDE5NlQwMDE8L2ViY0NvZGU+CiAgICAgICAgPG9yZGVyTm8+NDg1MTE3NzM2MDg8L29yZGVyTm8+CiAgICAgICAgPHJldHVyblN0YXR1cz4yPC9yZXR1cm5TdGF0dXM+CiAgICAgICAgPHJldHVyblRpbWU+MjAxNzAxMDIyMzE5MzIyMTA8L3JldHVyblRpbWU+CiAgICAgICAgPHJldHVybkluZm8+6K6i5Y2V5paw5aKe55Sz5oql5oiQ5YqfW0VERjY0MUM0LUUzNDEtNDc3Mi1BMUU0LUUxNjU3RTFGNTVCOV08L3JldHVybkluZm8+CiAgICA8L09yZGVyUmV0dXJuPgo8L0NFQjMxMk1lc3NhZ2U+Cg==</Data>\n" +
//                "</DxpMsg>" +
//                "</DxpMsgSv>"
//                ;
//        String m = DistributionUtils.removeDxpMsgSvHead(message);
//        System.out.println("m=" + m);
//    }

//    public static void main(String[] args) {
//        String message = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><DxpMsg xmlns=\"http://www.chinaport.gov.cn/dxp\" xmlns:ns2=\"http://www.w3.org/2000/09/xmldsig#\" ver=\"1.0\">\n" +
//                "    <TransInfo>\n" +
//                "        <CopMsgId>BDF401B3-36D7-4A73-BCAD-292E9F053C6D</CopMsgId>\n" +
//                "        <SenderId>DXPEDCCEB0000002</SenderId>\n" +
//                "        <ReceiverIds>\n" +
//                "            <ReceiverId>DXPENT0000011951</ReceiverId>\n" +
//                "        </ReceiverIds>\n" +
//                "        <CreatTime>2017-01-02T23:19:32</CreatTime>\n" +
//                "        <MsgType>CEB312Message</MsgType>\n" +
//                "    </TransInfo>\n" +
//                "    <Data>PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pgo8Q0VCMzEyTWVzc2FnZSB4bWxucz0iaHR0cDovL3d3dy5jaGluYXBvcnQuZ292LmNuL2NlYiIgZ3VpZD0iQkRGNDAxQjMtMzZENy00QTczLUJDQUQtMjkyRTlGMDUzQzZEIiB2ZXJzaW9uPSIxLjAiPgogICAgPE9yZGVyUmV0dXJuPgogICAgICAgIDxndWlkPmZmYjYxN2M2LTczNDktNGQ5Mi1hOWMwLTJjOTE0YzE2NzhkODwvZ3VpZD4KICAgICAgICA8ZWJwQ29kZT40MjAxOTZUMDAxPC9lYnBDb2RlPgogICAgICAgIDxlYmNDb2RlPjQyMDE5NlQwMDE8L2ViY0NvZGU+CiAgICAgICAgPG9yZGVyTm8+NDg1MTE3NzM2MDg8L29yZGVyTm8+CiAgICAgICAgPHJldHVyblN0YXR1cz4yPC9yZXR1cm5TdGF0dXM+CiAgICAgICAgPHJldHVyblRpbWU+MjAxNzAxMDIyMzE5MzIyMTA8L3JldHVyblRpbWU+CiAgICAgICAgPHJldHVybkluZm8+6K6i5Y2V5paw5aKe55Sz5oql5oiQ5YqfW0VERjY0MUM0LUUzNDEtNDc3Mi1BMUU0LUUxNjU3RTFGNTVCOV08L3JldHVybkluZm8+CiAgICA8L09yZGVyUmV0dXJuPgo8L0NFQjMxMk1lc3NhZ2U+Cg==</Data>\n" +
//                "</DxpMsg>";
//        System.out.println(message);
//        System.out.println(DistributionUtils.getDxpIdByMessage(message));
//        System.out.println(DistributionUtils.getMessageType(message));
//        Random random = new Random();
//        for (int i = 0; i < 100; i++) {
//            System.out.println(getRandomIndex(3));
//            System.out.println(getRandomRange(3));
//            System.out.println(random.nextInt(3) + 1);
//        }
//    }
}
