package org.modelix.incremental

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.Observables
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.kotlin.toObservable
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class ReactiveSandboxTest {
    @Test
    fun test() {
        listOf(2, 1, 0, -1).toObservable().flatMap { Observable.just(10 / it) }.doOnError { println("Error: $it") }.subscribeBy(
            onNext = { println(it) },
            onError = { println("Error: $it") },
            onComplete = { println("done") }
        )
    }
}