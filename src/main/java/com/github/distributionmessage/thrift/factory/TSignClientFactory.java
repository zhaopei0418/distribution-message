package com.github.distributionmessage.thrift.factory;

import com.github.distributionmessage.thrift.SignService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.layered.TFramedTransport;

@AllArgsConstructor
@Slf4j
public class TSignClientFactory extends BasePooledObjectFactory<SignService.Client> {

    private String serverIp;

    private int port;

    private int timeout;


    @Override
    public SignService.Client create() throws Exception {
        TSocket socket =  new TSocket(this.serverIp, this.port, this.timeout);
        TFramedTransport framedTransport = new TFramedTransport(socket);
        framedTransport.open();
        return new SignService.Client(new TCompactProtocol(framedTransport));
    }

    @Override
    public PooledObject<SignService.Client> wrap(SignService.Client client) {
        return new DefaultPooledObject<>(client);
    }

    @Override
    public void destroyObject(PooledObject<SignService.Client> p) throws Exception {
        p.getObject().getOutputProtocol().getTransport().close();
    }

    @Override
    public boolean validateObject(PooledObject<SignService.Client> p) {
        return p.getObject().getOutputProtocol().getTransport().isOpen();
    }
}
