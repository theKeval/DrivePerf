package com.driveperf.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * A shared presenter (ADR-002 section 4): one immutable [state], intents in through [dispatch], one-shot [effects]
 * (navigation, toasts) out. Presenters call use cases and repositories only, never `..data..`.
 *
 * Android: a `ViewModel` owns it on `viewModelScope`. iOS: an `@Observable @MainActor` view model owns it, observes
 * [state] with SKIE's `AsyncSequence` in a `.task {}`, and calls [close] on deinit.
 */
public interface Presenter<S, I, E> {
    public val state: StateFlow<S>

    /** One-shot events. Each is delivered to one collector, once. */
    public val effects: Flow<E>

    public fun dispatch(intent: I)

    /** Cancels the presenter's work. Its last [state] stays readable. */
    public fun close()
}

/**
 * The plumbing every presenter shares. Work runs in a child of [parent], so closing the presenter never cancels the
 * owner's scope, and cancelling the owner's scope (`viewModelScope`) closes the presenter.
 */
public abstract class BasePresenter<S, I, E>(initial: S, parent: CoroutineScope) : Presenter<S, I, E> {
    protected val scope: CoroutineScope =
        CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))

    private val mutableState = MutableStateFlow(initial)
    private val effectChannel = Channel<E>(Channel.BUFFERED)

    override val state: StateFlow<S> = mutableState.asStateFlow()
    override val effects: Flow<E> = effectChannel.receiveAsFlow()

    /** Applies [change] atomically. */
    protected fun update(change: (S) -> S) {
        mutableState.update(change)
    }

    protected fun emit(effect: E) {
        effectChannel.trySend(effect)
    }

    override fun close() {
        scope.cancel()
    }
}
