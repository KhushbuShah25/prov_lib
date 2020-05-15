package com.espressif.provision.listeners;

import com.espressif.provision.WiFiAccessPoint;

import java.util.ArrayList;

/**
 * Interface for BLE device scanning.
 */
public interface WiFiDeviceScanListener {

    /**
     * Callback method for scan completed.
     *
     * @param scanResults Scan result.
     */
    void scanCompleted(ArrayList<WiFiAccessPoint> scanResults);

    /**
     * Failed to scan for BLE bluetoothDevices.
     *
     * @param e Exception
     */
    void onFailure(Exception e);
}
