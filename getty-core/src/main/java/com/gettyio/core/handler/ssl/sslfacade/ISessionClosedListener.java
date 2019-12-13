package com.gettyio.core.handler.ssl.sslfacade;

/**
 * Monitors end of session notifications
 */
public interface ISessionClosedListener
{
    void onSessionClosed();
}
