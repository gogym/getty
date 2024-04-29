package test;

import com.gettyio.core.buffer.pool.ArrayRetainableByteBufferPool;
import com.gettyio.core.buffer.pool.BufferUtil;
import com.gettyio.core.buffer.pool.ByteBufferPool;
import com.gettyio.core.buffer.pool.RetainableByteBuffer;
import com.gettyio.core.util.FastArrayList;
import com.gettyio.core.util.FastCopyOnWriteArrayList;
import com.gettyio.core.util.list.TLinkable;
import com.gettyio.core.util.list.linked.TLinkedList;
import com.gettyio.core.util.timer.HashedWheelTimer;
import com.gettyio.core.util.timer.Timeout;
import com.gettyio.core.util.timer.TimerTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Test {

    public static void main(String[] args) {

//        int pageSize = 100;
//        int normCapacity = 1000;
//
//        int subpageOverflowMask = ~(pageSize - 1);
//        System.out.println(subpageOverflowMask);
//
//       boolean ss= ((normCapacity & subpageOverflowMask) != 0);
//        System.out.println((normCapacity & subpageOverflowMask));

        try {
            //testTLinkList();
            testPool();
        } catch (Exception e) {
            e.printStackTrace();
        }
        //testTimer();
    }


    public static void testFastArrayList() {
        FastArrayList<Integer> fastArrayList = new FastArrayList<>();
        fastArrayList.add(1);
        fastArrayList.add(2);
        fastArrayList.add(3);
        fastArrayList.add(4);
        fastArrayList.add(5);
        fastArrayList.add(6);
        fastArrayList.add(7);
        fastArrayList.add(8);
        fastArrayList.add(9);
        fastArrayList.add(10);

        fastArrayList.add(5, 11);
        fastArrayList.addFirst(33);
        fastArrayList.addLast(55);
        for (int i = 0; i < fastArrayList.size(); i++) {
            System.out.println(fastArrayList.get(i));
        }
        System.out.println(fastArrayList.getFirst());
        System.out.println(fastArrayList.getLast());
    }


    public static void testFastCopyOnWriteArrayList() {

        FastCopyOnWriteArrayList<Integer> fastCopyOnWriteArrayList = new FastCopyOnWriteArrayList<>();

        for (int i = 1; i <= 100; i++) {
            fastCopyOnWriteArrayList.add(i);
        }

//        for (int i = 0; i < 100; i++) {
//            Integer ii = fastCopyOnWriteArrayList.get(i);
//            System.out.println(ii);
//        }


        for (int i = 0; i < fastCopyOnWriteArrayList.size(); i++) {
            Integer ii = fastCopyOnWriteArrayList.get(i);
            System.out.println(ii);

            if (ii == 30) {
                fastCopyOnWriteArrayList.remove(ii);
                fastCopyOnWriteArrayList.add(666666);
                fastCopyOnWriteArrayList.set(50, 5555);

            }

        }


//        System.out.println(fastCopyOnWriteArrayList.get(50));
//        System.out.println(fastCopyOnWriteArrayList.getFirst());
//        System.out.println(fastCopyOnWriteArrayList.getLast());

        //插入指定位置
//        fastCopyOnWriteArrayList.add(50,101);
//        System.out.println(fastCopyOnWriteArrayList.get(50));
//        System.out.println(fastCopyOnWriteArrayList.size());
//        System.out.println(fastCopyOnWriteArrayList.get(51));

        //替换，长度不变
//        fastCopyOnWriteArrayList.set(50,6666);
//        System.out.println(fastCopyOnWriteArrayList.get(50));
//        System.out.println(fastCopyOnWriteArrayList.size());

    }


    public static void testPool() throws Exception {

        final byte[] s=new byte[64];

        final ByteBufferPool byteBufferPool = new ArrayRetainableByteBufferPool();
        int num = 1;
        long ct = System.currentTimeMillis();

        final CountDownLatch countDownLatch = new CountDownLatch(num);
        for (int i = 0; i < num; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 1000000; i++) {
                        RetainableByteBuffer buf = byteBufferPool.acquire(64);
                       // buf.put("123".getBytes());
//                        try {
//                          //  buf.flipToFlush();
//                          //  buf.get(s);
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
                       // buf.release();
                    }
                    countDownLatch.countDown();
                }
            }).start();
        }

        countDownLatch.await();

        long lt = System.currentTimeMillis();
        System.out.printf("总耗时(ms)：" + (lt - ct) + "\r\n");


    }


    public static void testTimer() {

//        HashedWheelTimer timer = new HashedWheelTimer(100,TimeUnit.MILLISECONDS,100);
//        timer.schedule(new Runnable() {
//            @Override
//            public void run() {
//                System.out.println("123");
//            }
//        },1,TimeUnit.SECONDS);
        HashedWheelTimer timer = new HashedWheelTimer(100, TimeUnit.MILLISECONDS, 100);
        timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                System.out.println("123");
            }
        }, 3, TimeUnit.SECONDS);
        timer.start();
    }


    public static void testTLinkList() throws Exception{

        final FastCopyOnWriteArrayList<Data> list=new FastCopyOnWriteArrayList<>();


        int num = 1;
        long ct = System.currentTimeMillis();

        final CountDownLatch countDownLatch = new CountDownLatch(num);
        for (int i = 0; i < num; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 1000000; i++) {
                        Data d=new Data(i);
                        list.add(d);
                    }
                    countDownLatch.countDown();
                }
            }).start();
        }

        countDownLatch.await();
        long lt = System.currentTimeMillis();
        System.out.printf("总耗时(ms)：" + (lt - ct) + "\r\n");

        ct = System.currentTimeMillis();
        for (int i = 0; i < list.size(); i++) {
            list.get(i);
        }
        lt = System.currentTimeMillis();
        System.out.printf("读总耗时(ms)：" + (lt - ct) + "\r\n");
    }



    static class Data implements TLinkable<Data> {

        protected int _val;


        public Data( int val ) {
            _val = val;
        }


        protected TLinkable<Data> _next;


        // NOTE: use covariant overriding
        /**
         * Get the value of next.
         *
         * @return value of next.
         */
        public Data getNext() {
            return (Data) _next;
        }


        /**
         * Set the value of next.
         *
         * @param next value to assign to next.
         */
        public void setNext( Data next ) {
            this._next = next;
        }


        protected Data _previous;

        // NOTE: use covariant overriding
        /**
         * Get the value of previous.
         *
         * @return value of previous.
         */
        public Data getPrevious() {
            return _previous;
        }


        /**
         * Set the value of previous.
         *
         * @param previous value to assign to previous.
         */
        public void setPrevious( Data previous ) {
            this._previous = previous;
        }



    }


}
