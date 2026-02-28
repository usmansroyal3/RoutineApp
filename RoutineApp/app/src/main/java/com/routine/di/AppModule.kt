package com.routine.di

import android.content.Context
import androidx.room.Room
import com.routine.data.db.AppDatabase
import com.routine.data.db.dao.*
import com.routine.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "routine_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideTaskLogDao(db: AppDatabase): TaskLogDao = db.taskLogDao()
    @Provides fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()
    @Provides fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideTaskRepository(
        taskDao: TaskDao,
        taskLogDao: TaskLogDao,
        routineDao: RoutineDao
    ): TaskRepository = TaskRepository(taskDao, taskLogDao, routineDao)

    @Provides
    @Singleton
    fun provideRoutineRepository(routineDao: RoutineDao): RoutineRepository =
        RoutineRepository(routineDao)

    @Provides
    @Singleton
    fun provideNoteRepository(noteDao: NoteDao, reminderDao: ReminderDao): NoteRepository =
        NoteRepository(noteDao, reminderDao)

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)
}

// ─── Entry Point for Widget Actions ──────────────────────────────────────────
// Widgets can't use constructor injection, so we use entry points

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun taskRepository(): TaskRepository
    fun routineRepository(): RoutineRepository
    fun noteRepository(): NoteRepository
    fun settingsRepository(): SettingsRepository

    companion object {
        fun get(context: Context): RepositoryEntryPoint {
            return dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                RepositoryEntryPoint::class.java
            )
        }
    }
}
