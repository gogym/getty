package test.udp;/*
 * 类名：UdpServer
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/17
 */

import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

public class UdpServer {


    public static void main(String[] args) {

        try {

            DatagramChannel dc = DatagramChannel.open();
            dc.configureBlocking(false);
            dc.bind(new InetSocketAddress(9898));
            Selector selector = Selector.open();
            dc.register(selector, SelectionKey.OP_READ);

            //线程池
            ThreadPool workerThreadPool = new ThreadPool(ThreadPool.FixedThread, 5);

            for (int i = 0; i < 1; i++) {

                workerThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            while (selector.select() > 0) {
                                Iterator<SelectionKey> it = selector.selectedKeys().iterator();

                                while (it.hasNext()) {
                                    SelectionKey sk = it.next();

                                    if (sk.isReadable()) {
                                        ByteBuffer buf = ByteBuffer.allocate(1024);
                                        //接收数据
                                        InetSocketAddress address = (InetSocketAddress) dc.receive(buf);
                                        System.out.println("接收来自：" + address.getAddress().getHostAddress() + ":" + address.getPort());
                                        buf.flip();
                                        System.out.println(new String(buf.array(), 0, buf.limit()));
                                        buf.clear();

                                        //返回消息给发送端
                                        ByteBuffer cbc = ByteBuffer.allocate(8);
                                        cbc.put("byte".getBytes());
                                        cbc.flip();
                                        dc.send(cbc, address);
                                    }
                                }

                                it.remove();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


}
