package tr.eaydgdu.email;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

public class FragmentOptions extends FragmentEx {

    private SwitchCompat swEnabled;
    private SwitchCompat swAvatars;
    private SwitchCompat swLight;
    private SwitchCompat swBrowse;
    private SwitchCompat swSwipe;
    private SwitchCompat swCompact;
    private SwitchCompat swInsecure;
    private SwitchCompat swDebug;

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setSubtitle(R.string.title_advanced);

        View view = inflater.inflate(R.layout.fragment_options, container, false);

        // Get controls
        swEnabled = view.findViewById(R.id.swEnabled);
        swAvatars = view.findViewById(R.id.swAvatars);
        swLight = view.findViewById(R.id.swLight);
        swBrowse = view.findViewById(R.id.swBrowse);
        swSwipe = view.findViewById(R.id.swSwipe);
        swCompact = view.findViewById(R.id.swCompact);
        swInsecure = view.findViewById(R.id.swInsecure);
        swDebug = view.findViewById(R.id.swDebug);

        // Wire controls

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        swEnabled.setChecked(prefs.getBoolean("enabled", true));
        swEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("enabled", checked).apply();
                if (checked)
                    ServiceSynchronize.start(getContext());
                else
                    ServiceSynchronize.stop(getContext());
            }
        });

        swAvatars.setChecked(prefs.getBoolean("avatars", true));
        swAvatars.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("avatars", checked).apply();
            }
        });

        swLight.setChecked(prefs.getBoolean("light", false));
        swLight.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("light", checked).apply();
            }
        });

        swBrowse.setChecked(prefs.getBoolean("browse", true));
        swBrowse.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("browse", checked).apply();
            }
        });

        swSwipe.setChecked(prefs.getBoolean("swipe", true));
        swSwipe.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("swipe", checked).apply();
            }
        });

        swCompact.setChecked(prefs.getBoolean("compact", false));
        swCompact.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("compact", checked).apply();
            }
        });

        swInsecure.setChecked(prefs.getBoolean("insecure", false));
        swInsecure.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("insecure", checked).apply();
            }
        });

        swDebug.setChecked(prefs.getBoolean("debug", false));
        swDebug.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                prefs.edit().putBoolean("debug", checked).apply();
                ServiceSynchronize.reload(getContext(), "debug=" + checked);
            }
        });

        swLight.setVisibility(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ? View.VISIBLE : View.GONE);

        return view;
    }
}
