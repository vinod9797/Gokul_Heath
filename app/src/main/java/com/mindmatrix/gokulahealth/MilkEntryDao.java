package com.mindmatrix.gokulahealth;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MilkEntryDao {
    @Insert
    long insert(MilkEntry entry);

    @Query("SELECT * FROM milk_entries WHERE cattleId = :cattleId ORDER BY entryDate DESC LIMIT 30")
    List<MilkEntry> lastThirtyForCow(long cattleId);

    @Query("SELECT * FROM milk_entries WHERE cattleId = :cattleId ORDER BY entryDate DESC LIMIT 7")
    List<MilkEntry> lastSevenForCow(long cattleId);

    @Query("SELECT AVG(morningLiters + eveningLiters) FROM milk_entries WHERE cattleId = :cattleId AND entryDate >= :fromDate")
    Double monthlyAverage(long cattleId, long fromDate);

    @Query("SELECT AVG(morningLiters + eveningLiters) FROM milk_entries WHERE cattleId = :cattleId AND entryDate >= :fromDate")
    Double averageSince(long cattleId, long fromDate);

    @Query("SELECT SUM(morningLiters + eveningLiters) FROM milk_entries WHERE entryDate = :date")
    Double totalForDate(long date);
}
