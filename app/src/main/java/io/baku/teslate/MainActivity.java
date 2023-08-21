package io.baku.teslate;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import java.io.IOException;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class MainActivity extends ComponentActivity {
    private static final int
            SCREEN_CAP_PERMS = 0;

    private MediaProjectionManager mProjectionManager;
    private Handler mUpdateButtonHandler;

    private Subscription mStatusUpdates;
    private final Runnable mUpdateButton = this::updateButton;
    private static final double MS_DRIVING_PER_MONTH = (4.0 * 5 + 10 * 2) * 4 * 30 / 28 * 3600000;

    private String formatStatus(final CastingService.Status status) {
        final double estPd = MS_DRIVING_PER_MONTH / status.uptime;
        return "Status: " + Formatter.formatShortFileSize(this, status.bytesTx) + " sent (" +
                Formatter.formatShortFileSize(this, status.bytesPayload) + " payload)\n" +
                Formatter.formatShortFileSize(this, status.bytesTx * 3600000 / status.uptime) +
                " per hour\n" +
                Formatter.formatShortFileSize(this, (long)(status.bytesTx * estPd)) +
                " per month (projected)\n" +
                "Latency: " + status.latency + " ms (avg 16: " + status.lat16 + " ms)";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Settings settings = new Settings(PreferenceManager.getDefaultSharedPreferences(this),
                new ErrorReporter(this));

        final EditText txtSessionId = (EditText) findViewById(R.id.session_id);
        txtSessionId.setText(settings.getSessionId());
        txtSessionId.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                settings.setSessionId(s.toString());
            }
        });

        final TextView txtServiceStatus = (TextView) findViewById(R.id.serviceStatus);
        mStatusUpdates = CastingService.getStatus()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(s -> txtServiceStatus.setText(formatStatus(s)));

        mProjectionManager = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);

        mUpdateButtonHandler = new Handler();
        updateButton();
    }

    @Override
    protected void onDestroy() {
        mStatusUpdates.unsubscribe();
        mUpdateButtonHandler.removeCallbacks(mUpdateButton);
        super.onDestroy();
    }

    private void updateButton() {
        final Button toggle = (Button) findViewById(R.id.toggle);
        final TextView inputStatus = (TextView) findViewById(R.id.inputStatus);

        if (!CastingService.isInputPossible()) {
            inputStatus.setText(R.string.adb_required);
            inputStatus.setTextColor(Color.RED);
        }

        if (CastingService.sRunning) {
            if (CastingService.isInputPossible()) {
                inputStatus.setText(R.string.adb_active);
                inputStatus.setTextColor(Color.GREEN);
            }

            toggle.setText(R.string.stop_casting);
            toggle.setOnClickListener(x -> {
                stopService(new Intent(this, CastingService.class));
            });
        } else {
            if (CastingService.isInputPossible()) {
                inputStatus.setText(R.string.adb_suspended);
                inputStatus.setTextColor(Color.YELLOW);
            }

            toggle.setText(R.string.start_casting);
            toggle.setOnClickListener(x -> {
                startActivityForResult(mProjectionManager.createScreenCaptureIntent(),
                        SCREEN_CAP_PERMS);
            });
        }
        mUpdateButtonHandler.postDelayed(mUpdateButton, 200);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
                                    final Intent data) {
        switch (requestCode) {
            case SCREEN_CAP_PERMS:
                final Intent intent = new Intent(this, CastingService.class);
                intent.putExtra(CastingService.MP_RESULT, resultCode);
                intent.putExtra(CastingService.MP_INTENT, data);
                startForegroundService(intent);
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
