package tr.eaydgdu.email.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import tr.eaydgdu.email.ActivitySetup;
import tr.eaydgdu.email.database.DB;
import tr.eaydgdu.email.EntityAccount;
import tr.eaydgdu.email.R;
import tr.eaydgdu.email.activities.ActivityFolder;
import tr.eaydgdu.email.views.BottomSheetDialog;
import tr.eaydgdu.email.views.CircularImageView;

public class MenuFragment extends BottomSheetDialog implements View.OnClickListener {

    private ListView listView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_menu, container, false);

        v.findViewById(R.id.settings).setOnClickListener(this);

        listView = v.findViewById(R.id.accounts);

        DB.getInstance(getContext()).account().liveAccounts(true).observe(this, new Observer<List<EntityAccount>>() {
            @Override
            public void onChanged(@Nullable List<EntityAccount> accounts) {
                if (accounts == null) accounts = new ArrayList<>();

                ArrayAdapterDrawer drawerArray = new ArrayAdapterDrawer(getContext());

                final Collator collator = Collator.getInstance(Locale.getDefault());
                collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc

                Collections.sort(accounts, new Comparator<EntityAccount>() {
                    @Override
                    public int compare(EntityAccount a1, EntityAccount a2) {
                        return collator.compare(a1.name, a2.name);
                    }
                });

                for (EntityAccount account : accounts)
                    drawerArray.add(new DrawerItem(R.layout.item_drawer, -1, R.drawable.ic_user, account.name, account.id));


                listView.setAdapter(drawerArray);
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DrawerItem item = (DrawerItem) parent.getAdapter().getItem(position);
                Intent intent = new Intent(getContext(), ActivityFolder.class);
                Bundle b = new Bundle();
                b.putLong("account",(long) item.getData());
                intent.putExtras(b);
                startActivity(intent);
            }
        });


        return v;
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.settings:
                startActivity(new Intent(getContext(), ActivitySetup.class));
                break;

        }
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

}
