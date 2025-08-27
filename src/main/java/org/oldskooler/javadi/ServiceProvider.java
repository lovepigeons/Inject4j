package org.oldskooler.javadi;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Root service provider that resolves services from a set of {@link ServiceDescriptor}
 * registrations and manages a shared singleton cache.
 *
 * <p><strong>Responsibilities</strong></p>
 * <ul>
 *   <li>Resolve services via exact match, best assignable match, or self-binding for concrete classes.</li>
 *   <li>Own and manage the application-wide <em>singleton</em> cache.</li>
 *   <li>Create child {@link Scope scopes} for per-scope (e.g., per-request) resolution.</li>
 * </ul>
 *
 * <p><strong>Lifetimes</strong></p>
 * <ul>
 *   <li><b>SINGLETON</b>: Cached here in {@code singletonCache} and reused across all resolutions/scopes.</li>
 *   <li><b>SCOPED</b>: Not resolvable at the root provider; requesting a scoped service here is an error.</li>
 *   <li><b>TRANSIENT</b>: Newly created on each request.</li>
 * </ul>
 *
 * <p><strong>Resolution order</strong></p>
 * <ol>
 *   <li>Exact descriptor match for the requested type.</li>
 *   <li>Assignable match: choose the most specific produced type; throw if ambiguous.</li>
 *   <li>Self-binding: if the requested type is concrete (not abstract/interface), construct it via
 *       {@link ConstructorFactory#createWithInjection(Class, Resolver, Deque)}.</li>
 * </ol>
 */
public class ServiceProvider implements Resolver {
    /** All registered descriptors known to this provider. */
    private final List<ServiceDescriptor<?>> descriptors;
    /** Application-wide cache of SINGLETON instances, shared with child scopes. */
    private final Map<Class<?>, Object> singletonCache = new HashMap<>();

    /**
     * Creates a new root provider.
     *
     * @param descriptors The service descriptors available for resolution.
     */
    public ServiceProvider(List<ServiceDescriptor<?>> descriptors) {
        this.descriptors = descriptors;
    }

    /**
     * Creates a new {@link Scope} that shares this provider's singleton cache,
     * but maintains its own scoped cache and disposal semantics.
     *
     * @return A new {@link Scope} for scoped resolutions.
     */
    public Scope createScope() {
        return new Scope(descriptors, singletonCache);
    }

    /**
     * Resolves a service for the requested type using the provider's resolution rules.
     *
     * <p>Order: exact match → best assignable match → self-binding for concrete classes.</p>
     *
     * @param <T>  The requested service type.
     * @param type The class object of the requested type.
     * @return A resolved instance.
     * @throws IllegalArgumentException if no descriptor is found and the type is not self-bindable.
     * @throws IllegalStateException    if multiple unrelated assignable candidates are found.
     */
    @Override
    public <T> T getService(Class<T> type) {
        // 1) Exact registration?
        ServiceDescriptor<T> exact = findDescriptorExact(type);
        if (exact != null) {
            return resolveFromDescriptor(exact, this);
        }

        // 2) Assignable registration? (e.g., Clock->SystemClock satisfies SystemClock param)
        Match<T> m = findBestAssignableMatch(type);
        if (m != null) {
            return resolveFromDescriptor(m.descriptor, this);
        }

        // 3) Self-binding for concrete, instantiable classes
        if (isConcrete(type)) {
            return ConstructorFactory.createWithInjection(type, this, null);
        }

        throw new IllegalArgumentException("No service registered for: " + type.getName());
    }

    /**
     * Indicates whether the provider could resolve the requested type if asked.
     *
     * <p>Returns {@code true} if an exact descriptor exists, a non-ambiguous assignable match exists,
     * or the type is concrete and eligible for self-binding.</p>
     *
     * @param type The requested service type.
     * @return {@code true} if resolution would succeed; otherwise {@code false}.
     */
    @Override
    public boolean canResolve(Class<?> type) {
        if (findDescriptorExact(type) != null) return true;
        if (findBestAssignableMatch(type) != null) return true;
        return isConcrete(type); // eligible for self-binding
    }

    // ---------- internal helpers ----------

    /**
     * Finds a descriptor whose {@code serviceType} exactly equals the requested type.
     *
     * @param <T>  The service type.
     * @param type The requested type.
     * @return The exact descriptor, or {@code null} if none.
     */
    @SuppressWarnings("unchecked")
    private <T> ServiceDescriptor<T> findDescriptorExact(Class<T> type) {
        for (ServiceDescriptor<?> d : descriptors) {
            if (d.serviceType.equals(type)) {
                return (ServiceDescriptor<T>) d;
            }
        }
        return null;
    }

    /**
     * Determines whether a class is concrete (neither an interface nor abstract).
     *
     * @param t The class to test.
     * @return {@code true} if concrete; otherwise {@code false}.
     */
    private boolean isConcrete(Class<?> t) {
        int m = t.getModifiers();
        return !t.isInterface() && !Modifier.isAbstract(m);
    }

    /**
     * Lightweight container representing an assignable candidate during matching.
     *
     * @param <T> The service type.
     */
    private static final class Match<T> {
        final ServiceDescriptor<T> descriptor;
        final Class<?> produced; // the concrete/known type it will produce
        Match(ServiceDescriptor<T> d, Class<?> produced) { this.descriptor = d; this.produced = produced; }
    }

    /**
     * Finds the best descriptor whose produced type is assignable to {@code paramType}.
     *
     * <p><strong>Produced type rules</strong> (no side effects):</p>
     * <ul>
     *   <li>If an {@code instance} is present, use {@code instance.getClass()}.</li>
     *   <li>Else if {@code implType} is present, use it.</li>
     *   <li>Else (supplier-only with unknown type), skip to avoid invoking suppliers.</li>
     * </ul>
     *
     * <p>When multiple candidates exist, they are sorted so the most specific type (nearest subclass)
     * comes first. If the top two candidates are unrelated (neither assignable from the other),
     * the match is ambiguous and an exception is thrown.</p>
     *
     * @param <T>       The requested service type.
     * @param paramType The requested class.
     * @return The best matching candidate, or {@code null} if none.
     * @throws IllegalStateException if ambiguity is detected among unrelated candidates.
     */
    @SuppressWarnings("unchecked")
    private <T> Match<T> findBestAssignableMatch(Class<T> paramType) {
        List<Match<T>> candidates = new ArrayList<>();

        for (ServiceDescriptor<?> d : descriptors) {
            Class<?> produced = producedType(d);
            if (produced == null) continue; // supplier-only with unknown type; skip to avoid constructing
            if (paramType.isAssignableFrom(produced)) {
                candidates.add(new Match<>((ServiceDescriptor<T>) d, produced));
            }
        }

        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        // Prefer the most specific produced type (i.e., the one that is assignable to all others)
        candidates.sort((a, b) -> {
            Class<?> A = a.produced, B = b.produced;
            if (A == B) return 0;
            if (A.isAssignableFrom(B)) return 1;   // B is more specific than A
            if (B.isAssignableFrom(A)) return -1;  // A is more specific than B
            // Unrelated classes: keep stable but produce an ambiguity error below.
            return 0;
        });

        // After sorting, check for ambiguity among top two if neither is subtype of the other
        Class<?> top = candidates.get(0).produced;
        Class<?> second = candidates.get(1).produced;
        boolean ambiguous = !(top.isAssignableFrom(second) || second.isAssignableFrom(top));
        if (ambiguous) {
            StringBuilder sb = new StringBuilder("Ambiguous assignment for ")
                    .append(paramType.getName()).append(". Candidates produce: ");
            for (Match<T> m : candidates) sb.append(m.produced.getName()).append(", ");
            sb.setLength(sb.length() - 2);
            sb.append(". Consider changing the parameter to the abstraction or registering a more specific mapping.");
            throw new IllegalStateException(sb.toString());
        }

        return candidates.get(0);
    }

    /**
     * Returns the class this descriptor will produce <em>without</em> creating instances.
     * <ul>
     *   <li>If {@code instance} is present, returns {@code instance.getClass()}.</li>
     *   <li>Else if {@code implType} is present, returns it.</li>
     *   <li>Else (supplier-only with unknown type), returns {@code null}.</li>
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
     * Resolves an instance from a descriptor according to its lifetime, using the given resolver
     * (typically {@code this}) for constructor injection.
     *
     * @param <T>      The service type.
     * @param d        The descriptor.
     * @param resolver The resolver passed into constructor injection for dependencies.
     * @return The resolved instance.
     * @throws IllegalStateException if a SCOPED service is requested from the root provider,
     *                               or if the lifetime is unknown.
     */
    @SuppressWarnings("unchecked")
    private <T> T resolveFromDescriptor(ServiceDescriptor<T> d, Resolver resolver) {
        switch (d.lifetime) {
            case SINGLETON:
                return (T) singletonCache.computeIfAbsent(d.serviceType, x -> createFromDescriptor(d, resolver));
            case TRANSIENT:
                return createFromDescriptor(d, resolver);
            case SCOPED:
                throw new IllegalStateException("Scoped service requested from root provider: " + d.serviceType);
            default:
                throw new IllegalStateException("Unknown lifetime");
        }
    }

    /**
     * Creates an instance according to the descriptor's construction strategy:
     * <ul>
     *   <li>If an {@code instance} is specified, returns it as-is.</li>
     *   <li>If a {@code supplier} is specified, invokes it to produce a new instance.</li>
     *   <li>Otherwise, constructs the {@code implType} via {@link ConstructorFactory} using the provided resolver.</li>
     * </ul>
     *
     * @param <T>      The service type.
     * @param d        The descriptor.
     * @param resolver The resolver for dependency injection during construction.
     * @return A newly created instance (not cached here unless SINGLETON handling applies).
     */
    private static <T> T createFromDescriptor(ServiceDescriptor<T> d, Resolver resolver) {
        if (d.instance != null) return d.instance;
        if (d.supplier != null) return d.supplier.get();
        @SuppressWarnings("unchecked")
        Class<T> impl = (Class<T>) d.implType;
        return ConstructorFactory.createWithInjection(impl, resolver, null);
    }
}
