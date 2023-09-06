package com.gettyio.core.handler.ssl.facade;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

class Worker {
    /*  Uses the SSLEngine and Buffers to perform wrap/unwrap operations.
     Also, provides access to SSLEngine ops for handshake
     */
    private final SSLEngine _engine;
    private final Buffers _buffers;
    private ISSLListener _sslListener;
    private ISessionClosedListener _sessionClosedListener = new DefaultOnCloseListener();

    Worker(SSLEngine engine, Buffers buffers) {
        _engine = engine;
        _buffers = buffers;
    }

    void setSessionClosedListener(final ISessionClosedListener scl) {
        _sessionClosedListener = scl;
    }

    void beginHandshake() throws SSLException {
        _engine.beginHandshake();
    }

    SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return _engine.getHandshakeStatus();
    }

    Runnable getDelegatedTask() {
        return _engine.getDelegatedTask();
    }

    SSLEngineResult wrap(ByteBuffer plainData) throws SSLException {
        _buffers.prepareForWrap(plainData);
        SSLEngineResult result = doWrap();

        emitWrappedData(result);

        switch (result.getStatus()) {
            case BUFFER_UNDERFLOW:
                throw new RuntimeException("BUFFER_UNDERFLOW while wrapping!");
            case BUFFER_OVERFLOW:
                _buffers.grow(BufferType.OUT_CIPHER);
                if (plainData != null && plainData.hasRemaining()) {
                    plainData.position(result.bytesConsumed());
                    ByteBuffer remainingData = BufferUtils.slice(plainData);
                    wrap(remainingData);
                }
                break;
            case OK:
                break;
            case CLOSED:
                _sessionClosedListener.onSessionClosed();
                break;
        }
        return result;
    }

    SSLEngineResult unwrap(ByteBuffer encryptedData) throws SSLException {
        ByteBuffer allEncryptedData = _buffers.prependCached(encryptedData);
        _buffers.prepareForUnwrap(allEncryptedData);
        SSLEngineResult result = doUnwrap();
        allEncryptedData.position(result.bytesConsumed());
        ByteBuffer unprocessedEncryptedData = BufferUtils.slice(allEncryptedData);

        emitPlainData(result);

        switch (result.getStatus()) {
            case BUFFER_UNDERFLOW:
                _buffers.cache(unprocessedEncryptedData);
                break;
            case BUFFER_OVERFLOW:
                _buffers.grow(BufferType.IN_PLAIN);
                if (unprocessedEncryptedData == null) {
                    throw new RuntimeException("Worker.unwrap had "
                            + "buffer_overflow but all data was consumed!!");
                } else {
                    unwrap(unprocessedEncryptedData);
                }
                break;
            case OK:
                if (unprocessedEncryptedData == null) {
                    _buffers.clearCache();
                } else {
                    _buffers.cache(unprocessedEncryptedData);
                }
                break;
            case CLOSED:

                break;
        }
        if (_buffers.isCacheEmpty() == false
                && result.getStatus() == SSLEngineResult.Status.OK
                && result.bytesConsumed() > 0) {
            result = unwrap(ByteBuffer.allocate(0));
        }
        return result;
    }

    void setSSLListener(ISSLListener SSLListener) {
        _sslListener = SSLListener;
    }

    void close(boolean properly) {
        _engine.closeOutbound();
        try {
            if (properly) {
                wrap(null); //sends a TLS close_notify alert
            }
            _engine.closeInbound();
        } catch (SSLException ignore) {
        }

    }

    boolean pendingUnwrap() {
        return !_buffers.isCacheEmpty();
    }
    /* Private */

    private void emitWrappedData(SSLEngineResult result) {
        if (result.bytesProduced() > 0) {
            ByteBuffer internalCipherBuffer = _buffers.get(BufferType.OUT_CIPHER);
            _sslListener.onWrappedData(makeExternalBuffer(internalCipherBuffer));
        }
    }

    private void emitPlainData(SSLEngineResult result) {
        if (result.bytesProduced() > 0) {
            ByteBuffer internalPlainBuffer = _buffers.get(BufferType.IN_PLAIN);
            _sslListener.onPlainData(makeExternalBuffer(internalPlainBuffer));
        }

    }

    private SSLEngineResult doWrap() throws SSLException {
        ByteBuffer plainText = _buffers.get(BufferType.OUT_PLAIN);
        ByteBuffer cipherText = _buffers.get(BufferType.OUT_CIPHER);
        return _engine.wrap(plainText, cipherText);
    }

    private SSLEngineResult doUnwrap() throws SSLException {
        ByteBuffer cipherText = _buffers.get(BufferType.IN_CIPHER);
        ByteBuffer plainText = _buffers.get(BufferType.IN_PLAIN);
        return _engine.unwrap(cipherText, plainText);
    }

    private static ByteBuffer makeExternalBuffer(ByteBuffer internalBuffer) {
        ByteBuffer newBuffer = ByteBuffer.allocate(internalBuffer.position());
        internalBuffer.flip();
        BufferUtils.copy(internalBuffer, newBuffer);
        return newBuffer;
    }

}
