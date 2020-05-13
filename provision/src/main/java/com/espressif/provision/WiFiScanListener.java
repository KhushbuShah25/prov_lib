package com.espressif.provision;

import java.util.ArrayList;

public interface WiFiScanListener {

    void onWifiListReceived(ArrayList<WiFiAccessPoint> wifiList);

    void onWiFiScanFailed(Exception e);
}
