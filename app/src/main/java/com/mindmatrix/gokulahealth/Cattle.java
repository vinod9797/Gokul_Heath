package com.mindmatrix.gokulahealth;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cattle")
public class Cattle {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name;
    public String earTag;
    public String breed;
    public int age;
    public double weight;
    public String gender;
    public String photoUri;
    public long purchaseDate;
    public boolean pregnant;
    public long lastHeatDate;
    public String breedingStatus;
    public long createdAt;
}
