// Copyright 2018 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.espressif.provisioningap;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.espressif.provision.DeviceProvEvent;
import com.espressif.provision.LibConstants;
import com.espressif.provision.Provision;
import com.espressif.provision.WiFiAccessPoint;
import com.espressif.provision.device_scanner.WiFiScanListener;
import com.espressif.provision.device_scanner.WiFiScanner;
import com.google.android.material.textfield.TextInputLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

public class WiFiProvisionLanding extends AppCompatActivity {

    private static final String TAG = WiFiProvisionLanding.class.getSimpleName();

    // Request codes
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 2;
    private static final String BASE_URL = "192.168.4.1:80";

    // Time out
    private static final long SCAN_TIMEOUT = 3000;
    private static final long DEVICE_CONNECT_TIMEOUT = 20000;

    public static boolean isBleWorkDone = false;

    private Button btnScan, btnPrefix;
    private ListView listView;
    private TextView textPrefix;
    private ProgressBar progressBar;
    private RelativeLayout prefixLayout;

    private WiFiListAdapter adapter;
    private WiFiScanner wifiScanner;
    private ArrayList<WiFiAccessPoint> deviceList;
    private Handler handler;

    private int position = -1;
    private boolean isDeviceConnected = false, isConnecting = false;
    private Provision provisionLib;
    private int securityType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bleprovision_landing);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_connect_device);
        setSupportActionBar(toolbar);
        securityType = getIntent().getIntExtra("security_type", 0);

        isConnecting = false;
        isDeviceConnected = false;
        handler = new Handler();
        deviceList = new ArrayList<>();

        provisionLib = Provision.getProvisionInstance(getApplicationContext());
        initViews();
        wifiScanner = new WiFiScanner(this, wifiScanListener);
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.

        if (!isDeviceConnected && !isConnecting) {
            startScan();
        }

        if (isBleWorkDone) {
            btnScan.setVisibility(View.VISIBLE);
            startScan();
        }
    }

    @Override
    public void onBackPressed() {
        isBleWorkDone = true;
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult, requestCode : " + requestCode + ", resultCode : " + resultCode);

        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }

        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            startScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {

        switch (requestCode) {

            case REQUEST_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startScan();
                } else if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    finish();
                }
            }
            break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(DeviceProvEvent event) {

        Log.d(TAG, "ON Device Prov Event RECEIVED : " + event.getEventType());
        handler.removeCallbacks(disconnectDeviceTask);

        switch (event.getEventType()) {

            case LibConstants.EVENT_DEVICE_CONNECTED:
                Log.e(TAG, "Device Connected Event Received");
                ArrayList<String> deviceCaps = provisionLib.getDeviceCapabilities();
                progressBar.setVisibility(View.GONE);
                isConnecting = false;
                isDeviceConnected = true;

                if (deviceCaps != null && !deviceCaps.contains("no_pop") && securityType == 1) {

                    goToPopActivity();

                } else if (deviceCaps.contains("wifi_scan")) {

                    goToWifiScanListActivity();

                } else {

                    goToProvisionActivity();
                }
                break;

            case LibConstants.EVENT_DEVICE_DISCONNECTED:

                progressBar.setVisibility(View.GONE);
                isConnecting = false;
                isDeviceConnected = false;
                Toast.makeText(WiFiProvisionLanding.this, "Device disconnected", Toast.LENGTH_SHORT).show();
                break;

            case LibConstants.EVENT_DEVICE_CONNECTION_FAILED:
                progressBar.setVisibility(View.GONE);
                isConnecting = false;
                isDeviceConnected = false;
//                Toast.makeText(BLEProvisionLanding.this, "Failed to connect with device", Toast.LENGTH_SHORT).show();
                alertForDeviceNotSupported("Failed to connect with device");
                break;
        }
    }

    private void initViews() {

        btnScan = findViewById(R.id.btn_scan);
        listView = findViewById(R.id.ble_devices_list);
        progressBar = findViewById(R.id.ble_landing_progress_indicator);
        prefixLayout = findViewById(R.id.prefix_layout);
        prefixLayout.setVisibility(View.GONE);

        adapter = new WiFiListAdapter(this, R.layout.item_ble_scan, deviceList);

        // Assign adapter to ListView
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(onDeviceCLickListener);

        btnScan.setOnClickListener(btnScanClickListener);
    }

    private boolean hasPermissions() {

        if (!hasLocationPermissions()) {

            requestLocationPermission();
            return false;
        }
        return true;
    }

    private boolean hasLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
        }
    }

    private void startScan() {

        if (!hasPermissions() || wifiScanner.isScanning()) {
            return;
        }

        deviceList.clear();
        wifiScanner.startScan();
        updateProgressAndScanBtn();
    }

    private void stopScan() {

        updateProgressAndScanBtn();

        if (deviceList.size() <= 0) {

            Toast.makeText(WiFiProvisionLanding.this, R.string.error_no_ble_device, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * This method will update UI (Scan button enable / disable and progressbar visibility)
     */
    private void updateProgressAndScanBtn() {

        if (wifiScanner.isScanning()) {

            btnScan.setEnabled(false);
            btnScan.setAlpha(0.5f);
            btnScan.setTextColor(Color.WHITE);
            progressBar.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);

        } else {

            btnScan.setEnabled(true);
            btnScan.setAlpha(1f);
            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
    }

    private void alertForDeviceNotSupported(String msg) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        builder.setTitle(R.string.error_title);
        builder.setMessage(msg);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                finish();
            }
        });

        builder.show();
    }

    private View.OnClickListener btnScanClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            adapter.clear();
            startScan();
        }
    };

    private WiFiScanListener wifiScanListener = new WiFiScanListener() {

        @Override
        public void scanCompleted(ArrayList<WiFiAccessPoint> scanResults) {

            deviceList.addAll(scanResults);
            listView.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
            updateProgressAndScanBtn();
        }

        @Override
        public void onFailure(Exception e) {
            e.printStackTrace();
        }
    };

    private AdapterView.OnItemClickListener onDeviceCLickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {

            isConnecting = true;
            isDeviceConnected = false;
            btnScan.setVisibility(View.GONE);
            listView.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            WiFiProvisionLanding.this.position = position;
            WiFiAccessPoint wifiDevice = adapter.getItem(position);
            Log.d(TAG, "=================== Connect to device : " + wifiDevice.getWifiName());

            if (wifiDevice.getSecurity() != LibConstants.WIFI_OPEN) {
                provisionLib.connectWiFiDevice(BASE_URL, wifiDevice.getWifiName(), "");
            } else {
                askForNetwork(wifiDevice.getWifiName(), wifiDevice.getSecurity());
            }

//            handler.postDelayed(disconnectDeviceTask, DEVICE_CONNECT_TIMEOUT);
        }
    };

    private Runnable disconnectDeviceTask = new Runnable() {

        @Override
        public void run() {
            Log.e(TAG, "Disconnect device");
            progressBar.setVisibility(View.GONE);
            alertForDeviceNotSupported(getString(R.string.error_device_not_supported));
        }
    };

    private void goToPopActivity() {

        Intent popIntent = new Intent(getApplicationContext(), ProofOfPossessionActivity.class);
        popIntent.putExtras(getIntent());
//        popIntent.putExtra(AppConstants.KEY_DEVICE_NAME, deviceList.get(position).getName());
        startActivity(popIntent);
    }

    private void goToWifiScanListActivity() {

        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        wifiListIntent.putExtras(getIntent());
        wifiListIntent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, "");
//        wifiListIntent.putExtra(AppConstants.KEY_DEVICE_NAME, deviceList.get(position).getName());
        startActivity(wifiListIntent);
    }

    private void goToProvisionActivity() {

        Intent provisionIntent = new Intent(getApplicationContext(), ProvisionActivity.class);
        provisionIntent.putExtras(getIntent());
        provisionIntent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, "");
//        provisionIntent.putExtra(AppConstants.KEY_DEVICE_NAME, deviceList.get(position).getName());
        startActivity(provisionIntent);
    }

    private void askForNetwork(final String ssid, final int authMode) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_wifi_network, null);
        builder.setView(dialogView);

        final EditText etSsid = dialogView.findViewById(R.id.et_ssid);
        final EditText etPassword = dialogView.findViewById(R.id.et_password);

        builder.setTitle(ssid);
        etSsid.setVisibility(View.GONE);

        builder.setPositiveButton(R.string.btn_provision, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                String password = etPassword.getText().toString();

                if (TextUtils.isEmpty(password)) {

                    if (authMode != LibConstants.WIFI_OPEN) {

                        TextInputLayout passwordLayout = dialogView.findViewById(R.id.layout_password);
                        passwordLayout.setError(getString(R.string.error_password_empty));

                    } else {

                        dialog.dismiss();
                        provisionLib.connectWiFiDevice(BASE_URL, ssid, password);
                    }

                } else {

                    if (authMode == LibConstants.WIFI_OPEN) {
                        password = "";
                    }
                    dialog.dismiss();
                    provisionLib.connectWiFiDevice(BASE_URL, ssid, password);
                }
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
}
