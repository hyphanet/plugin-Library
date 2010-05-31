package plugins.Library.util.concurrent;

public class Notifier {
	
	private boolean notified;
	
	public synchronized void notifyUpdate() {
		notified = true;
		notifyAll();
	}
	
	public synchronized void waitUpdate(int maxWait) {
		long start = -1;
		long now = -1;
		while(!notified) {
			if(start == -1) {
				start = now = System.currentTimeMillis();
			} else {
				now = System.currentTimeMillis();
			}
			if(start + maxWait <= now) return;
			try {
				wait((start + maxWait - now));
			} catch (InterruptedException e) {
				// Ignore
			}
		}
		notified = false;
	}

}
