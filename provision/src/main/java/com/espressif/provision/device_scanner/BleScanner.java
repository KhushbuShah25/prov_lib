package com.espressif.provision.device_scanner;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.espressif.provision.listeners.BleScanListener;

import java.util.ArrayList;
import java.util.List;

public class BleScanner {

    private static final String TAG = BleScanner.class.getSimpleName();

    private static final long MIN_SCAN_TIME = 6000;

    private Context context;
    private BleScanListener bleScanListener;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    private long scanTimeout;
    private boolean isScanning = false;

    public BleScanner(Context context, BleScanListener bleScannerListener) {

        this.context = context;
        this.scanTimeout = MIN_SCAN_TIME;
        this.bleScanListener = bleScannerListener;

        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    public BleScanner(Context context, long scanTimeoutInMillis, BleScanListener bleScannerListener) {

        this.context = context;
        this.bleScanListener = bleScannerListener;

        if (scanTimeoutInMillis >= MIN_SCAN_TIME) {
            this.scanTimeout = scanTimeoutInMillis;
        } else {
            Log.e(TAG, "Scan time should be more than 6 seconds.");
            this.scanTimeout = MIN_SCAN_TIME;
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    public void startScan() {

        Log.e(TAG, "startScan : " + scanTimeout);

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        List<ScanFilter> filters = new ArrayList<>();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();

        isScanning = true;
        bluetoothLeScanner.startScan(filters, settings, scanCallback);
        Handler someHandler = new Handler();
        someHandler.postDelayed(new Runnable() {

            @Override
            public void run() {

                stopScan();
            }
        }, 6000);
    }

    public void stopScan() {

        Log.e(TAG, "onStopBleScan()");

        if (bluetoothLeScanner != null && bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
        isScanning = false;
        bleScanListener.scanCompleted();
    }

    public boolean isScanning() {
        return isScanning;
    }

    final ScanCallback scanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            if (result.getDevice() != null && !TextUtils.isEmpty(result.getDevice().getName())) {

                Log.e(TAG, "========================== Device Found : " + result.getDevice().getName());
                bleScanListener.onPeripheralFound(result.getDevice(), result);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d(TAG, "onBatchScanResults()");
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(TAG, "onScanFailed errorCode:" + errorCode);
            bleScanListener.onFailure(new Exception("BLE connect failed with error code : " + errorCode));
        }
    };
}
