package eu.faircode.email;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FragmentFolders extends FragmentEx {

    private ToggleButton tbShowHidden;
    private RecyclerView rvFolder;
    private ProgressBar pbWait;
    private Group grpHintActions;
    private Group grpReady;
    private FloatingActionButton fab;

    private long account;
    private AdapterFolder adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get arguments
        Bundle args = getArguments();
        account = (args == null ? -1 : args.getLong("account"));
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_folders, container, false);

        // Get controls
        tbShowHidden = view.findViewById(R.id.tbShowHidden);
        rvFolder = view.findViewById(R.id.rvFolder);
        pbWait = view.findViewById(R.id.pbWait);
        grpHintActions = view.findViewById(R.id.grpHintActions);
        grpReady = view.findViewById(R.id.grpReady);
        fab = view.findViewById(R.id.fab);

        tbShowHidden.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                adapter.showHidden(isChecked);
            }
        });

        rvFolder.setHasFixedSize(false);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        rvFolder.setLayoutManager(llm);

        adapter = new AdapterFolder(getContext(), getViewLifecycleOwner());
        rvFolder.setAdapter(adapter);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle args = new Bundle();
                args.putLong("account", account);
                FragmentFolder fragment = new FragmentFolder();
                fragment.setArguments(args);
                FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("folder");
                fragmentTransaction.commit();
            }
        });

        // Initialize
        tbShowHidden.setVisibility(View.GONE);
        grpReady.setVisibility(View.GONE);
        pbWait.setVisibility(View.VISIBLE);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        grpHintActions.setVisibility(prefs.getBoolean("folder_actions", false) ? View.GONE : View.VISIBLE);

        DB db = DB.getInstance(getContext());

        // Observe account
        db.account().liveAccount(account).observe(getViewLifecycleOwner(), new Observer<EntityAccount>() {
            @Override
            public void onChanged(@Nullable EntityAccount account) {
                setSubtitle(account == null ? null : account.name);
                adapter.setAccountState(account.state);
            }
        });

        // Observe folders
        db.folder().liveFolders(account).observe(getViewLifecycleOwner(), new Observer<List<TupleFolderEx>>() {
            @Override
            public void onChanged(@Nullable List<TupleFolderEx> folders) {
                if (folders == null) {
                    finish();
                    return;
                }

                boolean hidden = false;
                for (TupleFolderEx folder : folders)
                    if (folder.hide) {
                        hidden = true;
                        break;
                    }
                tbShowHidden.setVisibility(hidden ? View.VISIBLE : View.GONE);

                adapter.set(folders);

                pbWait.setVisibility(View.GONE);
                grpReady.setVisibility(View.VISIBLE);
            }
        });
    }
}
