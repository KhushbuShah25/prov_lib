package com.espressif.provision;

import android.os.Bundle;

public class DeviceProvEvent {

    private short eventType;
    private Bundle data;

    public DeviceProvEvent(short type) {
        eventType = type;
    }

    public short getEventType() {
        return eventType;
    }

    public Bundle getData() {
        return data;
    }

    public void setData(Bundle data) {
        this.data = data;
    }
}
