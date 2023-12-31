package com.hesong.ipsc.ccf;

/**
 * CTI BUS 地址信息
 * <p>
 * Created by tanbr on 2016/8/9.
 */
public class BusAddress {
    private final byte unitId;
    private final byte clientId;

    public BusAddress(byte unitId, byte clientId) {
        this.unitId = unitId;
        this.clientId = clientId;
    }

    /**
     * @return CTI BUS 地址的 Unit 部分
     */
    public byte getUnitId() {
        return unitId;
    }

    /**
     * @return CTI BUS 地址的 Client 部分
     */
    public byte getClientId() {
        return clientId;
    }

    @Override
    public String toString() {
        return String.format("<%s unitId=%s, clientId=%s>", BusAddress.class, unitId, clientId);
    }
}
