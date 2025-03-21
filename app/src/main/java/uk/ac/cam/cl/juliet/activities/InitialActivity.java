package uk.ac.cam.cl.juliet.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Invisibly runs when the app first launches and decides which visible Activity to start the user
 * on.
 *
 * <p>If the user has already given storage permissions then an instance of <code>MainActivity
 * </code> will be started; otherwise an instance of <code>RequestPermissionsActivity</code> will be
 * started.
 */
public class InitialActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent;
        intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
