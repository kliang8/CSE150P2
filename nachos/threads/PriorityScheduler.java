package nachos.threads;

import nachos.machine.*;
import java.util.*;

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
    
    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
		    this.transferPriority = transferPriority;
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
		    // implement me   
		    if (waitQueue.isEmpty())
				return null;
		    // Sort the waitQueue by Effective Priority
		    sort();
		    // Return the thread with the highest effective priority
			return (KThread) waitQueue.removeFirst();
		}
	
		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		
		// Returns next thread without removing from queue
		protected ThreadState pickNextThread() {
		    // implement me
		    if (waitQueue.isEmpty())
				return null;
		    // Sort the waitQueue by Effective Priority
		    sort();
		    // Return the thread with the highest Effective Priority
			return  getThreadState((KThread) waitQueue.peekFirst());
		}
		
		public void print() {
		    Lib.assertTrue(Machine.interrupt().disabled());
		    // implement me (if you want)
		}
	
		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		
		// Added Functions
		
		// Sort sorts the waitQueue data structure by effective priority
		// If 2 threads have the same priority, the thread that has been waiting
		// the longest will be chosen, as in getEffectivePriority, the thread waiting 
		// the longest will be given higher priority.
		
		public void sort() {
			if (waitQueue.isEmpty()) {
				return;
			}
			// Comparator for sorting waitQueue
			Collections.sort(waitQueue, new Comparator<KThread>(){
				 public int compare(KThread o1, KThread o2) {
					 // Sort threads by highest effective priority (getEffectivePriority factors in waiting time when threads have equal priority)
					 if (getThreadState(o1).getEffectivePriority() < getThreadState(o2).getEffectivePriority()) {
						 return 1;
					 }  else {
						 return -1;
					 }
			     }
			});
		}
		
		// waitQueue (adapted from RoundRobinScheduler.java)
		// This is the actual data structure that holds the associated threads
		public LinkedList<KThread> waitQueue = new LinkedList<KThread>();
    }
    
    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    setPriority(priorityDefault);
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
	    // implement me
		if(threadQueue == null) {
			return priority;
		}	
		// If the queue is empty or only has 1 thread, return the original priority value
		if (threadQueue.waitQueue.isEmpty()) {
			return priority;
		}
		for(int i = 0; i < threadQueue.waitQueue.size(); i++) {
			//  If this thread has the same priority as another thread (and they are not the same thread)
			if (getThreadState(threadQueue.waitQueue.get(i)).priority == this.priority && getThreadState(threadQueue.waitQueue.get(i)).thread != this.thread) {
				// If this thread's starting wait time is earlier (this thread has been waiting for longer) then return priority + 1
				if (getThreadState(threadQueue.waitQueue.get(i)).waitStartTime > this.waitStartTime && this.priority != priorityMaximum) {
					return (this.priority + 1);
				}
			}	
		}
		return priority;	
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
	    // implement me
	    // Function does not need additional functionality
	    // as the re-configuring priority
	    // is accomplished by the data structure
	    // and it's sorting
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
	    // implement me
		Lib.assertTrue(Machine.interrupt().disabled());
		// Add this thread to the PriorityQueue's waitQueue
		waitQueue.waitQueue.add(thread);
		// Set the time that the thread was added to the waitQueue
		waitStartTime = Machine.timer().getTime();
		// Set this ThreadState's associated PriorityQueue to the waitQueue
		// that this thread was added to
		threadQueue = waitQueue;	
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
		Lib.assertTrue(Machine.interrupt().disabled());
		// If transfer priority is enabled and this thread has an associated threadQueue
		if(waitQueue.transferPriority == true && threadQueue != null) {
			// If the associated Priority Queue's waitQueue is not empty and has at least 2 threads in its waitQueue
			if (!threadQueue.waitQueue.isEmpty() && threadQueue.waitQueue.size() >= 2 ) {
				int maxWaitTimeIndex = 0;
				// Find the thread that has been waiting the longest
				for(int i = 1; i < threadQueue.waitQueue.size(); i++) {
					if (getThreadState(threadQueue.waitQueue.get(i)).waitStartTime < getThreadState(threadQueue.waitQueue.get(maxWaitTimeIndex)).waitStartTime) {
						maxWaitTimeIndex = i;
					}
				}
				// Donate priority to the longest waiting thread
				if (getThreadState(waitQueue.waitQueue.get(maxWaitTimeIndex)).getPriority() != priorityMaximum) {
					getThreadState(waitQueue.waitQueue.get(maxWaitTimeIndex)).setPriority(getThreadState(thread).getPriority());
	 			}
			}
		}
		// Remove the thread from its associated PriorityQueue's waitQueue
	    waitQueue.waitQueue.remove(thread);
	    // Set this thread's associated PriorityQueue reference to null;
	    threadQueue = null; 
	}

	/** The thread with which this object is associated. */	   
	public KThread thread;
	/** The priority of the associated thread. */
	public int priority;
	
	// The PriorityQueue that the thread has been added to.
	// When the associated thread is added to a queue (when waitForAccess is called), 
	// this ThreadQueue/PriorityQueue will be set to the queue that the thread was added to
	// This object is used to calculate getEffectivePriority and ultimately to effect scheduling decisions
	public PriorityQueue threadQueue = null;
	
	// Added threadAge to indicate how long a thread has been waiting
	public long waitStartTime = 0;
	
    }
}