package com.hesong.ipsc.ccf;

/**
 * Monitor 事件监听类
 * <p>
 * Created by tanbr on 2016/12/21.
 */
public interface MonitorEventListener {
    /**
     * CTI服务器负载变化回调
     *
     * @param source     变化事件发送者
     * @param serverInfo CTI服务器信息
     */
    void onServerLoadChanged(BusAddress source, ServerInfo serverInfo);
}
