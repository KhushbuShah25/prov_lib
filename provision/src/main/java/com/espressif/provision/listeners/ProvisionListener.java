package com.espressif.provision.listeners;

import com.espressif.provision.LibConstants;

public interface ProvisionListener {

    void createSessionFailed(Exception e);

    void wifiConfigSent();

    void wifiConfigFailed(Exception e);

    void wifiConfigApplied();

    void wifiConfigApplyFailed(Exception e);

    void provisionStatusUpdate(LibConstants.ProvisionFailureReason failureReason);

    void deviceProvisioningSuccess();

    void onProvisioningFailed(Exception e);
}
