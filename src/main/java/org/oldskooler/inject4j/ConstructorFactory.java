package org.oldskooler.inject4j;


import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;

final class ConstructorFactory {
    private ConstructorFactory() {}

    static <T> T createWithInjection(Class<T> implType, Resolver resolver, Deque<Class<?>> externalStack) {
        if (implType.isInterface() || Modifier.isAbstract(implType.getModifiers())) {
            throw new IllegalStateException("Cannot instantiate abstract/interface: " + implType);
        }

        Deque<Class<?>> stack = (externalStack != null) ? externalStack : new ArrayDeque<>();
        if (stack.contains(implType)) {
            throw new IllegalStateException("Circular dependency detected: " + renderCycle(stack, implType));
        }
        stack.push(implType);
        try {
            Constructor<T> ctor = chooseConstructor(implType, resolver);
            Object[] args = Arrays.stream(ctor.getParameterTypes())
                    .map(resolver::getService)
                    .toArray();
            try {
                ctor.setAccessible(true);
                return ctor.newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to construct " + implType + " via " + ctor, e);
            }
        } finally {
            stack.pop();
        }
    }

    // Strategy: pick the "greediest" (most parameters) constructor
    // for which ALL parameter types are registered (no construction during probing).
    private static <T> Constructor<T> chooseConstructor(Class<T> implType, Resolver resolver) {
        @SuppressWarnings("unchecked")
        Constructor<T>[] ctors = (Constructor<T>[]) implType.getDeclaredConstructors();

        List<Constructor<T>> candidates = new ArrayList<>();
        for (Constructor<T> c : ctors) {
            Class<?>[] params = c.getParameterTypes();
            boolean allResolvable = true;
            for (Class<?> p : params) {
                if (!resolver.canResolve(p)) { // <-- no side effects
                    allResolvable = false;
                    break;
                }
            }
            if (allResolvable) candidates.add(c);
        }

        if (!candidates.isEmpty()) {
            candidates.sort(Comparator.comparingInt((Constructor<T> c) -> c.getParameterCount()).reversed());
            return candidates.get(0);
        }

        // If no constructor is fully resolvable, try a no-arg constructor as a last resort.
        try {
            return implType.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            // Build a detailed error explaining what's missing for each constructor.
            StringBuilder msg = new StringBuilder();
            msg.append("No resolvable constructor for ").append(implType.getName()).append(".\n");
            msg.append("Register the missing dependencies or add a no-arg constructor.\n\n");
            msg.append("Constructors and missing parameters:\n");

            for (Constructor<T> c : ctors) {
                Class<?>[] ps = c.getParameterTypes();
                List<String> missing = new ArrayList<>();
                for (Class<?> p : ps) {
                    if (!resolver.canResolve(p)) {
                        missing.add(simple(p));
                    }
                }
                msg.append("  - ")
                        .append(signature(implType, ps))
                        .append(missing.isEmpty() ? "  (not chosen)" : "  missing: " + missing)
                        .append("\n");
            }

            // Also show what IS registered to help the user.
            msg.append("\nHint: ensure these types are registered: ")
                    .append("all constructor parameter types for the desired ctor.\n");

            throw new IllegalStateException(msg.toString());
        }
    }

    private static String renderCycle(Deque<Class<?>> stack, Class<?> repeat) {
        List<String> chain = new ArrayList<>();
        for (Class<?> c : stack) chain.add(c.getSimpleName());
        chain.add(repeat.getSimpleName());
        Collections.reverse(chain);
        return String.join(" -> ", chain);
    }

    private static String simple(Class<?> c) {
        return c.getSimpleName().isEmpty() ? c.getName() : c.getSimpleName();
    }

    private static String signature(Class<?> owner, Class<?>[] params) {
        StringBuilder sb = new StringBuilder();
        sb.append(owner.getSimpleName()).append("(");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(simple(params[i]));
        }
        sb.append(")");
        return sb.toString();
    }
}