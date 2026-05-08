package org.light.email;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface DaoContact {
    @Query("SELECT * FROM contact ORDER BY name COLLATE NOCASE")
    LiveData<List<EntityContact>> liveContacts();

    @Query("SELECT * FROM contact WHERE id = :id")
    EntityContact getContact(long id);

    @Query("SELECT * FROM contact WHERE name LIKE :query OR email LIKE :query ORDER BY name COLLATE NOCASE")
    List<EntityContact> searchContacts(String query);

    @Insert
    long insertContact(EntityContact contact);

    @Update
    void updateContact(EntityContact contact);

    @Query("DELETE FROM contact WHERE id = :id")
    void deleteContact(long id);
}
