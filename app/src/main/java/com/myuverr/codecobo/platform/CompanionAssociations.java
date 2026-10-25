// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;

import android.companion.*;
import android.content.Context;
import android.content.IntentSender;
import android.os.Handler;
import android.os.Looper;
import java.util.function.Consumer;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

/** CDM associations are separate from Bluetooth pairing. */
public final class CompanionAssociations {
    private final CompanionDeviceManager manager;
    private final Context app;
    public CompanionAssociations(Context context) {
        app = context.getApplicationContext();
        manager = context.getSystemService(CompanionDeviceManager.class);
    }
    public boolean associated(String address) {
        if (manager == null || address == null)
            return false;
        return manager.getMyAssociations().stream().anyMatch(a
            -> normalAssociation(a) && a.getDeviceMacAddress() != null
                && address.equalsIgnoreCase(a.getDeviceMacAddress().toString()));
    }
    static boolean normalAssociation(AssociationInfo info) {
        if (android.os.Build.VERSION.SDK_INT >= 35)
            return !info.isSelfManaged();
        try {
            return !((Boolean) HiddenApiBypass.invoke(AssociationInfo.class, info, "isSelfManaged"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }
    public void request(
        String address, Consumer<IntentSender> pending, Runnable created, Consumer<String> result) {
        if (manager == null) {
            result.accept("Companion device service unavailable");
            return;
        }
        BluetoothDeviceFilter filter = new BluetoothDeviceFilter.Builder().setAddress(address).build();
        AssociationRequest request =
            new AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(true).build();
        manager.associate(request, new CompanionDeviceManager.Callback() {
            @Override
            public void onAssociationPending(IntentSender sender) {
                pending.accept(sender);
            }
            @Override
            public void onAssociationCreated(AssociationInfo association) {
                DevicePresence.sync(app);
                created.run();
            }
            @Override
            public void onFailure(CharSequence error) {
                result.accept("Device association incomplete: " + error);
            }
        }, new Handler(Looper.getMainLooper()));
    }
}
