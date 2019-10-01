package org.getty.test;

import org.getty.core.buffer.Chunk;
import org.getty.core.buffer.ChunkPage;
import org.getty.core.buffer.ChunkPool;

public class ChunkTest {

    public static void main(String[] args) {

        ChunkPool cp = new ChunkPool(1024 * 256, 1, true);
        Chunk chunk = cp.allocateBufferPage();


        long ct = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            ChunkPage chunkPage = chunk.allocate(256);
            chunkPage.clean();
        }
        long lt = System.currentTimeMillis();
        System.out.println("耗时：" + (lt - ct));
    }
}
