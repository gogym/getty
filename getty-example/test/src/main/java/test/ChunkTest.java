package test;

import com.gettyio.core.buffer.ChunkPool;
import com.gettyio.core.buffer.Time;
import com.gettyio.core.util.ThreadPool;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;

public class ChunkTest {

    public static void main(String[] args) {

        Time t = new Time();
        ChunkPool bufferPool = new ChunkPool(20 * 1024 * 1024, t, true);
        try {
            long ct = System.currentTimeMillis();
            for (int i = 1; i <= 1000000; i++) {
                ByteBuffer b = bufferPool.allocate(9, 3000);
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
