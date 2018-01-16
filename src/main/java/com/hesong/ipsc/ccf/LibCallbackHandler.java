package com.hesong.ipsc.ccf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.hesong.ipsc.busnetcli.Head;
import com.hesong.ipsc.busnetcli.Callbacks;

import java.io.UnsupportedEncodingException;

class LibCallbackHandler implements Callbacks {

    private final Logger logger = LoggerFactory.getLogger(LibCallbackHandler.class);
    private final Logger jniLogger = LoggerFactory.getLogger("bus_net_cli");

    public void globalConnect(byte unitId, byte clientId, byte clientType, byte status, String addInfo) {
        logger.debug(">>> globalConnect(localUnitId={}, clientId={}, clientType={}, addInfo={}, status={})", unitId, clientId, clientType, addInfo, status);
        if (Unit.callbacks != null) {
            Unit.callbacks.globalConnectStateChanged(unitId, clientId, clientType, status, addInfo);
        }
        logger.debug("<<< globalConnect(localUnitId={}, clientId={}, clientType={}, addInfo={}, status={})", unitId, clientId, clientType, addInfo, status);
    }

    public void connect(byte localClientId, int accessPointUnitId, int errorCode) {
        logger.debug(">>> connect({}, {}, {})", localClientId, accessPointUnitId, errorCode);
        if (errorCode == 0)
            // 客户端连接成功
            logger.info("[{}:{}] connection succeed. ConnectingUnitId={}", Unit.getLocalUnitId(), localClientId, accessPointUnitId);
        else
            // 客户端连接失败
            logger.error("[{}:{}] connection failed. ErrorCode={}", Unit.getLocalUnitId(), localClientId, errorCode);
        byte connectingUnitId = (byte) accessPointUnitId;
        if (connectingUnitId < 0)
            throw new RuntimeException(String.format(
                    "argument accessPointUnitId less than zero in callback function connect(%d, %d, %d)",
                    localClientId, accessPointUnitId, errorCode));
        Client client = Unit.clients.get(localClientId);
        if (client == null)
            throw new RuntimeException(String.format(
                    "Can not find client<%d> in callback function connect(%d, %d, %d)",
                    localClientId, localClientId, accessPointUnitId, errorCode));
        client.connected = errorCode == 0;
        client.connectingUnitId = connectingUnitId;
        if (client.connected) {
            if (Unit.callbacks == null) {
                logger.debug("Unit.callbacks is null");
            } else {
                logger.debug(">>> Unit.callbacks.connectSucceed(client={})", client);
                Unit.callbacks.connectSucceed(client);
                logger.debug("<<< Unit.callbacks.connectSucceed(client={})", client);
            }
        } else {
            if (Unit.callbacks == null) {
                logger.debug("Unit.callbacks is null");
            } else {
                logger.debug(">>> Unit.callbacks.connectFailed(client={})", client);
                Unit.callbacks.connectFailed(client, errorCode);
                logger.debug("<<< Unit.callbacks.connectFailed(client={})", client);
            }
        }
        logger.debug("<<< connect({}, {}, {})", localClientId, accessPointUnitId, errorCode);
    }

    public void disconnect(byte localClientId) {
        logger.debug(">>> disconnect({}, {})", localClientId);
        logger.error("[{}:{}] connection lost", Unit.getLocalUnitId(), localClientId);
        Client client = Unit.clients.get(localClientId);
        if (client != null) {
            client.connected = false;
            if (Unit.callbacks == null) {
                logger.debug("Unit.callbacks is null");
            } else {
                logger.debug(">>> Unit.callbacks.connectLost(client={})", client);
                Unit.callbacks.connectLost(client);
                logger.debug("<<< Unit.callbacks.connectLost(client={})", client);
            }
        }
        logger.debug("<<< disconnect({}, {})", localClientId);
    }

    public void data(Head head, byte[] bytes) {
        logger.debug(">>> data(head={}, dataLength={})", head, bytes.length);
        String data = null;
        try {
            data = new String(bytes, "ASCII");
        } catch (UnsupportedEncodingException error) {
            logger.warn("Unsupported Encoding data:", error);
        }
        if (data != null) {
            byte cmdType = head.getCmdType();
            if (cmdType == (byte) 3) {
                Commander commander = (Commander) Unit.clients.get(head.getDstClientId());
                if (commander == null) {
                    logger.error("cannot find Commander client<id={}>", head.getDstClientId());
                    return;
                }
                String rpcTxt = data;
                commander.executor.execute(() -> {
                    commander.logger.debug(">>> commander<{}> executor.execute data: {}", commander, rpcTxt);
                    try {
                        RpcRequest req = null;
                        RpcResponse res = null;
                        // 收到了RPC事件通知？
                        if (commander.eventListener != null) {
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                req = mapper.readValue(rpcTxt, RpcRequest.class);
                            } catch (JsonProcessingException ignore) {
                            }
                            if (req != null) {
                                commander.logger.debug(">>> commander.eventListener.onEvent({})", req);
                                BusAddress source = new BusAddress(head.getSrcUnitId(), head.getSrcClientId());
                                commander.eventListener.onEvent(source, req);
                                commander.logger.debug("<<< commander.eventListener.onEvent()");
                                return;
                            }
                        } else {
                            commander.logger.debug("commander<{}> executor.execute NO eventListener", commander);
                        }
                        // 收到了RPC调用回复？
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            res = mapper.readValue(rpcTxt, RpcResponse.class);
                        } catch (JsonProcessingException ignore) {
                        }
                        if (res != null) {
                            Unit.rpcResponded(res);
                            return;
                        }
                        // 既不是RPC事件通知，也不是RPC请求回复，只能忽略了。
                        commander.logger.warn("unsupported RPC content received: {}", rpcTxt);
                    } catch (Exception e) {
                        commander.logger.error("error occurred in executor.execute()", e);
                    } finally {
                        commander.logger.debug("<<< commander<{}> executor.execute()", commander);
                    }

                });
            } else if (cmdType == (byte) 6) {
                Monitor monitor = (Monitor) Unit.clients.get(head.getDstClientId());
                if (monitor == null) {
                    logger.error("cannot find Monitor client<id={}>", head.getDstClientId());
                    return;
                }
                String finalData = data;
                if (monitor.executor != null) {
                    BusAddress source = new BusAddress(head.getSrcUnitId(), head.getSrcClientId());
                    monitor.executor.execute(() -> monitor.process(source, finalData));
                }
            }
        }
        logger.debug("<<< data()");
    }

    public void log(String msg, Boolean isErr) {
        if (isErr) {
            jniLogger.error(msg.trim());
        } else {
            jniLogger.info(msg.trim());
        }
    }

}