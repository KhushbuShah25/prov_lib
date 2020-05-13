package com.espressif.provisioningap;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.espressif.provision.LibConstants;
import com.espressif.provision.Provision;
import com.espressif.provision.WiFiAccessPoint;
import com.espressif.provision.WiFiScanListener;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

public class WiFiScanActivity extends AppCompatActivity {

    private static final String TAG = WiFiScanActivity.class.getSimpleName();

    private ProgressBar progressBar;
    private ArrayList<WiFiAccessPoint> wifiAPList;
    private WiFiListAdapter adapter;
    private ListView wifiListView;
    private ImageView ivRefresh;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_scan_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_wifi_scan_list);
        setSupportActionBar(toolbar);

        ivRefresh = findViewById(R.id.btn_refresh);
        wifiListView = findViewById(R.id.wifi_ap_list);
        progressBar = findViewById(R.id.wifi_progress_indicator);

        progressBar.setVisibility(View.VISIBLE);

        wifiAPList = new ArrayList<>();
        handler = new Handler();
        ivRefresh.setOnClickListener(refreshClickListener);

        adapter = new WiFiListAdapter(this, R.id.tv_wifi_name, wifiAPList);

        // Assign adapter to ListView
        wifiListView.setAdapter(adapter);
        wifiListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {

                Log.d(TAG, "Device to be connected -" + wifiAPList.get(pos));
                askForNetwork(wifiAPList.get(pos).getWifiName(), wifiAPList.get(pos).getSecurity());
            }
        });

        wifiListView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {

            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            }
        });

        startWifiScan();
    }

    @Override
    public void onBackPressed() {
        BLEProvisionLanding.isBleWorkDone = true;
        super.onBackPressed();
    }

    private void startWifiScan() {

        Log.d(TAG, "Start Wi-Fi Scan");
        wifiAPList.clear();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateProgressAndScanBtn(true);
            }
        });
        handler.postDelayed(stopScanningTask, 15000);

        Provision provisionLib = Provision.getProvisionInstance(getApplicationContext());
        provisionLib.scanNetworks(new WiFiScanListener() {

            @Override
            public void onWifiListReceived(final ArrayList<WiFiAccessPoint> wifiList) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        wifiAPList.addAll(wifiList);
                        completeWifiList();
                    }
                });
            }

            @Override
            public void onWiFiScanFailed(Exception e) {

                // TODO
                Log.e(TAG, "onWiFiScanFailed");
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateProgressAndScanBtn(false);
                        Toast.makeText(WiFiScanActivity.this, "Failed to get Wi-Fi scan list", Toast.LENGTH_SHORT).show();
                    }
                });

//                String statusText = getResources().getString(R.string.error_pop_incorrect);
//                finish();
//                Intent goToSuccessPage = new Intent(getApplicationContext(), ProvisionSuccessActivity.class);
//                goToSuccessPage.putExtra(AppConstants.KEY_STATUS_MSG, statusText);
//                goToSuccessPage.putExtras(getIntent());
//                startActivity(goToSuccessPage);
            }
        });
    }

    private void completeWifiList() {

        // Add "Join network" Option as a list item
        WiFiAccessPoint wifiAp = new WiFiAccessPoint();
        wifiAp.setWifiName(getString(R.string.join_other_network));
        wifiAPList.add(wifiAp);

        updateProgressAndScanBtn(false);
        handler.removeCallbacks(stopScanningTask);
    }

    private void askForNetwork(final String ssid, final int authMode) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_wifi_network, null);
        builder.setView(dialogView);

        final EditText etSsid = dialogView.findViewById(R.id.et_ssid);
        final EditText etPassword = dialogView.findViewById(R.id.et_password);

        if (ssid.equals(getString(R.string.join_other_network))) {

            builder.setTitle(R.string.dialog_title_network_info);

        } else {

            builder.setTitle(ssid);
            etSsid.setVisibility(View.GONE);
        }

        builder.setPositiveButton(R.string.btn_provision, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                String password = etPassword.getText().toString();

                if (ssid.equals(getString(R.string.join_other_network))) {

                    String networkName = etSsid.getText().toString();

                    if (TextUtils.isEmpty(networkName)) {

                        etSsid.setError(getString(R.string.error_ssid_empty));

                    } else {

                        dialog.dismiss();
                        goForProvisioning(networkName, password);
                    }

                } else {

                    if (TextUtils.isEmpty(password)) {

                        if (authMode != LibConstants.WIFI_OPEN) {

                            TextInputLayout passwordLayout = dialogView.findViewById(R.id.layout_password);
                            passwordLayout.setError(getString(R.string.error_password_empty));

                        } else {

                            dialog.dismiss();
                            goForProvisioning(ssid, password);
                        }

                    } else {

                        if (authMode == LibConstants.WIFI_OPEN) {
                            password = "";
                        }
                        dialog.dismiss();
                        goForProvisioning(ssid, password);
                    }
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

    private void goForProvisioning(String ssid, String password) {

        finish();
        Intent provisionIntent = new Intent(getApplicationContext(), ProvisionActivity.class);
        provisionIntent.putExtras(getIntent());
        provisionIntent.putExtra(AppConstants.KEY_WIFI_SSID, ssid);
        provisionIntent.putExtra(AppConstants.KEY_WIFI_PASSWORD, password);
        startActivity(provisionIntent);
    }

    private View.OnClickListener refreshClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            startWifiScan();
        }
    };

    private Runnable stopScanningTask = new Runnable() {

        @Override
        public void run() {

            updateProgressAndScanBtn(false);
        }
    };

    /**
     * This method will update UI (Scan button enable / disable and progressbar visibility)
     */
    private void updateProgressAndScanBtn(boolean isScanning) {

        if (isScanning) {

            progressBar.setVisibility(View.VISIBLE);
            wifiListView.setVisibility(View.GONE);
            ivRefresh.setVisibility(View.GONE);

        } else {

            progressBar.setVisibility(View.GONE);
            wifiListView.setVisibility(View.VISIBLE);
            ivRefresh.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }
}
