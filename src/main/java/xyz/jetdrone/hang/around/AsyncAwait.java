/*
 * Copyright 2015 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package xyz.jetdrone.hang.around;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.util.concurrent.locks.LockSupport;

public final class AsyncAwait {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncAwait.class);

    public static void asyncRun(Runnable runnable) {
        Thread
                .ofVirtual()
                .name("async-await")
                .uncaughtExceptionHandler((thread, err) -> LOG.error("Uncaught Exception @" + thread, err))
                .start(runnable);
    }

    public static <T> Handler<T> async(Handler<T> handler) {
        return value -> {
            final Thread carrier = Thread.currentThread();

            if (carrier.isVirtual() && "async-await".equals(carrier.getName())) {
                // we're inside an async-await block, no need to spawn another one
                handler.handle(value);
            } else {
                Thread
                        .ofVirtual()
                        .name("async-await")
                        .uncaughtExceptionHandler((thread, err) -> LOG.error("Uncaught Exception @" + thread, err))
                        .start(() -> handler.handle(value));
            }
        };
    }

    public static <T> T await(Future<T> future) {
        final Thread carrier = Thread.currentThread();

        if (!carrier.isVirtual() || !"async-await".equals(carrier.getName())) {
            throw new IllegalStateException("async-await thread mismatch");
        }

        return new AsyncAwaitResult<>(carrier, future).get();
    }

    private static final class AsyncAwaitResult<T> implements Handler<AsyncResult<T>> {

        private final Thread thread;
        private AsyncResult<T> ref;

        AsyncAwaitResult(Thread thread, Future<T> future) {
            this.thread = thread;
            future.onComplete(this);
        }

        @Override
        public void handle(AsyncResult<T> event) {
            this.ref = event;
            LockSupport.unpark(thread);
        }

        public T get() {
            LockSupport.park();
            // several things could lead to an unpark

            // 1. the thread was interrupted
            if (Thread.interrupted()) {
                // this could be problematic as the result has not been handled yet
                throw new IllegalStateException("async-await thread was interrupted");
            }

            // 1. unpark was called
            if (ref.succeeded()) {
                return ref.result();
            } else {
                Throwable cause = ref.cause();
                // throw as-is
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                // will wrap exoteric exceptions
                throw new RuntimeException(cause);
            }
        }
    }

}
