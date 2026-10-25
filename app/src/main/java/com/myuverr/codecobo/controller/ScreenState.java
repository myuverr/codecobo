// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.controller;

import com.myuverr.codecobo.platform.BluetoothRepository;
import androidx.annotation.StringRes;
import java.util.List;

public record ScreenState(List<BluetoothRepository.DeviceInfo> devices, String selectedAddress,
    BluetoothRepository.Snapshot snapshot, boolean busy, boolean bluetoothPermission, AccessMode accessMode,
    boolean shizukuReady, @StringRes int messageId, OperationFailure operationFailure, String detail,
    long editorRevision, boolean savePending, SubmissionFeedback submissionFeedback) {
    public enum AccessMode {
        SYSTEM,
        AUTHORIZING,
        SHIZUKU,
        AUTHORIZATION_FAILED;

        public boolean requested() {
            return this == AUTHORIZING || this == SHIZUKU;
        }
    }
    public enum OperationFailure { NONE, RESTORE, PROFILE, PROFILE_CHANGED }

    public boolean shizukuMode() {
        return accessMode.requested();
    }
    public boolean modeAvailable() {
        return !shizukuMode() || shizukuReady;
    }
    public boolean canPrepareEdit() {
        return !busy && bluetoothPermission && modeAvailable() && selectedAddress != null
            && devices.stream().anyMatch(d -> d.address().equals(selectedAddress));
    }
    public boolean canEdit() {
        return canPrepareEdit() && snapshot != null && !snapshot.capabilities().isEmpty();
    }
}
