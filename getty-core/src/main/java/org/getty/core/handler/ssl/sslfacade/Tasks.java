package org.getty.core.handler.ssl.sslfacade;

import javax.net.ssl.SSLException;

public class Tasks implements ITasks
{
    private final Worker _worker;
    private final Handshaker _hs;

    public Tasks(Worker worker, Handshaker hs)
    {
        _worker = worker;
        _hs = hs;
    }

    @Override
    public Runnable next()
    {
        return _worker.getDelegatedTask();
    }

    @Override
    public void done() throws SSLException
    {
        _hs.carryOn();
    }
}
