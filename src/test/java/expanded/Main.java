package expanded;

import org.oldskooler.javadi.Scope;
import org.oldskooler.javadi.ServiceCollection;
import org.oldskooler.javadi.ServiceProvider;


public class Main {
    public static void main(String[] args) {
        ServiceCollection services = new ServiceCollection();

        // Register leaf deps
        services.addSingleton(Config.class, new Config("bar"));         // instance
        services.addSingleton(Clock.class, SystemClock.class);            // class → singleton

        // Register higher-level types
        services.addTransient(Greeter.class, Greeter.class);              // class → transient

        ServiceProvider provider = services.buildServiceProvider();

        Greeter g1 = provider.getService(Greeter.class); // constructed with injected Clock + Config
        Greeter g2 = provider.getService(Greeter.class); // a different transient instance

        System.out.println(g1.greet("Alice"));
        System.out.println(g2.greet("Bob"));
    }
}