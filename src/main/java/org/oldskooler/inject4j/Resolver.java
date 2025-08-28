package org.oldskooler.inject4j;

interface Resolver {
    <T> T getService(Class<T> type);
    boolean canResolve(Class<?> type);
}