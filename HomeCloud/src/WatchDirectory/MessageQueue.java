package WatchDirectory;

import java.util.LinkedList;
import java.util.Queue;

// A termelo-fogyaszto pelda megvalositasa
public class MessageQueue {

    private final Queue<String> messages = new LinkedList<>();
    private final int capacity;

    public MessageQueue(int capacity) {
        this.capacity = capacity;
    }

    /* A termelo addig tesz bele a sorba adatot, ameddig el nem eri a max kapacitast.
    * Ekkor varakozasba kezd, hogy a fogyaszto szabaditson fel helyet. Minden alkalommal
    * amikor beleteszunk egy adatot a sorba, ertesitsuk a fogyasztot, mert lehet, hogy
    * nincs adat a sorban, es a fogyaszto pont az adatra var.*/
    public synchronized void put(String msg) {
        while(messages.size() == capacity) {
            try { wait(); } catch (InterruptedException ex) {}
        }
        messages.add(msg);
        notifyAll();
    }

    /* A fogyaszto adatot vesz ki a sorbol. Ha a sor ures, akkor addig var ameddig a
    * termelo adatot nem tesz bele. Ha kivettuk az adatot, ertesitsuk a termelot, mert
    * lehet hogy tele van es arra var hogy hely szabaduljon fel. A sorbol kivett ertekkel
    * terunk vissza. */
    public synchronized String get() {
        while( messages.isEmpty() ) {
            try { wait(); } catch (InterruptedException ex) {}
        }
        String ret = messages.remove();
        notifyAll();
        return ret;
    }

    /* mivel a sorbol valo kivetel blokkolo hatast valt ki ha ures, ezert nem art elotte megtudni,
    * hogy a sor ures-e, mert ezzel ki lehet valtani a blokkolo varakozast. */
    public synchronized boolean isEmpty() {
        return  messages.isEmpty();
    }

}