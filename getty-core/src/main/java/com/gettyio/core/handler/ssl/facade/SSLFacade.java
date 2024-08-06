package com.gettyio.core.handler.ssl.facade;


import com.gettyio.core.buffer.pool.ByteBufferPool;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

public class SSLFacade implements ISSLFacade {

    private final Handshaker _handshaker;
    private IHandshakeCompletedListener _hcl;
    private final Worker _worker;

    public SSLFacade(SSLContext context, boolean client, boolean clientAuthRequired, ITaskHandler taskHandler, ByteBufferPool byteBufferPool) {
        //Currently there is no support for SSL session reuse,
        // so no need to take a peerHost or port from the host application
        SSLEngine engine = makeSSLEngine(context, client, clientAuthRequired);
        engine.setEnabledProtocols(new String[]{context.getProtocol()});
        //engine.setEnabledProtocols(new String[]{"TLSv1", "TLSv1.1", "TLSv1.2"});
        Buffers buffers = new Buffers(engine.getSession());
        _worker = new Worker(engine, buffers);
        _handshaker = new Handshaker(_worker, taskHandler);
    }

    @Override
    public void setHandshakeCompletedListener(IHandshakeCompletedListener hcl) {
        _hcl = hcl;
        attachCompletionListener();
    }

    @Override
    public void setSSLListener(ISSLListener l) {
        _worker.setSSLListener(l);
    }

    @Override
    public void setCloseListener(ISessionClosedListener l) {
        _worker.setSessionClosedListener(l);
    }

    @Override
    public void beginHandshake() throws SSLException {
        _handshaker.begin();
    }

    @Override
    public boolean isHandshakeCompleted() {
        return (_handshaker == null) || _handshaker.isFinished();
    }

    @Override
    public void encrypt(ByteBuffer plainData) throws SSLException {
        _worker.wrap(plainData);
    }

    @Override
    public void decrypt(ByteBuffer encryptedData) throws SSLException {
        SSLEngineResult result = _worker.unwrap(encryptedData);
        _handshaker.handleUnwrapResult(result);
    }

    @Override
    public void close() {
        /* Called if we want to properly close SSL */
        _worker.close(true);
    }

    /* Privates */
    private void attachCompletionListener() {
        _handshaker.addCompletedListener(new IHandshakeCompletedListener() {
            @Override
            public void onComplete() {
                //_handshaker = null;
                if (_hcl != null) {
                    _hcl.onComplete();
                    _hcl = null;
                }
            }
        });
    }

    private SSLEngine makeSSLEngine(SSLContext context, boolean client, boolean clientAuthRequired) {
        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(client);
        engine.setNeedClientAuth(clientAuthRequired);
        return engine;
    }

}
