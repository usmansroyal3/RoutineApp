package com.routine.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.routine.data.db.dao.*
import com.routine.data.model.*

class Converters {
    @TypeConverter
    fun fromRoutineType(value: RoutineType): String = value.name

    @TypeConverter
    fun toRoutineType(value: String): RoutineType = RoutineType.valueOf(value)

    @TypeConverter
    fun fromRoutineStatus(value: RoutineStatus): String = value.name

    @TypeConverter
    fun toRoutineStatus(value: String): RoutineStatus = RoutineStatus.valueOf(value)
}

@Database(
    entities = [Task::class, TaskLog::class, Routine::class, Note::class, Reminder::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskLogDao(): TaskLogDao
    abstract fun routineDao(): RoutineDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
}
