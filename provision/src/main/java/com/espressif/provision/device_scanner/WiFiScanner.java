package com.espressif.provision.device_scanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.espressif.provision.LibConstants;
import com.espressif.provision.WiFiAccessPoint;
import com.espressif.provision.listeners.WiFiDeviceScanListener;

import java.util.ArrayList;
import java.util.List;

public class WiFiScanner {

    private static final String TAG = WiFiScanner.class.getSimpleName();

    private Context context;
    private WiFiDeviceScanListener wiFiScanListener;
    private WifiManager wifiManager;
    private ArrayList<WiFiAccessPoint> results;

    private boolean isScanning = false;

    public WiFiScanner(Context context, WiFiDeviceScanListener wiFiScanListener) {

        this.context = context;
        this.wiFiScanListener = wiFiScanListener;
        results = new ArrayList<>();
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (!wifiManager.isWifiEnabled()) {

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
//                Toast.makeText(this, "Please turn on Wi-Fi", Toast.LENGTH_LONG).show();
            } else {
                wifiManager.setWifiEnabled(true);
            }
        }
    }

    public void startScan() {

        Log.d(TAG, "startScan");

        results.clear();
        context.registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifiManager.startScan();
        Toast.makeText(context, "Scanning WiFi ...", Toast.LENGTH_SHORT).show();

//        Handler someHandler = new Handler();
//        someHandler.postDelayed(new Runnable() {
//
//            @Override
//            public void run() {
//
//                stopScan();
//            }
//        }, scanTimeout);
    }

    BroadcastReceiver wifiReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            List<ScanResult> scanResults = wifiManager.getScanResults();
            context.unregisterReceiver(this);

            for (ScanResult scanResult : scanResults) {
                Log.e(TAG, "Scan Result : " + scanResult.SSID + " - " + scanResult.capabilities);
                WiFiAccessPoint wiFiAccessPoint = new WiFiAccessPoint();
                wiFiAccessPoint.setWifiName(scanResult.SSID);
                wiFiAccessPoint.setRssi(scanResult.level);

                if (scanResult.capabilities.contains("WPA") || scanResult.capabilities.contains("WEP")) {
                    wiFiAccessPoint.setSecurity(LibConstants.WIFI_WEP);
                } else {
                    wiFiAccessPoint.setSecurity(LibConstants.WIFI_OPEN);
                }
                results.add(wiFiAccessPoint);
            }
            wiFiScanListener.scanCompleted(results);
        }
    };

    public boolean isScanning() {
        return isScanning;
    }
}
