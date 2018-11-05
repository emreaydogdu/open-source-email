package tr.eaydgdu.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018 by Marcel Bokhorst (M66B)
*/

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import com.google.android.material.snackbar.Snackbar;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import tr.eaydgdu.email.database.DB;

public class FragmentFolder extends FragmentEx {
    private ViewGroup view;
    private EditText etRename;
    private EditText etDisplay;
    private CheckBox cbHide;
    private CheckBox cbSynchronize;
    private CheckBox cbUnified;
    private EditText etAfter;
    private Button btnSave;
    private ImageButton ibDelete;
    private ProgressBar pbSave;
    private ProgressBar pbWait;

    private long id = -1;
    private long account = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get arguments
        Bundle args = getArguments();
        id = (args == null ? -1 : args.getLong("id"));
        account = (args == null ? -1 : args.getLong("account"));
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        setSubtitle(R.string.title_edit_folder);

        view = (ViewGroup) inflater.inflate(R.layout.fragment_folder, container, false);

        // Get controls
        etRename = view.findViewById(R.id.etRename);
        etDisplay = view.findViewById(R.id.etDisplay);
        cbHide = view.findViewById(R.id.cbHide);
        cbSynchronize = view.findViewById(R.id.cbSynchronize);
        cbUnified = view.findViewById(R.id.cbUnified);
        etAfter = view.findViewById(R.id.etAfter);
        btnSave = view.findViewById(R.id.btnSave);
        ibDelete = view.findViewById(R.id.ibDelete);
        pbSave = view.findViewById(R.id.pbSave);
        pbWait = view.findViewById(R.id.pbWait);

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Helper.setViewsEnabled(view, false);
                btnSave.setEnabled(false);
                ibDelete.setEnabled(false);
                pbSave.setVisibility(View.VISIBLE);

                Bundle args = new Bundle();
                args.putLong("id", id);
                args.putLong("account", account);
                args.putString("name", etRename.getText().toString());
                args.putString("display", etDisplay.getText().toString());
                args.putBoolean("hide", cbHide.isChecked());
                args.putBoolean("unified", cbUnified.isChecked());
                args.putBoolean("synchronize", cbSynchronize.isChecked());
                args.putString("after", etAfter.getText().toString());

                new SimpleTask<Void>() {
                    @Override
                    protected Void onLoad(Context context, Bundle args) throws Throwable {
                        long id = args.getLong("id");
                        long aid = args.getLong("account");
                        String name = args.getString("name");
                        String display = args.getString("display");
                        boolean hide = args.getBoolean("hide");
                        boolean unified = args.getBoolean("unified");
                        boolean synchronize = args.getBoolean("synchronize");
                        String after = args.getString("after");

                        if (TextUtils.isEmpty(display) || display.equals(name))
                            display = null;
                        int days = (TextUtils.isEmpty(after) ? EntityFolder.DEFAULT_USER_SYNC : Integer.parseInt(after));

                        IMAPStore istore = null;
                        DB db = DB.getInstance(getContext());
                        try {
                            db.beginTransaction();

                            EntityFolder folder = db.folder().getFolder(id);

                            if (folder == null || !folder.name.equals(name)) {
                                EntityAccount account = db.account().getAccount(folder == null ? aid : folder.account);

                                Properties props = MessageHelper.getSessionProperties(account.auth_type, account.insecure);
                                Session isession = Session.getInstance(props, null);
                                istore = (IMAPStore) isession.getStore(account.starttls ? "imap" : "imaps");
                                Helper.connect(context, istore, account);

                                if (folder == null) {
                                    Log.i(Helper.TAG, "Creating folder=" + name);

                                    IMAPFolder ifolder = (IMAPFolder) istore.getFolder(name);
                                    if (ifolder.exists())
                                        throw new IllegalArgumentException(getString(R.string.title_folder_exists, name));
                                    ifolder.create(Folder.HOLDS_MESSAGES);

                                    EntityFolder create = new EntityFolder();
                                    create.account = aid;
                                    create.name = name;
                                    create.display = display;
                                    create.hide = hide;
                                    create.type = EntityFolder.USER;
                                    create.unified = unified;
                                    create.synchronize = synchronize;
                                    create.after = days;
                                    db.folder().insertFolder(create);
                                } else {
                                    Log.i(Helper.TAG, "Renaming folder=" + name);

                                    IMAPFolder iold = (IMAPFolder) istore.getFolder(folder.name);
                                    IMAPFolder ifolder = (IMAPFolder) istore.getFolder(name);
                                    if (ifolder.exists())
                                        throw new IllegalArgumentException(getString(R.string.title_folder_exists, name));
                                    iold.renameTo(ifolder);
                                }
                            }

                            if (folder != null) {
                                Log.i(Helper.TAG, "Updating folder=" + name);
                                db.folder().setFolderProperties(id, name, display, hide, synchronize, unified, days);
                                if (!synchronize)
                                    db.folder().setFolderError(id, null);
                            }

                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();

                            if (istore != null)
                                istore.close();
                        }

                        ServiceSynchronize.reload(getContext(), "save folder");

                        return null;
                    }

                    @Override
                    protected void onLoaded(Bundle args, Void data) {
                        getFragmentManager().popBackStack();
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Helper.setViewsEnabled(view, true);
                        btnSave.setEnabled(true);
                        ibDelete.setEnabled(true);
                        pbSave.setVisibility(View.GONE);

                        if (ex instanceof IllegalArgumentException)
                            Snackbar.make(view, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                        else
                            Helper.unexpectedError(getContext(), ex);
                    }
                }.load(FragmentFolder.this, args);
            }
        });

        ibDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DialogBuilderLifecycle(getContext(), getViewLifecycleOwner())
                        .setMessage(R.string.title_folder_delete)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Helper.setViewsEnabled(view, false);
                                btnSave.setEnabled(false);
                                ibDelete.setEnabled(false);
                                pbSave.setVisibility(View.VISIBLE);

                                Bundle args = new Bundle();
                                args.putLong("id", id);

                                new SimpleTask<Void>() {
                                    @Override
                                    protected Void onLoad(Context context, Bundle args) throws Throwable {
                                        long id = args.getLong("id");

                                        IMAPStore istore = null;
                                        DB db = DB.getInstance(getContext());
                                        try {
                                            db.beginTransaction();

                                            EntityFolder folder = db.folder().getFolder(id);
                                            EntityAccount account = db.account().getAccount(folder.account);

                                            Properties props = MessageHelper.getSessionProperties(account.auth_type, account.insecure);
                                            Session isession = Session.getInstance(props, null);
                                            istore = (IMAPStore) isession.getStore(account.starttls ? "imap" : "imaps");
                                            Helper.connect(context, istore, account);

                                            IMAPFolder ifolder = (IMAPFolder) istore.getFolder(folder.name);
                                            ifolder.delete(false);

                                            db.folder().deleteFolder(id);

                                            db.setTransactionSuccessful();
                                        } finally {
                                            db.endTransaction();

                                            if (istore != null)
                                                istore.close();
                                        }

                                        ServiceSynchronize.reload(getContext(), "delete folder");

                                        return null;
                                    }

                                    @Override
                                    protected void onLoaded(Bundle args, Void data) {
                                        getFragmentManager().popBackStack();
                                    }

                                    @Override
                                    protected void onException(Bundle args, Throwable ex) {
                                        Helper.setViewsEnabled(view, true);
                                        btnSave.setEnabled(true);
                                        ibDelete.setEnabled(true);
                                        pbSave.setVisibility(View.GONE);

                                        if (ex instanceof IllegalArgumentException)
                                            Snackbar.make(view, ex.getMessage(), Snackbar.LENGTH_LONG).show();
                                        else
                                            Helper.unexpectedError(getContext(), ex);
                                    }
                                }.load(FragmentFolder.this, args);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });

        // Initialize
        Helper.setViewsEnabled(view, false);
        btnSave.setEnabled(false);
        ibDelete.setEnabled(false);
        ibDelete.setVisibility(View.GONE);
        pbSave.setVisibility(View.GONE);
        pbWait.setVisibility(View.VISIBLE);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Observe
        DB.getInstance(getContext()).folder().liveFolder(id).observe(getViewLifecycleOwner(), new Observer<EntityFolder>() {
            private boolean once = false;

            @Override
            public void onChanged(@Nullable final EntityFolder folder) {
                if (once)
                    return;
                once = true;

                if (savedInstanceState == null) {
                    etRename.setText(folder == null ? null : folder.name);
                    etDisplay.setText(folder == null ? null : (folder.display == null ? folder.name : folder.display));
                    etDisplay.setHint(folder == null ? null : folder.name);
                    cbHide.setChecked(folder == null ? false : folder.hide);
                    cbUnified.setChecked(folder == null ? false : folder.unified);
                    cbSynchronize.setChecked(folder == null || folder.synchronize);
                    etAfter.setText(Integer.toString(folder == null ? EntityFolder.DEFAULT_USER_SYNC : folder.after));
                }

                // Consider previous save as cancelled
                pbWait.setVisibility(View.GONE);
                Helper.setViewsEnabled(view, true);
                etRename.setEnabled(folder == null || EntityFolder.USER.equals(folder.type));
                btnSave.setEnabled(true);
                ibDelete.setVisibility(folder == null || !EntityFolder.USER.equals(folder.type) ? View.GONE : View.VISIBLE);
            }
        });
    }
}
