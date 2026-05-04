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
 * Copyright 2018-2023, Distopico (dystopia project) <distopico@riseup.net> and contributors
 */
package org.dystopia.email

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import org.dystopia.email.databinding.ItemAttachmentBinding

class AdapterAttachment(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val isReadOnly: Boolean
) : RecyclerView.Adapter<AdapterAttachment.ViewHolder?>() {
    private val debug: Boolean
    private var all: List<EntityAttachment> = ArrayList()
    private val filtered: MutableList<EntityAttachment> = ArrayList()

    inner class ViewHolder(binding: ItemAttachmentBinding) : RecyclerView.ViewHolder(binding.root),
        View.OnClickListener {
        var ivDelete = binding.ivDelete
        var tvName = binding.tvName
        var tvSize = binding.tvSize
        var ivStatus = binding.ivStatus
        var ivSave = binding.ivSave
        var tvType = binding.tvType
        var progressbar = binding.progressbar

        init {
            wire()
        }

        internal fun wire() {
            itemView.setOnClickListener(this)
            ivDelete.setOnClickListener(this)
            ivSave.setOnClickListener(this)
        }

        internal fun unwire() {
            itemView.setOnClickListener(null)
            ivDelete.setOnClickListener(null)
            ivSave.setOnClickListener(null)
        }

        internal fun bindTo(attachment: EntityAttachment) {
            ivDelete.visibility = if (isReadOnly) View.GONE else View.VISIBLE
            tvName.text = attachment.name

            if (attachment.size != null) {
                tvSize.text = Helper.humanReadableByteCount(attachment.size.toLong(), true)
            }
            tvSize.visibility =
                if (attachment.size == null) View.GONE else View.VISIBLE

            if (attachment.available) {
                ivStatus.setImageResource(R.drawable.baseline_visibility_24)
                ivStatus.visibility = View.VISIBLE
            } else {
                if (attachment.progress == null) {
                    ivStatus.setImageResource(R.drawable.baseline_cloud_download_24)
                    ivStatus.visibility = View.VISIBLE
                } else {
                    ivStatus.visibility = View.GONE
                }
            }
            ivSave.visibility =
                if (isReadOnly && attachment.available) View.VISIBLE else View.GONE
            if (attachment.progress != null) {
                progressbar.progress = attachment.progress
            }
            progressbar.visibility =
                if (attachment.progress == null || attachment.available) View.GONE else View.VISIBLE
            tvType.text = attachment.type + " " + attachment.cid
            tvType.visibility = if (debug) View.VISIBLE else View.GONE
        }

        override fun onClick(view: View) {
            val pos = adapterPosition
            if (pos == RecyclerView.NO_POSITION) {
                return
            }
            val attachment = filtered[pos]

            when (view.id) {
                R.id.ivDelete -> {
                    val args = Bundle()
                    args.putLong("id", attachment.id)

                    object : SimpleTask<Void>() {
                        override fun onLoad(context: Context, args: Bundle): Void? {
                            DB.getInstance(context).attachment().deleteAttachment(attachment.id)
                            EntityAttachment.getFile(context, attachment.id).delete()
                            return null
                        }
                    }.load(context, owner, args)

                }
                R.id.ivSave -> {
                    val lbm = LocalBroadcastManager.getInstance(context)
                    lbm.sendBroadcast(
                        Intent(ActivityView.ACTION_STORE_ATTACHMENT)
                            .putExtra("id", attachment.id)
                            .putExtra("name", attachment.name)
                            .putExtra("type", attachment.type)
                    )

                }
                else -> {
                    if (attachment.available) {
                        onShare(attachment)
                        return
                    }
                    if (attachment.progress == null) {
                        startDownload(attachment)
                    } else {
                        Toast.makeText(
                            context,
                            String.format(context.getString(R.string.title_attachment_downloading),
                                String.format("%s%%", attachment.progress)),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    init {
        debug = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("debug", false)
        setHasStableIds(true)
    }

    fun startDownload(attachment: EntityAttachment) {
        val args = Bundle()
        args.putLong("id", attachment.id)
        args.putLong("message", attachment.message)
        args.putInt("sequence", attachment.sequence)
        object : SimpleTask<Unit>() {
            override fun onLoad(context: Context, args: Bundle) {
                val id = args.getLong("id")
                val message = args.getLong("message")
                val sequence = args.getInt("sequence").toLong()
                val db = DB.getInstance(context)
                try {
                    db.beginTransaction()
                    db.attachment().setProgress(id, 0)
                    val msg = db.message().getMessage(message)
                    EntityOperation.queue(
                        db,
                        msg,
                        EntityOperation.ATTACHMENT,
                        sequence
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                EntityOperation.process(context)
            }
        }.load(context, owner, args)
    }

    fun onShare(attachment: EntityAttachment) {
        var type = attachment.type
        var name = attachment.name
        val file = EntityAttachment.getFile(context, attachment.id)
        val uri: Uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID, file)

        Log.i(Helper.TAG, "uri=$uri")

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndTypeAndNormalize(uri, type)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        if (!TextUtils.isEmpty(name)) {
            intent.putExtra(Intent.EXTRA_TITLE, name)
        }
        Log.i(Helper.TAG, "Intent=$intent type=$type")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            var ris: List<ResolveInfo>? = null
            try {
                val pm = context.packageManager
                ris = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                for (ri in ris) {
                    Log.i(Helper.TAG,"Target=$ri")
                    context.grantUriPermission(
                        ri.activityInfo.packageName, uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            } catch (err: Throwable) {
                Log.e(Helper.TAG, "Error opening attachment $err")
            }

            if (ris.isNullOrEmpty()) {
                Toast.makeText(
                    context,
                    String.format(context.getString(R.string.title_no_viewer), type),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        try {
            context.startActivity(intent)
        } catch (err: ActivityNotFoundException) {
            Toast.makeText(
                context,
                String.format(context.getString(R.string.title_no_viewer), type),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun set(attachments: List<EntityAttachment>) {
        Log.i(Helper.TAG, "Set attachments=" + attachments.size)

        attachments.sortedWith(compareBy { it.sequence })
        all = attachments
        val diff = DiffUtil.calculateDiff(MessageDiffCallback(filtered, all))
        filtered.clear()
        filtered.addAll(all)
        diff.dispatchUpdatesTo(
            object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) {
                    Log.i(Helper.TAG, "Inserted @$position #$count")
                }

                override fun onRemoved(position: Int, count: Int) {
                    Log.i(Helper.TAG, "Removed @$position #$count")
                }

                override fun onMoved(fromPosition: Int, toPosition: Int) {
                    Log.i(Helper.TAG, "Moved $fromPosition>$toPosition")
                }

                override fun onChanged(position: Int, count: Int, payload: Any?) {
                    Log.i(Helper.TAG, "Changed @$position #$count")
                }
            })
        diff.dispatchUpdatesTo(this)
    }

    private inner class MessageDiffCallback internal constructor(
        private val prev: List<EntityAttachment>,
        private val next: List<EntityAttachment>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int {
            return prev.size
        }

        override fun getNewListSize(): Int {
            return next.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val a1 = prev[oldItemPosition]
            val a2 = next[newItemPosition]
            return a1.id == a2.id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val a1 = prev[oldItemPosition]
            val a2 = next[newItemPosition]
            return a1 == a2
        }
    }

    override fun getItemId(position: Int): Long {
        return filtered[position].id
    }

    override fun getItemCount(): Int {
        return filtered.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAttachmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.unwire()
        val attachment = filtered[position]
        holder.bindTo(attachment)
        holder.wire()
    }
}
