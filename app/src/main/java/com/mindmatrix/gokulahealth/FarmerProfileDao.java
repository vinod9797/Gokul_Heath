package com.mindmatrix.gokulahealth;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface FarmerProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(FarmerProfile profile);

    @Query("SELECT * FROM farmer_profile WHERE id = 1 LIMIT 1")
    FarmerProfile get();
}
