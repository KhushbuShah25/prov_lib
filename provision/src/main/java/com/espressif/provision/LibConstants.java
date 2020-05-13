package com.espressif.provision;

public class LibConstants {

    public static final String DEFAULT_WIFI_BASE_URL = "192.168.4.1:80";

    public enum TransportType {
        TRANSPORT_BLE,
        TRANSPORT_SOFTAP
    }

    public enum SecurityType {
        SECURITY_0,
        SECURITY_1
    }

    public enum ProvisionFailureReason {
        AUTH_FAILED,
        NETWORK_NOT_FOUND,
        DEVICE_DISCONNECTED,
        UNKNOWN
    }

    // End point names
    public static final String HANDLER_PROV_SCAN = "prov-scan";
    public static final String HANDLER_PROTO_VER = "proto-ver";
    public static final String HANDLER_PROV_SESSION = "prov-session";
    public static final String HANDLER_PROV_CONFIG = "prov-config";
    public static final String HANDLER_CLOUD_USER_ASSOC = "cloud_user_assoc";

    // Event types
    public static final short EVENT_DEVICE_CONNECTED = 1;
    public static final short EVENT_DEVICE_CONNECTION_FAILED = 2;
    public static final short EVENT_DEVICE_COMMUNICATION_FAILED = 3;
    public static final short EVENT_DEVICE_DISCONNECTED = 4;

    // Constants for WiFi Security values (As per proto files)
    public static final short WIFI_OPEN = 0;
    public static final short WIFI_WEP = 1;
    public static final short WIFI_WPA_PSK = 2;
    public static final short WIFI_WPA2_PSK = 3;
    public static final short WIFI_WPA_WPA2_PSK = 4;
    public static final short WIFI_WPA2_ENTERPRISE = 5;

    // Keys
    public static final String KEY_WIFI_AP_LIST = "wifi_ap_list";
}
