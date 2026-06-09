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
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

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
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }
}
