package com.mindmatrix.gokulahealth;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Cattle.class, MilkEntry.class, Vaccination.class, FarmerProfile.class, HealthRecord.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    public abstract CattleDao cattleDao();
    public abstract MilkEntryDao milkEntryDao();
    public abstract VaccinationDao vaccinationDao();
    public abstract FarmerProfileDao farmerProfileDao();
    public abstract HealthRecordDao healthRecordDao();

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "gokula_health.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
