package com.xjy;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * 服务启动类：配置HTTP服务器、注册Handler、添加关闭钩子
 */
public class Main {
    private static final int PORT = 8001; // 服务端口（与示例一致）

    public static void main(String[] args) throws IOException {
        // 初始化核心管理器
        SessionManager sessionManager = new SessionManager();
        StakeManager stakeManager = new StakeManager();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // 配置线程池（固定线程数：CPU核心数×2，平衡并发与资源）
        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        server.setExecutor(Executors.newFixedThreadPool(corePoolSize));
        server.createContext("/", new RequestDispatcher(sessionManager, stakeManager));

        // 启动服务器
        server.start();
        System.out.printf("Betting Stake Service started on port %d%n", PORT);

        // 添加JVM关闭钩子（优雅清理资源）
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down service...");
            server.stop(0); // 立即停止服务器
            sessionManager.shutdown(); // 关闭Session清理线程池
            System.out.println("Service shut down successfully.");
        }));
    }
}