package tr.eaydgdu.email;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import tr.eaydgdu.email.database.DB;

public class FragmentMessages extends FragmentEx {

    private ViewGroup view;
    private View popupAnchor;
    private TextView tvNoEmail;
    private RecyclerView rvMessage;
    private BottomNavigationView bottom_navigation;
    private Group grpSupport;
    private Group grpHintSupport;
    private Group grpHintSwipe;
    private Group grpHintSelect;
    private Group grpReady;
    private FloatingActionButton fab;
    private FloatingActionButton fabMove;

    private long folder = -1;
    private long account = -1;
    private String thread = null;
    private String search = null;

    private long primary = -1;
    private boolean outbox = false;
    private boolean connected = false;
    private AdapterMessage adapter;
    private List<Long> archives = new ArrayList<>();
    private List<Long> trashes = new ArrayList<>();

    private AdapterMessage.ViewType viewType;
    private SelectionTracker<Long> selectionTracker = null;
    private LiveData<PagedList<TupleMessageEx>> messages = null;

    private int autoCount = 0;
    private boolean autoExpand = true;
    private List<Long> expanded = new ArrayList<>();
    private List<Long> headers = new ArrayList<>();
    private List<Long> images = new ArrayList<>();

    private BoundaryCallbackMessages searchCallback = null;

    private ExecutorService executor = Executors.newCachedThreadPool(Helper.backgroundThreadFactory);

    private static final int LOCAL_PAGE_SIZE = 50;
    private static final int REMOTE_PAGE_SIZE = 10;
    private static final int UNDO_TIMEOUT = 5000; // milliseconds

    private ShimmerFrameLayout mShimmerView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get arguments
        Bundle args = getArguments();
        account = args.getLong("account", -1);
        folder = args.getLong("folder", -1);
        thread = args.getString("thread");
        search = args.getString("search");

        if (TextUtils.isEmpty(search))
            if (thread == null)
                if (folder < 0)
                    viewType = AdapterMessage.ViewType.UNIFIED;
                else
                    viewType = AdapterMessage.ViewType.FOLDER;
            else
                viewType = AdapterMessage.ViewType.THREAD;
        else
            viewType = AdapterMessage.ViewType.SEARCH;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = (ViewGroup) inflater.inflate(R.layout.fragment_messages, container, false);

        setHasOptionsMenu(true);

        // Get controls
        popupAnchor = view.findViewById(R.id.popupAnchor);
        tvNoEmail = view.findViewById(R.id.tvNoEmail);
        rvMessage = view.findViewById(R.id.rvFolder);
        bottom_navigation = view.findViewById(R.id.bottom_navigation);
        mShimmerView = view.findViewById(R.id.shimmer_view_container);
        grpSupport = view.findViewById(R.id.grpSupport);
        grpHintSupport = view.findViewById(R.id.grpHintSupport);
        grpHintSwipe = view.findViewById(R.id.grpHintSwipe);
        grpHintSelect = view.findViewById(R.id.grpHintSelect);
        grpReady = view.findViewById(R.id.grpReady);
        fab = view.findViewById(R.id.fab);
        fabMove = view.findViewById(R.id.fabMove);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());


        rvMessage.setHasFixedSize(false);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        rvMessage.setLayoutManager(llm);

        adapter = new AdapterMessage(getContext(), getViewLifecycleOwner(), getFragmentManager(), viewType, new AdapterMessage.IProperties() {
            @Override
            public void setExpanded(long id, boolean expand) {
                if (expand) {
                    expanded.add(id);
                    handleExpand(id);
                } else
                    expanded.remove(id);
            }

            @Override
            public void setHeaders(long id, boolean show) {
                if (show)
                    headers.add(id);
                else
                    headers.remove(id);
            }

            @Override
            public void setImages(long id, boolean show) {
                if (show)
                    images.add(id);
                else
                    images.remove(id);
            }

            @Override
            public boolean isExpanded(long id) {
                return expanded.contains(id);
            }

            @Override
            public boolean showHeaders(long id) {
                return headers.contains(id);
            }

            @Override
            public boolean showImages(long id) {
                return images.contains(id);
            }
        });
        rvMessage.setAdapter(adapter);

        if (viewType == AdapterMessage.ViewType.FOLDER) {
            selectionTracker = new SelectionTracker.Builder<>(
                    "messages-selection",
                    rvMessage,
                    new ItemKeyProviderMessage(rvMessage),
                    new ItemDetailsLookupMessage(rvMessage),
                    StorageStrategy.createLongStorage())
                    .withSelectionPredicate(new SelectionPredicateMessage(rvMessage))
                    .build();
            adapter.setSelectionTracker(selectionTracker);

            selectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
                @Override
                public void onSelectionChanged() {
                    if (selectionTracker.hasSelection())
                        fabMove.show();
                    else
                        fabMove.hide();
                }
            });
        }

        new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                if (!prefs.getBoolean("swipe", true))
                    return 0;

                if (selectionTracker != null && selectionTracker.hasSelection())
                    return 0;

                int pos = viewHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION)
                    return 0;

                TupleMessageEx message = ((AdapterMessage) rvMessage.getAdapter()).getCurrentList().get(pos);
                if (message == null ||
                        expanded.contains(message.id) ||
                        EntityFolder.OUTBOX.equals(message.folderType))
                    return 0;

                int flags = 0;
                if (archives.contains(message.account))
                    flags |= ItemTouchHelper.RIGHT;
                if (trashes.contains(message.account))
                    flags |= ItemTouchHelper.LEFT;

                return makeMovementFlags(0, flags);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onChildDraw(Canvas canvas, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                int pos = viewHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION)
                    return;

                TupleMessageEx message = ((AdapterMessage) rvMessage.getAdapter()).getCurrentList().get(pos);
                if (message == null)
                    return;

                boolean inbox = (EntityFolder.ARCHIVE.equals(message.folderType) || EntityFolder.TRASH.equals(message.folderType));

                View itemView = viewHolder.itemView;
                int margin = Math.round(12 * (getResources().getDisplayMetrics().density));

                if (dX > margin) {
                    // Right swipe
                    Drawable d = getResources().getDrawable(
                            inbox ? R.drawable.baseline_move_to_inbox_24 : R.drawable.baseline_archive_24,
                            getContext().getTheme());
                    int padding = (itemView.getHeight() - d.getIntrinsicHeight());
                    d.setBounds(
                            itemView.getLeft() + margin,
                            itemView.getTop() + padding / 2,
                            itemView.getLeft() + margin + d.getIntrinsicWidth(),
                            itemView.getTop() + padding / 2 + d.getIntrinsicHeight());
                    d.draw(canvas);
                } else if (dX < -margin) {
                    // Left swipe
                    Drawable d = getResources().getDrawable(inbox ? R.drawable.baseline_move_to_inbox_24 : R.drawable.baseline_delete_24, getContext().getTheme());
                    int padding = (itemView.getHeight() - d.getIntrinsicHeight());
                    d.setBounds(
                            itemView.getLeft() + itemView.getWidth() - d.getIntrinsicWidth() - margin,
                            itemView.getTop() + padding / 2,
                            itemView.getLeft() + itemView.getWidth() - margin,
                            itemView.getTop() + padding / 2 + d.getIntrinsicHeight());
                    d.draw(canvas);
                }

                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION)
                    return;

                TupleMessageEx message = ((AdapterMessage) rvMessage.getAdapter()).getCurrentList().get(pos);
                if (message == null)
                    return;
                Log.i(Helper.TAG, "Swiped dir=" + direction + " message=" + message.id);

                Bundle args = new Bundle();
                args.putLong("id", message.id);
                args.putBoolean("thread", viewType != AdapterMessage.ViewType.THREAD);
                args.putInt("direction", direction);

                new SimpleTask<MessageTarget>() {
                    @Override
                    protected MessageTarget onLoad(Context context, Bundle args) {
                        long id = args.getLong("id");
                        boolean thread = args.getBoolean("thread");
                        int direction = args.getInt("direction");

                        MessageTarget result = new MessageTarget();
                        EntityFolder target = null;

                        // Get target folder and hide message
                        DB db = DB.getInstance(context);
                        try {
                            db.beginTransaction();

                            EntityMessage message = db.message().getMessage(id);

                            EntityFolder folder = db.folder().getFolder(message.folder);
                            if (EntityFolder.ARCHIVE.equals(folder.type) || EntityFolder.TRASH.equals(folder.type))
                                target = db.folder().getFolderByType(message.account, EntityFolder.INBOX);
                            else {
                                if (direction == ItemTouchHelper.RIGHT)
                                    target = db.folder().getFolderByType(message.account, EntityFolder.ARCHIVE);
                                if (direction == ItemTouchHelper.LEFT || target == null)
                                    target = db.folder().getFolderByType(message.account, EntityFolder.TRASH);
                                if (target == null)
                                    target = db.folder().getFolderByType(message.account, EntityFolder.INBOX);
                            }

                            result.target = target.name;
                            result.display = (target.display == null ? target.name : target.display);

                            if (thread) {
                                List<EntityMessage> messages =
                                        db.message().getMessageByThread(message.account, message.thread);
                                for (EntityMessage threaded : messages) {
                                    if (!threaded.ui_hide && threaded.folder.equals(message.folder))
                                        result.ids.add(threaded.id);
                                }
                            } else
                                result.ids.add(message.id);

                            for (long mid : result.ids) {
                                Log.i(Helper.TAG, "Move hide id=" + mid + " target=" + result.target);
                                db.message().setMessageUiHide(mid, true);
                            }

                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }

                        return result;
                    }

                    @Override
                    protected void onLoaded(final Bundle args, final MessageTarget result) {
                        // Show undo snackbar
                        final Snackbar snackbar = Snackbar.make(
                                view,
                                getString(R.string.title_moving, Helper.localizeFolderName(getContext(), result.display)),
                                Snackbar.LENGTH_INDEFINITE);
                        snackbar.setAction(R.string.title_undo, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                snackbar.dismiss();

                                Bundle args = new Bundle();
                                args.putSerializable("result", result);

                                // Show message again
                                new SimpleTask<Void>() {
                                    @Override
                                    protected Void onLoad(Context context, Bundle args) {
                                        MessageTarget result = (MessageTarget) args.getSerializable("result");
                                        for (long id : result.ids) {
                                            Log.i(Helper.TAG, "Move undo id=" + id);
                                            DB.getInstance(context).message().setMessageUiHide(id, false);
                                        }
                                        return null;
                                    }

                                    @Override
                                    protected void onException(Bundle args, Throwable ex) {
                                        super.onException(args, ex);
                                    }
                                }.load(FragmentMessages.this, args);
                            }
                        });
                        snackbar.show();

                        // Wait
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Log.i(Helper.TAG, "Move timeout");

                                // Remove snackbar
                                if (snackbar.isShown())
                                    snackbar.dismiss();

                                final Bundle args = new Bundle();
                                args.putSerializable("result", result);

                                // Process move in a thread
                                // - the fragment could be gone
                                executor.submit(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            MessageTarget result = (MessageTarget) args.getSerializable("result");

                                            DB db = DB.getInstance(snackbar.getContext());
                                            try {
                                                db.beginTransaction();

                                                for (long id : result.ids) {
                                                    EntityMessage message = db.message().getMessage(id);
                                                    if (message != null && message.ui_hide) {
                                                        Log.i(Helper.TAG, "Move id=" + id + " target=" + result.target);
                                                        EntityFolder folder = db.folder().getFolderByName(message.account, result.target);
                                                        EntityOperation.queue(db, message, EntityOperation.MOVE, folder.id);
                                                    }
                                                }

                                                db.setTransactionSuccessful();
                                            } finally {
                                                db.endTransaction();
                                            }

                                            EntityOperation.process(snackbar.getContext());

                                        } catch (Throwable ex) {
                                            Log.e(Helper.TAG, ex + "\n" + Log.getStackTraceString(ex));
                                        }
                                    }
                                });
                            }
                        }, UNDO_TIMEOUT);
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Helper.unexpectedError(getContext(), ex);
                    }
                }.load(FragmentMessages.this, args);
            }

            class MessageTarget implements Serializable {
                List<Long> ids = new ArrayList<>();
                String target;
                String display;
            }
        }).attachToRecyclerView(rvMessage);

        bottom_navigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                ViewModelMessages.Target[] pn = (ViewModelMessages.Target[]) bottom_navigation.getTag();
                ViewModelMessages.Target target = (menuItem.getItemId() == R.id.action_prev ? pn[0] : pn[1]);
                LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getContext());
                lbm.sendBroadcast(
                        new Intent(ActivityView.ACTION_VIEW_THREAD)
                                .putExtra("account", target.account)
                                .putExtra("thread", target.thread));
                return true;
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getContext(), ActivityCompose.class)
                        .putExtra("action", "new")
                        .putExtra("account", (Long) fab.getTag())
                );
            }
        });

        fabMove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle args = new Bundle();
                args.putLong("folder", folder);

                new SimpleTask<List<EntityFolder>>() {
                    @Override
                    protected List<EntityFolder> onLoad(Context context, Bundle args) {
                        long folder = args.getLong("folder");
                        DB db = DB.getInstance(context);

                        EntityFolder source = db.folder().getFolder(folder);
                        List<EntityFolder> folders = db.folder().getFolders(source.account);
                        List<EntityFolder> targets = new ArrayList<>();
                        for (EntityFolder f : folders)
                            if (!f.id.equals(folder) && !EntityFolder.DRAFTS.equals(f.type))
                                targets.add(f);

                        final Collator collator = Collator.getInstance(Locale.getDefault());
                        collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc

                        Collections.sort(targets, new Comparator<EntityFolder>() {
                            @Override
                            public int compare(EntityFolder f1, EntityFolder f2) {
                                int s = Integer.compare(
                                        EntityFolder.FOLDER_SORT_ORDER.indexOf(f1.type),
                                        EntityFolder.FOLDER_SORT_ORDER.indexOf(f2.type));
                                if (s != 0)
                                    return s;
                                return collator.compare(
                                        f1.name == null ? "" : f1.name,
                                        f2.name == null ? "" : f2.name);
                            }
                        });

                        return targets;
                    }

                    @Override
                    protected void onLoaded(final Bundle args, List<EntityFolder> folders) {
                        PopupMenu popupMenu = new PopupMenu(getContext(), popupAnchor);

                        int order = 0;
                        for (EntityFolder folder : folders) {
                            String name = (folder.display == null
                                    ? Helper.localizeFolderName(getContext(), folder.name)
                                    : folder.display);
                            popupMenu.getMenu().add(Menu.NONE, folder.id.intValue(), order++, name);
                        }

                        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(final MenuItem target) {
                                MutableSelection<Long> selection = new MutableSelection<>();
                                selectionTracker.copySelection(selection);

                                long[] ids = new long[selection.size()];
                                int i = 0;
                                for (Long id : selection)
                                    ids[i++] = id;

                                selectionTracker.clearSelection();

                                args.putLongArray("ids", ids);
                                args.putLong("target", target.getItemId());

                                new SimpleTask<Void>() {
                                    @Override
                                    protected Void onLoad(Context context, Bundle args) {
                                        long[] ids = args.getLongArray("ids");
                                        long target = args.getLong("target");

                                        DB db = DB.getInstance(context);
                                        try {
                                            db.beginTransaction();

                                            for (long id : ids) {
                                                db.message().setMessageUiHide(id, true);

                                                EntityMessage message = db.message().getMessage(id);
                                                EntityOperation.queue(db, message, EntityOperation.MOVE, target);
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
                                        Helper.unexpectedError(getContext(), ex);
                                    }
                                }.load(FragmentMessages.this, args);

                                return true;
                            }
                        });

                        popupMenu.show();
                    }

                    @Override
                    protected void onException(Bundle args, Throwable ex) {
                        Helper.unexpectedError(getContext(), ex);
                    }
                }.load(FragmentMessages.this, args);
            }
        });

        // Initialize
        tvNoEmail.setVisibility(View.GONE);
        bottom_navigation.setVisibility(View.GONE);
        grpReady.setVisibility(View.GONE);
        rvMessage.setVisibility(View.INVISIBLE);
        mShimmerView.startShimmer();

        fab.hide();
        fabMove.hide();

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("autoExpand", autoExpand);
        outState.putInt("autoCount", autoCount);
        outState.putLongArray("expanded", Helper.toLongArray(expanded));
        outState.putLongArray("headers", Helper.toLongArray(headers));
        outState.putLongArray("images", Helper.toLongArray(images));
        if (selectionTracker != null)
            selectionTracker.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            autoExpand = savedInstanceState.getBoolean("autoExpand");
            autoCount = savedInstanceState.getInt("autoCount");
            expanded = Helper.fromLongArray(savedInstanceState.getLongArray("expanded"));
            headers = Helper.fromLongArray(savedInstanceState.getLongArray("headers"));
            images = Helper.fromLongArray(savedInstanceState.getLongArray("images"));
            if (selectionTracker != null)
                selectionTracker.onRestoreInstanceState(savedInstanceState);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        grpHintSupport.setVisibility(prefs.getBoolean("app_support", false) || viewType != AdapterMessage.ViewType.UNIFIED ? View.GONE : View.VISIBLE);
        grpHintSwipe.setVisibility(prefs.getBoolean("message_swipe", false) || viewType == AdapterMessage.ViewType.THREAD ? View.GONE : View.VISIBLE);
        grpHintSelect.setVisibility(prefs.getBoolean("message_select", false) || viewType != AdapterMessage.ViewType.FOLDER ? View.GONE : View.VISIBLE);

        final DB db = DB.getInstance(getContext());

        // Primary account
        db.account().livePrimaryAccount().observe(getViewLifecycleOwner(), new Observer<EntityAccount>() {
            @Override
            public void onChanged(EntityAccount account) {
                primary = (account == null ? -1 : account.id);
                connected = (account != null && "connected".equals(account.state));
                getActivity().invalidateOptionsMenu();
            }
        });

        // Folder
        switch (viewType) {
            case UNIFIED:
                db.folder().liveUnified().observe(getViewLifecycleOwner(), new Observer<List<TupleFolderEx>>() {
                    @Override
                    public void onChanged(List<TupleFolderEx> folders) {
                        int unseen = 0;
                        if (folders != null)
                            for (TupleFolderEx folder : folders)
                                unseen += folder.unseen;
                        String name = getString(R.string.title_folder_unified);
                        if (unseen > 0)
                            setSubtitle(getString(R.string.title_folder_unseen, name, unseen));
                        else
                            setSubtitle(name);
                    }
                });
                break;

            case FOLDER:
                db.folder().liveFolderEx(folder).observe(getViewLifecycleOwner(), new Observer<TupleFolderEx>() {
                    @Override
                    public void onChanged(@Nullable TupleFolderEx folder) {
                        if (folder == null)
                            setSubtitle(null);
                        else {
                            String name = (folder.display == null
                                    ? Helper.localizeFolderName(getContext(), folder.name)
                                    : folder.display);
                            if (folder.unseen > 0)
                                setSubtitle(getString(R.string.title_folder_unseen, name, folder.unseen));
                            else
                                setSubtitle(name);

                            outbox = EntityFolder.OUTBOX.equals(folder.type);
                            getActivity().invalidateOptionsMenu();
                        }
                    }
                });
                break;

            case THREAD:
                setSubtitle(R.string.title_folder_thread);
                break;

            case SEARCH:
                setSubtitle(getString(R.string.title_searching, search));
                break;
        }

        // Folders and messages
        db.folder().liveSystemFolders(account).observe(getViewLifecycleOwner(), new Observer<List<EntityFolder>>() {
            @Override
            public void onChanged(List<EntityFolder> folders) {
                if (folders == null)
                    folders = new ArrayList<>();

                archives.clear();
                trashes.clear();

                for (EntityFolder folder : folders)
                    if (EntityFolder.ARCHIVE.equals(folder.type))
                        archives.add(folder.account);
                    else if (EntityFolder.TRASH.equals(folder.type))
                        trashes.add(folder.account);

                loadMessages();
            }
        });

        if (selectionTracker != null && selectionTracker.hasSelection())
            fabMove.show();
        else
            fabMove.hide();

        if (viewType == AdapterMessage.ViewType.THREAD) {
            // Navigation
            ViewModelMessages model = ViewModelProviders.of(getActivity()).get(ViewModelMessages.class);
            ViewModelMessages.Target[] pn = model.getPrevNext(thread);
            bottom_navigation.setTag(pn);
            bottom_navigation.getMenu().findItem(R.id.action_prev).setEnabled(pn[0] != null);
            bottom_navigation.getMenu().findItem(R.id.action_next).setEnabled(pn[1] != null);
            bottom_navigation.setVisibility(pn[0] == null && pn[1] == null ? View.GONE : View.VISIBLE);
        } else {
            // Compose FAB
            Bundle args = new Bundle();
            args.putLong("account", account);

            new SimpleTask<Long>() {
                @Override
                protected Long onLoad(Context context, Bundle args) {
                    long account = args.getLong("account", -1);

                    if (account < 0) {
                        EntityFolder primary = DB.getInstance(context).folder().getPrimaryDrafts();
                        return (primary == null ? null : primary.account);
                    } else
                        return account;
                }

                @Override
                protected void onLoaded(Bundle args, Long account) {
                    if (account != null) {
                        fab.setTag(account);
                        fab.show();
                    }
                }

                @Override
                protected void onException(Bundle args, Throwable ex) {
                    Helper.unexpectedError(getContext(), ex);
                }
            }.load(this, args);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        grpSupport.setVisibility(Helper.isPro(getContext()) ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_messages, menu);

        final MenuItem menuSearch = menu.findItem(R.id.menu_search);
        final SearchView searchView = (SearchView) menuSearch.getActionView();
        searchView.setQueryHint(getString(R.string.title_search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                menuSearch.collapseActionView();

                if (Helper.isPro(getContext())) {
                    Bundle args = new Bundle();
                    args.putLong("folder", folder);
                    args.putString("search", query);

                    new SimpleTask<Void>() {
                        @Override
                        protected Void onLoad(Context context, Bundle args) {
                            DB.getInstance(context).message().deleteFoundMessages();
                            return null;
                        }

                        @Override
                        protected void onLoaded(Bundle args, Void data) {
                            FragmentMessages fragment = new FragmentMessages();
                            fragment.setArguments(args);

                            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                            fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("search");
                            fragmentTransaction.commit();
                        }
                    }.load(FragmentMessages.this, args);
                } else {
                    FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                    fragmentTransaction.replace(R.id.content_frame, new FragmentPro()).addToBackStack("pro");
                    fragmentTransaction.commit();
                }

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_search).setVisible(folder >= 0 && search == null);
        menu.findItem(R.id.menu_sort_on).setVisible(TextUtils.isEmpty(search));
        menu.findItem(R.id.menu_folders).setVisible(primary >= 0);
        menu.findItem(R.id.menu_folders).setIcon(connected ? R.drawable.baseline_folder_24 : R.drawable.baseline_folder_open_24);
        menu.findItem(R.id.menu_move_sent).setVisible(outbox);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        String sort = prefs.getString("sort", "time");
        if ("time".equals(sort))
            menu.findItem(R.id.menu_sort_on_time).setChecked(true);
        else if ("unread".equals(sort))
            menu.findItem(R.id.menu_sort_on_unread).setChecked(true);
        else if ("starred".equals(sort))
            menu.findItem(R.id.menu_sort_on_starred).setChecked(true);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        switch (item.getItemId()) {
            case R.id.menu_sort_on_time:
                prefs.edit().putString("sort", "time").apply();
                item.setChecked(true);
                loadMessages();
                return true;

            case R.id.menu_sort_on_unread:
            case R.id.menu_sort_on_starred:
                if (Helper.isPro(getContext())) {
                    prefs.edit().putString("sort", item.getItemId() == R.id.menu_sort_on_unread ? "unread" : "starred").apply();
                    item.setChecked(true);
                    loadMessages();
                } else {
                    FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                    fragmentTransaction.replace(R.id.content_frame, new FragmentPro()).addToBackStack("pro");
                    fragmentTransaction.commit();
                }
                return true;

            case R.id.menu_folders:
                onMenuFolders();
                loadMessages();
                return true;

            case R.id.menu_move_sent:
                onMenuMoveSent();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onMenuFolders() {
        getFragmentManager().popBackStack("unified", 0);

        Bundle args = new Bundle();
        args.putLong("account", primary);

        FragmentFolders fragment = new FragmentFolders();
        fragment.setArguments(args);

        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("folders");
        fragmentTransaction.commit();
    }
    private void onMenuMoveSent() {
        Bundle args = new Bundle();
        args.putLong("folder", folder);

        new SimpleTask<Void>() {
            @Override
            protected Void onLoad(Context context, Bundle args) throws Throwable {
                long outbox = args.getLong("folder");

                DB db = DB.getInstance(context);
                try {
                    db.beginTransaction();

                    for (EntityMessage message : db.message().getMessageSeen(outbox)) {
                        EntityIdentity identity = db.identity().getIdentity(message.identity);
                        EntityFolder sent = db.folder().getFolderByType(identity.account, EntityFolder.SENT);
                        if (sent != null) {
                            message.folder = sent.id;
                            message.uid = null;
                            db.message().updateMessage(message);
                            Log.i(Helper.TAG, "Appending sent msgid=" + message.msgid);
                            EntityOperation.queue(db, message, EntityOperation.ADD); // Could already exist
                        }
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
                Helper.unexpectedError(getContext(), ex);
            }
        }.load(this, args);
    }

    private void loadMessages() {
        final DB db = DB.getInstance(getContext());

        ViewModelBrowse model = ViewModelProviders.of(getActivity()).get(ViewModelBrowse.class);
        model.set(getContext(), folder, search, REMOTE_PAGE_SIZE);

        // Observe folder/messages/search
        if (TextUtils.isEmpty(search)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            String sort = prefs.getString("sort", "time");
            boolean browse = prefs.getBoolean("browse", true);
            boolean debug = prefs.getBoolean("debug", false);

            if (messages != null)
                messages.removeObservers(getViewLifecycleOwner());

            switch (viewType) {
                case UNIFIED:
                    messages = new LivePagedListBuilder<>(db.message().pagedUnifiedInbox(sort, debug), LOCAL_PAGE_SIZE).build();
                    break;
                case FOLDER:
                    if (searchCallback == null)
                        searchCallback = new BoundaryCallbackMessages(this, model,
                                new BoundaryCallbackMessages.IBoundaryCallbackMessages() {
                                    @Override
                                    public void onLoading() {
                                        rvMessage.setVisibility(View.INVISIBLE);
                                        mShimmerView.setVisibility(View.VISIBLE);
                                        mShimmerView.startShimmer();
                                    }

                                    @Override
                                    public void onLoaded() {
                                        mShimmerView.setVisibility(View.GONE);
                                        rvMessage.setVisibility(View.VISIBLE);
                                        mShimmerView.stopShimmer();
                                    }

                                    @Override
                                    public void onError(Throwable ex) {
                                        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))
                                            new DialogBuilderLifecycle(getContext(), getViewLifecycleOwner())
                                                    .setMessage(Helper.formatThrowable(ex))
                                                    .setPositiveButton(android.R.string.cancel, null)
                                                    .create()
                                                    .show();
                                    }
                                });

                    PagedList.Config config = new PagedList.Config.Builder()
                            .setPageSize(LOCAL_PAGE_SIZE)
                            .setInitialLoadSizeHint(LOCAL_PAGE_SIZE)
                            .setPrefetchDistance(REMOTE_PAGE_SIZE)
                            .build();
                    LivePagedListBuilder<Integer, TupleMessageEx> builder = new LivePagedListBuilder<>(
                            db.message().pagedFolder(folder, sort, false, debug), config);
                    if (browse)
                        builder.setBoundaryCallback(searchCallback);
                    messages = builder.build();

                    break;
                case THREAD:
                    messages = new LivePagedListBuilder<>(db.message().pagedThread(account, thread, sort, debug), LOCAL_PAGE_SIZE).build();
                    break;
            }
        } else {
            if (searchCallback == null)
                searchCallback = new BoundaryCallbackMessages(this, model,
                        new BoundaryCallbackMessages.IBoundaryCallbackMessages() {
                            @Override
                            public void onLoading() {
                                tvNoEmail.setVisibility(View.GONE);
                                rvMessage.setVisibility(View.INVISIBLE);
                                mShimmerView.setVisibility(View.VISIBLE);
                                mShimmerView.startShimmer();
                            }

                            @Override
                            public void onLoaded() {
                                mShimmerView.setVisibility(View.GONE);
                                rvMessage.setVisibility(View.VISIBLE);
                                mShimmerView.stopShimmer();
                                if (messages.getValue().size() == 0)
                                    tvNoEmail.setVisibility(View.VISIBLE);
                            }

                            @Override
                            public void onError(Throwable ex) {
                                if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))
                                    new DialogBuilderLifecycle(getContext(), getViewLifecycleOwner())
                                            .setMessage(Helper.formatThrowable(ex))
                                            .setPositiveButton(android.R.string.cancel, null)
                                            .create()
                                            .show();
                            }
                        });

            PagedList.Config config = new PagedList.Config.Builder()
                    .setPageSize(LOCAL_PAGE_SIZE)
                    .setInitialLoadSizeHint(LOCAL_PAGE_SIZE)
                    .setPrefetchDistance(REMOTE_PAGE_SIZE)
                    .build();
            LivePagedListBuilder<Integer, TupleMessageEx> builder = new LivePagedListBuilder<>(
                    db.message().pagedFolder(folder, "time", true, false), config);
            builder.setBoundaryCallback(searchCallback);
            messages = builder.build();
        }

        messages.observe(getViewLifecycleOwner(), new Observer<PagedList<TupleMessageEx>>() {
            @Override
            public void onChanged(@Nullable PagedList<TupleMessageEx> messages) {
                if (messages == null ||
                        (viewType == AdapterMessage.ViewType.THREAD && messages.size() == 0)) {
                    finish();
                    return;
                }

                if (viewType == AdapterMessage.ViewType.THREAD) {
                    if (autoExpand) {
                        autoExpand = false;

                        int unseen = 0;
                        TupleMessageEx single = null;
                        TupleMessageEx see = null;
                        for (int i = 0; i < messages.size(); i++) {
                            TupleMessageEx message = messages.get(i);
                            if (!EntityFolder.ARCHIVE.equals(message.folderType) &&
                                    !EntityFolder.SENT.equals(message.folderType) &&
                                    !EntityFolder.OUTBOX.equals(message.folderType)) {
                                autoCount++;
                                single = message;
                                if (!message.ui_seen) {
                                    unseen++;
                                    see = message;
                                }
                            }
                        }

                        // Auto expand when:
                        // - single, non archived/sent message
                        // - one unread, non archived/sent message in conversation
                        // - sole message

                        TupleMessageEx expand = null;
                        if (autoCount == 1)
                            expand = single;
                        else if (unseen == 1)
                            expand = see;
                        else if (messages.size() == 1)
                            expand = messages.get(0);

                        if (expand != null) {
                            expanded.add(expand.id);
                            handleExpand(expand.id);
                        }
                    } else {
                        if (autoCount > 0) {
                            int count = 0;
                            for (int i = 0; i < messages.size(); i++) {
                                TupleMessageEx message = messages.get(i);
                                if (!EntityFolder.ARCHIVE.equals(message.folderType) &&
                                        !EntityFolder.SENT.equals(message.folderType) &&
                                        !EntityFolder.OUTBOX.equals(message.folderType)) {
                                    count++;
                                }
                            }

                            // Auto close when:
                            // - no more non archived/sent messages

                            if (count == 0)
                                finish();
                        }
                    }
                } else {
                    ViewModelMessages model = ViewModelProviders.of(getActivity()).get(ViewModelMessages.class);
                    model.setMessages(messages);
                }

                Log.i(Helper.TAG, "Submit messages=" + messages.size());
                adapter.submitList(messages);

                boolean searching = (searchCallback != null && searchCallback.isSearching());

                if (!searching){
                    rvMessage.setVisibility(View.VISIBLE);
                    mShimmerView.setVisibility(View.GONE);
                    mShimmerView.stopShimmer();
                }
                grpReady.setVisibility(View.VISIBLE);

                if (messages.size() == 0) {
                    if (searchCallback == null)
                        tvNoEmail.setVisibility(View.VISIBLE);
                    rvMessage.setVisibility(View.GONE);
                } else {
                    tvNoEmail.setVisibility(View.GONE);
                    rvMessage.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void handleExpand(long id) {
        Bundle args = new Bundle();
        args.putLong("id", id);

        new SimpleTask<Void>() {
            @Override
            protected Void onLoad(Context context, Bundle args) {
                long id = args.getLong("id");

                DB db = DB.getInstance(context);
                try {
                    db.beginTransaction();

                    EntityMessage message = db.message().getMessage(id);
                    EntityFolder folder = db.folder().getFolder(message.folder);

                    if (!message.content)
                        EntityOperation.queue(db, message, EntityOperation.BODY);

                    if (!message.ui_seen && !EntityFolder.OUTBOX.equals(folder.type)) {
                        db.message().setMessageUiSeen(message.id, true);
                        db.message().setMessageUiIgnored(message.id, true);
                        EntityOperation.queue(db, message, EntityOperation.SEEN, true);
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
                Helper.unexpectedError(getContext(), ex);
            }
        }.load(this, args);
    }
}
