package org.getty.core.handler.ssl.sslfacade.defaulthandlers;


import org.getty.core.handler.ssl.sslfacade.ISessionClosedListener;

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
