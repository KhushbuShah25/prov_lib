package com.espressif.provision.listeners;

import com.espressif.provision.WiFiAccessPoint;

import java.util.ArrayList;

public interface WiFiScanListener {

    void onWifiListReceived(ArrayList<WiFiAccessPoint> wifiList);

    void onWiFiScanFailed(Exception e);
}
