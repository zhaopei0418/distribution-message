# 分发总署上下行报文,支持四种分发模式 
1.根据DXPID分发, 配置如下:
```
dxpidDistribution: {DXPENT0000011951: DXP_11951, DXPENT0000011952: DXP_11952}
```
2.根据消息类型分发, 配置如下:
```
msgtypeDistribution: {CEB312Message: DXP_312, CEB412Message: DXP_412}
```
3.根据比例分发, 配置如下:
```
percentageDistribution: {GGFW_TO_ENT1: 1, GGFW_TO_ENT2: 2, GGFW_TO_ENT3: 3}
```
4.随机分发, 配置如下:
```
randomDistribution:
- GGFW_TO_DXP
- DXP_TO_GGFW.INVT
- ENT_TO_CUS
- CUS_TO_ENT
```

＃其他配置说明如下:
```
hostName: ibmmqip地址
port: 端口号
queueManager: 队列管理器名称
channel: 通道名称
ccsid: 字符集编码
queueName: 需要分发的源队列
minConcurrency: 最小并发数
maxConcurrency: 最大并发数
```
