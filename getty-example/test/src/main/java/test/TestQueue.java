package test;


import com.gettyio.core.logging.InternalLogger;
import com.gettyio.core.logging.InternalLoggerFactory;
import com.gettyio.core.util.LinkedNonReadBlockQueue;
import com.gettyio.core.util.LinkedQueue;
import com.gettyio.core.util.ThreadPool;

/**
 * @author gogym.ggj
 * @version 1.0.0
 * @ClassName TestQueue.java
 * @email gongguojun.ggj@alibaba-inc.com
 * @Description TODO
 * @createTime 2020/12/23/ 17:32:00
 */
public class TestQueue {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(TestQueue.class);

    static  ThreadPool threadPool=new ThreadPool(ThreadPool.FixedThread,5);
    final static LinkedQueue<String> queue = new LinkedNonReadBlockQueue<>(1024*1024);
    public static void main(String[] args) {


        testQ();

        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (true){
                    try {
                        queue.poll();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });


    }

    public static void testQ() {

        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                long ct = System.currentTimeMillis();
                for (int i=1;i<=10000000;i++){
                    try {
                       queue.put(i+"");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                long lt = System.currentTimeMillis();
                System.out.printf("一亿总耗时(ms)：" + (lt - ct) + "\r\n");
            }
        });

    }

}
