package homecloudmobile.WatchDirectory;

import java.util.LinkedList;
import java.util.Queue;

public class MessageQueue {

    private final Queue<String> messages = new LinkedList<>();
    private final int capacity;

    public MessageQueue(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void put(String msg) {
        while(messages.size() == capacity) {
            try { wait(); } catch (InterruptedException ex) {}
        }
        messages.add(msg);
        notifyAll();
    }

    public synchronized String get() {
        while( messages.isEmpty() ) {
            try { wait(); } catch (InterruptedException ex) {}
        }
        String ret = messages.remove();
        notifyAll();
        return ret;
    }

    public synchronized boolean isEmpty() {
        return  messages.isEmpty();
    }

}