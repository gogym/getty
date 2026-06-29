package test;

import com.gettyio.core.buffer.pool.GettyByteBufferPool;
import com.gettyio.core.buffer.pool.PooledByteBuffer;
import com.gettyio.core.util.list.FastArrayList;
import com.gettyio.core.util.timer.HashedWheelTimer;
import com.gettyio.core.util.timer.Timeout;
import com.gettyio.core.util.timer.TimerTask;

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


//        int[] array = new int[]{4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192};
//        int j = 256;
//        int index = -1;
//        //二分法
//        int low = 0, high = array.length - 1;
//        while (low <= high) {
//            int mid = low + (high - low) / 2;
//            if (array[mid] >= j) {
//                index = mid;
//                high = mid - 1;
//                break;
//            } else {
//                low = mid + 1;
//            }
//        }
//        System.out.println(index);


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


    public static void testPool() throws Exception {

        final GettyByteBufferPool byteBufferPool = new GettyByteBufferPool(false);
        int num = 1;
        long ct = System.currentTimeMillis();

        final CountDownLatch countDownLatch = new CountDownLatch(num);
        for (int i = 0; i < num; i++) {

            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 1000000; i++) {
                        PooledByteBuffer buf = byteBufferPool.acquire(63);
                        buf.release();
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


}
