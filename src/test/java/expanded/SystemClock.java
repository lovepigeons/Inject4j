package expanded;

public class SystemClock implements Clock {
    public long now() {
        return System.currentTimeMillis();
    }
}