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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

public class AdapterLog extends RecyclerView.Adapter<AdapterLog.ViewHolder> {
    private Context context;

    private List<EntityLog> all = new ArrayList<>();
    private List<EntityLog> filtered = new ArrayList<>();

    private static final DateFormat DF = SimpleDateFormat.getTimeInstance();

    public class ViewHolder extends RecyclerView.ViewHolder {
        View itemView;
        TextView tvTime;
        TextView tvData;

        ViewHolder(View itemView) {
            super(itemView);

            this.itemView = itemView;
            tvTime = itemView.findViewById(R.id.tvTime);
            tvData = itemView.findViewById(R.id.tvData);
        }

        private void bindTo(EntityLog log) {
            tvTime.setText(DF.format(log.time));
            tvData.setText(log.data);
        }
    }


    AdapterLog(Context context) {
        this.context = context;
        setHasStableIds(true);
    }

    public void set(@NonNull List<EntityLog> logs) {
        Log.i(Helper.TAG, "Set logs=" + logs.size());

        all = logs;

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
        private List<EntityLog> prev;
        private List<EntityLog> next;

        MessageDiffCallback(List<EntityLog> prev, List<EntityLog> next) {
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
            EntityLog l1 = prev.get(oldItemPosition);
            EntityLog l2 = next.get(newItemPosition);
            return l1.id.equals(l2.id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            EntityLog l1 = prev.get(oldItemPosition);
            EntityLog l2 = next.get(newItemPosition);
            return l1.equals(l2);
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
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_log, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EntityLog log = filtered.get(position);
        holder.bindTo(log);
    }
}
