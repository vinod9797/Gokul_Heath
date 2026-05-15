package com.mindmatrix.gokulahealth;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "vaccinations",
        foreignKeys = @ForeignKey(entity = Cattle.class, parentColumns = "id", childColumns = "cattleId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("cattleId"), @Index("dueDate")}
)
public class Vaccination {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long cattleId;
    public String vaccineName;
    public long dueDate;
    public int repeatDays;
    public boolean completed;
    public boolean reminderSet;
}
