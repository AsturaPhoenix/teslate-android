package rw.simplecast;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class SimpleCastActivity extends AppCompatActivity {
    private static final int
            SCREEN_CAP_PERMS = 0;

    private MediaProjectionManager mProjectionManager;
    private Handler mUpdateButtonHandler;

    private Subscription mStatusUpdates;
    private final Runnable mUpdateButton = this::updateButton;
    private static final double MS_DRIVING_PER_MONTH = (4.0 * 5 + 10 * 2) * 4 * 30 / 28 * 3600000;

    private String formatStatus(final SimpleCastService.Status status) {
        final double estPd = MS_DRIVING_PER_MONTH / status.uptime;
        return "Status: " + Formatter.formatShortFileSize(this, status.bytesSent) + " sent\n" +
                Formatter.formatShortFileSize(this, status.bytesSent * 3600000 / status.uptime) +
                " per hour\n" +
                Formatter.formatShortFileSize(this, (long)(status.bytesSent * estPd)) +
                " per month (projected)\n" +
                "Latency: " + status.latency + " ms (avg 16: " + status.lat16 + " ms)";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_cast);

        final TextView txtServiceStatus = (TextView) findViewById(R.id.serviceStatus);
        mStatusUpdates = SimpleCastService.getStatus()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(s -> txtServiceStatus.setText(formatStatus(s)));

        final String lastError = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(SimpleCastService.PREF_LAST_ERROR, null);
        final TextView txtLastError = (TextView) findViewById(R.id.lastError);
        if (lastError != null) {
            txtLastError.setText(lastError);
        } else {
            ((View) txtLastError.getParent()).setVisibility(View.INVISIBLE);
        }

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

        if (!RemoteInputService.isPossible()) {
            inputStatus.setText("ADB injection required for remote input");
            inputStatus.setTextColor(Color.RED);
        }

        if (SimpleCastService.sRunning) {
            if (RemoteInputService.isPossible()) {
                inputStatus.setText("Remote input enabled");
                inputStatus.setTextColor(Color.GREEN);
            }

            toggle.setText("Stop Server");
            toggle.setOnClickListener(x -> {
                stopService(new Intent(this, SimpleCastService.class));
            });
        } else {
            if (RemoteInputService.isPossible()) {
                inputStatus.setText("Remote input suspended");
                inputStatus.setTextColor(Color.YELLOW);
            }

            toggle.setText("Start Server");
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
                final Intent intent = new Intent(this, SimpleCastService.class);
                intent.putExtra(SimpleCastService.MP_RESULT, resultCode);
                intent.putExtra(SimpleCastService.MP_INTENT, data);
                startService(intent);
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
