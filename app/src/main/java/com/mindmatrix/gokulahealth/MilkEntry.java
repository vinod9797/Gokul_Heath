package com.mindmatrix.gokulahealth;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "milk_entries",
        foreignKeys = @ForeignKey(entity = Cattle.class, parentColumns = "id", childColumns = "cattleId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("cattleId"), @Index("entryDate")}
)
public class MilkEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long cattleId;
    public long entryDate;
    public double morningLiters;
    public double eveningLiters;
    public double totalLiters;
    public String notes;
}
