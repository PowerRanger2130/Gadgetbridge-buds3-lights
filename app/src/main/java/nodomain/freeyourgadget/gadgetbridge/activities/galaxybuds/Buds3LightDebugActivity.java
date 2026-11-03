/* SPDX-License-Identifier: AGPL-3.0-or-later */
package nodomain.freeyourgadget.gadgetbridge.activities.galaxybuds;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.BuildConfig;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.galaxy_buds.Buds3LightDiagnostics;

public class Buds3LightDebugActivity extends AbstractGBActivity {
    private GBDevice device;
    private Buds3LightDiagnostics diagnostics;
    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView status, capture, preview;
    private EditText payload, repeats, delay, note;
    private Spinner flags, experiment, style;
    private CheckBox live, stopOn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pendingExport;
    private final ActivityResultLauncher<String> saveReport = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
                if (uri == null || pendingExport == null) return;
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("Unable to open destination");
                    out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                    toast("Report saved");
                } catch (Exception exception) {
                    toast("Unable to save report: " + exception.getMessage());
                } finally { pendingExport = null; }
            });
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (diagnostics == null) return;
            final GBDevice current = GBApplication.app().getDeviceManager().getDeviceByAddress(device.getAddress());
            if (current != null) device = current;
            status.setText(deviceSummary());
            if (live.isChecked()) capture.setText(diagnostics.report());
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (device == null || device.getType() != DeviceType.GALAXY_BUDS3_PRO) { finish(); return; }
        diagnostics = Buds3LightDiagnostics.forDevice(device.getAddress());
        prefs = GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
        setTitle(R.string.galaxy_buds_3_pro_debug);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(content);
        setContentView(scroll);
        status = text(content, deviceSummary());
        text(content, "Compare a normal pinch-and-hold activation with app commands. Keep both earbuds outside your ears and above 30% battery. ACKs and queued commands do not prove that LEDs lit. Capture stays in memory until the app process ends; save or share it before closing the app.");
        button(content, "Turn on, 8E 01", () -> sendPower("01"));
        button(content, "Turn off, 8E 00", () -> sendPower("00"));
        button(content, "Cancel pending experiment", () -> send(Buds3LightDiagnostics.CANCEL));

        final LinearLayout recording = section("Capture and physical observations", true);
        button(recording, "Resume capture", () -> { diagnostics.setCapturing(true); refreshNow(); });
        button(recording, "Pause capture", () -> { send(Buds3LightDiagnostics.CANCEL); diagnostics.setCapturing(false); refreshNow(); });
        button(recording, "Clear capture", () -> { send(Buds3LightDiagnostics.CANCEL); diagnostics.clear(); refreshNow(); });
        button(recording, "MARK: about to pinch and hold", () -> mark("About to perform the physical pinch-and-hold gesture"));
        button(recording, "MARK: I can see the lights ON", () -> mark("USER OBSERVED PHYSICAL LEDs ON"));
        button(recording, "MARK: I can see the lights OFF", () -> mark("USER OBSERVED PHYSICAL LEDs OFF"));
        note = edit(recording, "Firmware version / wearing / case state / observation", "", false);
        button(recording, "Add observation", () -> { mark(note.getText().toString()); note.setText(""); });
        button(recording, "Read firmware / build information", () -> start("firmware"));

        final LinearLayout experiments = section("Paced experiments", false);
        text(experiments, "One experiment at a time. Pending steps stop when you leave this screen, disconnect, or cancel. Stop on ON report is optional. Style experiments reapply your current Gadgetbridge touch-control settings. Already queued frames may still be delivered.");
        experiment = spinner(experiments, new String[]{"Send ON three times", "OFF then ON", "Apply style then ON", "ON then apply style", "OFF, apply style, ON", "Sweep 8E values 00 through 0F", "Full 8E sweep 00 through FF, at least 4 minutes"});
        style = spinner(experiments, new String[]{"Blinking", "Fade", "Steady"});
        style.setSelection(Math.max(0, Math.min(2, Integer.parseInt(prefs.getString(DeviceSettingsPreferenceConst.PREF_GALAXY_BUDS_3_PRO_EARBUD_LIGHTS, "0")))));
        stopOn = new CheckBox(this);
        stopOn.setText("Stop pending steps on a new normal ON report");
        stopOn.setChecked(true);
        experiments.addView(stopOn);
        delay = edit(experiments, "Spacing between frames, ms, 250 to 10000", "1000", true);
        button(experiments, "Run selected experiment", () -> {
            final String[] actions = {"on3", "off_on", "style_on", "on_style", "off_style_on", "sweep", "sweep_all"};
            final String action = actions[experiment.getSelectedItemPosition()];
            if (action.contains("style")) {
                prefs.edit().putString(DeviceSettingsPreferenceConst.PREF_GALAXY_BUDS_3_PRO_EARBUD_LIGHTS,
                        Integer.toString(style.getSelectedItemPosition())).apply();
            }
            start(action);
        });

        final LinearLayout editor = section("Custom lighting packet, command 8E", false);
        text(editor, "Payload may be empty or contain up to 32 hex bytes. The command stays 8E; frame length and CRC are generated automatically. The 20 response-header option is an experiment, not a known on command.");
        final Spinner presets = spinner(editor, new String[]{"01", "00", "02", "03", "10", "11", "80", "81", "FF", "01 00", "00 01", "01 01", "empty payload"});
        payload = edit(editor, "Hex payload", prefs.getString(Buds3LightDiagnostics.PAYLOAD, "01"), false);
        button(editor, "Load selected payload", () -> payload.setText(presets.getSelectedItemPosition() == presets.getCount() - 1 ? "" : presets.getSelectedItem().toString()));
        button(editor, "Load last received lighting payload", () -> {
            final byte[] previous = diagnostics.lastLightPayload();
            if (previous == null) toast("No lighting frame has been captured yet");
            else payload.setText(Buds3LightDiagnostics.hex(previous));
        });
        flags = spinner(editor, new String[]{"00, request / notification format", "20, response format"});
        repeats = edit(editor, "Repeats, 1 to 16", "1", true);
        preview = text(editor, "Preview the complete packet before sending.");
        preview.setTypeface(Typeface.MONOSPACE);
        button(editor, "Preview frame and checksum", this::previewFrame);
        button(editor, "Send custom packet", () -> {
            try {
                Buds3LightDiagnostics.parsePayload(payload.getText().toString());
                prefs.edit().putString(Buds3LightDiagnostics.PAYLOAD, payload.getText().toString())
                        .putInt(Buds3LightDiagnostics.FLAGS, flags.getSelectedItemPosition() == 0 ? 0 : 0x20)
                        .putInt(Buds3LightDiagnostics.REPEAT, number(repeats, 1, 16)).apply();
                start("custom");
            } catch (IllegalArgumentException exception) { toast(exception.getMessage()); }
        });

        final LinearLayout report = section("Live packet report and export", true);
        live = new CheckBox(this);
        live.setText("Refresh the packet report every second");
        live.setChecked(true);
        report.addView(live);
        button(report, "Refresh report", this::refreshNow);
        button(report, "Copy full report", () -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Buds3 light debug", fullReport()));
            toast("Report copied");
        });
        button(report, "Share full report", () -> {
            final Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Buds3 light protocol report")
                    .putExtra(Intent.EXTRA_TEXT, fullReport());
            startActivity(Intent.createChooser(intent, "Share Buds3 light report"));
        });
        button(report, "Save report to a file", () -> {
            pendingExport = fullReport();
            saveReport.launch("buds3-lights-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".txt");
        });
        final ScrollView logScroll = new ScrollView(this);
        report.addView(logScroll, new LinearLayout.LayoutParams(-1, dp(320)));
        capture = new TextView(this);
        capture.setTextSize(11);
        capture.setTypeface(Typeface.MONOSPACE);
        capture.setTextIsSelectable(true);
        logScroll.addView(capture);
        refreshNow();
    }

    private void sendPower(final String value) {
        prefs.edit().putString(Buds3LightDiagnostics.PAYLOAD, value)
                .putInt(Buds3LightDiagnostics.FLAGS, 0).putInt(Buds3LightDiagnostics.REPEAT, 1).apply();
        start("custom");
    }

    private void start(final String action) {
        try {
            final int spacing = delay == null ? 1000 : number(delay, 250, 10000);
            prefs.edit().putString(Buds3LightDiagnostics.EXPERIMENT, action)
                    .putInt(Buds3LightDiagnostics.DELAY, spacing)
                    .putBoolean(Buds3LightDiagnostics.STOP_ON, stopOn == null || stopOn.isChecked()).apply();
            diagnostics.setCapturing(true);
            final Bundle request = new Bundle();
            request.putString(Buds3LightDiagnostics.EXPERIMENT, action);
            request.putString(Buds3LightDiagnostics.PAYLOAD, prefs.getString(Buds3LightDiagnostics.PAYLOAD, "01"));
            request.putInt(Buds3LightDiagnostics.FLAGS, prefs.getInt(Buds3LightDiagnostics.FLAGS, 0));
            request.putInt(Buds3LightDiagnostics.REPEAT, prefs.getInt(Buds3LightDiagnostics.REPEAT, 1));
            request.putInt(Buds3LightDiagnostics.DELAY, spacing);
            request.putBoolean(Buds3LightDiagnostics.STOP_ON, stopOn == null || stopOn.isChecked());
            GBApplication.deviceService(device).onTestNewFunction(request);
        } catch (IllegalArgumentException exception) { toast(exception.getMessage()); }
    }

    private void send(final String config) { GBApplication.deviceService(device).onSendConfiguration(config); }
    private void mark(final String message) { diagnostics.note("USER: " + message); refreshNow(); }
    private void refreshNow() { if (capture != null) capture.setText(diagnostics.report()); }
    private String fullReport() { return deviceSummary() + "\n" + diagnostics.report(); }
    private String deviceSummary() {
        return "App " + BuildConfig.VERSION_NAME + " / code " + BuildConfig.VERSION_CODE
                + "\nPackage " + getPackageName() + "\nGalaxy Buds3 Pro: " + device.getState()
                + "\nFirmware: " + device.getFirmwareVersion() + " / " + device.getFirmwareVersion2()
                + "\nBattery case / left / right: " + device.getBatteryLevel(0) + " / " + device.getBatteryLevel(1) + " / " + device.getBatteryLevel(2)
                + "\nUnknown firmware or battery values are not inferred. The Bluetooth address is not added; raw device payloads are included.\n";
    }
    private void previewFrame() {
        try {
            preview.setText(Buds3LightDiagnostics.hex(Buds3LightDiagnostics.frame(0x8e,
                    Buds3LightDiagnostics.parsePayload(payload.getText().toString()), flags.getSelectedItemPosition() == 0 ? 0 : 0x20)));
        } catch (IllegalArgumentException exception) { toast(exception.getMessage()); }
    }
    private int number(final EditText field, final int min, final int max) {
        try {
            final int value = Integer.parseInt(field.getText().toString().trim());
            if (value >= min && value <= max) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Enter a number from " + min + " to " + max);
    }
    private LinearLayout section(final String title, final boolean expanded) {
        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        button(content, title + "  ▾", () -> body.setVisibility(body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        content.addView(body);
        return body;
    }
    private TextView text(final LinearLayout parent, final String value) {
        final TextView view = new TextView(this); view.setText(value); view.setPadding(0, dp(6), 0, dp(6)); parent.addView(view); return view;
    }
    private EditText edit(final LinearLayout parent, final String hint, final String value, final boolean numeric) {
        final EditText view = new EditText(this); view.setHint(hint); view.setText(value); view.setSingleLine(true);
        if (numeric) view.setInputType(InputType.TYPE_CLASS_NUMBER);
        parent.addView(view); return view;
    }
    private Spinner spinner(final LinearLayout parent, final String[] values) {
        final Spinner view = new Spinner(this);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); view.setAdapter(adapter); parent.addView(view); return view;
    }
    private void button(final LinearLayout parent, final String label, final Runnable action) {
        final Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setOnClickListener(v -> action.run()); parent.addView(view);
    }
    private int dp(final int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(final String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() { super.onResume(); if (diagnostics != null) handler.post(refresh); }
    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        if (diagnostics != null) send(Buds3LightDiagnostics.CANCEL);
        super.onPause();
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
