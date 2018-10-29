package eu.faircode.email;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.lifecycle.LifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

public class AdapterFolder extends RecyclerView.Adapter<AdapterFolder.ViewHolder> {
    private Context context;
    private LifecycleOwner owner;
    private String accountState = null;

    private List<TupleFolderEx> all = new ArrayList<>();
    private List<TupleFolderEx> filtered = new ArrayList<>();

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        View itemView;
        ImageView ivState;
        TextView tvName;
        TextView tvMessages;
        ImageView ivUnified;
        TextView tvType;
        TextView tvAfter;
        ImageView ivSync;
        TextView tvError;

        private final static int action_synchronize_now = 1;
        private final static int action_delete_local = 2;
        private final static int action_empty_trash = 3;
        private final static int action_edit_properties = 4;

        ViewHolder(View itemView) {
            super(itemView);

            this.itemView = itemView.findViewById(R.id.clItem);
            ivState = itemView.findViewById(R.id.ivState);
            tvName = itemView.findViewById(R.id.tvName);
            tvMessages = itemView.findViewById(R.id.tvMessages);
            ivUnified = itemView.findViewById(R.id.ivUnified);
            tvType = itemView.findViewById(R.id.tvType);
            tvAfter = itemView.findViewById(R.id.tvAfter);
            ivSync = itemView.findViewById(R.id.ivSync);
            tvError = itemView.findViewById(R.id.tvError);
        }

        private void wire() {
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        private void unwire() {
            itemView.setOnClickListener(null);
            itemView.setOnLongClickListener(null);
        }

        private void bindTo(TupleFolderEx folder) {
            itemView.setAlpha(folder.hide ? 0.5f : 1.0f);

            if ("connected".equals(folder.state))
                ivState.setImageResource(R.drawable.baseline_cloud_24);
            else if ("connecting".equals(folder.state))
                ivState.setImageResource(R.drawable.baseline_cloud_queue_24);
            else if ("closing".equals(folder.state))
                ivState.setImageResource(R.drawable.baseline_close_24);
            else if ("syncing".equals(folder.state))
                ivState.setImageResource(R.drawable.baseline_compare_arrows_24);
            else if ("downloading".equals(folder.state))
                ivState.setImageResource(R.drawable.baseline_cloud_download_24);
            else
                ivState.setImageResource(R.drawable.baseline_cloud_off_24);
            ivState.setVisibility(folder.synchronize || folder.state != null ? View.VISIBLE : View.INVISIBLE);

            String name = (folder.display == null
                    ? Helper.localizeFolderName(context, folder.name)
                    : folder.display);
            if (folder.unseen > 0)
                tvName.setText(context.getString(R.string.title_folder_unseen, name, folder.unseen));
            else
                tvName.setText(name);
            tvName.setTypeface(null, folder.unseen > 0 ? Typeface.BOLD : Typeface.NORMAL);
            tvName.setTextColor(Helper.resolveColor(context, folder.unseen > 0 ? R.attr.colorUnread : android.R.attr.textColorSecondary));

            tvMessages.setText(String.format("%d/%d", folder.content, folder.messages));

            ivUnified.setVisibility(folder.unified ? View.VISIBLE : View.INVISIBLE);

            int resid = context.getResources().getIdentifier(
                    "title_folder_" + folder.type.toLowerCase(),
                    "string",
                    context.getPackageName());
            tvType.setText(resid > 0 ? context.getString(resid) : folder.type);

            if (folder.account == null) {
                tvAfter.setText(null);
                ivSync.setImageResource(R.drawable.baseline_sync_24);
            } else {
                tvAfter.setText(Integer.toString(folder.after));
                ivSync.setImageResource(folder.synchronize ? R.drawable.baseline_sync_24 : R.drawable.baseline_sync_disabled_24);
            }

            tvError.setText(folder.error);
            tvError.setVisibility(folder.error == null ? View.GONE : View.VISIBLE);
        }

        @Override
        public void onClick(View view) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION)
                return;

            TupleFolderEx folder = filtered.get(pos);

            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
            lbm.sendBroadcast(
                    new Intent(ActivityView.ACTION_VIEW_MESSAGES)
                            .putExtra("account", folder.account)
                            .putExtra("folder", folder.id));
        }

        @Override
        public boolean onLongClick(View v) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION)
                return false;

            final TupleFolderEx folder = filtered.get(pos);

            PopupMenu popupMenu = new PopupMenu(context, itemView);

            popupMenu.getMenu().add(Menu.NONE, action_synchronize_now, 1, R.string.title_synchronize_now);
            popupMenu.getMenu().findItem(action_synchronize_now).setEnabled("connected".equals(accountState));

            if (!EntityFolder.DRAFTS.equals(folder.type))
                popupMenu.getMenu().add(Menu.NONE, action_delete_local, 2, R.string.title_delete_local);
            if (EntityFolder.TRASH.equals(folder.type))
                popupMenu.getMenu().add(Menu.NONE, action_empty_trash, 3, R.string.title_empty_trash);

            if (folder.account != null)
                popupMenu.getMenu().add(Menu.NONE, action_edit_properties, 4, R.string.title_edit_properties);

            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem target) {
                    LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
                    switch (target.getItemId()) {
                        case action_synchronize_now:
                            Log.i(Helper.TAG, folder.name + " requesting sync");
                            lbm.sendBroadcast(
                                    new Intent(ServiceSynchronize.ACTION_SYNCHRONIZE_FOLDER)
                                            .setType("account/" + (folder.account == null ? "outbox" : Long.toString(folder.account)))
                                            .putExtra("folder", folder.id));
                            break;

                        case action_delete_local:
                            Bundle args = new Bundle();
                            args.putLong("id", folder.id);
                            args.putBoolean("outbox", folder.account == null);

                            new SimpleTask<Void>() {
                                @Override
                                protected Void onLoad(Context context, Bundle args) {
                                    long id = args.getLong("id");
                                    boolean outbox = args.getBoolean("outbox");
                                    Log.i(Helper.TAG, "Delete local messages outbox=" + outbox);
                                    if (outbox)
                                        DB.getInstance(context).message().deleteSeenMessages(id);
                                    else
                                        DB.getInstance(context).message().deleteLocalMessages(id);
                                    return null;
                                }

                                @Override
                                public void onException(Bundle args, Throwable ex) {
                                    Helper.unexpectedError(context, ex);
                                }
                            }.load(context, owner, args);

                            break;

                        case action_empty_trash:
                            new DialogBuilderLifecycle(context, owner)
                                    .setMessage(R.string.title_empty_trash_ask)
                                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {

                                            Bundle args = new Bundle();
                                            args.putLong("id", folder.id);

                                            new SimpleTask<Void>() {
                                                @Override
                                                protected Void onLoad(Context context, Bundle args) {
                                                    long id = args.getLong("id");

                                                    DB db = DB.getInstance(context);
                                                    try {
                                                        db.beginTransaction();

                                                        for (EntityMessage message : db.message().getMessageByFolder(id)) {
                                                            db.message().setMessageUiHide(message.id, true);
                                                            EntityOperation.queue(db, message, EntityOperation.DELETE);
                                                        }

                                                        db.setTransactionSuccessful();
                                                    } finally {
                                                        db.endTransaction();
                                                    }

                                                    EntityOperation.process(context);

                                                    return null;
                                                }

                                                @Override
                                                protected void onException(Bundle args, Throwable ex) {
                                                    Helper.unexpectedError(context, ex);
                                                }
                                            }.load(context, owner, args);
                                        }
                                    })
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show();
                            break;

                        case action_edit_properties:
                            lbm.sendBroadcast(
                                    new Intent(ActivityView.ACTION_EDIT_FOLDER)
                                            .putExtra("id", folder.id));
                            break;
                    }
                    return true;
                }
            });

            popupMenu.show();

            return true;
        }

    }

    AdapterFolder(Context context, LifecycleOwner owner) {
        this.context = context;
        this.owner = owner;
        setHasStableIds(true);
    }

    private boolean showAll = false;

    public void showHidden(boolean show) {
        showAll = show;
        set(all);
    }

    public void set(@NonNull List<TupleFolderEx> folders) {
        Log.i(Helper.TAG, "Set folders=" + folders.size());

        final Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc

        Collections.sort(folders, new Comparator<TupleFolderEx>() {
            @Override
            public int compare(TupleFolderEx f1, TupleFolderEx f2) {
                int s = Integer.compare(
                        EntityFolder.FOLDER_SORT_ORDER.indexOf(f1.type),
                        EntityFolder.FOLDER_SORT_ORDER.indexOf(f2.type));
                if (s != 0)
                    return s;
                int c = -f1.synchronize.compareTo(f2.synchronize);
                if (c != 0)
                    return c;
                return collator.compare(
                        f1.name == null ? "" : f1.name,
                        f2.name == null ? "" : f2.name);
            }
        });

        all = folders;

        List<TupleFolderEx> shown = new ArrayList<>();
        for (TupleFolderEx folder : folders)
            if (!folder.hide || showAll)
                shown.add(folder);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new MessageDiffCallback(filtered, shown));

        filtered.clear();
        filtered.addAll(shown);

        diff.dispatchUpdatesTo(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int count) {
                Log.i(Helper.TAG, "Inserted @" + position + " #" + count);
            }

            @Override
            public void onRemoved(int position, int count) {
                Log.i(Helper.TAG, "Removed @" + position + " #" + count);
            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {
                Log.i(Helper.TAG, "Moved " + fromPosition + ">" + toPosition);
            }

            @Override
            public void onChanged(int position, int count, Object payload) {
                Log.i(Helper.TAG, "Changed @" + position + " #" + count);
            }
        });
        diff.dispatchUpdatesTo(this);
    }

    public void setAccountState(String state) {
        this.accountState = state;
    }

    private class MessageDiffCallback extends DiffUtil.Callback {
        private List<TupleFolderEx> prev;
        private List<TupleFolderEx> next;

        MessageDiffCallback(List<TupleFolderEx> prev, List<TupleFolderEx> next) {
            this.prev = prev;
            this.next = next;
        }

        @Override
        public int getOldListSize() {
            return prev.size();
        }

        @Override
        public int getNewListSize() {
            return next.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            TupleFolderEx f1 = prev.get(oldItemPosition);
            TupleFolderEx f2 = next.get(newItemPosition);
            return f1.id.equals(f2.id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            TupleFolderEx f1 = prev.get(oldItemPosition);
            TupleFolderEx f2 = next.get(newItemPosition);
            return f1.equals(f2);
        }
    }

    @Override
    public long getItemId(int position) {
        return filtered.get(position).id;
    }

    @Override
    public int getItemCount() {
        return filtered.size();
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_folder, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.unwire();

        TupleFolderEx folder = filtered.get(position);
        holder.bindTo(folder);

        holder.wire();
    }
}
