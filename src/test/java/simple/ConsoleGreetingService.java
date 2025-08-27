package simple;

public class ConsoleGreetingService implements GreetingService {
    public void greet(String name) {
        System.out.println("Hello, " + name + "!");
    }
}