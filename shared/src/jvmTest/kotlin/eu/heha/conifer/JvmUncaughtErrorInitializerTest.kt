package eu.heha.conifer

import eu.heha.conifer.log.UncaughtError
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The default uncaught exception handler is process-wide state, so this saves and restores it -
 * a test that left this app's handler installed would report the *next* test's failures into it.
 */
class JvmUncaughtErrorInitializerTest {

    private val original = Thread.getDefaultUncaughtExceptionHandler()

    @AfterTest
    fun restoreHandler() {
        Thread.setDefaultUncaughtExceptionHandler(original)
    }

    @Test
    fun reportsTheErrorAndThenLetsThePreviousHandlerRun() {
        val reported = mutableListOf<UncaughtError>()
        val handledByPrevious = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> handledByPrevious += throwable }

        JvmUncaughtErrorInitializer.installHandler { reported += it }
        val boom = IllegalStateException("boom")
        Thread.getDefaultUncaughtExceptionHandler()
            .uncaughtException(Thread.currentThread(), boom)

        assertEquals(1, reported.size)
        assertSame(boom, reported.single().throwable)
        assertEquals(Thread.currentThread().name, reported.single().origin)
        // The crash still plays out the way it did before - Android's kill handler, the JVM's stderr
        // trace - because a crash this app quietly ate would be one nobody ever hears about.
        assertEquals(listOf<Throwable>(boom), handledByPrevious)
    }

    /** Nothing was installed before (a plain JVM): the handler still has to report and not throw. */
    @Test
    fun reportsWithNoPreviousHandlerInstalled() {
        val reported = mutableListOf<UncaughtError>()
        Thread.setDefaultUncaughtExceptionHandler(null)

        JvmUncaughtErrorInitializer.installHandler { reported += it }
        Thread.getDefaultUncaughtExceptionHandler()
            .uncaughtException(Thread.currentThread(), IllegalStateException("boom"))

        assertTrue(reported.size == 1, "expected one report, got $reported")
    }
}
