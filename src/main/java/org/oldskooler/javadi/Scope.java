package org.oldskooler.javadi;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A resolution scope that can construct and cache services according to their {@link ServiceLifetime}.
 * <p>
 * A {@code Scope} provides:
 * </p>
 * <ul>
 *   <li><b>Singleton</b> caching shared with the root provider (via {@code singletonCache}).</li>
 *   <li><b>Scoped</b> caching local to this scope (cleared on {@link #close()}).</li>
 *   <li><b>Transient</b> services created every time.</li>
 * </ul>
 *
 * <p>
 * Resolution proceeds in three steps:
 * </p>
 * <ol>
 *   <li><b>Exact</b> descriptor lookup for the requested service type.</li>
 *   <li><b>Assignable</b> match: find the most specific descriptor whose produced type is assignable to the
 *       requested type. If multiple unrelated types match, an ambiguity error is thrown.</li>
 *   <li><b>Self-binding</b>: if the requested type is concrete (not abstract, not an interface), attempt to
 *       construct it directly via {@link ConstructorFactory#createWithInjection(Class, Resolver, Deque)}.</li>
 * </ol>
 *
 * <p>
 * Instances that implement {@link AutoCloseable} and are scoped-cached will be closed when the scope is
 * {@linkplain #close() closed}. Singletons are <em>not</em> owned by the scope and are not closed here.
 * </p>
 */
public class Scope implements Resolver, AutoCloseable {
    /** All known service descriptors, typically from the root provider. */
    private final List<ServiceDescriptor<?>> descriptors;
    /** Cache for SINGLETON instances shared across all scopes created by the root provider. */
    private final Map<Class<?>, Object> singletonCache;  // shared with root
    /** Cache for SCOPED instances owned by this scope. Cleared and closed on {@link #close()}. */
    private final Map<Class<?>, Object> scopedCache = new ConcurrentHashMap<>();

    /**
     * Creates a new scope.
     *
     * @param descriptors     The registered service descriptors to consider during resolution.
     * @param singletonCache  A shared cache for SINGLETON services (owned by the root provider).
     */
    public Scope(List<ServiceDescriptor<?>> descriptors, Map<Class<?>, Object> singletonCache) {
        this.descriptors = descriptors;
        this.singletonCache = singletonCache;
    }

    /**
     * Resolves a service for the requested type using the provider's resolution rules.
     *
     * <p>Order: exact match → best assignable match → self-binding for concrete classes.</p>
     *
     * @param <T>  The requested service type.
     * @param type The class object of the requested type.
     * @return The resolved instance, or {@code null} if no service can be resolved.
     * @throws IllegalStateException if multiple unrelated assignable candidates are found,
     *                               or if a SCOPED service is requested from the root provider.
     */
    @Override
    public <T> T getService(Class<T> type) {
        // 1) Exact registration?
        ServiceDescriptor<T> exact = findDescriptorExact(type);
        if (exact != null) {
            return resolveFromDescriptor(exact);
        }

        // 2) Assignable registration?
        Match<T> m = findBestAssignableMatch(type);
        if (m != null) {
            return resolveFromDescriptor(m.descriptor);
        }

        // Removed below because we shouldn't try to create types that aren't registered in the service collection!

        // 3) Self-binding for concrete, instantiable classes
        // if (isConcrete(type)) {
        //    return ConstructorFactory.createWithInjection(type, this, null);
        // }

        // Nullable behavior: nothing found → return null (no exception here)
        return null;
    }

    /**
     * Resolves a service or throws if it cannot be found.
     *
     * @param <T>  The requested service type.
     * @param type The class object of the requested type.
     * @return The resolved instance (never {@code null}).
     * @throws ServiceNotFoundException if no service can be resolved for {@code type}.
     * @throws IllegalStateException    if multiple unrelated assignable candidates are found,
     *                                  or if a SCOPED service is requested from the root provider.
     */
    public <T> T getRequiredService(Class<T> type) {
        T instance = getService(type);
        if (instance != null) return instance;
        throw new ServiceNotFoundException(type);
    }

    /**
     * Creates an instance of the given {@code type}, filling its constructor parameters
     * from this {@link Scope}.
     * <p>
     * Works the same as {@link ServiceProvider#createInstance(Class, Object...)} but
     * resolves dependencies using the current scope, so scoped lifetimes are respected.
     * <p>
     * Behavior is modeled after .NET's
     * {@code ActivatorUtilities.CreateInstance}:
     * <ul>
     *   <li>Any {@code explicitArgs} are matched first by assignable type.</li>
     *   <li>Remaining constructor parameters are resolved from this scope.</li>
     *   <li>The "greediest" satisfiable constructor is chosen.</li>
     *   <li>If no constructor can be fully satisfied, a {@link ServiceNotFoundException} is thrown.</li>
     * </ul>
     *
     * @param type the concrete class to instantiate (does not need to be registered as a service)
     * @param explicitArgs optional arguments to bind to constructor parameters before falling back to DI
     * @param <T> the requested type
     * @return a new instance of {@code type}, with dependencies injected from this scope
     * @throws ServiceNotFoundException if no constructor can be satisfied
     */
    public <T> T createInstance(Class<T> type, Object... explicitArgs) {
        return Activator.createInstance(new Activator.ServiceResolver() {
            @Override public <U> U getService(Class<U> t) { return Scope.this.getService(t); }
        }, type, explicitArgs);
    }


    /**
     * Determines whether this scope can resolve the requested type.
     *
     * <p>Returns {@code true} if an exact descriptor exists, if there is an unambiguous assignable match,
     * or if the type is concrete and can be constructed via self-binding.</p>
     *
     * @param type The service type.
     * @return {@code true} if resolution would succeed; otherwise {@code false}.
     */
    @Override
    public boolean canResolve(Class<?> type) {
        if (findDescriptorExact(type) != null) return true;
        if (findBestAssignableMatch(type) != null) return true;
        return isConcrete(type);
    }

    // ---------- internal helpers ----------

    /**
     * Finds a descriptor whose {@code serviceType} exactly equals the requested type.
     *
     * @param <T>  The service type.
     * @param type The requested type.
     * @return The exact matching descriptor, or {@code null} if none.
     */
    @SuppressWarnings("unchecked")
    private <T> ServiceDescriptor<T> findDescriptorExact(Class<T> type) {
        for (ServiceDescriptor<?> d : descriptors) {
            if (d.serviceType.equals(type)) return (ServiceDescriptor<T>) d;
        }
        return null;
    }

    /**
     * Checks whether a type is self-bindable, i.e., concrete (not interface/abstract).
     *
     * @param t The type to check.
     * @return {@code true} if the type is concrete; otherwise {@code false}.
     */
    private boolean isConcrete(Class<?> t) {
        int m = t.getModifiers();
        return !t.isInterface() && !Modifier.isAbstract(m);
    }

    /**
     * Lightweight container for a candidate match during assignable lookup.
     *
     * @param <T> The service type.
     */
    private static final class Match<T> {
        final ServiceDescriptor<T> descriptor;
        final Class<?> produced;
        Match(ServiceDescriptor<T> d, Class<?> produced) { this.descriptor = d; this.produced = produced; }
    }

    /**
     * Finds the best assignable match for the requested type among all descriptors.
     *
     * <p>A descriptor is a candidate if the type it <em>produces</em> (instance class or implementation class)
     * is assignable to {@code paramType}. When multiple candidates exist, they are sorted so that the most
     * specific type comes first (i.e., if A is assignable from B, B wins). If the top two candidates are
     * unrelated (neither assignable from the other), the match is ambiguous and an exception is thrown.</p>
     *
     * @param <T>       The requested service type.
     * @param paramType The requested class.
     * @return The best {@link Match}, or {@code null} if none.
     * @throws IllegalStateException if multiple unrelated candidates are found.
     */
    @SuppressWarnings("unchecked")
    private <T> Match<T> findBestAssignableMatch(Class<T> paramType) {
        List<Match<T>> candidates = new ArrayList<>();
        for (ServiceDescriptor<?> d : descriptors) {
            Class<?> produced = producedType(d);
            if (produced == null) continue;
            if (paramType.isAssignableFrom(produced)) {
                candidates.add(new Match<>((ServiceDescriptor<T>) d, produced));
            }
        }
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        // Prefer the most specific (deepest) type
        candidates.sort((a, b) -> {
            Class<?> A = a.produced, B = b.produced;
            if (A == B) return 0;
            if (A.isAssignableFrom(B)) return 1;   // B is more specific
            if (B.isAssignableFrom(A)) return -1;  // A is more specific
            return 0; // unrelated
        });

        Class<?> top = candidates.get(0).produced;
        Class<?> second = candidates.get(1).produced;
        boolean ambiguous = !(top.isAssignableFrom(second) || second.isAssignableFrom(top));
        if (ambiguous) {
            StringBuilder sb = new StringBuilder("Ambiguous assignment for ")
                    .append(paramType.getName()).append(". Candidates produce: ");
            for (Match<T> mm : candidates) sb.append(mm.produced.getName()).append(", ");
            sb.setLength(sb.length() - 2);
            sb.append(".");
            throw new IllegalStateException(sb.toString());
        }

        return candidates.get(0);
    }

    /**
     * Determines the runtime type a descriptor will produce for matching purposes:
     * <ul>
     *   <li>If {@code instance} is present → {@code instance.getClass()}</li>
     *   <li>Else if {@code implType} is present → {@code implType}</li>
     *   <li>Else → {@code null}</li>
     * </ul>
     *
     * @param d The descriptor to inspect.
     * @return The produced class, or {@code null} if indeterminate.
     */
    private Class<?> producedType(ServiceDescriptor<?> d) {
        if (d.instance != null) return d.instance.getClass();
        if (d.implType != null) return d.implType;
        return null;
    }

    /**
     * Resolves an instance from a descriptor, applying lifetime caching rules.
     *
     * @param <T> The service type.
     * @param d   The descriptor.
     * @return The resolved instance (possibly retrieved from cache).
     * @throws IllegalStateException if the lifetime is unknown.
     */
    @SuppressWarnings("unchecked")
    private <T> T resolveFromDescriptor(ServiceDescriptor<T> d) {
        switch (d.lifetime) {
            case SINGLETON:
                return (T) singletonCache.computeIfAbsent(d.serviceType, x -> createFromDescriptor(d));
            case SCOPED:
                return (T) scopedCache.computeIfAbsent(d.serviceType, x -> createFromDescriptor(d));
            case TRANSIENT:
                return createFromDescriptor(d);
            default:
                throw new IllegalStateException("Unknown lifetime");
        }
    }

    /**
     * Creates an instance based on the descriptor's construction strategy:
     * <ul>
     *   <li>If an {@code instance} is specified, returns it as-is.</li>
     *   <li>If a {@code supplier} is specified, invokes it to produce a new instance.</li>
     *   <li>Else, uses the {@code implType} and {@link ConstructorFactory} to construct the instance.</li>
     * </ul>
     *
     * @param <T> The service type.
     * @param d   The descriptor.
     * @return A newly created instance (not cached here).
     */
    private static <T> T createFromDescriptor(ServiceDescriptor<T> d) {
        if (d.instance != null) return d.instance;
        if (d.supplier != null) return d.supplier.get();
        @SuppressWarnings("unchecked")
        Class<T> impl = (Class<T>) d.implType;
        return ConstructorFactory.createWithInjection(impl, (Resolver) null, null); // will not use resolver here
    }

    /**
     * Closes the scope and disposes of any {@link AutoCloseable} instances held in the scoped cache.
     * <p>
     * Each closeable is closed in an independent try/catch; exceptions are ignored to ensure all
     * closeables get a chance to run. The scoped cache is cleared afterward.
     * </p>
     */
    @Override
    public void close() {
        for (Object o : scopedCache.values()) {
            if (o instanceof AutoCloseable c) {
                try { c.close(); } catch (Exception ignored) {}
            }
        }
        scopedCache.clear();
    }
}
