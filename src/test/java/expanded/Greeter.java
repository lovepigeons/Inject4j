package expanded;

public class Greeter {
    private final Clock clock;
    private final Config cfg;

    // Constructor injection: container will supply Clock + Config
    public Greeter(SystemClock clock, Config cfg) {
        this.clock = clock;
        this.cfg = cfg;
    }

    public String greet(String who) {
        return "[" + clock.now() + "] " + cfg.name + " says: Hello, " + who + "!";
    }
}