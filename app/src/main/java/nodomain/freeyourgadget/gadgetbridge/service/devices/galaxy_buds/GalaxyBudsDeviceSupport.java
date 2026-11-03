/*  Copyright (C) 2021-2024 Arjan Schrijver, Petr Vaněk

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.galaxy_buds;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.serial.AbstractHeadphoneSerialDeviceSupportV2;

public class GalaxyBudsDeviceSupport extends AbstractHeadphoneSerialDeviceSupportV2<GalaxyBudsProtocol> {
    private final Handler debugHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger debugGeneration = new AtomicInteger();
    private volatile boolean debugRunning;
    private volatile long experimentOnReports;
    private volatile boolean stopOnReport = true;

    private Buds3LightDiagnostics diagnostics() {
        return Buds3LightDiagnostics.forDevice(getDevice().getAddress());
    }

    @Override
    public void onSendConfiguration(final String config) {
        if (getDevice().getType() != DeviceType.GALAXY_BUDS3_PRO) {
            super.onSendConfiguration(config);
            return;
        }
        if (Buds3LightDiagnostics.CANCEL.equals(config)) {
            cancelExperiment("Cancelled by user");
        } else if (Buds3LightDiagnostics.RUN.equals(config)) {
            diagnostics().note("NOT SENT: debugger requests require an immutable options bundle");
        } else {
            queueDebugFrame(mDeviceProtocol.encodeSendConfiguration(config), config);
        }
    }

    private void queueDebugFrame(final byte[] frame, final String label) {
        if (frame == null) return;
        if (!getDevice().isConnected()) {
            diagnostics().note("NOT SENT: device disconnected, " + label);
            return;
        }
        final TransactionBuilder builder = createTransactionBuilder("Buds3 lights: " + label);
        builder.write(frame);
        builder.queue();
        diagnostics().queued(frame, label);
    }

    private void cancelExperiment(final String reason) {
        debugGeneration.incrementAndGet();
        debugHandler.removeCallbacksAndMessages(null);
        if (debugRunning) diagnostics().note(reason + ". Already queued frames cannot be recalled.");
        debugRunning = false;
    }

    @Override
    public void onTestNewFunction(final Bundle options) {
        if (getDevice().getType() == DeviceType.GALAXY_BUDS3_PRO && options != null
                && options.containsKey(Buds3LightDiagnostics.EXPERIMENT)) {
            startExperiment(new Bundle(options));
            return;
        }
        super.onTestNewFunction(options);
    }

    private void startExperiment(final Bundle request) {
        cancelExperiment("Previous experiment replaced");
        if (!getDevice().isConnected()) {
            diagnostics().note("Experiment not started: device disconnected");
            return;
        }
        final String experiment = request.getString(Buds3LightDiagnostics.EXPERIMENT, "custom");
        final List<byte[]> frames = new ArrayList<>();
        final byte[] on = Buds3LightDiagnostics.frame(0x8e, new byte[]{1}, 0);
        final byte[] off = Buds3LightDiagnostics.frame(0x8e, new byte[]{0}, 0);
        try {
            final byte[] style = mDeviceProtocol.encodeSendConfiguration(
                    DeviceSettingsPreferenceConst.PREF_GALAXY_BUDS_3_PRO_EARBUD_LIGHTS);
            switch (experiment) {
                case "custom":
                    final byte[] payload = Buds3LightDiagnostics.parsePayload(request.getString(Buds3LightDiagnostics.PAYLOAD, "01"));
                    final int flags = request.getInt(Buds3LightDiagnostics.FLAGS, 0);
                    final int repeats = Math.max(1, Math.min(16, request.getInt(Buds3LightDiagnostics.REPEAT, 1)));
                    for (int i = 0; i < repeats; i++) frames.add(Buds3LightDiagnostics.frame(0x8e, payload, flags));
                    break;
                case "on3": frames.add(on); frames.add(on); frames.add(on); break;
                case "off_on": frames.add(off); frames.add(on); break;
                case "style_on": frames.add(style); frames.add(on); break;
                case "on_style": frames.add(on); frames.add(style); break;
                case "off_style_on": frames.add(off); frames.add(style); frames.add(on); break;
                case "sweep":
                    for (int i = 0; i <= 15; i++) frames.add(Buds3LightDiagnostics.frame(0x8e, new byte[]{(byte) i}, 0));
                    break;
                case "sweep_all":
                    for (int i = 0; i <= 255; i++) frames.add(Buds3LightDiagnostics.frame(0x8e, new byte[]{(byte) i}, 0));
                    break;
                case "manager_samsung":
                case "manager_other":
                    final int sdk = Math.max(1, Math.min(255, request.getInt(Buds3LightDiagnostics.SDK, 35)));
                    frames.add(Buds3LightDiagnostics.frame(0x88,
                            new byte[]{1, (byte) ("manager_samsung".equals(experiment) ? 1 : 2), (byte) sdk}, 0));
                    frames.add(on);
                    break;
                case "firmware":
                    frames.add(Buds3LightDiagnostics.frame(0x24, new byte[0], 0));
                    frames.add(Buds3LightDiagnostics.frame(0x28, new byte[0], 0));
                    break;
                default: throw new IllegalArgumentException("Unknown experiment");
            }
            final int delay = Math.max("sweep_all".equals(experiment) ? 1000 : 250, Math.min(10000, request.getInt(Buds3LightDiagnostics.DELAY, 1000)));
            final int generation = debugGeneration.get();
            experimentOnReports = diagnostics().onReportCount();
            stopOnReport = request.getBoolean(Buds3LightDiagnostics.STOP_ON, true);
            debugRunning = true;
            diagnostics().note("Experiment " + experiment + ", " + frames.size() + " frames, " + delay + " ms spacing. Stop on ON report: " + stopOnReport);
            for (int i = 0; i < frames.size(); i++) {
                final byte[] frame = frames.get(i);
                final int step = i + 1;
                debugHandler.postDelayed(() -> {
                    if (generation != debugGeneration.get()) return;
                    if (!getDevice().isConnected()) { cancelExperiment("Stopped on disconnect"); return; }
                    queueDebugFrame(frame, experiment + " " + step + "/" + frames.size());
                    if (step == frames.size()) {
                        debugRunning = false;
                        diagnostics().note("Experiment queue complete. Mark the physical LED result.");
                    }
                }, (long) i * delay);
            }
        } catch (IllegalArgumentException exception) {
            diagnostics().note("NOT SENT: " + exception.getMessage());
        }
    }

    @Override
    public void onSocketRead(final byte[] data) {
        super.onSocketRead(data);
        if (getDevice().getType() == DeviceType.GALAXY_BUDS3_PRO && debugRunning
                && stopOnReport && diagnostics().onReportCount() > experimentOnReports) {
            cancelExperiment("Stopped after a new ON report. Check physical LEDs separately");
        }
    }

    @Override
    public void disconnect() {
        cancelExperiment("Stopped on disconnect");
        super.disconnect();
    }

    @Override
    public void dispose() {
        cancelExperiment("Stopped because support was disposed");
        super.dispose();
    }

    @Override
    protected GalaxyBudsProtocol createDeviceProtocol() {
        return new GalaxyBudsProtocol(getDevice());
    }

    @Override
    protected UUID getSupportedService() {
        if (getDevice().getType().equals(DeviceType.GALAXY_BUDS)) {
            return GalaxyBudsProtocol.UUID_GALAXY_BUDS_DEVICE_CTRL;
        }
        return GalaxyBudsProtocol.UUID_GALAXY_BUDS_LIVE_DEVICE_CTRL;
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        if (getDevice().getType() == DeviceType.GALAXY_BUDS3_PRO) {
            cancelExperiment("Device connection initialized");
            mDeviceProtocol.resetLightStream();
            diagnostics().note("Buds3 Pro connection initialized");
        }
        builder.setDeviceState(GBDevice.State.INITIALIZED);

        return builder;
    }
}
