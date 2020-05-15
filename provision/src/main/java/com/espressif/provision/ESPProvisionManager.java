package com.espressif.provision;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.espressif.provision.device_scanner.BleScanner;
import com.espressif.provision.device_scanner.WiFiScanner;
import com.espressif.provision.listeners.BleScanListener;
import com.espressif.provision.listeners.WiFiDeviceScanListener;
import com.espressif.provision.security.Security;
import com.espressif.provision.transport.BLETransport;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;
import com.google.protobuf.InvalidProtocolBufferException;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cloud.Cloud;

public class ESPProvisionManager {

    private static final String TAG = ESPProvisionManager.class.getSimpleName();

    private static ESPProvisionManager provision;
    private static Transport transport;
    private static Security security;
    private static Session session;

    private ESPDevice espDevice;
    private BleScanner bleScanner;
    private WiFiScanner wifiScanner;

    private Context context;
    private Handler handler;

    private String softApBaseUrl;

    private ArrayList<String> deviceCapabilities = new ArrayList<>();

    public static ESPProvisionManager getProvisionInstance(Context context) {

        if (provision == null) {
            provision = new ESPProvisionManager(context);
        }
        return provision;
    }

    private ESPProvisionManager(Context context) {
        this.context = context;
        handler = new Handler();
    }

    public ESPDevice createESPDevice(LibConstants.TransportType transportType, LibConstants.SecurityType securityType) {

        espDevice = new ESPDevice(context, transportType, securityType);
        return espDevice;
    }

    public ESPDevice getEspDevice() {
        return espDevice;
    }

    int REQUEST_CAMERA_PERMISSION = 10;
    boolean isScanned = false;

//    public void scanQRCode(final Activity activityContext, final SurfaceView surfaceView) {
//
//        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(activityContext)
//                .setBarcodeFormats(Barcode.QR_CODE)
//                .build();
//
//        if (ActivityCompat.checkSelfPermission(activityContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
//
//            surfaceView.setVisibility(View.VISIBLE);
//        } else {
//            ActivityCompat.requestPermissions(activityContext, new
//                    String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
//        }
//
//        final CameraSource cameraSource = new CameraSource.Builder(activityContext, barcodeDetector)
//                .setRequestedPreviewSize(1920, 1080)
//                .setAutoFocusEnabled(true) //you should add this feature
//                .build();
//
//        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
//
//            @Override
//            public void surfaceCreated(SurfaceHolder holder) {
//
//                try {
//                    if (ActivityCompat.checkSelfPermission(activityContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
//
//                        if (cameraSource != null) {
//                            cameraSource.start(surfaceView.getHolder());
//                        }
//                    } else {
//                        ActivityCompat.requestPermissions(activityContext, new
//                                String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
//                    }
//
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//
//            @Override
//            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//            }
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder holder) {
//                if (cameraSource != null) {
//                    cameraSource.stop();
//                }
//            }
//        });
//
//        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
//
//            @Override
//            public void release() {
//            }
//
//            @Override
//            public void receiveDetections(Detector.Detections<Barcode> detections) {
//
//                final SparseArray<Barcode> barcodes = detections.getDetectedItems();
//
//                if (barcodes.size() != 0 && !isScanned) {
//
////                    runOnUiThread(new Runnable() {
////
////                        @Override
////                        public void run() {
////                            showLoading();
////                        }
////                    });
//
//                    Log.d(TAG, "Barcodes size : " + barcodes.size());
//                    Barcode barcode = barcodes.valueAt(0);
//                    Log.d(TAG, "QR Code Data : " + barcode.rawValue);
//                    String scannedData = barcode.rawValue;
//
//                    scheduleWiFiConnectionFailure();
//
//                    try {
//                        JSONObject jsonObject = new JSONObject(scannedData);
//
//                        String deviceName = jsonObject.optString("name");
//                        String pop = jsonObject.optString("pop");
//                        String transport = jsonObject.optString("transport");
//                        String password = jsonObject.optString("password");
//                        isScanned = true;
//
//                        Handler handler = new Handler(Looper.getMainLooper());
//                        handler.post(new Runnable() {
//
//                            @Override
//                            public void run() {
//                                cameraSource.release();
//                            }
//                        });
//
//                        connectWiFiDevice(activityContext, deviceName, password);
//                        checkDeviceConnection();
//
//                    } catch (JSONException e) {
//
//                        e.printStackTrace();
//                        // TODO
////                        runOnUiThread(new Runnable() {
////                            @Override
////                            public void run() {
////                                intent.putExtra(AppConstants.KEY_ERROR_MSG, getString(R.string.error_qr_code_not_valid));
////                                setResult(RESULT_CANCELED, intent);
////                                finish();
////                            }
////                        });
//                    }
//                }
//            }
//        });
//    }

    public void searchBleEspDevices(BleScanListener bleScannerListener) {

        bleScanner = new BleScanner(context, bleScannerListener);
        bleScanner.startScan();
    }

    public void searchBleEspDevices(long scanTimeoutInMillis, BleScanListener bleScannerListener) {

        bleScanner = new BleScanner(context, scanTimeoutInMillis, bleScannerListener);
        bleScanner.startScan();
    }

    public void searchWiFiEspDevices(WiFiDeviceScanListener wiFiDeviceScanListener) {

        wifiScanner = new WiFiScanner(context, wiFiDeviceScanListener);
        wifiScanner.startScan();
    }

    public void connectBLEDevice(BluetoothDevice bluetoothDevice, UUID primaryServiceUuid) {

        if (transport instanceof BLETransport) {
            ((BLETransport) transport).connect(bluetoothDevice, primaryServiceUuid);
        } else {
            // TODO Send Error
            Log.e(TAG, "Wrong transport init");
        }
    }

    public void connectWiFiDevice(String baseUrl) {

        softApBaseUrl = baseUrl;

        if (transport instanceof SoftAPTransport) {

            enableOnlyWifiNetwork();
            deviceConnectionReqCount = 0;
            ((SoftAPTransport) transport).setBaseUrl(softApBaseUrl);
            connectWiFiDevice();

        } else {
            // TODO Send Error
            Log.e(TAG, "Wrong transport init");
        }
    }

    public void connectWiFiDevice(Activity activity, String baseUrl, String ssid, String password) {

        Log.e(TAG, "connectWiFiDevice ==========================");
        softApBaseUrl = baseUrl;
        connectWiFiDevice(activity, ssid, password);

        // TODO

        if (transport instanceof SoftAPTransport) {

//            enableOnlyWifiNetwork();
//            deviceConnectionReqCount = 0;
//            ((SoftAPTransport) transport).setBaseUrl(softApBaseUrl);
//            connectWiFiDevice();

        } else {
            // TODO Send Error
            Log.e(TAG, "Wrong transport init");
        }
    }


    public ArrayList<String> getDeviceCapabilities() {

        if (transport instanceof BLETransport) {
            return ((BLETransport) transport).deviceCapabilities;
        } else {
            return deviceCapabilities;
        }
    }

    public void associateDevice(String userId, final String secretKey, final ResponseListener listener) {

        byte[] message = Messenger.prepareAssociateDeviceMsg(userId, secretKey);
        byte[] encryptedMsg = security.encrypt(message);

        transport.sendConfigData(LibConstants.HANDLER_CLOUD_USER_ASSOC, encryptedMsg, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                processAssociateDeviceDetails(returnData, secretKey);
            }

            @Override
            public void onFailure(Exception e) {

                if (listener != null) {
                    listener.onFailure(e);
                }
            }
        });
    }

    private void processAssociateDeviceDetails(byte[] responseData, String secretKey) {

        try {
            Cloud.CloudConfigPayload payload = Cloud.CloudConfigPayload.parseFrom(responseData);
            Cloud.RespGetSetDetails response = payload.getRespGetSetDetails();

            if (response.getStatus() == Cloud.CloudConfigStatus.Success) {

                String deviceSecret = response.getDeviceSecret();
                // TODO
            }
        } catch (InvalidProtocolBufferException e) {

            e.printStackTrace();
        }
    }

    ConnectivityManager connectivityManager;
    ConnectivityManager.NetworkCallback networkCallback;

    private void enableOnlyWifiNetwork() {

        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder request = new NetworkRequest.Builder();
        request.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);

        networkCallback = new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(Network network) {

                if (Build.VERSION.RELEASE.equalsIgnoreCase("6.0")) {

                    if (!Settings.System.canWrite(context)) {
                        Intent goToSettings = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        goToSettings.setData(Uri.parse("package:" + context.getPackageName()));
                        context.startActivity(goToSettings);
                    }
                }
                connectivityManager.bindProcessToNetwork(network);
            }
        };
        connectivityManager.registerNetworkCallback(request.build(), networkCallback);
    }

    public void disableOnlyWifiNetwork() {

        Log.d(TAG, "disableOnlyWifiNetwork()");

        if (connectivityManager != null) {

            try {
                connectivityManager.bindProcessToNetwork(null);
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.e(TAG, "Connectivity Manager is already unregistered");
            }
        }
    }

    private int deviceConnectionReqCount = 0;

    private Runnable connectWithDeviceTask = new Runnable() {

        @Override
        public void run() {

            Log.d(TAG, "Connecting to device");
            deviceConnectionReqCount++;
            String tempData = "ESP";

            transport.sendConfigData(LibConstants.HANDLER_PROTO_VER, tempData.getBytes(), new ResponseListener() {

                @Override
                public void onSuccess(byte[] returnData) {

                    String data = new String(returnData, StandardCharsets.UTF_8);
                    Log.d(TAG, "Value : " + data);
                    deviceCapabilities = new ArrayList<>();

                    try {
                        JSONObject jsonObject = new JSONObject(data);
                        JSONObject provInfo = jsonObject.getJSONObject("prov");

                        String versionInfo = provInfo.getString("ver");
                        Log.d(TAG, "Device Version : " + versionInfo);

                        JSONArray capabilities = provInfo.getJSONArray("cap");

                        for (int i = 0; i < capabilities.length(); i++) {
                            String cap = capabilities.getString(i);
                            deviceCapabilities.add(cap);
                        }
                        Log.d(TAG, "Capabilities : " + deviceCapabilities);

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.d(TAG, "Capabilities JSON not available.");
                    }
                    EventBus.getDefault().post(new DeviceProvEvent(LibConstants.EVENT_DEVICE_CONNECTED));
                }

                @Override
                public void onFailure(Exception e) {
                    e.printStackTrace();

                    if (deviceConnectionReqCount == 3) {

                        handler.removeCallbacks(connectWithDeviceTask);
                        sendDeviceConnectionFailure();
                    } else {
                        connectWiFiDevice();
                    }
                }
            });
        }
    };

    private Runnable deviceConnectionFailedTask = new Runnable() {

        @Override
        public void run() {

            handler.removeCallbacks(connectWithDeviceTask);
            EventBus.getDefault().post(new DeviceProvEvent(LibConstants.EVENT_DEVICE_CONNECTION_FAILED));
        }
    };

    private void sendDeviceConnectionFailure() {
        handler.postDelayed(deviceConnectionFailedTask, 1000);
    }

    private void connectWiFiDevice() {

        handler.removeCallbacks(connectWithDeviceTask);
        handler.postDelayed(connectWithDeviceTask, 100);
    }

    private void scheduleWiFiConnectionFailure() {
        handler.postDelayed(wifiConnectionFailedTask, 12000);
    }

    private void connectWiFiDevice(Activity activity, String ssid, String password) {

        enableOnlyWifiNetwork();
//        routeNetworkRequestsThroughWifi(AddDeviceActivity.this, deviceName);

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }

        Log.d(TAG, "Device name : " + ssid);
        Log.d(TAG, "Device password : " + password);

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = String.format("\"%s\"", ssid);

        if (TextUtils.isEmpty(password)) {
            Log.i(TAG, "Connect to open network");
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else {
            Log.i(TAG, "Connect to secure network");
            config.preSharedKey = String.format("\"%s\"", password);
        }

        int netId = -1;
        List<WifiConfiguration> apList = wifiManager.getConfiguredNetworks();
        Log.d(TAG, "List Size : " + apList.size());

        for (WifiConfiguration i : apList) {

            if (i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
                netId = i.networkId;
            }
        }

        if (netId == -1) {

            netId = wifiManager.addNetwork(config);
            Log.d(TAG, "Network Id : " + netId);
        }

        if (netId != -1) {

            Log.d(TAG, "Connect to network : " + netId);
            wifiManager.enableNetwork(netId, true);

        } else {

            List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();

            for (WifiConfiguration i : list) {

                if (i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
//                                    Log.d(TAG, "i.networkId : " + i.networkId);
                    wifiManager.removeNetwork(i.networkId);
                    break;
                }
            }

            netId = wifiManager.addNetwork(config);
            wifiManager.disconnect();
            wifiManager.enableNetwork(netId, true);
            wifiManager.reconnect();
            checkDeviceConnection(activity, ssid);
        }
    }

    FetchNetworkName task;

    private void checkDeviceConnection(Activity activity, String ssid) {

        task = new FetchNetworkName(activity, ssid);
        handler.postDelayed(task, 2000);
    }

    private class FetchNetworkName implements Runnable {

        private Activity activity;
        private String ssid;

        FetchNetworkName(Activity activity, String ssid) {
            this.activity = activity;
            this.ssid = ssid;
        }

        @Override
        public void run() {

            String networkName = fetchWiFiSSID(activity);
            Log.d(TAG, "Fetch SSID : " + networkName);
            Log.d(TAG, "SSID : " + ssid);

            if (ssid != null && ssid.startsWith(networkName)) {

                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                handler.removeCallbacks(wifiConnectionFailedTask);
//                connectWithDevice();
            } else {

                handler.removeCallbacks(task);
                checkDeviceConnection(activity, ssid);
            }
        }
    }

    private Runnable wifiConnectionFailedTask = new Runnable() {

        @Override
        public void run() {

            handler.removeCallbacks(task);
            // TODO Send error.
//            intent.putExtra(AppConstants.KEY_ERROR_MSG, getString(R.string.error_device_connect_failed));
//            setResult(RESULT_CANCELED, intent);
//            finish();
        }
    };

    private String fetchWiFiSSID(final Activity activity) {

        String ssid = null;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo == null) {
                return null;
            }

            if (networkInfo.isConnected()) {
                final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
                if (connectionInfo != null && connectionInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                    ssid = connectionInfo.getSSID();
                    ssid = ssid.replaceAll("^\"|\"$", "");
                }
            }
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
        }

        Log.e(TAG, "Returning ssid : " + ssid);
        return ssid;
    }
}
