package tr.eaydgdu.email;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.mail.Address;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.paging.PagedList;
import tr.eaydgdu.email.database.DB;
import tr.eaydgdu.email.fragments.MenuFragment;
import tr.eaydgdu.email.views.CircularImageView;

public class ActivityView extends ActivityBilling implements FragmentManager.OnBackStackChangedListener, View.OnClickListener {

    private MenuFragment menuFragment;
    private DrawerLayout drawerLayout;
    private ListView drawerList;

    private long attachment = -1;
    private PagedList<TupleMessageEx> messages = null;

    private static final int ATTACHMENT_BUFFER_SIZE = 8192; // bytes

    static final int REQUEST_UNIFIED = 1;
    static final int REQUEST_THREAD = 2;
    static final int REQUEST_ERROR = 3;

    static final int REQUEST_ATTACHMENT = 1;

    static final String ACTION_VIEW_MESSAGES = BuildConfig.APPLICATION_ID + ".VIEW_MESSAGES";
    static final String ACTION_VIEW_THREAD = BuildConfig.APPLICATION_ID + ".VIEW_THREAD";
    static final String ACTION_VIEW_FULL = BuildConfig.APPLICATION_ID + ".VIEW_FULL";
    static final String ACTION_EDIT_FOLDER = BuildConfig.APPLICATION_ID + ".EDIT_FOLDER";
    static final String ACTION_EDIT_ANSWER = BuildConfig.APPLICATION_ID + ".EDIT_ANSWER";
    static final String ACTION_STORE_ATTACHMENT = BuildConfig.APPLICATION_ID + ".STORE_ATTACHMENT";
    static final String ACTION_SHOW_PRO = BuildConfig.APPLICATION_ID + ".SHOW_PRO";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(this).inflate(R.layout.activity_view, null));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        findViewById(R.id.menu).setOnClickListener(this);

        drawerLayout = findViewById(R.id.drawer_layout);
        drawerList = findViewById(R.id.drawer_list);
        drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DrawerItem item = (DrawerItem) parent.getAdapter().getItem(position);
                switch (item.getId()) {
                    case -1:
                        onMenuFolders((long) item.getData());
                        break;
                    case R.string.menu_answers:
                        onMenuAnswers();
                        break;
                    case R.string.menu_operations:
                        onMenuOperations();
                        break;
                    case R.string.menu_legend:
                        onMenuLegend();
                        break;
                    case R.string.menu_faq:
                        onMenuFAQ();
                        break;
                    case R.string.menu_pro:
                        onMenuPro();
                        break;
                    case R.string.menu_privacy:
                        onMenuPrivacy();
                        break;
                    case R.string.menu_about:
                        onMenuAbout();
                        break;
                }

                drawerLayout.closeDrawer(drawerList);
            }
        });

        getSupportFragmentManager().addOnBackStackChangedListener(this);

        DB.getInstance(this).account().liveAccounts(true).observe(this, new Observer<List<EntityAccount>>() {
            @Override
            public void onChanged(@Nullable List<EntityAccount> accounts) {
                if (accounts == null) accounts = new ArrayList<>();

                ArrayAdapterDrawer drawerArray = new ArrayAdapterDrawer(ActivityView.this);

                final Collator collator = Collator.getInstance(Locale.getDefault());
                collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc

                Collections.sort(accounts, new Comparator<EntityAccount>() {
                    @Override
                    public int compare(EntityAccount a1, EntityAccount a2) {
                        return collator.compare(a1.name, a2.name);
                    }
                });

                for (EntityAccount account : accounts)
                    drawerArray.add(new DrawerItem(R.layout.item_drawer, -1, R.drawable.baseline_folder_24, account.name, account.id));

                drawerArray.add(new DrawerItem(ActivityView.this, R.layout.item_drawer, R.drawable.baseline_reply_24, R.string.menu_answers));

                if (PreferenceManager.getDefaultSharedPreferences(ActivityView.this).getBoolean("debug", false))
                    drawerArray.add(new DrawerItem(ActivityView.this, R.layout.item_drawer, R.drawable.baseline_list_24, R.string.menu_operations));

                drawerArray.add(new DrawerItem(ActivityView.this, R.layout.item_drawer, R.drawable.baseline_help_24, R.string.menu_legend));

                if (getIntentFAQ().resolveActivity(getPackageManager()) != null)
                    drawerArray.add(new DrawerItem(ActivityView.this, R.layout.item_drawer, R.drawable.baseline_question_answer_24, R.string.menu_faq));

                Intent pro = getIntentPro();
                if (pro == null || pro.resolveActivity(getPackageManager()) != null)
                    drawerArray.add(new DrawerItem(ActivityView.this, R.layout.item_drawer, R.drawable.baseline_monetization_on_24, R.string.menu_pro));

                if (Helper.getIntentPrivacy().resolveActivity(getPackageManager()) != null)
                    drawerArray.add(new DrawerItem(ActivityView.this, R.layout.item_drawer, R.drawable.baseline_account_box_24, R.string.menu_privacy));

                drawerArray.add(new DrawerItem(ActivityView.this, R.layout.item_drawer, R.drawable.baseline_info_24, R.string.menu_about));

                drawerList.setAdapter(drawerArray);
            }
        });

        if (getSupportFragmentManager().getFragments().size() == 0) {
            Bundle args = new Bundle();
            args.putLong("folder", -1);

            FragmentMessages fragment = new FragmentMessages();
            fragment.setArguments(args);

            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("unified");
            fragmentTransaction.commit();
        }

        checkFirst();
        checkCrash();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        IntentFilter iff = new IntentFilter();
        iff.addAction(ACTION_VIEW_MESSAGES);
        iff.addAction(ACTION_VIEW_THREAD);
        iff.addAction(ACTION_VIEW_FULL);
        iff.addAction(ACTION_EDIT_FOLDER);
        iff.addAction(ACTION_EDIT_ANSWER);
        iff.addAction(ACTION_STORE_ATTACHMENT);
        iff.addAction(ACTION_SHOW_PRO);
        lbm.registerReceiver(receiver, iff);

        Intent intent = getIntent();
        String action = intent.getAction();
        Log.i(Helper.TAG, "View intent=" + intent + " action=" + action);
        if (action != null && action.startsWith("thread")) {
            intent.setAction(null);
            setIntent(intent);

            ViewModelMessages model = ViewModelProviders.of(this).get(ViewModelMessages.class);
            model.setMessages(null);

            intent.putExtra("thread", action.split(":", 2)[1]);
            onViewThread(intent);
        }

        if (getIntent().hasExtra(Intent.EXTRA_PROCESS_TEXT)) {
            String search = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString();

            intent.removeExtra(Intent.EXTRA_PROCESS_TEXT);
            setIntent(intent);

            if (Helper.isPro(this)) {
                Bundle args = new Bundle();
                args.putString("search", search);

                new SimpleTask<Long>() {
                    @Override
                    protected Long onLoad(Context context, Bundle args) {
                        DB db = DB.getInstance(context);

                        EntityFolder archive = db.folder().getPrimaryArchive();
                        if (archive == null)
                            throw new IllegalArgumentException(getString(R.string.title_no_primary_archive));

                        db.message().deleteFoundMessages();

                        return archive.id;
                    }

                    @Override
                    protected void onLoaded(Bundle args, Long archive) {
                        Bundle sargs = new Bundle();
                        sargs.putLong("folder", archive);
                        sargs.putString("search", args.getString("search"));

                        FragmentMessages fragment = new FragmentMessages();
                        fragment.setArguments(sargs);

                        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                        fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("search");
                        fragmentTransaction.commit();
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Helper.unexpectedError(ActivityView.this, ex);
                    }
                }.load(this, args);
            } else {
                FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.content_frame, new FragmentPro()).addToBackStack("pro");
                fragmentTransaction.commit();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.unregisterReceiver(receiver);
        if(menuFragment != null){ if(menuFragment.isVisible()) menuFragment.dismiss();}
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(drawerList))
            drawerLayout.closeDrawer(drawerList);
        else if(menuFragment != null) if(menuFragment.isVisible()) menuFragment.dismiss();
        else super.onBackPressed();
    }

    @Override
    public void onBackStackChanged() {
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (count == 0)
            finish();
        else {
            if (drawerLayout.isDrawerOpen(drawerList))
                drawerLayout.closeDrawer(drawerList);
            //drawerToggle.setDrawerIndicatorEnabled(count == 1);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //if (drawerToggle.onOptionsItemSelected(item)) return true;

        switch (item.getItemId()) {
            case android.R.id.home:
                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))
                    getSupportFragmentManager().popBackStack();
                return true;
            default:
                return false;
        }
    }

    private void checkFirst() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("first", true)) {
            new DialogBuilderLifecycle(this, this)
                    .setMessage(getString(R.string.title_hint_sync))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            prefs.edit().putBoolean("first", false).apply();
                        }
                    })
                    .show();
        }
    }
    private void checkCrash() {
        new SimpleTask<Long>() {
            @Override
            protected Long onLoad(Context context, Bundle args) throws Throwable {
                File file = new File(context.getCacheDir(), "crash.log");
                if (file.exists()) {
                    // Get version info
                    StringBuilder sb = new StringBuilder();

                    sb.append(context.getString(R.string.title_crash_info_remark) + "\n\n\n\n");

                    sb.append(String.format("%s: %s %s/%s\r\n",
                            context.getString(R.string.app_name),
                            BuildConfig.APPLICATION_ID,
                            BuildConfig.VERSION_NAME,
                            Helper.hasValidFingerprint(context) ? "1" : "3"));
                    sb.append(String.format("Android: %s (SDK %d)\r\n", Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
                    sb.append("\r\n");

                    // Get device info
                    sb.append(String.format("Brand: %s\r\n", Build.BRAND));
                    sb.append(String.format("Manufacturer: %s\r\n", Build.MANUFACTURER));
                    sb.append(String.format("Model: %s\r\n", Build.MODEL));
                    sb.append(String.format("Product: %s\r\n", Build.PRODUCT));
                    sb.append(String.format("Device: %s\r\n", Build.DEVICE));
                    sb.append(String.format("Host: %s\r\n", Build.HOST));
                    sb.append(String.format("Display: %s\r\n", Build.DISPLAY));
                    sb.append(String.format("Id: %s\r\n", Build.ID));
                    sb.append("\r\n");

                    BufferedReader in = null;
                    try {
                        String line;
                        in = new BufferedReader(new FileReader(file));
                        while ((line = in.readLine()) != null)
                            sb.append(line).append("\r\n");
                    } finally {
                        if (in != null)
                            in.close();
                    }

                    file.delete();

                    String body = "<pre>" + sb.toString().replaceAll("\\r?\\n", "<br />") + "</pre>";

                    EntityMessage draft = null;

                    DB db = DB.getInstance(context);
                    try {
                        db.beginTransaction();

                        EntityFolder drafts = db.folder().getPrimaryDrafts();
                        if (drafts != null) {
                            draft = new EntityMessage();
                            draft.account = drafts.account;
                            draft.folder = drafts.id;
                            draft.msgid = EntityMessage.generateMessageId();
                            draft.to = new Address[]{Helper.myAddress()};
                            draft.subject = context.getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME + " crash log";
                            draft.content = true;
                            draft.received = new Date().getTime();
                            draft.seen = false;
                            draft.ui_seen = false;
                            draft.flagged = false;
                            draft.ui_flagged = false;
                            draft.ui_hide = false;
                            draft.ui_found = false;
                            draft.ui_ignored = false;
                            draft.id = db.message().insertMessage(draft);
                            draft.write(context, body);
                        }

                        EntityOperation.queue(db, draft, EntityOperation.ADD);

                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }

                    EntityOperation.process(context);

                    return (draft == null ? null : draft.id);
                }

                return null;
            }

            @Override
            protected void onLoaded(Bundle args, Long id) {
                if (id != null)
                    startActivity(
                            new Intent(ActivityView.this, ActivityCompose.class)
                                    .putExtra("action", "edit")
                                    .putExtra("id", id));

            }
        }.load(this, new Bundle());
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.menu:
                menuFragment = new MenuFragment();
                menuFragment.show(getSupportFragmentManager(), menuFragment.getTag());
                break;
        }
    }

    private Intent getIntentFAQ() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://github.com/M66B/open-source-email/blob/master/FAQ.md"));
        return intent;
    }

    private void onMenuFolders(long account) {
        getSupportFragmentManager().popBackStack("unified", 0);

        Bundle args = new Bundle();
        args.putLong("account", account);

        FragmentFolders fragment = new FragmentFolders();
        fragment.setArguments(args);

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("folders");
        fragmentTransaction.commit();
    }
    private void onMenuAnswers() {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, new FragmentAnswers()).addToBackStack("answers");
        fragmentTransaction.commit();
    }
    private void onMenuOperations() {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, new FragmentOperations()).addToBackStack("operations");
        fragmentTransaction.commit();
    }
    private void onMenuLegend() {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, new FragmentLegend()).addToBackStack("legend");
        fragmentTransaction.commit();
    }
    private void onMenuFAQ() {
        Helper.view(this, getIntentFAQ());
    }
    private void onMenuPro() {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, new FragmentPro()).addToBackStack("pro");
        fragmentTransaction.commit();
    }
    private void onMenuPrivacy() {
        Helper.view(this, Helper.getIntentPrivacy());
    }
    private void onMenuAbout() {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, new FragmentAbout()).addToBackStack("about");
        fragmentTransaction.commit();
    }

    private class DrawerItem {
        private int layout;
        private int id;
        private int icon;
        private String title;
        private Object data;

        DrawerItem(int layout) {
            this.layout = layout;
        }

        DrawerItem(Context context, int layout, int icon, int title) {
            this.layout = layout;
            this.id = title;
            this.icon = icon;
            this.title = context.getString(title);
        }

        DrawerItem(int layout, int id, int icon, String title, Object data) {
            this.layout = layout;
            this.id = id;
            this.icon = icon;
            this.title = title;
            this.data = data;
        }

        public int getId() {
            return this.id;
        }

        public Object getData() {
            return this.data;
        }
    }

    private static class ArrayAdapterDrawer extends ArrayAdapter<DrawerItem> {
        ArrayAdapterDrawer(@NonNull Context context) {
            super(context, -1);
        }

        @NonNull
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            DrawerItem item = getItem(position);
            View row = LayoutInflater.from(getContext()).inflate(item.layout, null);

            CircularImageView iv = row.findViewById(R.id.ivItem);
            TextView tv = row.findViewById(R.id.tvItem);

            if (iv != null)
                iv.setImageResource(item.icon);
            if (tv != null)
                tv.setText(item.title);

            return row;
        }
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_VIEW_MESSAGES.equals(intent.getAction()))
                onViewMessages(intent);
            else if (ACTION_VIEW_THREAD.equals(intent.getAction()))
                onViewThread(intent);
            else if (ACTION_VIEW_FULL.equals(intent.getAction()))
                onViewFull(intent);
            else if (ACTION_EDIT_FOLDER.equals(intent.getAction()))
                onEditFolder(intent);
            else if (ACTION_EDIT_ANSWER.equals(intent.getAction()))
                onEditAnswer(intent);
            else if (ACTION_STORE_ATTACHMENT.equals(intent.getAction()))
                onStoreAttachment(intent);
            else if (ACTION_SHOW_PRO.equals(intent.getAction()))
                onShowPro(intent);
        }
    };

    private void onViewMessages(Intent intent) {
        Bundle args = new Bundle();
        args.putLong("account", intent.getLongExtra("account", -1));
        args.putLong("folder", intent.getLongExtra("folder", -1));

        FragmentMessages fragment = new FragmentMessages();
        fragment.setArguments(args);

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("messages");
        fragmentTransaction.commit();
    }

    private void onViewThread(Intent intent) {
        getSupportFragmentManager().popBackStack("thread", FragmentManager.POP_BACK_STACK_INCLUSIVE);

        Bundle args = new Bundle();
        args.putLong("account", intent.getLongExtra("account", -1));
        args.putString("thread", intent.getStringExtra("thread"));

        FragmentMessages fragment = new FragmentMessages();
        fragment.setArguments(args);

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("thread");
        fragmentTransaction.commit();
    }

    private void onViewFull(Intent intent) {
        FragmentWebView fragment = new FragmentWebView();
        fragment.setArguments(intent.getExtras());

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("webview");
        fragmentTransaction.commit();
    }

    private void onEditFolder(Intent intent) {
        FragmentFolder fragment = new FragmentFolder();
        fragment.setArguments(intent.getExtras());
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("folder");
        fragmentTransaction.commit();
    }

    private void onEditAnswer(Intent intent) {
        FragmentAnswer fragment = new FragmentAnswer();
        fragment.setArguments(intent.getExtras());
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("answer");
        fragmentTransaction.commit();
    }

    private void onStoreAttachment(Intent intent) {
        attachment = intent.getLongExtra("id", -1);
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType(intent.getStringExtra("type"));
        create.putExtra(Intent.EXTRA_TITLE, intent.getStringExtra("name"));
        startActivityForResult(create, REQUEST_ATTACHMENT);
    }

    private void onShowPro(Intent intent) {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, new FragmentPro()).addToBackStack("pro");
        fragmentTransaction.commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(Helper.TAG, "View onActivityResult request=" + requestCode + " result=" + resultCode + " data=" + data);
        if (resultCode == Activity.RESULT_OK)
            if (requestCode == REQUEST_ATTACHMENT) {
                Bundle args = new Bundle();
                args.putLong("id", attachment);
                args.putParcelable("uri", data.getData());
                new SimpleTask<Void>() {
                    @Override
                    protected Void onLoad(Context context, Bundle args) throws Throwable {
                        long id = args.getLong("id");
                        Uri uri = args.getParcelable("uri");

                        File file = EntityAttachment.getFile(context, id);

                        ParcelFileDescriptor pfd = null;
                        FileOutputStream fos = null;
                        FileInputStream fis = null;
                        try {
                            pfd = context.getContentResolver().openFileDescriptor(uri, "w");
                            fos = new FileOutputStream(pfd.getFileDescriptor());
                            fis = new FileInputStream(file);

                            byte[] buffer = new byte[ATTACHMENT_BUFFER_SIZE];
                            int read;
                            while ((read = fis.read(buffer)) != -1) {
                                fos.write(buffer, 0, read);
                            }
                        } finally {
                            try {
                                if (pfd != null)
                                    pfd.close();
                            } catch (Throwable ex) {
                                Log.w(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                            }
                            try {
                                if (fos != null)
                                    fos.close();
                            } catch (Throwable ex) {
                                Log.w(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                            }
                            try {
                                if (fis != null)
                                    fis.close();
                            } catch (Throwable ex) {
                                Log.w(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                            }
                        }

                        return null;
                    }

                    @Override
                    protected void onLoaded(Bundle args, Void data) {
                        Toast.makeText(ActivityView.this, R.string.title_attachment_saved, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Log.e(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                        Helper.unexpectedError(ActivityView.this, ex);
                    }
                }.load(this, args);
            }
    }
}