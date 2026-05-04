package org.dystopia.email;

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

    Copyright 2018, Marcel Bokhorst (M66B)
*/

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static androidx.room.ForeignKey.CASCADE;

@Entity(
    tableName = EntityOperation.TABLE_NAME,
    foreignKeys = {
        @ForeignKey(
            childColumns = "folder",
            entity = EntityFolder.class,
            parentColumns = "id",
            onDelete = CASCADE),
        @ForeignKey(
            childColumns = "message",
            entity = EntityMessage.class,
            parentColumns = "id",
            onDelete = CASCADE)
    },
    indices = {@Index(value = {"folder"}), @Index(value = {"message"})})
public class EntityOperation {
    static final String TABLE_NAME = "operation";

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull
    public Long folder;
    public Long message;
    @NonNull
    public String name;
    @NonNull
    public String args;
    @NonNull
    public Long created;

    public static final String SEEN = "seen";
    public static final String ADD = "add";
    public static final String MOVE = "move";
    public static final String DELETE = "delete";
    public static final String SEND = "send";
    public static final String HEADERS = "headers";
    public static final String BODY = "body";
    public static final String ATTACHMENT = "attachment";
    public static final String FLAG = "flag";
    public static final String SYNC = "sync";

    private static List<Intent> queue = new ArrayList<>();

    static void queue(DB db, EntityMessage message, String name) {
        JSONArray jsonArray = new JSONArray();
        queue(db, message, name, jsonArray);
    }

    static void queue(DB db, EntityMessage message, String name, Object value) {
        JSONArray jsonArray = new JSONArray();
        jsonArray.put(value);
        queue(db, message, name, jsonArray);
    }

    private static void queue(DB db, EntityMessage message, String name, JSONArray jsonArray) {
        EntityOperation operation = new EntityOperation();
        operation.folder = message.folder;
        operation.message = message.id;
        operation.name = name;
        operation.args = jsonArray.toString();
        operation.created = new Date().getTime();
        operation.id = db.operation().insertOperation(operation);

        Intent intent = new Intent();
        intent.setType("account/" + (SEND.equals(name) ? "outbox" : message.account));
        intent.setAction(ServiceSynchronize.ACTION_PROCESS_OPERATIONS);
        intent.putExtra("folder", message.folder);

        synchronized (queue) {
            queue.add(intent);
        }

        Log.i(
            Helper.TAG,
            "Queued op="
                + operation.id
                + "/"
                + operation.name
                + " msg="
                + message.folder
                + "/"
                + operation.message
                + " args="
                + operation.args);
    }

    private static void queue(DB db, long folder, Long message, String name, JSONArray jargs) {
        EntityOperation operation = new EntityOperation();
        operation.folder = folder;
        operation.message = message;
        operation.name = name;
        operation.args = jargs.toString();
        operation.created = new Date().getTime();
        operation.id = db.operation().insertOperation(operation);

        Log.i(Helper.TAG, "Queued op=" + operation.id + "/" + operation.name +
            " msg=" + operation.folder + "/" + operation.message +
            " args=" + operation.args);
    }

    public static void process(Context context) {
        // Processing needs to be done after committing to the database
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
        synchronized (queue) {
            for (Intent intent : queue) {
                lbm.sendBroadcast(intent);
            }
            queue.clear();
        }
    }

    static void sync(DB db, long folder) {
        if (db.operation().getOperationCount(folder, EntityOperation.SYNC) == 0) {
            queue(db, folder, null, EntityOperation.SYNC, new JSONArray());
            db.folder().setFolderSyncState(folder, "requested");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EntityOperation) {
            EntityOperation other = (EntityOperation) obj;
            return (this.folder.equals(other.folder)
                && this.message.equals(other.message)
                && this.name.equals(other.name)
                && this.args.equals(other.args));
        } else {
            return false;
        }
    }
}
