package com.gettyio.core.handler.ssl.sslfacade.defaulthandlers;


import com.gettyio.core.handler.ssl.sslfacade.ISessionClosedListener;

/** By default do nothing on the close event.
 *
 */
public class DefaultOnCloseListener implements ISessionClosedListener
{
  @Override
  public void onSessionClosed()
  {
  } 
}
