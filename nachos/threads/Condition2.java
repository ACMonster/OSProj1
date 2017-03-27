package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;

    waitQueue = new LinkedList<KThread>();
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());

    boolean intStatus = Machine.interrupt().disable();

    waitQueue.add(KThread.currentThread());

	conditionLock.release();

    KThread.sleep();

	conditionLock.acquire();

    Machine.interrupt().restore(intStatus);
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());

    boolean intStatus = Machine.interrupt().disable();

    if(!waitQueue.isEmpty())
        waitQueue.removeFirst().ready();

    Machine.interrupt().restore(intStatus);
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());

    boolean intStatus = Machine.interrupt().disable();

    while(!waitQueue.isEmpty())
        wake();

    Machine.interrupt().restore(intStatus);

    }

    public static void selfTest() {

        class R implements Runnable {
            int NUM_THREADS;

            public R(int threads)
            {
                NUM_THREADS = threads;
            }

            public void run() {

                final Lock lock = new Lock();
                final Condition2 cond = new Condition2(lock);

                KThread th[] = new KThread[NUM_THREADS];

                for(int i = 0; i < NUM_THREADS; i++) {
                    th[i] = new KThread(new Runnable() {
                        public void run() {
                            lock.acquire();
                            cond.sleep();
                            System.out.println("conditionTest: " + KThread.currentThread().getName() + " wakes up!");
                            lock.release();
                        }
                    });
                    th[i].setName("Child thread " + i).fork();
                }

                System.out.println("conditionTest: Test with " + NUM_THREADS + " Child threads.");
                System.out.println("conditionTest: Parent thread starting.");

                KThread.yield();
                lock.acquire();
                cond.wake();
                cond.wake();
                cond.wake();
                System.out.println("conditionTest: Parent thread working.");
                lock.release();

                KThread.yield();
                lock.acquire();
                cond.wakeAll();
                lock.release();
                System.out.println("conditionTest: Parent thread ending.");
                for(int i = 0; i < NUM_THREADS; i++)
                    th[i].join();
            }
        }
        KThread parent = new KThread(new R(2));

        parent.fork();
        parent.join();

        parent = new KThread(new R(10));

        parent.fork();
        parent.join();
    }

    private Lock conditionLock;
    private LinkedList<KThread> waitQueue;
}
