package eu.heha.conifer.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * The main thread a [BitsViewModel] test needs, put up before each test and taken down after —
 * shared by every test that builds a real view model, because getting the taking down wrong is a
 * race that only some tests lose and none of them are about.
 *
 * A single thread of its own stands in for the main one. `viewModelScope` needs a Main dispatcher at
 * all, and it has to be a real one: the test bodies run on it because that is where the screen calls
 * the view model from, and `state = state.copy(...)` reads before it writes, so a call arriving on a
 * second thread can drop what a coroutine of its own wrote in between.
 *
 * Not [Dispatchers.Unconfined], which cannot serve as Main: it refuses to dispatch (only `yield` may
 * ask it to), while the dispatcher `setMain` installs never asks whether dispatching is needed and
 * simply does. Every database query hops to [Dispatchers.IO] and hops back, and that hop back is a
 * dispatch — it throws on a pool thread, where no test catches it, and kotlinx-coroutines-test
 * reports it against whichever test runs next.
 */
@OptIn(ExperimentalCoroutinesApi::class) // Dispatchers.setMain/resetMain
abstract class BitsViewModelTestCase {

    private lateinit var mainThread: ExecutorCoroutineDispatcher

    private val models = mutableListOf<BitsViewModel>()

    @BeforeTest
    fun installMainThread() {
        mainThread = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-main") }
            .asCoroutineDispatcher()
        Dispatchers.setMain(mainThread)
    }

    /**
     * Stops every view model [track]ed by the test, waits for it, and only then hands Main back.
     *
     * A view model's own coroutines outlive the test body — the collection of the bits in
     * particular, which hops to [Dispatchers.IO] for every query and hops back onto Main. Taking the
     * Main dispatcher away from under one still doing that fails with "Dispatchers.Main is used
     * concurrently with setting it", against this test or, worse, against whichever runs next. The
     * screen puts a view model down by going away; nothing here is testing what a half-cancelled
     * one does.
     */
    @AfterTest
    fun stopViewModelsAndReleaseMainThread() {
        runBlocking {
            models.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        }
        models.clear()
        Dispatchers.resetMain()
        mainThread.close()
    }

    /**
     * Registers [model] to be stopped when the test ends, and hands it straight back so that a
     * factory can read `track(BitsViewModel(...))`. Every view model a test builds wants this.
     */
    protected fun track(model: BitsViewModel): BitsViewModel = model.also { models += it }
}
