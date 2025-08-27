package org.oldskooler.javadi;

/**
 * Thrown when a requested service cannot be resolved by the DI container.
 */
public class ServiceNotFoundException extends RuntimeException {
    public ServiceNotFoundException(String message) {
        super(message);
    }

    public ServiceNotFoundException(Class<?> type) {
        super("No service registered for: " + type.getName());
    }
}