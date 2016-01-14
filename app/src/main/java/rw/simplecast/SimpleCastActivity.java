package rw.simplecast;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;

public class SimpleCastActivity extends AppCompatActivity
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final int
            SCREEN_CAP_PERMS = 0,
            RESOLVE_CONNECTION = 1;

    private GoogleApiClient mGoogleApiClient;
    private MediaProjectionManager mProjectionManager;
    private Handler mUpdateButtonHandler;

    private final Runnable mUpdateButton = this::updateButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_cast);

        SimpleCastService.mG = mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mProjectionManager = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);

        mUpdateButtonHandler = new Handler();
        updateButton();
    }

    @Override
    protected void onDestroy() {
        mUpdateButtonHandler.removeCallbacks(mUpdateButton);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    private void updateButton() {
        final Button toggle = (Button) findViewById(R.id.toggle);
        if (SimpleCastService.sRunning) {
            toggle.setText("Stop Server");
            toggle.setOnClickListener(x -> {
                stopService(new Intent(this, SimpleCastService.class));
            });
        } else {
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
            case RESOLVE_CONNECTION:
                if (resultCode == RESULT_OK) {
                    mGoogleApiClient.connect();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(final ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, RESOLVE_CONNECTION);
            } catch (final IntentSender.SendIntentException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        } else {
            GoogleApiAvailability.getInstance()
                    .getErrorDialog(this, connectionResult.getErrorCode(), 0).show();
        }
    }
}
