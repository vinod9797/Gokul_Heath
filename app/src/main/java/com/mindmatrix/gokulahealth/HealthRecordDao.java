package com.mindmatrix.gokulahealth;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HealthRecordDao {
    @Insert
    long insert(HealthRecord record);

    @Query("SELECT * FROM health_records WHERE cattleId = :cattleId ORDER BY recordDate DESC")
    List<HealthRecord> forCow(long cattleId);
}
