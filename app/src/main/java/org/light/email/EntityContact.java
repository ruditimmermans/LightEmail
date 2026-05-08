package org.light.email;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = EntityContact.TABLE_NAME,
    indices = {@Index(value = {"email"}, unique = true)})
public class EntityContact {
    static final String TABLE_NAME = "contact";

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull
    public String name;
    @NonNull
    public String email;

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EntityContact) {
            EntityContact other = (EntityContact) obj;
            return (this.name.equals(other.name) && this.email.equals(other.email));
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return name + " <" + email + ">";
    }
}
