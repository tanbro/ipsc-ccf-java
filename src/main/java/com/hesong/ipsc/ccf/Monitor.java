package com.hesong.ipsc.ccf;

import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * CTI BUS 负载数据监听器
 * <p>
 * Created by tanbr on 2016/8/15.
 * <p>
 *
 * @see <a href="http://cf.liushuixingyun.com/pages/viewpage.action?pageId=1803231">YEP 8 -- 区域代理配置数据项</a>
 */
public class Monitor extends Client {

    MonitorEventListener eventListener;
    ThreadPoolExecutor executor;
    private ConcurrentHashMap<String, ServerInfo> serverInfoMap;

    /**
     * @param unitId 所属的本地Unit节点的ID
     * @param id     客户端ID
     * @param ip     要连接的 CTI BUS 服务器 IP
     * @param port   要连接的 CTI BUS 服务器端口
     */
    Monitor(byte unitId, byte id, String ip, short port, MonitorEventListener eventListener, ThreadPoolExecutor executor) {
        super(unitId, id, (byte) 3, ip, port);
        this.logger = LoggerFactory.getLogger(Monitor.class);
        this.eventListener = eventListener;
        this.executor = executor;
        this.executor.prestartAllCoreThreads();

        serverInfoMap = new ConcurrentHashMap<>();
    }

    private Map<String, String> parseKeyValStr(String s) {
        String[] ss = s.split("([,;|])");
        Map<String, String> result = new HashMap<>(ss.length);
        for (String i : ss) {
            String[] kv = i.split("=", 2);
            String key = null;
            String value = null;
            if (kv.length > 0)
                key = kv[0];
            if (kv.length > 1)
                value = kv[1].trim();
            if (key != null)
                result.put(key, value);
        }
        return result;
    }

    void process(BusAddress source, String s) {
        Integer flag = null;
        String[] parts = s.split(":", 2);
        if ("svr".equals(parts[0].toLowerCase())) {
            flag = 0;
        } else if ("svrres".equals(parts[0].toLowerCase())) {
            flag = 1;
        }
        if (flag == null)
            return;
        Map<String, String> kvs = parseKeyValStr(parts[1]);
        String id = kvs.remove("id");
        ServerInfo si = serverInfoMap.computeIfAbsent(id, ServerInfo::new);
        if (flag == 0) {
            si.name = kvs.get("name");
            if (kvs.get("type") == null)
                si.type = null;
            else
                si.type = Integer.parseInt(kvs.get("type"));
            si.machineName = kvs.get("machinename");
            si.os = kvs.get("os");
            if (kvs.get("mode") == null)
                si.mode = null;
            else
                si.mode = Integer.parseInt(kvs.get("mode"));
            si.prj = kvs.get("prj");
            if (kvs.get("pi") == null)
                si.pi = null;
            else
                si.pi = Long.parseLong(kvs.get("pi"));
            si.ipscVersion = kvs.get("ipsc_version");
            if (kvs.get("startup_time") == null)
                si.startupTime = null;
            else
                si.startupTime = LocalDateTime.parse(kvs.get("startup_time").trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (kvs.get("dog_status") == null) si.dogStatus = null;
            else si.dogStatus = Integer.parseInt(kvs.get("dog_status"));
            if (kvs.get("loadlevel") == null)
                si.loadlevel = null;
            else
                si.loadlevel = Integer.parseInt(kvs.get("loadlevel"));
        } else {
            kvs.forEach((k, v) -> {
                Integer _v = null;
                if (v != null)
                    _v = Integer.parseInt(v);
                si.loads.put(k, _v);
            });
            if (this.eventListener != null) {
                this.eventListener.onServerLoadChanged(source, si.copy());
            }
        }
    }

    public Map<String, ServerInfo> getServerInfoMap() {
        return new HashMap<>(serverInfoMap);
    }

    @Override
    public String toString() {
        return String.format("<%s unitId=%s, clientId=%s>", Monitor.class, getUnitId(), getId());
    }

}
