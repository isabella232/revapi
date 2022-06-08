/*
 * Copyright 2014-2021 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.java.spi;

import javax.annotation.Nonnull;

/**
 * A utility class to try to overcome the {@code CompletionFailure} exceptions thrown from the Java compiler. Such
 * exceptions "disappear" when the method is re-tried which this helper enables.
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public final class IgnoreCompletionFailures {
    private static final int RETRY_CNT = 10;

    private IgnoreCompletionFailures() {

    }

    private static boolean isCompletionFailure(@Nonnull Throwable t) {
        Class<?> c = t.getClass();
        while (c != null) {
            if (c.getName().endsWith("CompletionFailure")) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    public static <R> R in(Fn0<R> action) {
        Throwable fail;
        int cnt = 0;

        do {
            try {
                return action.call();
            } catch (Exception e) {
                fail = e;
            }
        } while (cnt++ < RETRY_CNT || isCompletionFailure(fail));

        throw new IllegalStateException(fail);
    }

    public static void inVoid(VoidFn0 action) {
        Throwable fail;
        int cnt = 0;

        do {
            try {
                action.call();
                return;
            } catch (Exception e) {
                fail = e;
            }
        } while (cnt++ < RETRY_CNT || isCompletionFailure(fail));

        throw new IllegalStateException(fail);
    }

    public static <R, T> R in(Fn1<R, T> action, T arg) {
        Throwable fail;
        int cnt = 0;

        do {
            try {
                return action.call(arg);
            } catch (Exception e) {
                fail = e;
            }
        } while (cnt++ < RETRY_CNT || isCompletionFailure(fail));

        throw new IllegalStateException(fail);
    }

    public static <T> void inVoid(VoidFn1<T> action, T arg) {
        Throwable fail;
        int cnt = 0;

        do {
            try {
                action.call(arg);
                return;
            } catch (Exception e) {
                fail = e;
            }
        } while (cnt++ < RETRY_CNT || isCompletionFailure(fail));

        throw new IllegalStateException(fail);
    }

    public static <R, T1, T2> R in(Fn2<R, T1, T2> action, T1 arg1, T2 arg2) {
        Throwable fail;
        int cnt = 0;

        do {
            try {
                return action.call(arg1, arg2);
            } catch (Exception e) {
                fail = e;
            }
        } while (cnt++ < RETRY_CNT || isCompletionFailure(fail));

        throw new IllegalStateException(fail);
    }

    public static <T1, T2> void inVoid(VoidFn2<T1, T2> action, T1 arg1, T2 arg2) {
        Throwable fail;
        int cnt = 0;

        do {
            try {
                action.call(arg1, arg2);
                return;
            } catch (Exception e) {
                fail = e;
            }
        } while (cnt++ < RETRY_CNT || isCompletionFailure(fail));

        throw new IllegalStateException(fail);
    }

    public static <R, T1, T2, T3> R in(Fn3<R, T1, T2, T3> action, T1 arg1, T2 arg2, T3 arg3) {
        Throwable fail;
        int cnt = 0;

        do {
            try {
                return action.call(arg1, arg2, arg3);
            } catch (Exception e) {
                fail = e;
            }
        } while (cnt++ < RETRY_CNT || isCompletionFailure(fail));

        throw new IllegalStateException(fail);
    }

    public static <T1, T2, T3> void inVoid(VoidFn3<T1, T2, T3> action, T1 arg1, T2 arg2, T3 arg3) {
        Throwable fail;
        int cnt = 0;

        do {
            try {
                action.call(arg1, arg2, arg3);
                return;
            } catch (Exception e) {
                fail = e;
            }
        } while (cnt++ < RETRY_CNT || isCompletionFailure(fail));

        throw new IllegalStateException(fail);
    }

    @FunctionalInterface
    public interface Fn0<R> {
        R call() throws Exception;
    }

    @FunctionalInterface
    public interface Fn1<R, T> {
        R call(T t) throws Exception;
    }

    @FunctionalInterface
    public interface Fn2<R, T1, T2> {
        R call(T1 t1, T2 t2) throws Exception;
    }

    @FunctionalInterface
    public interface Fn3<R, T1, T2, T3> {
        R call(T1 t1, T2 t2, T3 t3) throws Exception;
    }

    @FunctionalInterface
    public interface VoidFn0 {
        void call() throws Exception;
    }

    @FunctionalInterface
    public interface VoidFn1<T> {
        void call(T t) throws Exception;
    }

    @FunctionalInterface
    public interface VoidFn2<T1, T2> {
        void call(T1 arg1, T2 arg2) throws Exception;
    }

    @FunctionalInterface
    public interface VoidFn3<T1, T2, T3> {
        void call(T1 t1, T2 t2, T3 t3) throws Exception;
    }
}
