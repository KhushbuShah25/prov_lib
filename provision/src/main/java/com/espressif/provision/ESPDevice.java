package com.espressif.provision;

import android.content.Context;
import android.util.Log;

import com.espressif.provision.listeners.ProvisionListener;
import com.espressif.provision.listeners.WiFiScanListener;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.security.Security1;
import com.espressif.provision.transport.BLETransport;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;

import espressif.Constants;
import espressif.WifiConfig;
import espressif.WifiConstants;
import espressif.WifiScan;

import static java.lang.Thread.sleep;

public class ESPDevice {

    private static final String TAG = ESPDevice.class.getSimpleName();

    private Context context;

    private Session session;
    private static Security security;
    private static Transport transport;

    private WiFiScanListener wifiScanListener;
    private ProvisionListener provisionListener;
    private LibConstants.TransportType transportType;
    private LibConstants.SecurityType securityType;

    private String proofOfPossession;
    private int totalCount;
    private int startIndex;
    private ArrayList<WiFiAccessPoint> wifiApList;

    public ESPDevice(Context context, LibConstants.TransportType transportType, LibConstants.SecurityType securityType) {

        this.context = context;
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

    public void setProofOfPossession(String pop) {
        this.proofOfPossession = pop;
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
                    // TODO
//                    session = null;
//                    disableOnlyWifiNetwork();

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
}
