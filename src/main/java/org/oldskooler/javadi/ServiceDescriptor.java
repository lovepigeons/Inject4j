package org.oldskooler.javadi;

import java.util.function.Supplier;

final class ServiceDescriptor<T> {
    final Class<T> serviceType;
    final Class<? extends T> implType;     // optional
    final Supplier<? extends T> supplier;  // optional
    final T instance;                      // optional (for eager singletons / prebuilt)
    final ServiceLifetime lifetime;

    private ServiceDescriptor(Class<T> serviceType,
                              Class<? extends T> implType,
                              Supplier<? extends T> supplier,
                              T instance,
                              ServiceLifetime lifetime) {
        this.serviceType = serviceType;
        this.implType = implType;
        this.supplier = supplier;
        this.instance = instance;
        this.lifetime = lifetime;
    }

    static <T> ServiceDescriptor<T> instance(Class<T> serviceType, T instance, ServiceLifetime l) {
        return new ServiceDescriptor<>(serviceType, null, null, instance, l);
    }
    static <T> ServiceDescriptor<T> supplier(Class<T> serviceType, Supplier<? extends T> s, ServiceLifetime l) {
        return new ServiceDescriptor<>(serviceType, null, s, null, l);
    }
    static <T, I extends T> ServiceDescriptor<T> implementedBy(Class<T> serviceType, Class<I> implType, ServiceLifetime l) {
        return new ServiceDescriptor<>(serviceType, implType, null, null, l);
    }
}