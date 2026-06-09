package com.customer.websocket;

import com.customer.service.AgentService;
import com.customer.service.MessageService;
import com.customer.service.RedisAssignmentService;
import com.customer.service.RedisSettingService;
import com.customer.service.RedisWebSocketManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NettyWebSocketServer {

    @Value("${websocket.port:9090}")
    private int port;

    @Value("${websocket.boss-threads:1}")
    private int bossThreads;

    @Value("${websocket.worker-threads:0}")   // 0 = Netty 默认（CPU 核数 × 2）
    private int workerThreads;

    private final MessageService messageService;
    private final AgentService agentService;
    private final RedisAssignmentService assignmentService;
    private final RedisSettingService settingService;
    private final RedisWebSocketManager wsManager;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyWebSocketServer(MessageService messageService, AgentService agentService,
                                 RedisAssignmentService assignmentService,
                                 RedisSettingService settingService,
                                 RedisWebSocketManager wsManager) {
        this.messageService = messageService;
        this.agentService = agentService;
        this.assignmentService = assignmentService;
        this.settingService = settingService;
        this.wsManager = wsManager;
    }

    @PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup(bossThreads);
        // workerThreads ≤ 0 时使用 Netty 默认值（Runtime.getRuntime().availableProcessors() × 2）
        workerGroup = workerThreads > 0 ? new NioEventLoopGroup(workerThreads) : new NioEventLoopGroup();

        // ========== 兜底：JVM 关闭钩子 ==========
        // 当进程被 kill/Ctrl+C 时，确保 Netty 线程停止、端口释放。
        // @PreDestroy 在正常 Spring 关闭时也做同样的事，但强制杀进程时只有 shutdownHook 可靠。
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("JVM shutting down, stopping Netty WebSocket server...");
            stopNetty();
        }));

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new ChunkedWriteHandler());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            // checkStartsWith=true so /ws/user/xxx and /ws/agent/xxx are all matched
                            pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null, false, 65536, false, true));
                            pipeline.addLast(new WebSocketHandler(messageService, assignmentService, agentService, settingService, wsManager));
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("Netty WebSocket server started on port " + port);
            future.channel().closeFuture().addListener(f -> {
                System.out.println("Netty WebSocket server stopped");
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void stop() {
        System.out.println("Spring shutting down, stopping Netty WebSocket server...");
        stopNetty();
    }

    private void stopNetty() {
        if (bossGroup != null && !bossGroup.isShuttingDown()) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null && !workerGroup.isShuttingDown()) {
            workerGroup.shutdownGracefully();
        }
    }
}
