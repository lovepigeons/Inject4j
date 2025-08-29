# Inject4j

A small, lightweight and simple dependency injection (DI) container for Java. It's designed to work almost the same way as Microsoft's [ServiceCollection](https://learn.microsoft.com/en-us/dotnet/api/microsoft.extensions.dependencyinjection.servicecollection) implementation., so if you've used DI in .NET it should feel very familiar.

With it, you can register services as singletons, scoped, or transient, and then resolve them using constructor injection.

**Built and tested against Java 1.8**

[![](https://jitpack.io/v/lovepigeons/Inject4j.svg)](https://jitpack.io/#lovepigeons/Inject4j)

---

## Table of Contents

- [Features](#features)
- [Installation](#installation)
  - [Gradle](#gradle)
  - [Maven](#maven)
- [Quick Start](#quick-start)
  - [1. Define services](#1-define-services)
  - [2. Register services](#2-register-services)
- [Service Lifetimes](#service-lifetimes)
  - [Singleton](#singleton)
  - [Scoped](#scoped)
  - [Transient](#transient)
- [Constructor Injection Example](#constructor-injection-example)
- [Injecting Interface vs Concrete Types](#injecting-interface-vs-concrete-types)
- [Service Registration Shortcuts](#service-registration-shortcuts)
- [Resolving Services](#resolving-services)
- [Simple Activator Example](#simple-activator-example)
  - [Setup](#setup)
  - [Service Interface and Implementation](#service-interface-and-implementation)
  - [The Unregistered Class](#the-unregistered-class)
- [FAQ](#faq)

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

Add the [JitPack](https://jitpack.io/#lovepigeons/Inject4j) repository:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

Then add the dependency:

```groovy
dependencies {
    implementation 'com.github.lovepigeons:Inject4j:v0.1.0'
}
```

### Maven

Add the [JitPack](https://jitpack.io/#lovepigeons/Inject4j) repository:

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
        <groupId>com.github.lovepigeons</groupId>
        <artifactId>Inject4j</artifactId>
        <version>v0.1.0</version>
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
ServiceCollection services = new ServiceCollection();

// Register FooService as a singleton
services.addSingleton(FooService.class, FooServiceImpl.class);

// Build provider
ServiceProvider provider = services.buildServiceProvider();

// Resolve and use service
FooService foo = provider.getService(FooService.class);
foo.doSomething(); // prints "Hello from Foo!"
```

## Service Lifetimes

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

## Simple Activator Example

Use `createInstance` to instantiate a class that is **not registered** in the container,  
while still having its constructor arguments filled from DI.  
This mirrors .NET's `ActivatorUtilities.CreateInstance`.

---

### Setup

```java
ServiceCollection services = new ServiceCollection();
services.addSingleton(Logger.class, () -> Logger.getLogger("demo"));
services.addTransient(MessageService.class, ConsoleMessageService.class);

ServiceProvider provider = services.buildServiceProvider();

// Mix explicit arg + DI
ReportController custom = provider.createInstance(ReportController.class, "Sales");
custom.run();
```

**Output**

```
INFO: Generating report with service Console Message Service: Sales
```

### Service Interface and Implementation

```java
public interface MessageService {
    String getName();
}

public class ConsoleMessageService implements MessageService {
    @Override
    public String getName() {
        return "Console Message Service";
    }
}
```

### The Unregistered Class

```java
public class ReportController {
    private final Logger log;
    private final MessageService service;
    private final String name;

    public ReportController(Logger log, MessageService service, String name) {
        this.log = log;
        this.service = service;
        this.name = name != null ? name : "Default";
    }

    public void run() {
        log.info("Generating report with service " + service.getName() + ": " + name);
    }
}
```

---

## FAQ

### Q: What Java versions are supported?
**A:** The library is built and tested against the latest JDK LTS (currently Java 21).

### Q: How does this compare to Spring's dependency injection?
**A:** This is a much lighter alternative focused on simplicity. While Spring offers extensive features and integrations, Inject4j provides just the core DI functionality with a clean, easy-to-use API similar to .NET's ServiceCollection.

### Q: Can I register factory methods for services?
**A:** Yes, you can use lambda expressions when registering services. For example: `services.addSingleton(Logger.class, () -> Logger.getLogger("demo"));`

### Q: What happens if I try to resolve a service that isn't registered?
**A:** With `getService()`, you'll get `null`. With `getRequiredService()`, you'll get a `ServiceNotFoundException`. However, if you try to resolve a concrete class that isn't registered, the container can automatically construct it using self-binding.

### Q: How do scoped services work in practice?
**A:** Scoped services are perfect for web applications where you want one instance per HTTP request. Create a new scope for each request, resolve services within that scope, and dispose the scope when the request completes.

### Q: Can services implement AutoCloseable?
**A:** Yes! Scoped services that implement `AutoCloseable` will be automatically disposed when their scope is closed using the try-with-resources pattern.

### Q: What's the difference between this and other Java DI frameworks?
**A:** This library is specifically designed to mirror .NET's ServiceCollection API, making it familiar for developers coming from the .NET ecosystem. It's also much more lightweight than frameworks like Spring or Guice.

### Q: Can I mix explicit constructor arguments with dependency injection?
**A:** Yes! Use `createInstance()` to provide some constructor arguments explicitly while having others resolved from the DI container.

### Q: Is constructor injection the only supported injection method?
**A:** Yes, currently the library only supports constructor injection, which is generally considered the best practice for dependency injection as it ensures all dependencies are provided at object creation time.
