// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import com.myuverr.codecobo.R;
import com.myuverr.codecobo.controller.CodecController;
import com.myuverr.codecobo.controller.ScreenState;
import com.myuverr.codecobo.core.CodecRequest;
import com.myuverr.codecobo.core.SubmissionIntent;
import com.myuverr.codecobo.platform.*;
import com.myuverr.codecobo.ui.components.FloatingOverlayState;
import com.myuverr.codecobo.ui.management.ManagementLoader;
import com.myuverr.codecobo.ui.management.ManagementReader;
import com.myuverr.codecobo.ui.theme.SystemChromeKt;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity
    extends ComponentActivity implements HomePanel.Actions, LibraryPanel.Actions, SettingsPanel.Actions {
    private static final int BLUETOOTH_PERMISSION = 10;
    private static final AtomicInteger ASSOCIATION_CODES = new AtomicInteger(1000);
    private final String uiTraceId = RestoreTrace.id();
    private int associationCode;
    private CodecController controller;
    private HomePanel home;
    private LibraryPanel library;
    private SettingsPanel settings;
    private MainNavigation navigation;
    private final FloatingOverlayState floatingOverlay = new FloatingOverlayState();
    private ManagementLoader management;
    private StatusNotificationControl notificationControl;
    private ProfileStore profiles;
    private androidx.lifecycle.LiveData<ProfileFacts> profileFacts;
    private FrameLayout content;
    private PageTransitions pageTransitions;
    private boolean closed;
    private ScreenState.AccessMode lastAccessMode;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        SystemChromeKt.prepareWindowTheme(this);
        RestoreTrace.event(this, uiTraceId, "activity", "activity.created", "");
        setContentView(R.layout.activity_main);
        findViewById(R.id.root).setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars =
                insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        content = findViewById(R.id.content);
        pageTransitions = new PageTransitions(content);
        profiles = new ProfileStore(this);
        home = new HomePanel(this, this, floatingOverlay);
        library = new LibraryPanel(this, this, floatingOverlay);
        settings = new SettingsPanel(this, this, floatingOverlay);
        if (saved != null) {
            home.restore(saved);
            library.restore(saved);
            settings.restore(saved);
        }
        controller =
            new CodecController(this, saved == null ? null : saved.getString("selectedDevice"), this::render);
        notificationControl = new StatusNotificationControl(this, saved, value -> {
            settings.renderNotification(value);
            if (management != null)
                management.refresh();
        });
        management = new ManagementLoader(new ManagementReader(this), data -> {
            if (closed)
                return;
            library.renderData(data);
            settings.renderData(data);
        });
        management.refresh();
        profileFacts = profiles.facts();
        profileFacts.observe(this, facts -> {
            if (!closed) {
                render(controller.state());
                management.refresh();
            }
        });
        navigation = new MainNavigation(findViewById(R.id.navigation), page -> {
            if (page == MainNavigation.Page.HEADPHONES)
                library.showRoot();
            if (page == MainNavigation.Page.SETTINGS)
                settings.showRoot();
            showPage(page);
        }, floatingOverlay);
        MainNavigation.Page page = MainNavigation.Page.CONTROL;
        if (saved != null) {
            try {
                page = MainNavigation.Page.valueOf(saved.getString("ui.page", "CONTROL"));
            } catch (IllegalArgumentException ignored) { /* Ignore invalid saved navigation state. */
            }
        }
        showPage(page);
        render(controller.state());
        if (saved == null && !getPreferences(MODE_PRIVATE).getBoolean("askedBluetooth", false))
            nearby();
    }

    void showPage(MainNavigation.Page page) {
        navigation.show(page);
        library.setActive(page == MainNavigation.Page.HEADPHONES);
        settings.setActive(page == MainNavigation.Page.SETTINGS);
        View next = switch (page) {
            case CONTROL -> home.view();
            case HEADPHONES -> library.view();
            case SETTINGS -> settings.view();
        };
        pageTransitions.show(page, next);
        if (page != MainNavigation.Page.CONTROL)
            management.refresh();
    }

    private void render(ScreenState state) {
        if (closed)
            return;
        ProfileFacts facts = profileFacts == null ? null : profileFacts.getValue();
        if (facts != null) {
            home.render(state, facts.profile(state.selectedAddress()), facts.getPaused());
            library.render(state, facts.getPaused());
        }
        settings.render(state);
        if (notificationControl != null)
            settings.renderNotification(notificationControl.state());
        if (state.accessMode() == ScreenState.AccessMode.AUTHORIZATION_FAILED
            && lastAccessMode != state.accessMode()) {
            Toast.makeText(this, getString(R.string.notice_access_denied_fallback), Toast.LENGTH_LONG).show();
        }
        lastAccessMode = state.accessMode();
    }

    @Override
    public void selectDevice(String address) {
        controller.select(address);
    }
    @Override
    public void submit(CodecRequest request, SubmissionIntent intent, boolean enableRestore) {
        controller.submit(request, intent, enableRestore, this::openAssociation, this::submissionCompleted);
    }
    @Override
    public void prepareEdit() {
        controller.prepareEdit(this::openAssociation, this::submissionCompleted);
    }
    private void submissionCompleted(String text) {
        if (closed)
            return;
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        management.refresh();
    }
    @Override
    public void refreshManagement() {
        management.refresh();
    }
    @Override
    public void mode(boolean enabled) {
        controller.setShizukuMode(enabled);
    }
    @Override
    public void notification(boolean enabled) {
        notificationControl.setEnabled(enabled);
    }
    @Override
    public void enabled(String address, boolean enabled) {
        controller.setAutoRestore(
            address, enabled, success -> submissionCompleted(getString(controller.state().messageId())));
    }
    @Override
    public void paused(boolean paused) {
        controller.pauseRestore(paused);
    }

    @Override
    public void openMode() {
        settings.showAccess();
        showPage(MainNavigation.Page.SETTINGS);
    }
    @Override
    public void openRecord(String address, int id) {
        library.openRecord(address, id);
        showPage(MainNavigation.Page.HEADPHONES);
    }
    @Override
    public void returnToRecords() {
        settings.showRecords();
        showPage(MainNavigation.Page.SETTINGS);
    }
    @Override
    public void edit(String address) {
        ScreenState state = controller.state();
        if (state.busy())
            return;
        if (state.devices().stream().noneMatch(device -> device.address().equalsIgnoreCase(address))) {
            library.showError(getString(R.string.library_connection_changed));
            return;
        }
        ProfileFacts facts = profileFacts.getValue();
        if (facts == null)
            return;
        var profile = facts.profile(address);
        home.editSaved(address, profile == null ? null : profile.request());
        showPage(MainNavigation.Page.CONTROL);
    }
    @Override
    public void remove(String address, long revision) {
        controller.removeProfile(address, revision);
    }
    @Override
    public void batterySettings() {
        String error = BatterySettings.open(this);
        if (!error.isEmpty()) {
            RestoreTrace.event(this, uiTraceId, "system_ui", "system.battery_settings_failed",
                "reason=system_ui_unavailable");
            showError(getString(R.string.notice_battery_settings_unavailable),
                getString(R.string.notice_battery_settings_manual));
        }
    }
    @Override
    public void clearDiagnostics() {
        RestoreTrace.clear(this);
        management.refresh();
    }
    @Override
    public void openProjectLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException | SecurityException error) {
            showError(getString(R.string.about_link_error_title),
                getString(R.string.about_link_error_message, url));
        }
    }
    @Override
    public void copy(String title, String text) {
        ClipData clip = ClipData.newPlainText(title, text);
        var extras = new android.os.PersistableBundle();
        extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
        clip.getDescription().setExtras(extras);
        getSystemService(ClipboardManager.class).setPrimaryClip(clip);
    }
    @Override
    public void openBluetooth() {
        openSettings(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    @Override
    protected void onResume() {
        super.onResume();
        RestoreTrace.event(this, uiTraceId, "activity", "activity.resumed", "");
        if (controller != null)
            controller.refresh();
        RestoreService.requestCheck(this, null, "activity_resumed");
        if (notificationControl != null)
            notificationControl.resume();
        if (management != null)
            management.refresh();
        DevicePresence.sync(this);
    }
    @Override
    protected void onPause() {
        if (pageTransitions != null)
            pageTransitions.finish();
        RestoreTrace.event(this, uiTraceId, "activity", "activity.paused", "");
        super.onPause();
    }

    @Override
    public void nearby() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            controller.refresh();
            return;
        }
        boolean asked = getPreferences(MODE_PRIVATE).getBoolean("askedBluetooth", false);
        if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.notice_nearby_permission_denied))
                .setMessage(getString(R.string.notice_nearby_permission_manual))
                .setPositiveButton(getString(R.string.notice_open_app_settings),
                    (dialog, which)
                        -> openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()))))
                .setNegativeButton(getString(R.string.notice_cancel), null)
                .show();
        } else {
            getPreferences(MODE_PRIVATE).edit().putBoolean("askedBluetooth", true).apply();
            requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION);
        }
    }
    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (controller != null)
            controller.refresh();
        if (code == BLUETOOTH_PERMISSION)
            RestoreService.requestCheck(this, null, "nearby_permission_returned");
        if (notificationControl != null)
            notificationControl.permissionResult(code);
        if (management != null)
            management.refresh();
    }
    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (associationCode != 0 && request == associationCode && controller != null) {
            associationCode = 0;
            controller.associationActivityResult(result == RESULT_OK);
        }
    }
    private void openAssociation(IntentSender sender) {
        // Late system results must not match a newer request.
        associationCode = ASSOCIATION_CODES.incrementAndGet();
        if (associationCode > 65535) {
            associationCode = 0;
            controller.associationActivityResult(false);
            showError(getString(R.string.notice_association_ui_unavailable),
                getString(R.string.notice_reopen_app_retry));
            return;
        }
        try {
            startIntentSenderForResult(sender, associationCode, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException | RuntimeException error) {
            associationCode = 0;
            controller.associationActivityResult(false);
            RestoreTrace.failure(this, uiTraceId, "system_ui", "system.association_ui_failed", error);
            showError(getString(R.string.notice_association_ui_unavailable),
                getString(R.string.notice_reconnect_retry));
        }
    }

    private void openSettings(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            RestoreTrace.failure(this, uiTraceId, "system_ui", "system.settings_failed", error);
            showError(getString(R.string.notice_system_settings_unavailable),
                getString(R.string.notice_system_settings_manual));
        }
    }
    private void showError(String title, String text) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton(getString(R.string.notice_close), null)
            .show();
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        home.save(out);
        library.save(out);
        settings.save(out);
        notificationControl.save(out);
        out.putString("selectedDevice", controller.state().selectedAddress());
        out.putString("ui.page", navigation.getSelectedPage().name());
    }
    @Override
    protected void onDestroy() {
        closed = true;
        if (pageTransitions != null)
            pageTransitions.close();
        if (management != null)
            management.close();
        if (notificationControl != null)
            notificationControl.close();
        if (controller != null)
            controller.close();
        super.onDestroy();
    }
}
