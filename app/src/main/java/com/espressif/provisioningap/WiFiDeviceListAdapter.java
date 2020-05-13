package com.espressif.provisioningap;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class WiFiDeviceListAdapter extends ArrayAdapter<ScanResult> {

    private Context context;
    private ArrayList<ScanResult> wifiDevices;

    public WiFiDeviceListAdapter(Context context, int resource, ArrayList<ScanResult> wifiDevices) {
        super(context, resource, wifiDevices);
        this.context = context;
        this.wifiDevices = wifiDevices;
    }

    public View getView(int position, View convertView, ViewGroup parent) {

        ScanResult scanResult = wifiDevices.get(position);

        //get the inflater and inflate the XML layout for each item
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.item_ble_scan, null);

        TextView bleDeviceNameText = view.findViewById(R.id.tv_ble_device_name);
        bleDeviceNameText.setText(scanResult.SSID);

        return view;
    }
}
