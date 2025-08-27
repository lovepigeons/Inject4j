# java-di

A small, lightweight and simple dependency injection (DI) container for Java. It’s designed to work almost the same way as Microsoft’s [ServiceCollection](https://learn.microsoft.com/en-us/dotnet/api/microsoft.extensions.dependencyinjection.servicecollection) implementation., so if you’ve used DI in .NET it should feel very familiar.

With it, you can register services as singletons, scoped, or transient, and then resolve them using constructor injection.


**Built and tested against the latest JDK LTS (currently Java 21).**

[![](https://jitpack.io/v/Quackster/java-di.svg)](https://jitpack.io/#Quackster/java-di)

---

## Features

- **Simple service registration API**
- **Supports lifetimes**: Singleton, Scoped, Transient
- **Constructor injection** out of the box
- **Self-binding**: can automatically construct unregistered concrete types
- **Auto-closeable scopes**: `Scoped` services implementing `AutoCloseable` are disposed when the scope ends

---

## Installation

### Gradle

Add the [JitPack](https://jitpack.io/#Quackster/java-di) repository:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

Then add the dependency:

```groovy
dependencies {
    implementation 'com.github.Quackster:java-di:v1.0.3'
}
```

### Maven

Add the [JitPack](https://jitpack.io/#Quackster/java-di) repository:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Then add the dependency:

```xml
<dependencies>
    <dependency>
        <groupId>com.github.Quackster</groupId>
        <artifactId>java-di</artifactId>
        <version>v1.0.3</version>
    </dependency>
</dependencies>
```

## Quick Start

### 1. Define services

```java
public interface FooService {
    void doSomething();
}

public class FooServiceImpl implements FooService {
    @Override
    public void doSomething() {
        System.out.println("Hello from Foo!");
    }
}
```

### 2. Register services

```java
import org.oldskooler.javadi.*;

public class Main {
    public static void main(String[] args) {
        ServiceCollection services = new ServiceCollection();
        
        // Register FooService as a singleton
        services.addSingleton(FooService.class, FooServiceImpl.class);
        
        // Build provider
        ServiceProvider provider = services.buildServiceProvider();
        
        // Resolve and use service
        FooService foo = provider.getService(FooService.class);
        foo.doSomething(); // prints "Hello from Foo!"
    }
}
```

## Lifetimes

### Singleton
Same instance reused across all resolutions (application-wide).

### Scoped
New instance per scope. Scopes are created via `provider.createScope()`.

Example (per-request in a web app):

```java
try (Scope scope = provider.createScope()) {
    MyService svc = scope.getService(MyService.class);
    // ...
} // svc disposed here if AutoCloseable
```

### Transient
Always creates a fresh instance.

## Constructor Injection Example

Dependencies are automatically injected via constructors:

```java
public class SmtpEmailService implements EmailService {
    private final DatabaseService database;
    
    public SmtpEmailService(DatabaseService database) {
        this.database = database;
    }
    
    @Override
    public void sendNotification(String message) {
        // Use the injected database service
        String user = database.getUserEmail();
        System.out.println("Sending email to " + user + ": " + message);
    }
}
```
Our DatabaseService implementation:

```java
public class MySqlDatabaseService implements DatabaseService {
    @Override
    public String getUserEmail() {
        return "user@example.com";
    }
}
```

Register the services:

```java
services.addSingleton(DatabaseService.class, MySqlDatabaseService.class);
services.addTransient(EmailService.class, SmtpEmailService.class);
```

Resolve and use:

```java
EmailService emailService = provider.getService(EmailService.class);
emailService.sendNotification("Welcome!"); 
// Output: Sending email to user@example.com: Welcome!
```

The `SmtpEmailService` constructor automatically receives the registered `DatabaseService` implementation when resolved from the container.

## Injecting Interface vs Concrete Types

You can inject either the interface or the concrete implementation in your constructor:

```java
// Option 1: Inject the interface (recommended)
public SmtpEmailService(DatabaseService database) {
    this.database = database;
}

// Option 2: Inject the concrete implementation directly
public SmtpEmailService(MySqlDatabaseService database) {
    this.database = database;
}
```

**Best Practice:** Depend on the interface (`DatabaseService`) rather than the concrete type (`MySqlDatabaseService`) to maintain loose coupling and make your code more testable and flexible.

## Service Registration Shortcuts

In addition to registering an abstraction with a separate implementation type:

```java
services.addSingleton(FooService.class, FooServiceImpl.class);
```

You can also register a concrete class as both the service and implementation using convenient one-argument overloads:

```java
// Registers MyService as a singleton, using itself as the implementation
services.addSingleton(MyService.class);

// Registers MyService as scoped (new instance per scope)
services.addScoped(MyService.class);

// Registers MyService as transient (new instance every time)
services.addTransient(MyService.class);
```

## Resolving Services

By default, you resolve services from a `ServiceProvider` (or a `Scope`) using `getService`:

```java
FooService foo = provider.getService(FooService.class);
if (foo != null) {
    foo.doSomething();
}
```

## getService vs getRequiredService

### getService(type)
- Returns the service instance or `null` if none can be resolved
- Useful when the service is optional

### getRequiredService(type)
- Returns the service instance or throws `ServiceNotFoundException` if not found
- Use this when the service is mandatory and missing bindings should be treated as programming errors

```java
// Nullable resolution
FooService optionalFoo = provider.getService(FooService.class);

// Strict resolution
FooService foo = provider.getRequiredService(FooService.class);
foo.doSomething();
```
