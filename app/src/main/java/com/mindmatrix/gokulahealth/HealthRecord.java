package com.mindmatrix.gokulahealth;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "health_records",
        foreignKeys = @ForeignKey(entity = Cattle.class, parentColumns = "id", childColumns = "cattleId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("cattleId"), @Index("recordDate")}
)
public class HealthRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long cattleId;
    public long recordDate;
    public String symptoms;
    public String treatment;
    public String vetNotes;
}
