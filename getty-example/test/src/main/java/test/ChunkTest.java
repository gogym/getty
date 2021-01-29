package test;

import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.util.ThreadPool;
import com.gettyio.core.util.Time;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;

public class ChunkTest {


    static ThreadPool threadPool=new ThreadPool(ThreadPool.FixedThread,5);


    public static void main(String[] args) {

        final Time t = new Time();
        final ChunkPool bufferPool = new ChunkPool(20 * 1024 * 1024, t, true);

        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                allocate(bufferPool);
            }
        });

        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                allocate(bufferPool);
            }
        });

        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                allocate(bufferPool);
            }
        });

        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                allocate(bufferPool);
            }
        });

    }


    private static   void allocate(ChunkPool bufferPool){

        try {
            long ct = System.currentTimeMillis();
            for (int i = 1; i <= 10000000; i++) {
                ByteBuffer b = bufferPool.allocate(9, 100);
                bufferPool.deallocate(b);
            }
            long lt = System.currentTimeMillis();
            System.out.println("耗时：" + (lt - ct));

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }


    }

}
