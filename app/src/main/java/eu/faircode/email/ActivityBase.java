package eu.faircode.email;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

abstract class ActivityBase extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(Helper.TAG, "Create " + this.getClass().getName() + " version=" + BuildConfig.VERSION_NAME);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = (Helper.isPro(this) ? prefs.getString("theme", "light") : "light");
        setTheme("light".equals(theme) ? R.style.AppThemeLight : R.style.AppThemeDark);
        prefs.registerOnSharedPreferenceChangeListener(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        Log.i(Helper.TAG, "Resume " + this.getClass().getName());
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(Helper.TAG, "Pause " + this.getClass().getName());
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i(Helper.TAG, "Config " + this.getClass().getName());
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        Log.i(Helper.TAG, "Destroy " + this.getClass().getName());
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Log.i(Helper.TAG, "Preference " + key + "=" + prefs.getAll().get(key));
        if ("theme".equals(key)) {
            finish();
            startActivity(getIntent());
        } else if (!this.getClass().equals(ActivitySetup.class) && ("compact".equals(key) || "debug".equals(key)))
            finish();
    }

    private List<IBackPressedListener> backPressedListeners = new ArrayList<>();

    public void addBackPressedListener(IBackPressedListener listener) {
        backPressedListeners.add(listener);
    }

    @Override
    public void onBackPressed() {
        for (IBackPressedListener listener : backPressedListeners)
            if (listener.onBackPressed())
                return;
        super.onBackPressed();
    }

    public interface IBackPressedListener {
        boolean onBackPressed();
    }
}
