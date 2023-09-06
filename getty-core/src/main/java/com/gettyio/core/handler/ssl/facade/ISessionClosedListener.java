package com.gettyio.core.handler.ssl.facade;

/**
 * Monitors end of session notifications
 */
public interface ISessionClosedListener {
    void onSessionClosed();
}
