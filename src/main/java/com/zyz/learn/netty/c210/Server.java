package com.zyz.learn.netty.c210;

import com.sun.corba.se.spi.ior.ObjectKey;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * @author ZhangYuanzhuo
 * @since 2017/9/10
 */
public class Server {
    public static void main(String[] args) throws IOException {
        int port = 8080;

        // 1 打开ServerSocketChannel，用于监听客户端连接，是所有客户端连接的父管道
        ServerSocketChannel acceptorSvr = ServerSocketChannel.open();

        // 2 绑定监听端口，设置为非阻塞模式
        acceptorSvr.socket().bind(new InetSocketAddress(InetAddress.getByName("IP"), port));
        acceptorSvr.configureBlocking(false);

        // 3 创建Reactor线程，创建多路复用器并启动线程
        Selector selector = Selector.open();
        new Thread(new ReactorTask()).start();

        // 4 将ServerSocketChannel注册到Reactor线程的多路复用器Selector上，监听ACCEPT事件
        Object ioHandler = null;
        SelectionKey key = acceptorSvr.register(selector, SelectionKey.OP_ACCEPT, ioHandler);

        // 5 多路复用器在线程run方法的无线循环体内轮询准备就绪的Key
        int num = selector.select();
        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        Iterator<SelectionKey> it = selectionKeys.iterator();
        while (it.hasNext()) {
            SelectionKey next = it.next();
            // ... deal with I/O event ...
        }

        // 6 多路复用器监听到有新的客户端接入，处理新的介入请求，完成TCP三次握手，建立物理链路
        SocketChannel channel = acceptorSvr.accept();

        // 7 设置客户端链路为非阻塞模式
        channel.configureBlocking(false);
        channel.socket().setReuseAddress(true);
        // ......

        // 8 将新接入的客户端连接注册到Reactor线程的多路复用器上，监听读操作，读取客户端发送的网络消息
        SelectionKey register = channel.register(selector, SelectionKey.OP_READ, ioHandler);

        // 9 异步读取客户端请求消息到缓冲区
        ByteBuffer receivedBuffer = null;
        int readNumber = channel.read(receivedBuffer);

        // 10
        Object message = null;
        ByteBuffer buffer = null;
        ConcurrentLinkedDeque<Object> messageList = null;
        while (buffer.hasRemaining()) {
            buffer.mark();
//            message = decode(buffer);
            if (message == null) {
                buffer.reset();
                break;
            }
            messageList.add(message);
        }
        if (!buffer.hasRemaining()) {
            buffer.clear();
        } else {
            buffer.compact();
        }
        if (messageList != null && !messageList.isEmpty()) {
            for (Object e : messageList) {
//                handlerTask(e);
            }
        }

        // 11
        channel.write(buffer);
    }
}
