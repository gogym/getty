package org.getty.test;

import org.getty.core.buffer.Chunk;
import org.getty.core.buffer.ChunkPage;
import org.getty.core.buffer.ChunkPool;
import org.getty.core.util.ThreadPool;

public class ChunkTest {

    public static void main(String[] args) {

        ThreadPool threadPool = new ThreadPool(ThreadPool.FixedThread, 120);

        ChunkPool cp = new ChunkPool(1024 * 1024, 1, true);
        Chunk chunk = cp.allocateChunk();


        long ct = System.currentTimeMillis();

        for (int j = 0; j <1; j++) {

            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 1000000; i++) {
                        ChunkPage chunkPage = chunk.allocate(256);
                        chunkPage.clean();
                    }
                    long lt = System.currentTimeMillis();
                    System.out.println("耗时：" + (lt - ct));
                }
            });
        }

    }
}
