package com.hesong.ipsc.ccf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * CTI BUS 单元
 * <p>
 * 它实际上是对 CTI BUS JNI 客户端共享库的一次再封装，以适应云呼你项目中关于CTI服务调用的规定。
 * <p>
 * 一个进程只用使用一个 {@link Unit}
 */
public class Unit {
    static final Map<Byte, Client> clients = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(Unit.class);
    private static final Map<String, RpcResultListener> rpcResultMap = new ConcurrentHashMap<>();
    static UnitCallbacks callbacks;
    private static Byte localUnitId;
    private static ScheduledThreadPoolExecutor rpcResultTimer;

    /**
     * 初始化 JNI 库
     * <p>
     * 在使用 {@link Unit} 的其它功能之前，必须使用该静态方法进行初始化。
     * 该方法只能执行一次。
     *
     * @param localUnitId    该单元在 CTI BUS 中的单元ID(Unit Id)
     * @param callbacks      单元级别的事件回调函数
     * @param rpcResultTimer RPC返回超时计时器
     */
    public static void initiate(byte localUnitId, UnitCallbacks callbacks, ScheduledThreadPoolExecutor rpcResultTimer) {
        logger.info(">>> initiate(localUnitId={}, callbacks={})", localUnitId, callbacks);
        Unit.localUnitId = localUnitId;
        int errCode = com.hesong.ipsc.busnetcli.Client.initiateLibrary(Unit.localUnitId);
        if (errCode != 0) {
            throw new RuntimeException(
                    String.format(
                            "com.lsxy.app.area.cti.busnetcli.Client.initiateLibrary(localUnitId=%d) returns %d",
                            localUnitId, errCode
                    )
            );
        }
        Unit.callbacks = callbacks;
        com.hesong.ipsc.busnetcli.Client.setCallbacks(new LibCallbackHandler());
        if (rpcResultTimer == null) {
            Unit.rpcResultTimer = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
            Unit.rpcResultTimer.setRemoveOnCancelPolicy(true);
        } else {
            Unit.rpcResultTimer = rpcResultTimer;
        }
        logger.info("<<< initiate()");
    }

    /**
     * 初始化 JNI 库
     * <p>
     * 在使用 {@link Unit} 的其它功能之前，必须使用该静态方法进行初始化。
     * 该方法只能执行一次。
     *
     * @param localUnitId 该单元在 CTI BUS 中的单元ID(Unit Id)
     * @param callbacks   单元级别的事件回调函数
     */
    public static void initiate(byte localUnitId, UnitCallbacks callbacks) {
        initiate(localUnitId, callbacks, null);
    }

    /**
     * 初始化 JNI 库
     * <p>
     * 在使用 {@link Unit} 的其它功能之前，必须使用该静态方法进行初始化。
     * 该方法只能执行一次。
     *
     * @param localUnitId 该单元在 CTI BUS 中的单元ID(Unit Id)
     */
    public static void initiate(byte localUnitId) {
        initiate(localUnitId, null, null);
    }

    /**
     * 释放JNI库
     */
    public static void release() {
        logger.warn(">>> release()");
        com.hesong.ipsc.busnetcli.Client.releaseLibrary();
        logger.warn("<<< release()");
    }

    /**
     * @return 该命令处理器的 CTI BUS 单元ID (Unit Id)
     */
    public static Byte getLocalUnitId() {
        return localUnitId;
    }

    static void pushRpcResultListener(final RpcResultListener rpcResultListener) {
        logger.debug(">>> pushRpcResultListener(id={})", rpcResultListener.getId());
        ScheduledFuture fut = rpcResultTimer.schedule(() -> {
            logger.debug("OutgoingRpcReceiver(id={}) Timeout", rpcResultListener.getId());
            try {
                rpcResultMap.remove(rpcResultListener.getId());
                rpcResultListener.onTimeout();
            } catch (Exception e) {
                logger.error(String.format("rpcResultTimer schedule error(id=%s)", rpcResultListener.getId()), e);
                throw e;
            }
        }, rpcResultListener.getTimeout(), TimeUnit.MILLISECONDS);
        rpcResultListener.setFuture(fut);
        rpcResultMap.put(rpcResultListener.getId(), rpcResultListener);
        logger.debug("<<< pushRpcResultListener()");
    }

    static RpcResultListener popRpcResultListener(String rpcId) {
        RpcResultListener receiver = rpcResultMap.remove(rpcId);
        if (receiver == null) return null;
        receiver.getFuture().cancel(false);
        return receiver;
    }

    static RpcResultListener popRpcResultListener(RpcResultListener rpcResultListener) {
        return popRpcResultListener(rpcResultListener.getId());
    }

    static void rpcResponded(RpcResponse response) {
        logger.debug(">>> rpcResponded(response={})", response);
        RpcResultListener receiver = popRpcResultListener(response.getId());
        if (receiver == null) {
            logger.warn("rpcResponded(response={}) cannot be found in rpcResultMap.", response);
            return;
        }
        if (response.getError() != null) {
            receiver.onError(response.getError());
        } else {
            receiver.onResult(response.getResult());
        }
        logger.debug("<<< rpcResponded()");
    }

    /**
     * 建立一个bus命令客户端
     *
     * @param localClientId 本地clientid
     * @param ip            BUS服务器IP地址
     * @param port          BUS服务器端口
     * @param eventListener 该客户端的事件监听器
     * @param executor      该客户端内部的ThreadPoolExecutor，用于处理异步的消息返回。如果为 {@code null} 就收不到事件。
     * @return 新建的客户端对象
     */
    public static Commander createCommander(byte localClientId, String ip, short port,
                                            RpcEventListener eventListener, ThreadPoolExecutor executor) {
        logger.info(
                ">>> createCommander(localClientId={}, ip={}, port={}, eventListener={}, executor={})",
                localClientId, ip, port, eventListener, executor
        );
        if (executor == null) {
            int processors = Runtime.getRuntime().availableProcessors();
            executor = new ThreadPoolExecutor(
                    processors, processors * 5, 1, TimeUnit.MINUTES,
                    new ArrayBlockingQueue<>(processors * 1000, true)
            );
        }
        Commander commander = new Commander(localUnitId, localClientId, ip, port, eventListener, executor);
        clients.put(localClientId, commander);
        logger.info("<<< createCommander() -> {}", commander);
        return commander;
    }

    /**
     * 建立一个bus命令客户端
     *
     * @param localClientId 本地 Client Id
     * @param ip            BUS服务器IP地址
     * @param port          BUS服务器端口
     * @param eventListener 该客户端的事件监听器。如果为 {@code null} 就收不到事件。
     * @return 新建的客户端对象
     */
    public static Commander createCommander(byte localClientId, String ip, short port, RpcEventListener eventListener) {
        return createCommander(localClientId, ip, port, eventListener, null);
    }

    /**
     * 建立一个bus命令客户端
     * <p>
     * 新建的 {@link Client} 对象的线程池执行器的
     * 新建的 {@link Client} 对象的线程池执行器的 corePoolSize是处理器核心数，
     * maximumPoolSize是处理器核心数乘以5，
     * keepAliveTime是1分钟，
     * capacity是处理器核心数乘以1000.
     * 其客户端BUS类型是10。连接的端口是 8088。
     *
     * @param localClientId 本地clientid
     * @param ip            BUS服务器IP地址
     * @param eventListener 该客户端的事件监听器。如果为 {@code null} 就收不到事件。
     * @return 新建的客户端对象
     */
    public static Commander createCommander(byte localClientId, String ip, RpcEventListener eventListener) {
        return createCommander(localClientId, ip, (short) 8088, eventListener);
    }

    /**
     * 建立一个bus命令客户端，同时一个监控客户端 {@link Monitor}，连接到同一个 CTI 服务器
     * <p>
     * 可以通过 {@link Commander#getMonitor} 获取与这个命令器客户端一同建立的 {@link Monitor}
     * <p>
     * <strong>注意</strong>：新建的 {@link Monitor} 的 localClientId 是该构造函数中同名参数的值加上1，
     * 一旦这个ID被占用，记得下一个 {@link Commander} 要间隔一个ID哦 (￣▽￣)" ！
     *
     * @param localClientId        本地clientid
     * @param ip                   BUS服务器IP地址
     * @param commandEventListener {@link Commander}客户端的事件监听器
     * @param monitorEventListener {@link Monitor}客户端的事件监听器
     * @return 新建的 {@link Commander} 客户端对象
     */
    public static Commander createCommander(byte localClientId, String ip, RpcEventListener commandEventListener, MonitorEventListener monitorEventListener) {
        Commander command = createCommander(localClientId, ip, commandEventListener);
        Monitor monitor = createMonitor((byte) (localClientId + 1), ip, monitorEventListener);
        command.setMonitor(monitor);
        return command;
    }

    /**
     * 建立一个bus监控客户端
     *
     * @param localClientId 本地 Client Id
     * @param ip            BUS服务器IP地址
     * @param port          BUS服务器端口
     * @param eventListener 事件监听器。如果为 {@code null} 就收不到事件。
     * @param executor      该客户端内部的ThreadPoolExecutor，用于处理异步的消息返回。如果为 {@code null} 就收不到事件。
     * @return 新建的客户端对象
     */
    public static Monitor createMonitor(byte localClientId, String ip, short port, MonitorEventListener eventListener, ThreadPoolExecutor executor) {
        logger.info(
                ">>> createMonitor(localClientId={}, ip={}, port={})",
                localClientId, ip, port
        );
        if (executor == null) {
            int processors = Runtime.getRuntime().availableProcessors();
            executor = new ThreadPoolExecutor(
                    1, processors, 1, TimeUnit.MINUTES,
                    new ArrayBlockingQueue<>(processors * 100, true)
            );
        }
        Monitor monitor = new Monitor(localUnitId, localClientId, ip, port, eventListener, executor);
        clients.put(localClientId, monitor);
        logger.info("<<< createMonitor() -> {}", monitor);
        return monitor;
    }

    /**
     * 建立一个bus监控客户端
     *
     * @param localClientId 本地 Client Id
     * @param ip            BUS服务器IP地址
     * @param eventListener 事件监听器。如果为 {@code null} 就收不到事件。
     * @return 新建的客户端对象
     */
    public static Monitor createMonitor(byte localClientId, String ip, MonitorEventListener eventListener) {
        return createMonitor(localClientId, ip, (short) 8088, eventListener, null);
    }
}
