package club.ozgur.gifland.core

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Singleton object providing a centralized coroutine scope for the entire application.
 * This ensures all long-running operations are properly supervised and cancellable.
 *
 * Benefits:
 * - Prevents multiple uncoordinated coroutine scopes
 * - Ensures proper cleanup on application exit
 * - Provides consistent error handling across the app
 * - Prevents race conditions from multiple scope instances
 */
object ApplicationScope {

    /**
     * Main application scope using SupervisorJob to prevent child failures from canceling siblings
     */
    private val supervisorJob = SupervisorJob()

    /**
     * Exception handler for uncaught coroutine exceptions
     */
    private val exceptionHandler = CoroutineExceptionHandler { context, throwable ->
        println("ApplicationScope: Uncaught exception in coroutine: ${throwable.message}")
        throwable.printStackTrace()
        // In production, this could send to a crash reporting service
    }

    /**
     * The main application scope
     * Uses Main dispatcher by default for UI operations
     */
    val scope: CoroutineScope = CoroutineScope(
        Dispatchers.Main + supervisorJob + exceptionHandler
    )

    /**
     * IO scope for disk/network operations
     */
    val ioScope: CoroutineScope = CoroutineScope(
        Dispatchers.IO + supervisorJob + exceptionHandler
    )

    /**
     * Default scope for CPU-intensive operations
     */
    val defaultScope: CoroutineScope = CoroutineScope(
        Dispatchers.Default + supervisorJob + exceptionHandler
    )

    /**
     * Launch a coroutine in the main scope
     */
    fun launch(
        context: CoroutineContext = Dispatchers.Main,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job = scope.launch(context, start, block)

    /**
     * Launch a coroutine in the IO scope
     */
    fun launchIO(
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job = ioScope.launch(start = start, block = block)

    /**
     * Launch a coroutine with async for returning results
     */
    fun <T> async(
        context: CoroutineContext = Dispatchers.Main,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> T
    ): Deferred<T> = scope.async(context, start, block)

    /**
     * Cancel all coroutines and cleanup
     * Should be called when the application exits
     */
    fun shutdown() {
        supervisorJob.cancel("Application shutdown")
    }

    /**
     * Check if the scope is still active
     */
    val isActive: Boolean
        get() = supervisorJob.isActive
}