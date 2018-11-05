package tr.eaydgdu.email.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.ToggleButton;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import tr.eaydgdu.email.AdapterFolder;
import tr.eaydgdu.email.R;

public class ActivityFolder extends AppCompatActivity {

    private long account;
    private AdapterFolder adapter;

    private ToggleButton tbShowHidden;
    private RecyclerView rvFolder;
    private ProgressBar pbWait;
    private Group grpHintActions;
    private Group grpReady;
    private FloatingActionButton fab;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle b = getIntent().getExtras();
        account = (b == null ? -1 : b.getLong("account"));
        setContentView(R.layout.fragment_folders);


        // Get controls
        tbShowHidden = findViewById(R.id.tbShowHidden);
        rvFolder = findViewById(R.id.rvFolder);
        pbWait = findViewById(R.id.pbWait);
        grpHintActions = findViewById(R.id.grpHintActions);
        grpReady = findViewById(R.id.grpReady);
        fab = findViewById(R.id.fab);

        tbShowHidden.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                adapter.showHidden(isChecked);
            }
        });

        rvFolder.setHasFixedSize(false);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        rvFolder.setLayoutManager(llm);

        //adapter = new AdapterFolder(this, getViewLifecycleOwner());
        //rvFolder.setAdapter(adapter);
/*
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
*/
        // Initialize
        tbShowHidden.setVisibility(View.GONE);
        grpReady.setVisibility(View.GONE);
        pbWait.setVisibility(View.VISIBLE);

    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
