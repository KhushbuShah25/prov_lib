package com.espressif.provision;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.security.Security1;
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
import java.util.UUID;

import cloud.Cloud;
import espressif.Constants;
import espressif.WifiConfig;
import espressif.WifiConstants;
import espressif.WifiScan;

import static java.lang.Thread.sleep;

public class Provision {

    private static final String TAG = Provision.class.getSimpleName();

    private static Provision provision;
    private static Transport transport;
    private static Security security;
    private static Session session;

    private Context context;
    private Handler handler;

    private int totalCount;
    private int startIndex;
    private String softApBaseUrl;
    private String proofOfPossession;

    private ArrayList<WiFiAccessPoint> wifiApList;
    private ArrayList<String> deviceCapabilities = new ArrayList<>();
    private ProvisionListener provisionListener;
    private WiFiScanListener wifiScanListener;
    private LibConstants.TransportType transportType = LibConstants.TransportType.TRANSPORT_SOFTAP;
    private LibConstants.SecurityType securityType = LibConstants.SecurityType.SECURITY_0;

    public static Provision getProvisionInstance(Context context) {

        if (provision == null) {
            provision = new Provision(context);
        }
        return provision;
    }

    private Provision(Context context) {
        this.context = context;
        handler = new Handler();
    }

    public void setProvisionListener(ProvisionListener provisionListener) {
        this.provisionListener = provisionListener;
    }

    public void setProvisionLib(LibConstants.TransportType transportType, LibConstants.SecurityType securityType) {

        this.transportType = transportType;
        this.securityType = securityType;

        switch (transportType) {

            case TRANSPORT_BLE:
                transport = new BLETransport(context);
                break;

            case TRANSPORT_SOFTAP:
                transport = new SoftAPTransport();
                break;
        }
    }

    public void connectBLEDevice(BluetoothDevice bluetoothDevice, UUID primaryServiceUuid) {

        if (transport instanceof BLETransport) {
            ((BLETransport) transport).connect(bluetoothDevice, primaryServiceUuid);
        } else {
            // TODO Send Error
            Log.e(TAG, "Wrong transport init");
        }
    }

    public void connectWiFiDevice(String baseUrl, String ssid, String password) {

        Log.e(TAG, "connectWiFiDevice ==========================");
        softApBaseUrl = baseUrl;

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

    public ArrayList<String> getDeviceCapabilities() {

        if (transport instanceof BLETransport) {
            return ((BLETransport) transport).deviceCapabilities;
        } else {
            return deviceCapabilities;
        }
    }

    public void setProofOfPossession(String pop) {
        this.proofOfPossession = pop;
    }

    private void initSession(final ResponseListener listener) {

        if (securityType.equals(LibConstants.SecurityType.SECURITY_0)) {
            security = new Security0();
        } else {
            security = new Security1(proofOfPossession);
        }

        session = new Session(transport, security);

        session.init(null, new Session.SessionListener() {

            @Override
            public void OnSessionEstablished() {
                listener.onSuccess(null);
            }

            @Override
            public void OnSessionEstablishFailed(Exception e) {
                listener.onFailure(e);
            }
        });
    }

    public void scanNetworks(final WiFiScanListener wifiScanListener) {

        this.wifiScanListener = wifiScanListener;

        initSession(new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {
                startNetworkScan();
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                if (wifiScanListener != null) {
                    wifiScanListener.onWiFiScanFailed(new RuntimeException("Failed to create session."));
                }
            }
        });
    }

    public void provision(final String ssid, final String passphrase, final ProvisionListener provisionListener) {

        this.provisionListener = provisionListener;

        if (session == null || !session.isEstablished()) {

            initSession(new ResponseListener() {

                @Override
                public void onSuccess(byte[] returnData) {
                    sendWiFiConfig(ssid, passphrase, provisionListener);
                }

                @Override
                public void onFailure(Exception e) {
                    e.printStackTrace();
                    if (provisionListener != null) {
                        provisionListener.createSessionFailed(new RuntimeException("Failed to create session."));
                    }
                }
            });
        } else {
            sendWiFiConfig(ssid, passphrase, provisionListener);
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

    private void startNetworkScan() {

        totalCount = 0;
        startIndex = 0;
        wifiApList = new ArrayList<>();
        byte[] scanCommand = Messenger.prepareWiFiScanMsg();

        session.sendDataToDevice(LibConstants.HANDLER_PROV_SCAN, scanCommand, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                processStartScanResponse(returnData);
                // TODO Check status of processed msg.

                byte[] getScanStatusCmd = Messenger.prepareGetWiFiScanStatusMsg();
                session.sendDataToDevice(LibConstants.HANDLER_PROV_SCAN, getScanStatusCmd, new ResponseListener() {

                    @Override
                    public void onSuccess(byte[] returnData) {
                        processWifiStatusResponse(returnData);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        e.printStackTrace();
                        if (wifiScanListener != null) {
                            wifiScanListener.onWiFiScanFailed(new RuntimeException("Failed to send Wi-Fi scan command."));
                        }
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                if (wifiScanListener != null) {
                    wifiScanListener.onWiFiScanFailed(new RuntimeException("Failed to send Wi-Fi scan command."));
                }
            }
        });
    }

    private void getFullWiFiList() {

        Log.e(TAG, "Total count : " + totalCount + " and start index is : " + startIndex);

        if (totalCount < 4) {

            getWiFiScanList(0, totalCount);

        } else {

            int temp = totalCount - startIndex;

            if (temp > 0) {

                if (temp > 4) {
                    getWiFiScanList(startIndex, 4);
                } else {
                    getWiFiScanList(startIndex, temp);
                }

            } else {
                Log.d(TAG, "Nothing to do. Wifi list completed.");
                completeWifiList();
            }
        }
    }

    private void getWiFiScanList(int start, int count) {

        Log.d(TAG, "Getting " + count + " SSIDs");

        if (count <= 0) {
            completeWifiList();
            return;
        }

        byte[] data = Messenger.prepareGetWiFiScanListMsg(start, count);
        session.sendDataToDevice(LibConstants.HANDLER_PROV_SCAN, data, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {
                Log.d(TAG, "Successfully got SSID list");
                processGetSSIDs(returnData);
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                if (wifiScanListener != null) {
                    wifiScanListener.onWiFiScanFailed(new RuntimeException("Failed to get Wi-Fi Networks."));
                }
            }
        });
    }

    private void completeWifiList() {

        if (wifiScanListener != null) {
            wifiScanListener.onWifiListReceived(wifiApList);
        }
    }

    private void sendWiFiConfig(final String ssid, final String passphrase, final ProvisionListener provisionListener) {

        byte[] scanCommand = Messenger.prepareWiFiConfigMsg(ssid, passphrase);

        session.sendDataToDevice(LibConstants.HANDLER_PROV_CONFIG, scanCommand, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Constants.Status status = processWifiConfigResponse(returnData);
                if (provisionListener != null) {
                    if (status != Constants.Status.Success) {
                        provisionListener.wifiConfigFailed(new RuntimeException("Failed to send wifi credentials to device"));
                    } else {
                        provisionListener.wifiConfigSent();
                    }
                }

                if (status == Constants.Status.Success) {
                    applyWiFiConfig();
                }
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                if (provisionListener != null) {
                    provisionListener.wifiConfigFailed(new RuntimeException("Failed to send wifi credentials to device"));
                }
            }
        });
    }

    private void applyWiFiConfig() {

        byte[] scanCommand = Messenger.prepareApplyWiFiConfigMsg();

        session.sendDataToDevice(LibConstants.HANDLER_PROV_CONFIG, scanCommand, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Constants.Status status = processApplyConfigResponse(returnData);

                if (status == Constants.Status.Success) {
                    if (provisionListener != null) {
                        provisionListener.wifiConfigApplied();
                    }

                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    pollForWifiConnectionStatus();
                } else {
                    if (provisionListener != null) {
                        provisionListener.wifiConfigApplyFailed(new RuntimeException("Failed to apply wifi credentials"));
                    }
                }
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                if (provisionListener != null) {
                    provisionListener.wifiConfigApplyFailed(new RuntimeException("Failed to apply wifi credentials"));
                }
            }
        });
    }

    private void pollForWifiConnectionStatus() {

        byte[] message = Messenger.prepareGetWiFiConfigStatusMsg();
        session.sendDataToDevice(LibConstants.HANDLER_PROV_CONFIG, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Object[] statuses = processProvisioningStatusResponse(returnData);
                WifiConstants.WifiStationState wifiStationState = (WifiConstants.WifiStationState) statuses[0];
                WifiConstants.WifiConnectFailedReason failedReason = (WifiConstants.WifiConnectFailedReason) statuses[1];

                if (wifiStationState == WifiConstants.WifiStationState.Connected) {

                    // Provision success
                    if (provisionListener != null) {
                        provisionListener.deviceProvisioningSuccess();
                    }
                    session = null;
                    disableOnlyWifiNetwork();

                } else if (wifiStationState == WifiConstants.WifiStationState.Disconnected) {

                    // Device disconnected but Provision may got success / failure
                    if (provisionListener != null) {
                        provisionListener.provisionStatusUpdate(LibConstants.ProvisionFailureReason.DEVICE_DISCONNECTED);
                    }

                } else if (wifiStationState == WifiConstants.WifiStationState.Connecting) {

                    try {
                        sleep(5000);
                        pollForWifiConnectionStatus();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        provisionListener.onProvisioningFailed(new RuntimeException("Change error msg"));
                    }
                } else {

                    if (failedReason == WifiConstants.WifiConnectFailedReason.AuthError) {

                        provisionListener.provisionStatusUpdate(LibConstants.ProvisionFailureReason.AUTH_FAILED);

                    } else if (failedReason == WifiConstants.WifiConnectFailedReason.NetworkNotFound) {

                        provisionListener.provisionStatusUpdate(LibConstants.ProvisionFailureReason.NETWORK_NOT_FOUND);

                    } else {
                        provisionListener.provisionStatusUpdate(LibConstants.ProvisionFailureReason.UNKNOWN);
                    }
                }
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                provisionListener.onProvisioningFailed(new RuntimeException("Change error msg"));
            }
        });
    }

    private void processStartScanResponse(byte[] responseData) {

        try {
            WifiScan.WiFiScanPayload payload = WifiScan.WiFiScanPayload.parseFrom(responseData);
            WifiScan.RespScanStart response = WifiScan.RespScanStart.parseFrom(payload.toByteArray());
            // TODO Proto should send status as ok started or failed
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    private void processWifiStatusResponse(byte[] responseData) {

        try {
            WifiScan.WiFiScanPayload payload = WifiScan.WiFiScanPayload.parseFrom(responseData);
            WifiScan.RespScanStatus response = payload.getRespScanStatus();
            boolean scanFinished = response.getScanFinished();

            if (scanFinished) {
                totalCount = response.getResultCount();
                getFullWiFiList();
            } else {
                // TODO Error case
            }

        } catch (InvalidProtocolBufferException e) {

            e.printStackTrace();
            if (wifiScanListener != null) {
                wifiScanListener.onWiFiScanFailed(new RuntimeException("Failed to get Wi-Fi status."));
            }
        }
    }

    private void processGetSSIDs(byte[] responseData) {

        try {
            WifiScan.WiFiScanPayload payload = WifiScan.WiFiScanPayload.parseFrom(responseData);
            final WifiScan.RespScanResult response = payload.getRespScanResult();

            Log.e(TAG, "Response count : " + response.getEntriesCount());

            for (int i = 0; i < response.getEntriesCount(); i++) {

                Log.e(TAG, "SSID : " + response.getEntries(i).getSsid().toStringUtf8());
                String ssid = response.getEntries(i).getSsid().toStringUtf8();
                int rssi = response.getEntries(i).getRssi();
                boolean isAvailable = false;

                for (int index = 0; index < wifiApList.size(); index++) {

                    if (ssid.equals(wifiApList.get(index).getWifiName())) {

                        isAvailable = true;

                        if (wifiApList.get(index).getRssi() < rssi) {

                            wifiApList.get(index).setRssi(rssi);
                        }
                        break;
                    }
                }

                if (!isAvailable) {

                    WiFiAccessPoint wifiAp = new WiFiAccessPoint();
                    wifiAp.setWifiName(ssid);
                    wifiAp.setRssi(response.getEntries(i).getRssi());
                    wifiAp.setSecurity(response.getEntries(i).getAuthValue());
                    wifiApList.add(wifiAp);
                }

                Log.e(TAG, "Size of  list : " + wifiApList.size());
            }

            startIndex = startIndex + 4;

            int temp = totalCount - startIndex;

            if (temp > 0) {

                getFullWiFiList();

            } else {

                Log.e(TAG, "Wi-Fi LIST Completed");
                completeWifiList();
            }
        } catch (InvalidProtocolBufferException e) {

            e.printStackTrace();
        }
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

    private Constants.Status processWifiConfigResponse(byte[] responseData) {

        Constants.Status status = Constants.Status.InvalidSession;
        try {
            WifiConfig.WiFiConfigPayload wiFiConfigPayload = WifiConfig.WiFiConfigPayload.parseFrom(responseData);
            status = wiFiConfigPayload.getRespSetConfig().getStatus();
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
    }

    private Constants.Status processApplyConfigResponse(byte[] responseData) {
        Constants.Status status = Constants.Status.InvalidSession;
        try {
            WifiConfig.WiFiConfigPayload wiFiConfigPayload = WifiConfig.WiFiConfigPayload.parseFrom(responseData);
            status = wiFiConfigPayload.getRespApplyConfig().getStatus();
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
    }

    private Object[] processProvisioningStatusResponse(byte[] responseData) {

        WifiConstants.WifiStationState wifiStationState = WifiConstants.WifiStationState.Disconnected;
        WifiConstants.WifiConnectFailedReason failedReason = WifiConstants.WifiConnectFailedReason.UNRECOGNIZED;

        if (responseData == null) {
            return new Object[]{wifiStationState, failedReason};
        }

        try {
            WifiConfig.WiFiConfigPayload wiFiConfigPayload = WifiConfig.WiFiConfigPayload.parseFrom(responseData);
            wifiStationState = wiFiConfigPayload.getRespGetStatus().getStaState();
            failedReason = wiFiConfigPayload.getRespGetStatus().getFailReason();
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return new Object[]{wifiStationState, failedReason};
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
}
