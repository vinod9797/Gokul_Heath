package com.mindmatrix.gokulahealth;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "farmer_profile")
public class FarmerProfile {
    @PrimaryKey
    public long id = 1;
    public String mobile;
    public String name;
    public String village;
    public int cattleCount;
    public String language;
}
