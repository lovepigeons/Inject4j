package simple;

import org.oldskooler.inject4j.Scope;
import org.oldskooler.inject4j.ServiceCollection;
import org.oldskooler.inject4j.ServiceProvider;

public class Main {
    public static void main(String[] args) {
        ServiceCollection services = new ServiceCollection();
        services.addSingleton(GreetingService.class, ConsoleGreetingService.class);
        services.addScoped(CounterService.class, CounterService.class);

        ServiceProvider provider = services.buildServiceProvider();

        // Using scopes (like per request)
        Scope scope1 = provider.createScope();
        CounterService c1a = scope1.getService(CounterService.class);
        CounterService c1b = scope1.getService(CounterService.class);

        System.out.println("Scope1: " + c1a.increment()); // 1
        System.out.println("Scope1: " + c1b.increment()); // 2 (same instance within scope1)

        Scope scope2 = provider.createScope();
        CounterService c2 = scope2.getService(CounterService.class);
        System.out.println("Scope2: " + c2.increment()); // 1 (different instance)
    }
}