package com.espressif.provisioningap;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.espressif.provision.DeviceProvEvent;
import com.espressif.provision.ESPDevice;
import com.espressif.provision.ESPProvisionManager;
import com.espressif.provision.ESPConstants;
import com.espressif.provision.listeners.QRCodeScanListener;
import com.wang.avi.AVLoadingIndicatorView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

public class AddDeviceActivity extends AppCompatActivity {

    private static final String TAG = AddDeviceActivity.class.getSimpleName();

    private static final int REQUEST_CAMERA_PERMISSION = 201;
    private static final int REQUEST_ACCESS_FINE_LOCATION = 202;

    private TextView tvTitle, tvBack, tvCancel;
    private CardView btnAddManually;
    private TextView txtAddManuallyBtn;

    private SurfaceView surfaceView;
    private AVLoadingIndicatorView loader;
    private Intent intent;
    private ESPDevice espDevice;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_device);
        intent = new Intent();
        initViews();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
//        if (cameraSource != null) {
//            try {
//                cameraSource.release();
//            } catch (NullPointerException ignored) {
//            }
//            cameraSource = null;
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {

        hideLoading();
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Log.e(TAG, "onRequestPermissionsResult , requestCode : " + requestCode);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {

            initialiseDetectorsAndSources();

        } else if (requestCode == REQUEST_ACCESS_FINE_LOCATION) {
            
            initialiseDetectorsAndSources();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(DeviceProvEvent event) {

        Log.d(TAG, "ON Device Prov Event RECEIVED : " + event.getEventType());

        switch (event.getEventType()) {

            case ESPConstants.EVENT_DEVICE_CONNECTED:

                Log.e(TAG, "Device Connected Event Received");
                ArrayList<String> deviceCaps = espDevice.getDeviceCapabilities();

                if (deviceCaps.contains("wifi_scan")) {

                    goToWifiScanListActivity();

                } else {

                    goToProvisionActivity();
                }
                break;

            case ESPConstants.EVENT_DEVICE_DISCONNECTED:
                Toast.makeText(AddDeviceActivity.this, "Device disconnected", Toast.LENGTH_SHORT).show();
                break;

            case ESPConstants.EVENT_DEVICE_CONNECTION_FAILED:
                Toast.makeText(AddDeviceActivity.this, "Failed to connect with device", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    View.OnClickListener btnAddManuallyClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            SharedPreferences sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
            final boolean isSec1 = sharedPreferences.getBoolean("security_type", true);
            Log.e(TAG, "isSec1 : " + isSec1);
            int securityType = 0;
            if (isSec1) {
                securityType = 1;
            }

            ESPProvisionManager provisionManager = ESPProvisionManager.getInstance(getApplicationContext());
            if (isSec1) {
                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_1);
            } else {
                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_0);
            }
            finish();
            Intent intent1 = new Intent(getApplicationContext(), WiFiProvisionLanding.class);
            intent1.putExtra("security_type", securityType);
            startActivity(intent1);
        }
    };

    private View.OnClickListener cancelBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            setResult(RESULT_CANCELED, intent);
            finish();
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);

        tvTitle.setText(R.string.title_activity_add_device);
        tvBack.setVisibility(View.GONE);
        tvCancel.setVisibility(View.VISIBLE);
        tvCancel.setOnClickListener(cancelBtnClickListener);

        surfaceView = findViewById(R.id.surfaceView);
        btnAddManually = findViewById(R.id.btn_add_device_manually);
        txtAddManuallyBtn = findViewById(R.id.text_btn);
        loader = findViewById(R.id.loader);

        txtAddManuallyBtn.setText(R.string.btn_no_qr_code);
        btnAddManually.setOnClickListener(btnAddManuallyClickListener);
        initialiseDetectorsAndSources();
    }

    private void initialiseDetectorsAndSources() {

        if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            ESPProvisionManager provisionManager = ESPProvisionManager.getInstance(getApplicationContext());
            provisionManager.scanQRCode(this, surfaceView, qrCodeScanListener);
            surfaceView.setVisibility(View.VISIBLE);

        } else {
            Log.e(TAG, "All permissions are not granted.");
            askForPermissions();
        }
    }

    private void askForPermissions() {

        if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(AddDeviceActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);

        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(AddDeviceActivity.this, new
                    String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    private void showLoading() {
        loader.setVisibility(View.VISIBLE);
        loader.show();
    }

    private void hideLoading() {
        loader.hide();
    }

    private QRCodeScanListener qrCodeScanListener = new QRCodeScanListener() {

        @Override
        public void qrCodeScanned() {

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    showLoading();
                }
            });
        }

        @Override
        public void deviceDetected(ESPDevice device) {

            espDevice = device;
            if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            device.connectToDevice(AddDeviceActivity.this);
        }

        @Override
        public void onFailure(final Exception e) {

            Log.e(TAG, "Error : " + e.getMessage());
            e.printStackTrace();

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    hideLoading();
                    Toast.makeText(AddDeviceActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }
    };

    private void goToWifiScanListActivity() {

        finish();
        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        wifiListIntent.putExtras(getIntent());
        wifiListIntent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, espDevice.getProofOfPossession());
        startActivity(wifiListIntent);
    }

    private void goToProvisionActivity() {

        finish();
        Intent provisionIntent = new Intent(getApplicationContext(), ProvisionActivity.class);
        provisionIntent.putExtras(getIntent());
        provisionIntent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, espDevice.getProofOfPossession());
        startActivity(provisionIntent);
    }
}
