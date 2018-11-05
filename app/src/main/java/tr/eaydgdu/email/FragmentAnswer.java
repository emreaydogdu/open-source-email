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
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.lifecycle.Observer;
import tr.eaydgdu.email.database.DB;

public class FragmentAnswer extends FragmentEx {
    private ViewGroup view;
    private EditText etName;
    private EditText etText;
    private BottomNavigationView bottom_navigation;
    private ProgressBar pbWait;
    private Group grpReady;

    private long id = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get arguments
        Bundle args = getArguments();
        id = (args == null ? -1 : args.getLong("id", -1));
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = (ViewGroup) inflater.inflate(R.layout.fragment_answer, container, false);

        // Get controls
        etName = view.findViewById(R.id.etName);
        etText = view.findViewById(R.id.etText);
        etText = view.findViewById(R.id.etText);
        bottom_navigation = view.findViewById(R.id.bottom_navigation);
        pbWait = view.findViewById(R.id.pbWait);
        grpReady = view.findViewById(R.id.grpReady);

        bottom_navigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.action_delete:
                        onActionTrash();
                        return true;
                    case R.id.action_save:
                        onActionSave();
                        return true;
                }
                return false;
            }
        });

        // Initialize
        grpReady.setVisibility(View.GONE);
        pbWait.setVisibility(View.VISIBLE);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        DB.getInstance(getContext()).answer().liveAnswer(id).observe(getViewLifecycleOwner(), new Observer<EntityAnswer>() {
            @Override
            public void onChanged(EntityAnswer answer) {
                etName.setText(answer == null ? null : answer.name);
                etText.setText(answer == null ? null : Html.fromHtml(answer.text));
                bottom_navigation.findViewById(R.id.action_delete).setVisibility(answer == null ? View.GONE : View.VISIBLE);

                pbWait.setVisibility(View.GONE);
                grpReady.setVisibility(View.VISIBLE);
            }
        });
    }

    private void onActionTrash() {
        Helper.setViewsEnabled(view, false);

        Bundle args = new Bundle();
        args.putLong("id", id);

        new SimpleTask<Void>() {
            @Override
            protected Void onLoad(Context context, Bundle args) {
                long id = args.getLong("id");
                DB.getInstance(context).answer().deleteAnswer(id);
                return null;
            }

            @Override
            protected void onLoaded(Bundle args, Void data) {
                finish();
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Helper.setViewsEnabled(view, true);
                Helper.unexpectedError(getContext(), ex);
            }
        }.load(this, args);
    }

    private void onActionSave() {
        Helper.setViewsEnabled(view, false);

        Bundle args = new Bundle();
        args.putLong("id", id);
        args.putString("name", etName.getText().toString());
        args.putString("text", Html.toHtml(etText.getText()));

        new SimpleTask<Void>() {
            @Override
            protected Void onLoad(Context context, Bundle args) {
                long id = args.getLong("id");
                String name = args.getString("name");
                String text = args.getString("text");

                DB db = DB.getInstance(context);
                if (id < 0) {
                    EntityAnswer answer = new EntityAnswer();
                    answer.name = name;
                    answer.text = text;
                    answer.id = db.answer().insertAnswer(answer);
                } else {
                    EntityAnswer answer = db.answer().getAnswer(id);
                    answer.name = name;
                    answer.text = text;
                    db.answer().updateAnswer(answer);
                }

                return null;
            }

            @Override
            protected void onLoaded(Bundle args, Void data) {
                finish();
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                Helper.setViewsEnabled(view, true);
                Helper.unexpectedError(getContext(), ex);
            }
        }.load(this, args);
    }
}
