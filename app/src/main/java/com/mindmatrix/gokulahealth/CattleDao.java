package com.mindmatrix.gokulahealth;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CattleDao {
    @Insert
    long insert(Cattle cattle);

    @Update
    void update(Cattle cattle);

    @Delete
    void delete(Cattle cattle);

    @Query("SELECT * FROM cattle ORDER BY createdAt DESC")
    List<Cattle> getAll();

    @Query("SELECT * FROM cattle WHERE id = :id LIMIT 1")
    Cattle getById(long id);

    @Query("SELECT * FROM cattle WHERE earTag LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    List<Cattle> search(String query);

    @Query("SELECT COUNT(*) FROM cattle WHERE pregnant = 1")
    int pregnantCount();
}
