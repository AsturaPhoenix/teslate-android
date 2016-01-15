package rw.simplecast;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;

public class SimpleCastActivity extends AppCompatActivity {
    private static final int
            SCREEN_CAP_PERMS = 0;

    private MediaProjectionManager mProjectionManager;
    private Handler mUpdateButtonHandler;

    private final Runnable mUpdateButton = this::updateButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_cast);

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
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
