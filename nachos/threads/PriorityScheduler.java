package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

	private static KThread lockThread(Lock lock, String name, int priority, int workLoad, boolean needLock) {
		KThread t = new KThread(new Runnable() {
			public void run() {
				if (needLock) {
					System.out.println(name + " tries to acquire lock...");
					lock.acquire();
					System.out.println(name + " acquires lock...");
				}

				System.out.println(name + " starts working...");
				for (int i = 0; i < workLoad; i++)
					KThread.yield();
				System.out.println(name + " finishes working...");

				if (needLock) {
					lock.release();
					System.out.println(name + " releases lock...");
				}
			}
		}).setName(name);

		boolean intStatus = Machine.interrupt().disable();
		ThreadedKernel.scheduler.setPriority(t, priority);
		Machine.interrupt().restore(intStatus);

		return t;
	}

	private static final int NUM = 3;
	private static final int lowPriority = priorityDefault;
	private static final int mediumPriority = 4;
	private static final int highPriority = 6;

	public static void selfTest() {
		Lock lock = new Lock();
		KThread low[] = new KThread[NUM];
		KThread medium[] = new KThread[NUM];
		KThread high[] = new KThread[NUM];
		for(int i = 0; i < NUM; i++) {
			low[i] = lockThread(lock, "Low " + i, lowPriority, 10000, true);
			medium[i] = lockThread(lock, "Medium " + i, mediumPriority, 100, false);
			high[i] = lockThread(lock, "High " + i, highPriority, 10000, true);
		}
		for(int i = 0; i < NUM; i++)
			low[i].fork();
		for(int i = 0; i < 100; i++)
			KThread.yield();
		for(int i = 0; i < NUM; i++)
			medium[i].fork();
		for(int i = 0; i < 1000; i++)
			KThread.yield();
		for(int i = 0; i < NUM; i++)
			high[i].fork();
		for(int i = 0; i < NUM; i++) {
			low[i].join();
			medium[i].join();
			high[i].join();
		}
	}

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {

    /* current holder of the resource */
    private ThreadState holder;

    /* threads waiting on the queue */
    public ArrayList<ThreadState> threadWaitingQueue;

	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
	    holder = null;
	    threadWaitingQueue = new ArrayList<ThreadState>();
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	}

	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    if(transferPriority)
	    	Lib.assertTrue(holder != null);

	    /* remove edge (q, holder) for all q in queue, if transferPriority */
	    if(holder != null && transferPriority) {
			for(ThreadState q : threadWaitingQueue) {
				q.delSucc(holder);
				holder.delPred(q);
			}
			holder.invalidate();
	    }
		holder = null;	    	

	    ThreadState nextThreadState = pickNextThread();
	    if(nextThreadState != null) {
	    	acquire(nextThreadState.thread);
	    	threadWaitingQueue.remove(nextThreadState);
		    return nextThreadState.thread;
	    }
	    return null;
	}

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
	    int maxPriority = -1;
	    ThreadState nextThreadState = null;
	    for(ThreadState q : threadWaitingQueue) {
	    	int thisPriority = q.getEffectivePriority();
	    	if(thisPriority > maxPriority) {
	    		maxPriority = thisPriority;
	    		nextThreadState = q;
	    	}
	    }
	    return nextThreadState;
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement (if you want)
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {

    /* cached effective priority and its validity */
    private boolean invalid;
    private int cache;

    /* edges in the waiting graph */
    private ArrayList<ThreadState> pred;
    private ArrayList<ThreadState> succ;

	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;

	    invalid = true;

	    pred = new ArrayList<ThreadState>();
	    succ = new ArrayList<ThreadState>();

	    setPriority(priorityDefault);
	}

	void addSucc(ThreadState x) {
		succ.add(x);
	}

	void addPred(ThreadState x) {
		pred.add(x);
	}

	void delSucc(ThreadState x) {
		succ.remove(x);
	}

	void delPred(ThreadState x) {
		pred.remove(x);
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {

	    if(invalid) {
	    	invalid = false;
	    	cache = priority;
	    	for(ThreadState ts : pred)
	    		cache = Math.max(cache, ts.getEffectivePriority());
	    }

	    return cache;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
			return;
	    
	    this.priority = priority;
	    
	    invalidate();
	}

	/*
	 *	Invalidate this thread and its successors in the waiting graph
	 */

	public void invalidate() {
		if(!invalid) {
			invalid = true;
			for(ThreadState ts : succ)
				ts.invalidate();
		}
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
		if(waitQueue.transferPriority)
	    	Lib.assertTrue(waitQueue.holder != null);

	   	waitQueue.threadWaitingQueue.add(this);

	    /* no need to add edge if no donation */
	    if(!waitQueue.transferPriority)
	    	return;

	    this.addSucc(waitQueue.holder);
	    waitQueue.holder.addPred(this);

	    waitQueue.holder.invalidate();
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    Lib.assertTrue(waitQueue.holder == null);

	    waitQueue.holder = this;

	    /* no need to add edge if no donation */
	    if(!waitQueue.transferPriority)
	    	return;

	    for(ThreadState q : waitQueue.threadWaitingQueue) {
	    	q.addSucc(this);
	    	this.addPred(q);
	    	this.invalidate();
	    }
	}	

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
    }
}
