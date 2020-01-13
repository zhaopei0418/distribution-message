package com.github.distributionmessage.utils;

import com.alibaba.fastjson.JSON;
import com.github.distributionmessage.config.DistributionProp;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DistributionUtils {

    private final static Log logger = LogFactory.getLog(DistributionUtils.class);

    public static String getDestinationQueueName(DistributionProp distributionProp, String dxpid, String msgtype) {
        String result = null;

        if (distributionProp.getConditionMutualExclusion()) {
            if (null != distributionProp.getDxpidDistribution() && !distributionProp.getDxpidDistribution().isEmpty()) {
                logger.debug("dxpid distribution");
                result = distributionProp.getDxpidDistribution().get(dxpid);
            } else if (null != distributionProp.getMsgtypeDistribution() && !distributionProp.getMsgtypeDistribution().isEmpty()) {
                logger.debug("msgtype distribution");
                result = distributionProp.getMsgtypeDistribution().get(msgtype);
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

    public static String getMessageType(String message) {
        if (null == message) {
            return null;
        }
        Pattern pattern = Pattern.compile("<MsgType>(.+)</MsgType>");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    //public static void main(String[] args) {
    //    String queueNames = "DXP_TO_GGFW,ENT_TO_GGFW:,DXP_TO_GGFW::";
    //    for (int i = 0; i < 20; i++) {
    //        System.out.println(getRandomQueueName(queueNames));
    //    }
    //}

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
