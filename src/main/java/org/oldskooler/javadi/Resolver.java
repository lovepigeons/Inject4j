package org.oldskooler.javadi;

import java.util.Deque;

interface Resolver {
    <T> T getService(Class<T> type);
    boolean canResolve(Class<?> type);
}