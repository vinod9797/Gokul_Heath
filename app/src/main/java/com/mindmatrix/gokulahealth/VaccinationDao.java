package com.mindmatrix.gokulahealth;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface VaccinationDao {
    @Insert
    long insert(Vaccination vaccination);

    @Update
    void update(Vaccination vaccination);

    @Query("SELECT * FROM vaccinations ORDER BY dueDate ASC")
    List<Vaccination> getAllUpcoming();

    @Query("SELECT * FROM vaccinations WHERE completed = 0 ORDER BY dueDate ASC")
    List<Vaccination> pending();

    @Query("SELECT COUNT(*) FROM vaccinations WHERE completed = 0 AND dueDate <= :untilDate")
    int dueCount(long untilDate);
}
