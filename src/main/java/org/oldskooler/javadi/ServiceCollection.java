package org.oldskooler.javadi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * A collection for registering service descriptors that define how services
 * should be constructed and managed. This class supports registration of
 * services with different lifetimes (singleton, scoped, transient) and
 * different creation strategies (pre-existing instances, factories, or
 * implementation types).
 *
 * <p>
 * After configuring the collection, call {@link #buildServiceProvider()} to
 * produce a {@link ServiceProvider} capable of resolving registered services.
 * </p>
 */
public class ServiceCollection {
    private final List<ServiceDescriptor<?>> serviceDescriptors = new ArrayList<>();

    // --- Singleton registrations ---

    /**
     * Registers a singleton where the service type is the same as the implementation type.
     * The container will construct the implementation once and reuse it for all requests.
     *
     * <p>Equivalent to calling {@code addSingleton(type, type)}.</p>
     *
     * @param <T>  The concrete service type.
     * @param type The concrete class to register as both the service and implementation.
     */
    public <T> void addSingleton(Class<T> type) {
        serviceDescriptors.add(ServiceDescriptor.implementedBy(type, type, ServiceLifetime.SINGLETON));
    }

    /**
     * Registers a specific pre-created instance as a singleton for the given service type.
     * The same instance will always be returned when this service is requested.
     *
     * @param <T>         The service type.
     * @param serviceType The service abstraction or interface.
     * @param instance    The concrete instance to use.
     */
    public <T> void addSingleton(Class<T> serviceType, T instance) {
        serviceDescriptors.add(ServiceDescriptor.instance(serviceType, instance, ServiceLifetime.SINGLETON));
    }

    /**
     * Registers a singleton using a factory that produces the instance eagerly at registration time.
     * The produced instance will always be reused for this service type.
     *
     * @param <T>         The service type.
     * @param serviceType The service abstraction or interface.
     * @param factory     A supplier that produces the singleton instance. It is invoked immediately.
     */
    public <T> void addSingletonFactory(Class<T> serviceType, Supplier<? extends T> factory) {
        T instance = factory.get(); // eager by design
        serviceDescriptors.add(ServiceDescriptor.instance(serviceType, instance, ServiceLifetime.SINGLETON));
    }

    /**
     * Registers a singleton service by mapping an abstraction to an implementation type.
     * The container will construct the implementation once and reuse it.
     *
     * @param <T>              The service type (abstraction).
     * @param <I>              The implementation type.
     * @param serviceType      The service abstraction or interface.
     * @param implementationType The concrete class implementing the service.
     */
    public <T, I extends T> void addSingleton(Class<T> serviceType, Class<I> implementationType) {
        serviceDescriptors.add(ServiceDescriptor.implementedBy(serviceType, implementationType, ServiceLifetime.SINGLETON));
    }

    // --- Scoped registrations ---

    /**
     * Registers a scoped service using a factory.
     * A new instance is created per scope (e.g., per request in a web context).
     *
     * @param <T>         The service type.
     * @param serviceType The service abstraction or interface.
     * @param factory     A supplier that produces a new instance per scope.
     */
    public <T> void addScopedFactory(Class<T> serviceType, Supplier<? extends T> factory) {
        serviceDescriptors.add(ServiceDescriptor.supplier(serviceType, factory, ServiceLifetime.SCOPED));
    }

    /**
     * Registers a scoped service where the service type is the same as the implementation type.
     * A new instance is created per scope (e.g., per web request).
     *
     * <p>Equivalent to calling {@code addScoped(type, type)}.</p>
     *
     * @param <T>  The concrete service type.
     * @param type The concrete class to register as both the service and implementation.
     */
    public <T> void addScoped(Class<T> type) {
        serviceDescriptors.add(ServiceDescriptor.implementedBy(type, type, ServiceLifetime.SCOPED));
    }

    /**
     * Registers a scoped service by mapping an abstraction to an implementation type.
     * A new instance is created per scope.
     *
     * @param <T>              The service type (abstraction).
     * @param <I>              The implementation type.
     * @param serviceType      The service abstraction or interface.
     * @param implementationType The concrete class implementing the service.
     */
    public <T, I extends T> void addScoped(Class<T> serviceType, Class<I> implementationType) {
        serviceDescriptors.add(ServiceDescriptor.implementedBy(serviceType, implementationType, ServiceLifetime.SCOPED));
    }

    // --- Transient registrations ---

    /**
     * Registers a transient service using a factory.
     * A new instance is created each time the service is requested.
     *
     * @param <T>         The service type.
     * @param serviceType The service abstraction or interface.
     * @param factory     A supplier that produces a fresh instance on each request.
     */
    public <T> void addTransientFactory(Class<T> serviceType, Supplier<? extends T> factory) {
        serviceDescriptors.add(ServiceDescriptor.supplier(serviceType, factory, ServiceLifetime.TRANSIENT));
    }

    /**
     * Registers a transient service by mapping an abstraction to an implementation type.
     * A new instance is created each time the service is requested.
     *
     * @param <T>              The service type (abstraction).
     * @param <I>              The implementation type.
     * @param serviceType      The service abstraction or interface.
     * @param implementationType The concrete class implementing the service.
     */
    public <T, I extends T> void addTransient(Class<T> serviceType, Class<I> implementationType) {
        serviceDescriptors.add(ServiceDescriptor.implementedBy(serviceType, implementationType, ServiceLifetime.TRANSIENT));
    }

    /**
     * Registers a transient service where the service type is the same as the implementation type.
     * A new instance is created each time the service is requested.
     *
     * <p>Equivalent to calling {@code addTransient(type, type)}.</p>
     *
     * @param <T>  The concrete service type.
     * @param type The concrete class to register as both the service and implementation.
     */
    public <T> void addTransient(Class<T> type) {
        serviceDescriptors.add(ServiceDescriptor.implementedBy(type, type, ServiceLifetime.TRANSIENT));
    }

    /**
     * Builds an immutable {@link ServiceProvider} based on the registered service descriptors.
     *
     * @return A {@link ServiceProvider} capable of resolving services defined in this collection.
     */
    public ServiceProvider buildServiceProvider() {
        return new ServiceProvider(Collections.unmodifiableList(serviceDescriptors));
    }
}
