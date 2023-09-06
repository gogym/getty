package com.gettyio.core.handler.ssl.facade;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

class Handshaker {
  /*
   The purpose of this class is to conduct a SSL handshake. To do this it
   requires a SSLEngine as a provider of SSL knowhow. Byte buffers that are
   required by the SSLEngine to execute its wrap and unwrap methods. And a
   ITaskHandler callback that is used to delegate the responsibility of
   executing long-running/IO tasks to the host application. By providing a
   ITaskHandler the host application gains the flexibility of executing
   these tasks in compliance with its own compute/IO strategies.
   */

    private final ITaskHandler _taskHandler;
    private final Worker _worker;
    private boolean _finished;
    private IHandshakeCompletedListener _hscl;


    public Handshaker(Worker worker, ITaskHandler taskHandler) {
        _worker = worker;
        _taskHandler = taskHandler;
        _finished = false;
    }

    void begin() throws SSLException {
        _worker.beginHandshake();
        shakehands();
    }

    void carryOn() throws SSLException {
        shakehands();
    }

    void handleUnwrapResult(SSLEngineResult result) throws SSLException {
        if (result.getHandshakeStatus().equals(SSLEngineResult.HandshakeStatus.FINISHED)) {
            handshakeFinished();
        } else {
            shakehands();
        }
    }

    void addCompletedListener(IHandshakeCompletedListener hscl) {
        _hscl = hscl;
    }

    boolean isFinished() {
        return _finished;
    }


    /* Privates */
    private void shakehands() throws SSLException {
        switch (_worker.getHandshakeStatus()) {
            case NOT_HANDSHAKING:
                /* Occurs after handshake is over */
                break;
            case FINISHED:
                handshakeFinished();
                break;
            case NEED_TASK:
                _taskHandler.process(new Tasks(_worker, this));
                break;
            case NEED_WRAP:
                SSLEngineResult w_result = _worker.wrap(null);
                if (w_result.getHandshakeStatus().equals(SSLEngineResult.HandshakeStatus.FINISHED)) {
                    handshakeFinished();
                } else {
                    shakehands();
                }
                break;
            case NEED_UNWRAP:
                if (_worker.pendingUnwrap()) {
                    SSLEngineResult u_result = _worker.unwrap(null);
                    if (u_result.getHandshakeStatus().equals(SSLEngineResult.HandshakeStatus.FINISHED)) {
                        handshakeFinished();
                    }
                    if (u_result.getStatus().equals(SSLEngineResult.Status.OK)) {
                        shakehands();
                    }
                }
                break;
        }
    }

    private void handshakeFinished() {
        _finished = true;
        _hscl.onComplete();
    }

}
