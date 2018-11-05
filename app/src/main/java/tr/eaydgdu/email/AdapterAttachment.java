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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import tr.eaydgdu.email.database.DB;

public class AdapterAttachment extends RecyclerView.Adapter<AdapterAttachment.ViewHolder> {
    private Context context;
    private LifecycleOwner owner;
    private boolean readonly;
    private boolean debug;

    private List<EntityAttachment> all = new ArrayList<>();
    private List<EntityAttachment> filtered = new ArrayList<>();

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        View itemView;
        ImageView ivDelete;
        TextView tvName;
        TextView tvSize;
        ImageView ivStatus;
        ImageView ivSave;
        TextView tvType;
        ProgressBar progressbar;

        ViewHolder(View itemView) {
            super(itemView);

            this.itemView = itemView;
            ivDelete = itemView.findViewById(R.id.ivDelete);
            tvName = itemView.findViewById(R.id.tvName);
            tvSize = itemView.findViewById(R.id.tvSize);
            ivStatus = itemView.findViewById(R.id.ivStatus);
            ivSave = itemView.findViewById(R.id.ivSave);
            tvType = itemView.findViewById(R.id.tvType);
            progressbar = itemView.findViewById(R.id.progressbar);
        }

        private void wire() {
            itemView.setOnClickListener(this);
            ivDelete.setOnClickListener(this);
            ivSave.setOnClickListener(this);
        }

        private void unwire() {
            itemView.setOnClickListener(null);
            ivDelete.setOnClickListener(null);
            ivSave.setOnClickListener(null);
        }

        private void bindTo(EntityAttachment attachment) {
            ivDelete.setVisibility(readonly ? View.GONE : View.VISIBLE);
            tvName.setText(attachment.name);

            if (attachment.size != null)
                tvSize.setText(Helper.humanReadableByteCount(attachment.size, true));
            tvSize.setVisibility(attachment.size == null ? View.GONE : View.VISIBLE);

            if (attachment.available) {
                ivStatus.setImageResource(R.drawable.baseline_visibility_24);
                ivStatus.setVisibility(View.VISIBLE);
            } else {
                if (attachment.progress == null) {
                    ivStatus.setImageResource(R.drawable.baseline_cloud_download_24);
                    ivStatus.setVisibility(View.VISIBLE);
                } else
                    ivStatus.setVisibility(View.GONE);
            }

            ivSave.setVisibility(readonly && attachment.available ? View.VISIBLE : View.GONE);

            if (attachment.progress != null)
                progressbar.setProgress(attachment.progress);
            progressbar.setVisibility(
                    attachment.progress == null || attachment.available ? View.GONE : View.VISIBLE);

            tvType.setText(attachment.type + " " + attachment.cid);
            tvType.setVisibility(debug ? View.VISIBLE : View.GONE);
        }

        @Override
        public void onClick(View view) {
            int pos = getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION)
                return;
            final EntityAttachment attachment = filtered.get(pos);

            if (view.getId() == R.id.ivDelete) {
                Bundle args = new Bundle();
                args.putLong("id", attachment.id);

                new SimpleTask<Void>() {
                    @Override
                    protected Void onLoad(Context context, Bundle args) {
                        DB.getInstance(context).attachment().deleteAttachment(attachment.id);
                        EntityAttachment.getFile(context, attachment.id).delete();
                        return null;
                    }
                }.load(context, owner, args);

            } else if (view.getId() == R.id.ivSave) {
                LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
                lbm.sendBroadcast(
                        new Intent(ActivityView.ACTION_STORE_ATTACHMENT)
                                .putExtra("id", attachment.id)
                                .putExtra("name", attachment.name)
                                .putExtra("type", attachment.type));

            } else {
                if (attachment.available) {
                    // Build file name
                    File file = EntityAttachment.getFile(context, attachment.id);

                    // https://developer.android.com/reference/android/support/v4/content/FileProvider
                    Uri uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID, file);
                    Log.i(Helper.TAG, "uri=" + uri);

                    // Build intent
                    final Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, attachment.type);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    if (!TextUtils.isEmpty(attachment.name))
                        intent.putExtra(Intent.EXTRA_TITLE, attachment.name);
                    Log.i(Helper.TAG, "Sharing " + file + " type=" + attachment.type);
                    Log.i(Helper.TAG, "Intent=" + intent);

                    // Set permissions
                    List<ResolveInfo> targets = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                    for (ResolveInfo resolveInfo : targets) {
                        Log.i(Helper.TAG, "Target=" + resolveInfo);
                        context.grantUriPermission(resolveInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }

                    // Check if viewer available
                    if (targets.size() == 0) {
                        Toast.makeText(context, context.getString(R.string.title_no_viewer, attachment.type), Toast.LENGTH_LONG).show();
                        return;
                    }

                    Helper.view(context, intent);
                } else {
                    if (attachment.progress == null) {
                        Bundle args = new Bundle();
                        args.putLong("id", attachment.id);
                        args.putLong("message", attachment.message);
                        args.putInt("sequence", attachment.sequence);

                        new SimpleTask<Void>() {
                            @Override
                            protected Void onLoad(Context context, Bundle args) {
                                long id = args.getLong("id");
                                long message = args.getLong("message");
                                long sequence = args.getInt("sequence");

                                DB db = DB.getInstance(context);
                                try {
                                    db.beginTransaction();

                                    db.attachment().setProgress(id, 0);

                                    EntityMessage msg = db.message().getMessage(message);
                                    EntityOperation.queue(db, msg, EntityOperation.ATTACHMENT, sequence);

                                    db.setTransactionSuccessful();
                                } finally {
                                    db.endTransaction();
                                }

                                EntityOperation.process(context);

                                return null;
                            }
                        }.load(context, owner, args);
                    }
                }
            }
        }
    }

    AdapterAttachment(Context context, LifecycleOwner owner, boolean readonly) {
        this.context = context;
        this.owner = owner;
        this.readonly = readonly;
        this.debug = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("debug", false);
        setHasStableIds(true);
    }

    public void set(@NonNull List<EntityAttachment> attachments) {
        Log.i(Helper.TAG, "Set attachments=" + attachments.size());

        Collections.sort(attachments, new Comparator<EntityAttachment>() {
            @Override
            public int compare(EntityAttachment a1, EntityAttachment a2) {
                return a1.sequence.compareTo(a2.sequence);
            }
        });

        all = attachments;

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new MessageDiffCallback(filtered, all));

        filtered.clear();
        filtered.addAll(all);

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

    private class MessageDiffCallback extends DiffUtil.Callback {
        private List<EntityAttachment> prev;
        private List<EntityAttachment> next;

        MessageDiffCallback(List<EntityAttachment> prev, List<EntityAttachment> next) {
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
            EntityAttachment a1 = prev.get(oldItemPosition);
            EntityAttachment a2 = next.get(newItemPosition);
            return a1.id.equals(a2.id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            EntityAttachment a1 = prev.get(oldItemPosition);
            EntityAttachment a2 = next.get(newItemPosition);
            return a1.equals(a2);
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
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_attachment, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.unwire();

        EntityAttachment attachment = filtered.get(position);
        holder.bindTo(attachment);

        holder.wire();
    }
}
