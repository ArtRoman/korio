@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package com.soywiz.korio.async

import java.io.Closeable

class Signal<T>(val onRegister: () -> Unit = {}) : AsyncSequence<T> {
	internal val handlers = arrayListOf<(T) -> Unit>()

	fun once(handler: (T) -> Unit): Closeable {
		var c: Closeable? = null
		c = add {
			c!!.close()
			handler(it)
		}
		return c
	}

	fun add(handler: (T) -> Unit): Closeable {
		onRegister()
		synchronized(handlers) { handlers += handler }
		return Closeable { synchronized(handlers) { handlers -= handler } }
	}

	operator fun invoke(value: T) {
		EventLoop.queue {
			for (handler in synchronized(handlers) { handlers.toList() }) {
				handler(value)
			}
		}
		//while (handlers.isNotEmpty()) {
		//	val handler = handlers.remove()
		//	handler.invoke(value)
		//}
	}

	operator fun invoke(value: (T) -> Unit): Closeable = add(value)

	override fun iterator(): AsyncIterator<T> = asyncGenerate {
		while (true) {
			yield(waitOne())
		}
	}.iterator()
}

operator fun Signal<Unit>.invoke() = invoke(Unit)

suspend fun <T> Signal<T>.waitOne(): T = suspendCancellableCoroutine { c ->
	var close: Closeable? = null
	close = add {
		close?.close()
		c.resume(it)
	}
	c.onCancel {
		close?.close()
	}
}