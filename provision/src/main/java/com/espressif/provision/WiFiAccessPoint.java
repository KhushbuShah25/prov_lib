package com.espressif.provision;

import android.os.Parcel;
import android.os.Parcelable;

public class WiFiAccessPoint implements Parcelable {

    private String wifiName; // SSID
    private int rssi;
    private int security;

    public String getWifiName() {
        return wifiName;
    }

    public void setWifiName(String wifiName) {
        this.wifiName = wifiName;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public int getSecurity() {
        return security;
    }

    public void setSecurity(int security) {
        this.security = security;
    }

    public WiFiAccessPoint() {
    }

    protected WiFiAccessPoint(Parcel in) {

        wifiName = in.readString();
        rssi = in.readInt();
        security = in.readInt();
    }

    public static final Creator<WiFiAccessPoint> CREATOR = new Creator<WiFiAccessPoint>() {
        @Override
        public WiFiAccessPoint createFromParcel(Parcel in) {
            return new WiFiAccessPoint(in);
        }

        @Override
        public WiFiAccessPoint[] newArray(int size) {
            return new WiFiAccessPoint[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        dest.writeString(wifiName);
        dest.writeInt(rssi);
        dest.writeInt(security);
    }
}
