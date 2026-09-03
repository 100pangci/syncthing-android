package com.nutomic.syncthingandroid.activities;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.service.Constants;

/**
 * Provides a themed instance of AppCompatActivity.
 */
public abstract class ThemedAppCompatActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Opt-in to edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Load theme. The custom value 3 means "AMOLED black" which is based
        // on the dark (night) resource set, so map it to MODE_NIGHT_YES.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int prefAppTheme = Integer.parseInt(sharedPreferences.getString(
                Constants.PREF_APP_THEME, 
                Integer.toString(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        );
        AppCompatDelegate.setDefaultNightMode(
                prefAppTheme == 3
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : prefAppTheme);
        super.onCreate(savedInstanceState);
    }
}
