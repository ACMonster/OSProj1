package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */

    class WakeSchedule {
        public long time;
        public KThread thread;
        WakeSchedule(long time, KThread thread) {
            this.time = time;
            this.thread = thread;
        }
    }

    static ArrayList<WakeSchedule> schedule;

    public Alarm() {
    schedule = new ArrayList<WakeSchedule>();
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        ArrayList<WakeSchedule> newList = new ArrayList<WakeSchedule>();
        for(WakeSchedule sch: schedule) {
            if(Machine.timer().getTime() < sch.time)
                newList.add(sch);
            else
                sch.thread.ready();
        }
        schedule = newList;
    	KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {

	long wakeTime = Machine.timer().getTime() + x;

    schedule.add(new WakeSchedule(wakeTime, KThread.currentThread()));

    boolean intStatus = Machine.interrupt().disable();

    KThread.currentThread().sleep();

    Machine.interrupt().restore(intStatus);

    }
}
