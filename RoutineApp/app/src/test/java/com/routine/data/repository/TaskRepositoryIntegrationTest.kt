package com.routine.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.routine.data.db.AppDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskRepositoryIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: TaskRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = TaskRepository(
            database.taskDao(),
            database.taskLogDao(),
            database.routineDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `logging a completion is emitted by the observable task state`() = runTest {
        val taskId = repository.createTask("Water plants", "🌱")
        val before = repository.observeTasksWithState()
            .first { tasks -> tasks.any { it.task.id == taskId } }

        assertNull(before.single { it.task.id == taskId }.lastLog)

        val completionUpdate = async {
            repository.observeTasksWithState().first { tasks ->
                tasks.firstOrNull { it.task.id == taskId }?.lastLog != null
            }
        }

        repository.logTask(taskId, null, null, null)

        val completedTask = completionUpdate.await().single { it.task.id == taskId }
        assertNotNull(completedTask.lastLog)
        assertEquals(taskId, completedTask.lastLog?.taskId)
    }

    @Test
    fun `clearing a deleted widget makes its task available again`() = runTest {
        val taskId = repository.createTask("Feed dog", "🐕")
        repository.assignWidget(taskId, 101)
        assertEquals(101, repository.getTask(taskId)?.widgetId)

        repository.clearWidgetAssignments(intArrayOf(101))

        assertEquals(-1, repository.getTask(taskId)?.widgetId)
    }
}
