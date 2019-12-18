package test.udp;/*
 * 类名：UdpClient
 * 版权：Copyright by www.getty.com
 * 描述：
 * 修改人：gogym
 * 时间：2019/12/17
 */

import com.gettyio.core.util.ThreadPool;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Date;
import java.util.Iterator;
import java.util.Scanner;

public class UdpClient {

    public static void main(String[] args) {
        test();
    }


    public static void test() {

        try {
            DatagramChannel dc = DatagramChannel.open();
            dc.configureBlocking(false);

            Selector selector = Selector.open();
            dc.register(selector, SelectionKey.OP_READ);

            //线程池
            ThreadPool workerThreadPool = new ThreadPool(ThreadPool.FixedThread, 5);

            workerThreadPool.execute(new Runnable() {
                @Override
                public void run() {

                    try {
                        while (selector.select() > 0) {
                            Iterator<SelectionKey> it = selector.selectedKeys().iterator();

                            while (it.hasNext()) {
                                SelectionKey sk = it.next();
                                DatagramChannel curdc = (DatagramChannel) sk.channel();

                                if (sk.isReadable()) {
                                    ByteBuffer buf = ByteBuffer.allocate(1024);
                                    //接收数据
                                    InetSocketAddress address = (InetSocketAddress) curdc.receive(buf);
                                    System.out.println("接收来自：" + address.getAddress().getHostAddress() + ":" + address.getPort());
                                    buf.flip();
                                    System.out.println(new String(buf.array(), 0, buf.limit()));
                                    buf.clear();

                                }
                            }

                            it.remove();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            });

            ByteBuffer buf = ByteBuffer.allocate(1024);
            Scanner scan = new Scanner(System.in);
            while (scan.hasNext()) {
                String str = scan.next();
                buf.put((new Date().toString() + ":\n" + str).getBytes());
                buf.flip();

                DatagramPacket packet = new DatagramPacket(str.getBytes(), str.getBytes().length);

                dc.send(buf, new InetSocketAddress("127.0.0.1", 8888));
                buf.clear();
            }
            dc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public static void test2() {
        try {
            DatagramChannel dc = DatagramChannel.open();
            dc.configureBlocking(true);
            new Thread(() -> {
                try {
                    while (true) {
                        ByteBuffer bb = ByteBuffer.allocate(1024);
                        dc.receive(bb);
                        String m1 = new String(bb.array(), 0, bb.limit(), "utf-8");
                        System.out.println("收到服务器消息:" + m1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            ByteBuffer buf = ByteBuffer.allocate(1024);
            Scanner scan = new Scanner(System.in);
            while (scan.hasNext()) {
                String str = scan.next();
                buf.put((new Date().toString() + ":\n" + str).getBytes());
                buf.flip();
                dc.send(buf, new InetSocketAddress("127.0.0.1", 9898));
                buf.clear();
            }
            dc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
