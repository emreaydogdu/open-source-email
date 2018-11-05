package tr.eaydgdu.email;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.ArrayList;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public class ActivityCompose extends ActivityBilling implements FragmentManager.OnBackStackChangedListener {
    static final int REQUEST_CONTACT_TO = 1;
    static final int REQUEST_CONTACT_CC = 2;
    static final int REQUEST_CONTACT_BCC = 3;
    static final int REQUEST_IMAGE = 4;
    static final int REQUEST_ATTACHMENT = 5;
    static final int REQUEST_ENCRYPT = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);

        //getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getSupportFragmentManager().addOnBackStackChangedListener(this);

        if (getSupportFragmentManager().getFragments().size() == 0) {
            Bundle args;
            Intent intent = getIntent();
            String action = intent.getAction();
            if (Intent.ACTION_VIEW.equals(action) ||
                    Intent.ACTION_SENDTO.equals(action) ||
                    Intent.ACTION_SEND.equals(action) ||
                    Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                args = new Bundle();
                args.putString("action", "new");
                args.putLong("account", -1);

                Uri uri = intent.getData();
                if (uri != null && "mailto".equals(uri.getScheme()))
                    args.putString("to", uri.getSchemeSpecificPart());

                if (intent.hasExtra(Intent.EXTRA_EMAIL))
                    args.putString("to", TextUtils.join(", ", intent.getStringArrayExtra(Intent.EXTRA_EMAIL)));

                if (intent.hasExtra(Intent.EXTRA_CC))
                    args.putString("cc", TextUtils.join(", ", intent.getStringArrayExtra(Intent.EXTRA_CC)));

                if (intent.hasExtra(Intent.EXTRA_BCC))
                    args.putString("bcc", TextUtils.join(", ", intent.getStringArrayExtra(Intent.EXTRA_BCC)));

                if (intent.hasExtra(Intent.EXTRA_SUBJECT))
                    args.putString("subject", intent.getStringExtra(Intent.EXTRA_SUBJECT));

                if (intent.hasExtra(Intent.EXTRA_TEXT))
                    args.putString("body", intent.getStringExtra(Intent.EXTRA_TEXT)); // Intent.EXTRA_HTML_TEXT

                if (intent.hasExtra(Intent.EXTRA_STREAM))
                    if (Intent.ACTION_SEND_MULTIPLE.equals(action))
                        args.putParcelableArrayList("attachments", intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM));
                    else {
                        ArrayList<Uri> uris = new ArrayList<>();
                        uris.add((Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM));
                        args.putParcelableArrayList("attachments", uris);
                    }
            } else
                args = intent.getExtras();

            FragmentCompose fragment = new FragmentCompose();
            fragment.setArguments(args);

            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("compose");
            fragmentTransaction.commit();
        }
    }

    @Override
    public void onBackStackChanged() {
        if (getSupportFragmentManager().getBackStackEntryCount() == 0)
            finish();
    }
}
