package org.oldskooler.inject4j;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Internal helper for constructor-based activation of types.
 * <p>
 * This class is not used directly by consumers. Instead, it powers
 * {@link ServiceProvider#createInstance(Class, Object...)} and
 * {@link Scope#createInstance(Class, Object...)} by resolving constructor
 * arguments using a combination of explicit arguments and services.
 * <p>
 * Behavior is modeled after .NET's
 * {@code ActivatorUtilities.CreateInstance}:
 * <ul>
 *   <li>Any explicit arguments are matched first by assignable type.</li>
 *   <li>Remaining parameters are resolved from the current service provider/scope.</li>
 *   <li>The "greediest" satisfiable constructor is chosen.</li>
 *   <li>If no constructor can be fully satisfied, a {@link ServiceNotFoundException} is thrown.</li>
 * </ul>
 *
 * @implNote This class is package-private and not intended for external use.
 */
final class Activator {

    /** Hidden constructor to prevent instantiation. */
    private Activator() {}

    /**
     * Core activation routine used by {@link ServiceProvider} and {@link Scope}.
     * <p>
     * Tries each constructor (preferring those with more parameters) and
     * attempts to bind parameters using:
     * <ol>
     *   <li>Explicit arguments (consumed by type assignability)</li>
     *   <li>Fallback resolution via the {@link ServiceResolver}</li>
     * </ol>
     *
     * @param resolver     callback to resolve services from a provider/scope
     * @param type         the concrete class to instantiate (never {@code null})
     * @param explicitArgs optional explicit arguments to bind before DI
     * @param <T>          the requested type
     * @return a new instance of {@code type}
     * @throws ServiceNotFoundException if no constructor can be fully satisfied
     * @throws RuntimeException         if instantiation fails (reflection issues)
     */
    static <T> T createInstance(ServiceResolver resolver, Class<T> type, Object... explicitArgs) {
        Objects.requireNonNull(type, "type");
        List<Object> remaining = new ArrayList<>(Arrays.asList(explicitArgs));

        Constructor<?>[] ctors = type.getDeclaredConstructors();
        Arrays.sort(ctors, (a, b) -> Integer.compare(b.getParameterCount(), a.getParameterCount()));

        for (Constructor<?> ctor : ctors) {
            Object[] bound = tryBind(ctor, resolver, remaining);
            if (bound != null) {
                try {
                    ctor.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    T instance = (T) ctor.newInstance(bound);
                    return instance;
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException("Failed to construct " + type.getName() + " via " + ctor, e);
                }
            }
        }

        throw new ServiceNotFoundException("No constructor of " + type.getName()
                + " could be satisfied by explicit arguments + services");
    }

    /**
     * Attempts to bind constructor parameters from explicit arguments first,
     * then from the service resolver. Returns {@code null} if binding fails.
     */
    private static Object[] tryBind(Constructor<?> ctor, ServiceResolver resolver, List<Object> explicit) {
        Class<?>[] params = ctor.getParameterTypes();
        Object[] bound = new Object[params.length];
        List<Object> remaining = new ArrayList<>(explicit);

        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i];

            int idx = indexOfAssignable(remaining, p);
            if (idx >= 0) {
                bound[i] = remaining.remove(idx);
                continue;
            }

            Object service = resolver.getService(p);
            if (service != null) {
                bound[i] = service;
                continue;
            }

            return null; // cannot satisfy this constructor
        }

        explicit.clear();
        explicit.addAll(remaining);
        return bound;
    }

    /** Finds the index of the first assignable object in the list. */
    private static int indexOfAssignable(List<Object> list, Class<?> target) {
        for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (o != null && target.isAssignableFrom(o.getClass())) return i;
        }
        return -1;
    }

    /**
     * Callback abstraction so both {@link ServiceProvider} and {@link Scope}
     * can act as service resolvers.
     */
    interface ServiceResolver {
        /**
         * Resolves a service instance for the requested type.
         *
         * @param type the service type to resolve
         * @param <T>  the service type
         * @return the resolved service instance, or {@code null} if not found
         */
        <T> T getService(Class<T> type);
    }
}
