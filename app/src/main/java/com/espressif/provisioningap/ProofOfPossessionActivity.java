package com.espressif.provisioningap;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.espressif.provision.ESPProvisionManager;

import java.util.ArrayList;

public class ProofOfPossessionActivity extends AppCompatActivity {

    private static final String TAG = ProofOfPossessionActivity.class.getSimpleName();

    private TextView tvTitle, tvBack, tvCancel;
    private CardView btnNext;
    private TextView txtNextBtn;

    private String deviceName;
    private TextView tvPopInstruction;
    private EditText etPop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pop);

        initViews();

        deviceName = getIntent().getStringExtra(AppConstants.KEY_DEVICE_NAME);

        if (!TextUtils.isEmpty(deviceName)) {
            String popText = getString(R.string.pop_instruction) + " " + deviceName;
            tvPopInstruction.setText(popText);
        }

        btnNext.setOnClickListener(nextBtnClickListener);
        String pop = AppConstants.DEFAULT_POP;

        if (!TextUtils.isEmpty(pop)) {

            etPop.setText(pop);
            etPop.setSelection(etPop.getText().length());
        }
        etPop.requestFocus();
    }

    @Override
    public void onBackPressed() {
        BLEProvisionLanding.isBleWorkDone = true;
        super.onBackPressed();
    }

    private View.OnClickListener nextBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            final String pop = etPop.getText().toString();
            Log.d(TAG, "POP : " + pop);
            ESPProvisionManager provisionLib = ESPProvisionManager.getProvisionInstance(getApplicationContext());
            provisionLib.getEspDevice().setProofOfPossession(pop);
            ArrayList<String> deviceCaps = provisionLib.getDeviceCapabilities();

            if (deviceCaps.contains("wifi_scan")) {
                goToWiFiScanListActivity();
            } else {
                goToProvisionActivity();
            }
        }
    };

    private View.OnClickListener cancelBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            finish();
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);
        tvPopInstruction = findViewById(R.id.tv_pop);
        etPop = findViewById(R.id.et_pop);

        tvTitle.setText(R.string.title_activity_pop);
        tvBack.setVisibility(View.GONE);
        tvCancel.setVisibility(View.VISIBLE);

        tvCancel.setOnClickListener(cancelBtnClickListener);

        btnNext = findViewById(R.id.btn_next);
        txtNextBtn = findViewById(R.id.text_btn);

        txtNextBtn.setText(R.string.btn_next);
        btnNext.setOnClickListener(nextBtnClickListener);
    }

    private void goToWiFiScanListActivity() {

        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        wifiListIntent.putExtras(getIntent());
        startActivity(wifiListIntent);
        finish();
    }

    private void goToProvisionActivity() {

        Intent provisionIntent = new Intent(getApplicationContext(), ProvisionActivity.class);
        provisionIntent.putExtras(getIntent());
        startActivity(provisionIntent);
        finish();
    }
}
