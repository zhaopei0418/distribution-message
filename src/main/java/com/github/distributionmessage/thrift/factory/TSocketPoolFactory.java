package com.github.distributionmessage.thrift.factory;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;

@AllArgsConstructor
@Slf4j
public class TSocketPoolFactory extends BasePooledObjectFactory<TSocket> {

    private String serverIp;

    private int port;

    private int timeout;


    @Override
    public TSocket create() throws Exception {
        return new TSocket(this.serverIp, this.port, this.timeout);
    }

    @Override
    public PooledObject<TSocket> wrap(TSocket socket) {
        try {
            socket.open();
        } catch (TTransportException e) {
            log.error(String.format("socket open error ip:[%s], port:[%d], timeout:[%d]", this.serverIp, this.port, this.timeout), e);
            return null;
        }
        return new DefaultPooledObject<>(socket);
    }

    @Override
    public void destroyObject(PooledObject<TSocket> p) throws Exception {
        p.getObject().close();
    }

    @Override
    public boolean validateObject(PooledObject<TSocket> p) {
        return p.getObject().isOpen();
    }
}
