# java-di

A lightweight dependency injection (DI) container for Java.  
It provides a simple API for registering services with different lifetimes (singleton, scoped, transient) and resolving them with constructor injection.

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
    implementation 'com.github.Quackster:java-di:v1.0.1'
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
        <version>v1.0.1</version>
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

## Constructor Injection

Dependencies are automatically injected via constructors:

```java
public class BarService {
    private final FooService foo;
    
    public BarService(FooService foo) {
        this.foo = foo;
    }
    
    public void work() {
        foo.doSomething();
    }
}
```

Register it:

```java
services.addTransient(BarService.class, BarService.class);
```

Resolve it:

```java
BarService bar = provider.getService(BarService.class);
bar.work(); // uses injected FooService
```

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