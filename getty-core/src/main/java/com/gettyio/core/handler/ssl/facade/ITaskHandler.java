package com.gettyio.core.handler.ssl.facade;

import javax.net.ssl.SSLException;

public interface ITaskHandler {
    /*
    In order to continue handshakes after tasks are processed the
    tasks.done() method must be called.
     */
    void process(ITasks tasks) throws SSLException;
}
