package com.zyz.learn.netty.c28;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

/**
 * 多路复用类，它是个一个独立的线程，负责轮询多路复用器Selector，可处理客户端并发接入
 *
 * @author ZhangYuanzhuo
 * @since 2017/9/12
 */
public class MultiplexerTimeServer implements Runnable {

    private Selector selector;

    private ServerSocketChannel serverChannel;

    private volatile boolean stop;

    /**
     * 初始化多路复用器，绑定监听端口
     *
     * @param port
     */
    public MultiplexerTimeServer(int port) {
        try {
            // 创建多路复用器Selector、ServerSocketChannel
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            // 设置异步非阻塞
            serverChannel.configureBlocking(false);
            // 设置端口
            serverChannel.socket().bind(new InetSocketAddress(port), 1024);
            // 将ServerSocketChannel注册到Selector，监听SelectionKey.OP_ACCEPT操作位
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("The time server is start in port: " + port);
        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    public void stop() {
        this.stop = true;
    }

    @Override
    public void run() {
        // while循环中遍历selector
        while (!stop) {
            try {
                // 休眠时间1s，每隔1s被唤醒一次
                selector.select(1000);
                // 当有处于就绪状态的Channel时，返回该Channel的SelectionKey集合
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                // 通过对Channel集合进行迭代，可以进行网络的异步读写操作
                Iterator<SelectionKey> it = selectionKeys.iterator();
                SelectionKey key;
                while (it.hasNext()) {
                    key = it.next();
                    it.remove();
                    try {
                        handleInput(key);
                    } catch (Exception e) {
                        if (key != null) {
                            key.cancel();
                            if (key.channel() != null) {
                                key.channel().close();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /*
         * 多路复用器关闭后，所有注册在上面的Channel和Pipe等资源都会被自动去注册并关闭，
         * 所以不需要重复释放资源
         */
        if (selector != null) {
            try {
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * 处理新接入的客户端请求消息
     */
    private void handleInput(SelectionKey key) throws IOException {
        if (key.isValid()) {
            if (key.isAcceptable()) {
                // Accept the new connection
                ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                // 接收客户端的连接请求并创建SocketChannel实例
                SocketChannel sc = ssc.accept();
                // 上述操作相当于完成了TCP的三次握手，TCP物理链路正式简历
                // 异步非阻塞
                sc.configureBlocking(false);
                // Add the new connection to the selector
                sc.register(selector, SelectionKey.OP_READ);
            }

            if (key.isReadable()) {
                // Read the data
                SocketChannel sc = (SocketChannel) key.channel();
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                int readBytes = sc.read(readBuffer);
                if (readBytes > 0) {
                    readBuffer.flip();
                    byte[] bytes = new byte[readBuffer.remaining()];
                    readBuffer.get(bytes);
                    String body = new String(bytes, "UTF-8");
                    System.out.println("The time server receive order: " + body);
                    String currentTime = "QUERY TIME ORDER".
                            equalsIgnoreCase(body) ? new Date(
                                    System.currentTimeMillis()).toString() : "BAD ORDER";
                    doWrite(sc, currentTime);
                } else if (readBytes < 0) {
                    // 对端链路关闭
                    key.cancel();
                    sc.close();
                } else {
                    // 读到0字节，忽略
                }
            }
        }
    }

    private void doWrite(SocketChannel channel, String response) throws IOException {
        if (response != null && response.trim().length() > 0) {
            byte[] bytes = response.getBytes();
            ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
            writeBuffer.put(bytes);
            writeBuffer.flip();
            channel.write(writeBuffer);
        }
    }
}
