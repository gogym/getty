package com.gettyio.test;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;


import java.util.concurrent.TimeUnit;

/**
 * @author gogym
 * $
 */
public class Test {


    public static void main(String[] args) {
        HashedWheelTimer timer = new HashedWheelTimer();

        timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                System.out.println("123");
            }
        }, 1, TimeUnit.SECONDS);
        timer.start();
    }

}
