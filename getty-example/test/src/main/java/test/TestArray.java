package test;

import com.gettyio.core.buffer.allocator.ByteBufAllocator;
import com.gettyio.core.buffer.bytebuf.ByteBuf;
import com.gettyio.core.buffer.pool.PooledByteBufAllocator;
import com.gettyio.core.util.FastArrayList;
import com.gettyio.core.util.FastCopyOnWriteArrayList;
import com.gettyio.core.util.timer.HashedWheelTimer;
import com.gettyio.core.util.timer.Timeout;
import com.gettyio.core.util.timer.TimerTask;


import java.util.concurrent.TimeUnit;

public class TestArray {

    public static void main(String[] args) {

//        int pageSize = 100;
//        int normCapacity = 1000;
//
//        int subpageOverflowMask = ~(pageSize - 1);
//        System.out.println(subpageOverflowMask);
//
//       boolean ss= ((normCapacity & subpageOverflowMask) != 0);
//        System.out.println((normCapacity & subpageOverflowMask));

        testPool();
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


    public static void testPool(){


        ByteBufAllocator byteBufAllocator=new PooledByteBufAllocator();

        long ct = System.currentTimeMillis();
        for (int i=0;i<1000000;i++){
            ByteBuf buf=byteBufAllocator.buffer(64);
            //buf.release();
        }
        long lt = System.currentTimeMillis();
        System.out.printf("总耗时(ms)：" + (lt - ct) + "\r\n");


    }


    public static void testTimer(){

//        HashedWheelTimer timer = new HashedWheelTimer(100,TimeUnit.MILLISECONDS,100);
//        timer.schedule(new Runnable() {
//            @Override
//            public void run() {
//                System.out.println("123");
//            }
//        },1,TimeUnit.SECONDS);
        HashedWheelTimer timer = new HashedWheelTimer(100,TimeUnit.MILLISECONDS,100);
        timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                System.out.println("123");
            }
        }, 3, TimeUnit.SECONDS);
        timer.start();
    }


}
