package org.modelix.incremental

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableSource
import io.reactivex.rxjava3.kotlin.Observables
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.kotlin.toObservable
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class ReactiveSandboxTest {
    @Test
    fun test() {
        val rootNode = MNode("Root").apply {
            child("Entity", "entities") {
                property("name", "EntityA")
                child("Property", "properties") {
                    property("name", "propertyA1")
                }
                child("Property", "properties") {
                    property("name", "propertyA2")
                }
            }
        }
        val function: (MNode) -> ObservableSource<out MNode> = { it.children.toObservable() }
        Observable.just(rootNode).flatMap(function)
        listOf(2, 1, 0, -1).toObservable().flatMap { Observable.just(10 / it) }.doOnError { println("Error: $it") }.subscribeBy(
            onNext = { println(it) },
            onError = { println("Error: $it") },
            onComplete = { println("done") }
        )
    }
}