/*
 * This file is part of FairEmail.
 *
 * FairEmail is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * FairEmail is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with FairEmail. If not,
 * see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2018, Marcel Bokhorst (M66B)
 * Copyright 2018-2020, Distopico (dystopia project) <distopico@riseup.net> and contributors
 */
package org.dystopia.email

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.Log
import android.view.*
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import androidx.recyclerview.selection.MutableSelection
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import org.dystopia.email.ActivityBase.IBackPressedListener
import org.dystopia.email.AdapterMessage.IProperties
import org.dystopia.email.AdapterMessage.ViewType
import org.dystopia.email.BoundaryCallbackMessages.IBoundaryCallbackMessages
import org.dystopia.email.databinding.FragmentMessagesBinding
import java.io.Serializable
import java.text.Collator
import java.util.*
import java.util.concurrent.Executors


class FragmentMessages : FragmentEx() {
    private var _fragmentMessagesBinding: FragmentMessagesBinding? = null
    private var folder: Long = -1
    private var account: Long = -1
    private var folderType: String? = null
    private var thread: String? = null
    private var search: String? = null
    private var primary: Long = -1
    private var outbox = false
    private var connected = false
    private var adapter: AdapterMessage? = null
    private val archives: MutableList<Long> = ArrayList()
    private val trashes: MutableList<Long> = ArrayList()
    private var viewType: ViewType? = null
    private var selectionTracker: SelectionTracker<Long>? = null
    private var messages: LiveData<PagedList<TupleMessageEx>?>? = null
    private var autoCount = 0
    private var autoExpand = true
    private var expanded: MutableList<Long> = ArrayList()
    private val details: MutableList<Long> = ArrayList()
    private var headers: MutableList<Long> = ArrayList()
    private var images: MutableList<Long> = ArrayList()
    private var searchCallback: BoundaryCallbackMessages? = null
    private val executor = Executors.newCachedThreadPool(Helper.backgroundThreadFactory)

    private val binding get() = _fragmentMessagesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get arguments
        val args = arguments
        if (args != null) {
            account = args.getLong("account", -1)
            folder = args.getLong("folder", -1)
            folderType = args.getString("folderType")
            thread = args.getString("thread")
            search = args.getString("search")
        }
        viewType = if (TextUtils.isEmpty(search)) {
            if (thread == null) {
                if (folder < 0) {
                    ViewType.UNIFIED
                } else {
                    ViewType.FOLDER
                }
            } else {
                ViewType.THREAD
            }
        } else {
            ViewType.SEARCH
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        _fragmentMessagesBinding = FragmentMessagesBinding.inflate(inflater, container, false)
        var view = binding?.root

        //view = inflater.inflate(R.layout.fragment_messages, container, false) as ViewGroup
        setHasOptionsMenu(true)

        // Get controls
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val metrics = resources.displayMetrics
        val mSpinnerDistance = (SWIPE_REFRESH_DISTANCE * metrics.density).toInt()

        // Wire controls
        binding?.swipeRefresh?.setDistanceToTriggerSync(mSpinnerDistance)
        binding?.swipeRefresh?.setOnRefreshListener {
            val args = Bundle()
            args.putLong("folder", folder)
            onRefreshHandler(args)
        }
        binding?.ibHintSelect?.setOnClickListener(View.OnClickListener {
            prefs.edit().putBoolean("message_select", true).apply()
            binding?.grpHintSelect?.setVisibility(View.GONE)
        })
        binding?.ibHintSupport?.setOnClickListener(View.OnClickListener {
            prefs.edit().putBoolean("app_support", true).apply()
            binding?.grpHintSupport?.setVisibility(View.GONE)
        })
        binding?.rvFolder?.setHasFixedSize(false)

        val llm = LinearLayoutManager(context)
        binding?.rvFolder?.setLayoutManager(llm)

        adapter = AdapterMessage(context, viewLifecycleOwner, fragmentManager, viewType, folder, object : IProperties {
            override fun setExpanded(id: Long, expand: Boolean) {
                if (expand) {
                    expanded.add(id)
                    // Automatically allow loading images when a message is expanded
                    images.add(id)
                    handleExpand(id)
                } else {
                    expanded.remove(id)
                    // Optionally stop showing images when collapsing
                    images.remove(id)
                }
            }

            override fun setDetails(id: Long, show: Boolean) {
                if (show) {
                    details.add(id)
                } else {
                    details.remove(id)
                }
            }

            override fun setHeaders(id: Long, show: Boolean) {
                if (show) {
                    headers.add(id)
                } else {
                    headers.remove(id)
                }
            }

            override fun setImages(id: Long, show: Boolean) {
                if (show) {
                    images.add(id)
                } else {
                    images.remove(id)
                }
            }

            override fun isExpanded(id: Long): Boolean {
                return expanded.contains(id)
            }

            override fun showDetails(id: Long): Boolean {
                return details.contains(id)
            }

            override fun showHeaders(id: Long): Boolean {
                return headers.contains(id)
            }

            override fun showImages(id: Long): Boolean {
                return images.contains(id)
            }
        })
        binding?.rvFolder?.setAdapter(adapter)
        if (viewType == ViewType.FOLDER) {
            var selectionTracker = SelectionTracker.Builder("messages-selection", binding!!.rvFolder,
                    ItemKeyProviderMessage(binding?.rvFolder), ItemDetailsLookupMessage(binding?.rvFolder),
                    StorageStrategy.createLongStorage())
                    .withSelectionPredicate(SelectionPredicateMessage(binding?.rvFolder)).build()
            adapter!!.setSelectionTracker(selectionTracker)
            selectionTracker.addObserver(object : SelectionTracker.SelectionObserver<Any?>() {
                override fun onSelectionChanged() {
                    binding?.swipeRefresh?.setEnabled(false)
                    if (selectionTracker.hasSelection()) {
                        binding?.fabMove?.show()
                    } else {
                        binding?.fabMove?.hide()
                        binding?.swipeRefresh?.setEnabled(true)
                    }
                }
            })
        }
        binding?.fab?.setOnClickListener(View.OnClickListener {
            startActivity(Intent(context, ActivityCompose::class.java).putExtra("action", "new")
                    .putExtra("account", binding?.fab?.getTag() as Long))
        })
        binding?.fabMove?.setOnClickListener(View.OnClickListener {
            val args = Bundle()
            args.putLong("folder", folder)
            object : SimpleTask<List<EntityFolder?>?>() {
                override fun onLoad(context: Context, args: Bundle): List<EntityFolder> {
                    val folder = args.getLong("folder")
                    val db = DB.getInstance(context)
                    val source = db.folder().getFolder(folder)
                    val folders = db.folder().getFolders(source.account)
                    val targets: MutableList<EntityFolder> = ArrayList()
                    for (f in folders) {
                        if (f.id != folder && EntityFolder.DRAFTS != f.type) {
                            targets.add(f)
                        }
                    }
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.SECONDARY // Case insensitive, process accents
                    // etc
                    Collections.sort(targets) { f1, f2 ->
                        val s = Integer.compare(EntityFolder.FOLDER_SORT_ORDER.indexOf(f1.type),
                                EntityFolder.FOLDER_SORT_ORDER.indexOf(f2.type))
                        if (s != 0) {
                            s
                        } else collator.compare(if (f1.name.isEmpty()) "" else f1.name,
                                if (f2.name.isEmpty()) "" else f2.name)
                    }
                    return targets
                }

                override fun onLoaded(args: Bundle?, folders: List<EntityFolder?>?) {
                    if (binding?.popupAnchor == null) return
                    val popupMenu = PopupMenu(context!!, binding!!.popupAnchor)
                    var order = 0
                    for (folder in folders.orEmpty()) {
                        if (folder == null) return
                        val name = if (folder.display == null) Helper.localizeFolderName(context, folder.name) else folder.display
                        popupMenu.menu.add(Menu.NONE, folder.id.toInt(), order++, name)
                    }
                    popupMenu.setOnMenuItemClickListener { targetInput ->
                        val selection = MutableSelection<Long>()
                        selectionTracker?.copySelection(selection)
                        val idsInput = LongArray(selection.size())
                        var i = 0
                        for (id in selection) {
                            idsInput[i++] = id
                        }
                        selectionTracker?.clearSelection()
                        args?.putLongArray("ids", idsInput)
                        args?.putLong("target", targetInput.itemId.toLong())
                        object : SimpleTask<Void?>() {
                            override fun onLoad(context: Context, args: Bundle): Void? {
                                val ids = args.getLongArray("ids")
                                val target = args.getLong("target")
                                val db = DB.getInstance(context)
                                try {
                                    db.beginTransaction()
                                    for (id in ids!!) {
                                        val message = db.message().getMessage(id)
                                        val messages = db.message().getMessageByThread(message.account, message.thread)
                                        for (threaded in messages) {
                                            if (threaded.folder == message.folder) {
                                                db.message().setMessageUiHide(threaded.id, true)
                                                EntityOperation.queue(db, threaded, EntityOperation.MOVE, target)
                                            }
                                        }
                                    }
                                    db.setTransactionSuccessful()
                                } finally {
                                    db.endTransaction()
                                }
                                EntityOperation.process(context)
                                return null
                            }

                            override fun onException(args: Bundle, ex: Throwable) {
                                Helper.unexpectedError(context, ex)
                            }
                        }.load(this@FragmentMessages, args)
                        true
                    }
                    popupMenu.show()
                }

                override fun onException(args: Bundle, ex: Throwable) {
                    Helper.unexpectedError(context, ex)
                }
            }.load(this@FragmentMessages, args)
        })
        (activity as ActivityBase?)!!.addBackPressedListener(IBackPressedListener {
            if (selectionTracker != null && selectionTracker!!.hasSelection()) {
                selectionTracker!!.clearSelection()
                return@IBackPressedListener true
            }
            false
        })

        // Initialize
        binding?.swipeRefresh?.setEnabled(viewType == ViewType.UNIFIED || viewType == ViewType.FOLDER)
        binding?.tvNoEmail?.setVisibility(View.GONE)
        binding?.bottomNavigation?.setVisibility(View.GONE)
        binding?.grpReady?.setVisibility(View.GONE)
        // TODO: implement in load more items
        binding?.pbWait?.setVisibility(View.GONE)
        binding?.fab?.hide()
        binding?.fabMove?.hide()
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _fragmentMessagesBinding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("autoExpand", autoExpand)
        outState.putInt("autoCount", autoCount)
        outState.putLongArray("expanded", Helper.toLongArray(expanded))
        outState.putLongArray("headers", Helper.toLongArray(headers))
        outState.putLongArray("images", Helper.toLongArray(images))
        if (selectionTracker != null) {
            selectionTracker!!.onSaveInstanceState(outState)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (savedInstanceState != null) {
            autoExpand = savedInstanceState.getBoolean("autoExpand")
            autoCount = savedInstanceState.getInt("autoCount")
            expanded = Helper.fromLongArray(savedInstanceState.getLongArray("expanded"))
            headers = Helper.fromLongArray(savedInstanceState.getLongArray("headers"))
            images = Helper.fromLongArray(savedInstanceState.getLongArray("images"))
            if (selectionTracker != null) {
                selectionTracker!!.onRestoreInstanceState(savedInstanceState)
            }
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        binding?.grpHintSupport?.visibility = if (prefs.getBoolean("app_support", false) || viewType != ViewType.UNIFIED) View.GONE else View.VISIBLE
        binding?.grpHintSwipe?.visibility = View.GONE
        binding?.grpHintSelect?.visibility = if (prefs.getBoolean("message_select", false) || viewType != ViewType.FOLDER) View.GONE else View.VISIBLE
        val db = DB.getInstance(context)

        // Primary account
        db.account().livePrimaryAccount().observe(viewLifecycleOwner,
                Observer { account ->
                    primary = if (account == null) -1 else account.id
                    connected = account != null && "connected" == account.state
                    requireActivity().invalidateOptionsMenu()
                })
        when (viewType) {
            ViewType.UNIFIED -> db.folder().liveUnified().observe(viewLifecycleOwner,
                    Observer { folders ->
                        var unseen = 0
                        if (folders != null) {
                            for (folder in folders) {
                                unseen += folder.unseen
                            }
                        }
                        val name = getString(R.string.title_folder_unified)
                        if (unseen > 0) {
                            setSubtitle(getString(R.string.title_folder_unseen, name, unseen))
                        } else {
                            setSubtitle(name)
                        }
                        var isRefreshing = false
                        for (folder in folders!!) {
                            if (folder.sync_state != null && "connected" == folder.accountState) {
                                isRefreshing = true
                                break
                            }
                        }
                        binding?.swipeRefresh?.isRefreshing = isRefreshing
                    })
            ViewType.FOLDER -> db.folder().liveFolderEx(folder).observe(viewLifecycleOwner,
                    Observer { folder ->
                        if (folder == null) {
                            setSubtitle(null)
                        } else {
                            val name = if (folder.display == null) Helper.localizeFolderName(context, folder.name) else folder.display
                            if (folder.unseen > 0) {
                                setSubtitle(getString(R.string.title_folder_unseen, name, folder.unseen))
                            } else {
                                setSubtitle(name)
                            }
                            outbox = EntityFolder.OUTBOX == folder.type
                            requireActivity().invalidateOptionsMenu()
                        }
                        binding?.swipeRefresh?.isRefreshing = folder != null && folder.sync_state != null && "connected" == if (EntityFolder.OUTBOX == folder.type) folder.state else folder.accountState
                    })
            ViewType.THREAD -> setSubtitle(R.string.title_folder_thread)
            ViewType.SEARCH -> setSubtitle(String.format(getString(R.string.title_searching), search))
            else -> {}
        }

        // Folders and messages
        db.folder().liveSystemFolders(account).observe(viewLifecycleOwner,
                Observer { foldersInput ->
                    var folders = foldersInput
                    if (folders == null) {
                        folders = ArrayList()
                    }
                    archives.clear()
                    trashes.clear()
                    for (folder in folders) {
                        if (EntityFolder.ARCHIVE == folder.type) {
                            archives.add(folder.account)
                        } else if (EntityFolder.TRASH == folder.type) {
                            trashes.add(folder.account)
                        }
                    }
                    loadMessages()
                })
        if (selectionTracker != null && selectionTracker!!.hasSelection()) {
            binding?.fabMove?.show()
        } else {
            binding?.fabMove?.hide()
        }
        if (viewType == ViewType.THREAD) {
            // Navigation
            val model = ViewModelProviders.of(requireActivity()).get(ViewModelMessages::class.java)
            val pn = model.getPrevNext(thread)
            binding?.bottomNavigation?.tag = pn
            binding?.bottomNavigation?.menu?.findItem(R.id.action_prev)?.isEnabled = pn[0] != null
            binding?.bottomNavigation?.menu?.findItem(R.id.action_next)?.isEnabled = pn[1] != null
            binding?.bottomNavigation?.visibility = if (pn[0] == null && pn[1] == null) View.GONE else View.VISIBLE
        } else {
            // Compose FAB
            val args = Bundle()
            args.putLong("account", account)
            object : SimpleTask<Long?>() {
                override fun onLoad(context: Context, args: Bundle): Long {
                    val account: Long = args.getLong("account", -1)
                    return if (account < 0) {
                        val primary = DB.getInstance(context).folder().primaryDrafts
                        // TODO: check this
                        primary!!.account
                    } else {
                        account
                    }
                }

                override fun onLoaded(args: Bundle?, account: Long?) {
                    if (account != null) {
                        binding?.fab?.tag = account
                        binding?.fab?.show()
                    }
                }

                override fun onException(args: Bundle, ex: Throwable) {
                    Helper.unexpectedError(context, ex)
                }
            }.load(this, args)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_messages, menu)
        val menuSearch = menu.findItem(R.id.menu_search)
        val searchView = menuSearch.actionView as SearchView
        searchView.queryHint = getString(R.string.title_search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                menuSearch.collapseActionView()
                val args = Bundle()
                args.putLong("folder", folder)
                args.putString("search", query)
                object : SimpleTask<Void?>() {
                    override fun onLoad(context: Context, args: Bundle): Void? {
                        DB.getInstance(context).message().deleteFoundMessages()
                        return null
                    }

                    override fun onLoaded(args: Bundle?, data: Void?) {
                        val fragment = FragmentMessages()
                        fragment.arguments = args
                        val fragmentTransaction = fragmentManager?.beginTransaction()
                        fragmentTransaction?.replace(R.id.content_frame, fragment)?.addToBackStack("search")
                        fragmentTransaction?.commit()
                    }
                }.load(this@FragmentMessages, args)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return false
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_search).isVisible = folder >= 0 && search == null
        menu.findItem(R.id.menu_sort_on).isVisible = TextUtils.isEmpty(search)
        menu.findItem(R.id.menu_folders).isVisible = primary >= 0
        menu.findItem(R.id.menu_folders)
                .setIcon(if (connected) R.drawable.baseline_folder_24 else R.drawable.baseline_folder_open_24)
        menu.findItem(R.id.menu_move_sent).isVisible = outbox
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val sort = prefs.getString("sort", "time")
        if ("time" == sort) {
            menu.findItem(R.id.menu_sort_on_time).isChecked = true
        } else if ("unread" == sort) {
            menu.findItem(R.id.menu_sort_on_unread).isChecked = true
        } else if ("starred" == sort) {
            menu.findItem(R.id.menu_sort_on_starred).isChecked = true
        }
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return when (item.itemId) {
            R.id.menu_sort_on_time -> {
                prefs.edit().putString("sort", "time").apply()
                item.isChecked = true
                loadMessages()
                true
            }
            R.id.menu_sort_on_unread, R.id.menu_sort_on_starred -> {
                prefs.edit()
                        .putString("sort", if (item.itemId == R.id.menu_sort_on_unread) "unread" else "starred")
                        .apply()
                item.isChecked = true
                loadMessages()
                true
            }
            R.id.menu_folders -> {
                onMenuFolders()
                loadMessages()
                true
            }
            R.id.menu_move_sent -> {
                onMenuMoveSent()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Handler when OnRefreshListener is called
     *
     * @param args a `Bundle` with account and folder
     */
    private fun onRefreshHandler(args: Bundle) {
        object : SimpleTask<Boolean?>() {
            override fun onLoad(context: Context, args: Bundle): Boolean {
                val fid = args.getLong("folder")
                val db = DB.getInstance(context)
                var isConnected = false
                var isOutbox = false

                try {
                    db.beginTransaction()
                    val folders: MutableList<EntityFolder> = ArrayList()
                    if (fid < 0) {
                        folders.addAll(db.folder().unifiedFolders)
                    } else {
                        folders.add(db.folder().getFolder(fid))
                    }

                    for (folder in folders) {
                        EntityOperation.sync(db, folder.id)
                        isConnected = if (folder.account == null) { // outbox
                            isOutbox = true
                            "connected" == folder.state
                        } else {
                            val account = db.account().getAccount(folder.account)
                            "connected" == account.state
                        }
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }

                if (!isConnected && !isOutbox) {
                    ServiceSynchronize.reload(context, "refresh")
                }
                return isConnected
            }

            override fun onLoaded(args: Bundle?, isConnected: Boolean?) {
                binding?.swipeRefresh?.isRefreshing = false
            }
        }.load(this@FragmentMessages, args)
    }

    private fun onMenuFolders() {
        requireFragmentManager().popBackStack("unified", 0)
        val args = Bundle()
        args.putLong("account", primary)
        val fragment = FragmentFolders()
        fragment.arguments = args
        val fragmentTransaction = requireFragmentManager().beginTransaction()
        fragmentTransaction.replace(R.id.content_frame, fragment).addToBackStack("folders")
        fragmentTransaction.commit()
    }

    private fun onMenuMoveSent() {
        val args = Bundle()
        args.putLong("folder", folder)
        object : SimpleTask<Void?>() {
            @Throws(Throwable::class)
            override fun onLoad(context: Context, args: Bundle): Void? {
                val outbox = args.getLong("folder")
                val db = DB.getInstance(context)
                try {
                    db.beginTransaction()
                    for (message in db.message().getMessageSeen(outbox)) {
                        val identity = db.identity().getIdentity(message.identity)
                        val sent = db.folder().getFolderByType(identity.account, EntityFolder.SENT)
                        if (sent != null) {
                            message.folder = sent.id
                            message.uid = null
                            db.message().updateMessage(message)
                            Log.i(Helper.TAG, "Appending sent msgid=" + message.msgid)
                            EntityOperation.queue(db, message, EntityOperation.ADD) // Could already exist
                        }
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                EntityOperation.process(context)
                return null
            }

            override fun onException(args: Bundle, ex: Throwable) {
                Helper.unexpectedError(context, ex)
            }
        }.load(this, args)
    }

    private fun loadMessages() {
        val db = DB.getInstance(context)
        val model = ViewModelProviders.of(requireActivity()).get(ViewModelBrowse::class.java)
        model[context, folder, search] = REMOTE_PAGE_SIZE

        // Observe folder/messages/search
        if (TextUtils.isEmpty(search)) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val sort = prefs.getString("sort", "time")
            val browse = prefs.getBoolean("browse", true)
            val debug = prefs.getBoolean("debug", false)

            messages?.removeObservers(viewLifecycleOwner)

            when (viewType) {
                ViewType.UNIFIED -> messages = LivePagedListBuilder(db.message().pagedUnifiedInbox(sort, debug),
                        LOCAL_PAGE_SIZE).build()
                ViewType.FOLDER -> {
                    if (searchCallback == null) {
                        searchCallback = BoundaryCallbackMessages(this, model,
                                object : IBoundaryCallbackMessages {
                                    override fun onLoading() {
                                        binding?.swipeRefresh?.isRefreshing = true
                                    }

                                    override fun onLoaded() {
                                        binding?.swipeRefresh?.isRefreshing = false
                                    }

                                    override fun onError(context: Context, ex: Throwable) {
                                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                            DialogBuilderLifecycle(getContext(), viewLifecycleOwner)
                                                    .setMessage(Helper.formatThrowable(ex))
                                                    .setPositiveButton(android.R.string.cancel, null).create().show()
                                        }
                                    }
                                })
                    }
                    val config = PagedList.Config.Builder().setPageSize(LOCAL_PAGE_SIZE)
                            .setInitialLoadSizeHint(LOCAL_PAGE_SIZE).setPrefetchDistance(REMOTE_PAGE_SIZE)
                            .build()
                    val builder = LivePagedListBuilder(
                            db.message().pagedFolder(folder, folderType, sort, false, debug), config)
                    if (browse) {
                        builder.setBoundaryCallback(searchCallback)
                    }
                    messages = builder.build()
                }
                ViewType.THREAD -> messages = LivePagedListBuilder(
                        db.message().pagedThread(account, folder, thread, sort, debug), LOCAL_PAGE_SIZE)
                        .build()
                else -> {}
            }
        } else {
            if (searchCallback == null) {
                searchCallback = BoundaryCallbackMessages(this, model,
                        object : IBoundaryCallbackMessages {
                            override fun onLoading() {
                                binding?.tvNoEmail?.visibility = View.GONE
                                binding?.swipeRefresh?.isRefreshing = true
                            }

                            override fun onLoaded() {
                                binding?.swipeRefresh?.isRefreshing = false
                                if (messages?.value?.size == 0) {
                                    binding?.tvNoEmail?.visibility = View.VISIBLE
                                }
                            }

                            override fun onError(context: Context, ex: Throwable) {
                                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                    DialogBuilderLifecycle(getContext(), viewLifecycleOwner)
                                            .setMessage(Helper.formatThrowable(ex))
                                            .setPositiveButton(android.R.string.cancel, null).create().show()
                                }
                            }
                        })
            }
            val config = PagedList.Config.Builder().setPageSize(LOCAL_PAGE_SIZE)
                    .setInitialLoadSizeHint(LOCAL_PAGE_SIZE).setPrefetchDistance(REMOTE_PAGE_SIZE).build()
            val builder = LivePagedListBuilder(
                    db.message().pagedFolder(folder, folderType, "time", true, false), config)
            builder.setBoundaryCallback(searchCallback)
            messages = builder.build()
        }

        messages?.observe(viewLifecycleOwner, Observer { messages ->
            if (messages == null
                || viewType == ViewType.THREAD && messages.size == 0) {
                finish()
                return@Observer
            }
            if (viewType == ViewType.THREAD) {
                if (autoExpand) {
                    autoExpand = false
                    var unseen = 0
                    var single: TupleMessageEx? = null
                    var see: TupleMessageEx? = null
                    for (i in messages.indices) {
                        val message = messages[i]
                        if (EntityFolder.ARCHIVE != message!!.folderType
                            && EntityFolder.SENT != message.folderType
                            && EntityFolder.OUTBOX != message.folderType) {
                            autoCount++
                            single = message
                            if (!message.ui_seen) {
                                unseen++
                                see = message
                            }
                        }
                    }

                    // Auto expand when:
                    // - single, non archived/sent message
                    // - one unread, non archived/sent message in conversation
                    // - sole message
                    var expand: TupleMessageEx? = null
                    if (autoCount == 1) {
                        expand = single
                    } else if (unseen == 1) {
                        expand = see
                    } else if (messages.size == 1) {
                        expand = messages[0]
                    }
                    if (expand != null) {
                        expanded.add(expand.id)
                        handleExpand(expand.id)
                    }
                } else if (autoCount > 0) {
                    var count = 0
                    for (i in messages.indices) {
                        val message = messages[i]
                        if (EntityFolder.ARCHIVE != message!!.folderType
                            && EntityFolder.SENT != message.folderType
                            && EntityFolder.OUTBOX != message.folderType) {
                            count++
                        }
                    }

                    // Auto close when:
                    // - no more non archived/sent messages
                    if (count == 0) {
                        finish()
                    }
                }
            } else {
                val modelMessages = ViewModelProviders.of(requireActivity()).get(ViewModelMessages::class.java)
                modelMessages.setMessages(messages)
            }
            Log.i(Helper.TAG, "Submit messages=" + messages.size)
            adapter!!.submitList(messages)
            val searching = searchCallback != null && searchCallback!!.isSearching
            if (!searching) {
                binding?.swipeRefresh?.isRefreshing = false
            }
            binding?.grpReady?.visibility = View.VISIBLE
            if (messages.size == 0) {
                if (searchCallback == null) {
                    binding?.tvNoEmail?.visibility = View.VISIBLE
                }
                binding?.rvFolder?.visibility = View.GONE
            } else {
                binding?.tvNoEmail?.visibility = View.GONE
                binding?.rvFolder?.visibility = View.VISIBLE
            }
        })
    }

    private fun handleExpand(handleId: Long) {
        // Ensure images are allowed when expanded via auto-expand paths
        if (!images.contains(handleId)) {
            images.add(handleId)
        }
        val args = Bundle()
        args.putLong("id", handleId)
        object : SimpleTask<Void?>() {
            override fun onLoad(context: Context, args: Bundle): Void? {
                val id = args.getLong("id")
                val db = DB.getInstance(context)
                try {
                    db.beginTransaction()
                    val message = db.message().getMessage(id)
                    val folder = db.folder().getFolder(message.folder)
                    if (!message.content) {
                        EntityOperation.queue(db, message, EntityOperation.BODY)
                    }
                    if (!message.ui_seen && EntityFolder.OUTBOX != folder.type) {
                        db.message().setMessageUiSeen(message.id, true)
                        db.message().setMessageUiIgnored(message.id, true)
                        EntityOperation.queue(db, message, EntityOperation.SEEN, true)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                EntityOperation.process(context)
                return null
            }

            override fun onException(args: Bundle, ex: Throwable) {
                Helper.unexpectedError(context, ex)
            }
        }.load(this, args)
    }

    companion object {
        private const val LOCAL_PAGE_SIZE = 50
        private const val REMOTE_PAGE_SIZE = 10
        private const val UNDO_TIMEOUT = 5000 // milliseconds
        private const val SWIPE_REFRESH_DISTANCE = 200
    }
}
