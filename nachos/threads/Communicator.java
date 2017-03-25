package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {

	/*
	 * Lock and condition variable.
	 */
    private Lock conditionLock;
    private Condition cond;

    /*
     * Number of waiting listeners and speakers.
     */
    private int numListener = 0;
    private int numSpeaker = 0;

    /*
     * The queue of pending words to send.
     */

    private LinkedList<Integer> valQueue;

    /**
     * Allocate a new communicator.
     */

    public Communicator() {
    	conditionLock = new Lock();
    	cond = new Condition(conditionLock);
    	valQueue = new LinkedList<Integer>();
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
    	conditionLock.acquire();

    	valQueue.add(new Integer(word));

    	if(numListener > 0) {
    		/* Listeners are waiting: wake up a listener */
    		numListener--;
    		cond.wake();
    	} else {
    		/* put myself into waiting queue */
    		numSpeaker++;
    		cond.sleep();
    	}

    	conditionLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	conditionLock.acquire();

    	int msg;

    	if(numSpeaker > 0) {
    		/* wake up a speaker */
    		msg = valQueue.removeFirst();
    		numSpeaker--;
    		cond.wake();
    	} else {
    		/* wait for a speaker */
    		numListener++;
    		cond.sleep();
    		msg = valQueue.removeFirst();
    	}

    	conditionLock.release();

		return msg;
    }
}
