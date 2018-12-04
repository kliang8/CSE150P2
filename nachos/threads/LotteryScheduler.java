package nachos.threads;

import nachos.machine.*;
//import nachos.threads.KThread.PingTest;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends PriorityScheduler {

	// Variables
	public static final int priorityDefault = 1;
	public static final int priorityMinimum = 1;
	public static final int priorityMaximum = Integer.MAX_VALUE;

	// Constructor

	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}

	// Functions

	@Override
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
	}

	@Override
	protected LotteryThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new LotteryThreadState(thread);

		return (LotteryThreadState) thread.schedulingState;
	}

	// Tests

	private static class PingTest implements Runnable {
		PingTest(int which) {
			this.which = which;
		}

		public void run() {
			for (int i = 0; i < 5; i++) {
				System.out.println("*** thread " + which + " looped " + i + " times");
				KThread.currentThread().yield();
			}
		}

		private int which;
	}

	public static void selfTest() {
		selfTest1();
	}

	public static void selfTest1() {

		System.out.println("Running Self Test");
		KThread[] threads = new KThread[4];
		int[] tickets = new int[4];

		tickets[0] = 15;
		tickets[1] = 30;
		tickets[2] = 50;
		tickets[3] = 5;

		LotteryScheduler scheduler = (LotteryScheduler) (ThreadedKernel.scheduler);

		//ThreadQueue tq = scheduler.newThreadQueue(true);

		// Create Threads
		for (int i = 0; i < 4; i++) {
			threads[i] = new KThread(new PingTest(i)).setName("threads " + String.valueOf(i) + " ");
			scheduler.getThreadState(threads[i]).setPriority(tickets[i]);
		}

		// Fork Threads
		for (int i = 0; i < 4; i++) {
			System.out.println("Thread " + i + " forked.");
			threads[i].fork();
		}

		// Join Threads
		//for (int i = 0; i < 4; i++) {
		//	threads[i].join();
		//}

		System.out.println("End of Self Test");
	}

	public static void selfTest2() {
		System.out.println("Running Self Test");
		KThread[] threads = new KThread[2];
		int[] tickets = new int[2];

		tickets[0] = 51;
		tickets[1] = 49;

		LotteryScheduler scheduler = (LotteryScheduler) (ThreadedKernel.scheduler);

		// Create Threads and Fork Them
		for (int i = 0; i < 2; i++) {
			threads[i] = new KThread(new PingTest(i)).setName("threads " + String.valueOf(i) + " ");
			scheduler.getThreadState(threads[i]).setPriority(tickets[i]);
			System.out.println("Thread " + i + " forked.");
			threads[i].fork();
		}

		// Join Threads
		for (int i = 0; i < 2; i++) {
			threads[i].join();
		}

		System.out.println("End of Self Test");
	}

	// Lottery Queue
	public class LotteryQueue extends PriorityQueue {

		// Variables
		public LotteryThreadState owner = null;

		public LotteryQueue(boolean transferPriority) {
			super(transferPriority);
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);

			if (owner != null) {

			} else {
				owner = (LotteryThreadState) getThreadState(thread);
			}

		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			if (waitQueue.isEmpty())
				return null;

			if (owner != null) {
				owner.noLongerOwn(this);
			}
			owner = null;


			int[] ticketSum = new int[waitQueue.size()];

			// Case 1: priority donation
			if (transferPriority) {

				// Calculate each threads ticket count, including the tickets from queues owned
				// by the thread
				for (int i = 0; i < waitQueue.size(); i++) {
					int ownedQueueSize = getThreadState(waitQueue.get(i)).ownedQueues.size();
					int ticketCount = getThreadState(waitQueue.get(i)).getPriority();
					for (int j = 0; j < ownedQueueSize; j++) {
						int ownedWaitingQueueSize = (((getThreadState(waitQueue.get(i))).ownedQueues).get(j)).waitQueue
								.size();
						int KticketSum = 0;
						for (int k = 0; k < ownedWaitingQueueSize; k++) {
							KticketSum += getThreadState(
									(((getThreadState(waitQueue.get(i))).ownedQueues).get(j).waitQueue
											.get(k))).priority;
						}
						ticketCount += KticketSum;
					}
					ticketSum[i] = ticketCount;
				}

				// Calculate the total number of tickets across all threads
				int totalTickets = 0;
				for (int i = 0; i < ticketSum.length; i++) {
					totalTickets += ticketSum[i];
				}

				// Run the lottery
				int randomNumber = (int) (Lib.random() * totalTickets);
				for (int i = 0; i < waitQueue.size(); i++) {
					randomNumber -= ticketSum[i];
					if (randomNumber < 0) {
						return (KThread) waitQueue.remove(i);
					}
				}
				
			// Case 2: no priority donation
			} else {
				
				// Calculate each threads ticket count, WITHOUT ticket donation / priority donation
				for (int i = 0; i < waitQueue.size(); i++) {
					ticketSum[i] = getThreadState(waitQueue.get(i)).getPriority();
				}
				
				// Calculate the total number of tickets across all threads
				int totalTickets = 0;
				for (int i = 0; i < ticketSum.length; i++) {
					totalTickets += ticketSum[i];
				}
				
				// Run the lottery
				int randomNumber = (int) (Lib.random() * totalTickets);
				for (int i = 0; i < waitQueue.size(); i++) {
					randomNumber -= ticketSum[i];
					if (randomNumber < 0) {
						return (KThread) waitQueue.remove(i);
					}
				}
				
			}
			
			
			
			/*
			 * Return thread with most tickets
			 * 
			 * int max = 0; int index = 0; for (int i = 0; i < waitQueue.size(); i++) { if
			 * (max < getThreadState(waitQueue.get(i)).priority ) { max =
			 * getThreadState(waitQueue.get(i)).priority; index = i; } }
			 * 
			 * return (KThread) waitQueue.get(index);
			 */

			// Case 2: no priority donation
			/*
			 * for (int i = 0; i < waitQueue.size(); i++) { int ticketCount =
			 * getThreadState(waitQueue.get(i)).getPriority(); ticketSum[i] = ticketCount; }
			 */

			// Return the thread with the highest effective priority
			// return (KThread) waitQueue.removeFirst();

			return null;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return, without
		 * modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */

		// Returns next thread without removing from queue
		
		// FIX
		
		protected ThreadState pickNextThread() {
			// implement me
			if (waitQueue.isEmpty())
				return null;
			
			//if (waitQueue.isEmpty())
			//	return null;

			//if (owner != null) {
			//	owner.noLongerOwn(this);
			//}
			//owner = null;


			int[] ticketSum = new int[waitQueue.size()];

			// Case 1: priority donation
			if (transferPriority) {

				// Calculate each threads ticket count, including the tickets from queues owned
				// by the thread
				for (int i = 0; i < waitQueue.size(); i++) {
					int ownedQueueSize = getThreadState(waitQueue.get(i)).ownedQueues.size();
					int ticketCount = getThreadState(waitQueue.get(i)).getPriority();
					for (int j = 0; j < ownedQueueSize; j++) {
						int ownedWaitingQueueSize = (((getThreadState(waitQueue.get(i))).ownedQueues).get(j)).waitQueue
								.size();
						int KticketSum = 0;
						for (int k = 0; k < ownedWaitingQueueSize; k++) {
							KticketSum += getThreadState(
									(((getThreadState(waitQueue.get(i))).ownedQueues).get(j).waitQueue
											.get(k))).priority;
						}
						ticketCount += KticketSum;
					}
					ticketSum[i] = ticketCount;
				}

				// Calculate the total number of tickets across all threads
				int totalTickets = 0;
				for (int i = 0; i < ticketSum.length; i++) {
					totalTickets += ticketSum[i];
				}

				// Run the lottery
				int randomNumber = (int) (Lib.random() * totalTickets);
				for (int i = 0; i < waitQueue.size(); i++) {
					randomNumber -= ticketSum[i];
					if (randomNumber < 0) {
						return getThreadState(waitQueue.get(i));
					}
				}
				
			// Case 2: no priority donation
			} else {
				
				// Calculate each threads ticket count, WITHOUT ticket donation / priority donation
				for (int i = 0; i < waitQueue.size(); i++) {
					ticketSum[i] = getThreadState(waitQueue.get(i)).getPriority();
				}
				
				// Calculate the total number of tickets across all threads
				int totalTickets = 0;
				for (int i = 0; i < ticketSum.length; i++) {
					totalTickets += ticketSum[i];
				}
				
				// Run the lottery
				int randomNumber = (int) (Lib.random() * totalTickets);
				for (int i = 0; i < waitQueue.size(); i++) {
					randomNumber -= ticketSum[i];
					if (randomNumber < 0) {
						return getThreadState(waitQueue.get(i));
					}
				}
				
			}
			
			
			
			// Sort the waitQueue by Effective Priority
			// sort();
			// Return the thread with the highest Effective Priority
			return null;
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)

			System.out.println("Print Called");

		}

	}

	// Thread State

	protected class LotteryThreadState extends ThreadState {

		// Variables
		protected LinkedList<PriorityQueue> ownedQueues = new LinkedList<PriorityQueue>();

		public LotteryThreadState(KThread thread) {
			super(thread);
		}
		
		@Override
		public int getEffectivePriority() {
		    // implement me
			//if(threadQueue == null) {
			//	return priority;
			//}	
			// If the queue is empty or only has 1 thread, return the original priority value
			//if (threadQueue.waitQueue.isEmpty()) {
			//	return priority;
			//}
			
			
			int ticketSum = this.priority;
			for (int i = 0; i < ownedQueues.size(); i++) {
				int ownedWaitingQueueSize = ownedQueues.get(i).waitQueue.size();
				int KticketSum = 0;
				for (int j = 0; j < ownedWaitingQueueSize; j++) {
					KticketSum += getThreadState(ownedQueues.get(i).waitQueue.get(j)).priority;
							
							
				}
				ticketSum += KticketSum;
				
			}
			priority = ticketSum;
			
			return priority;
			
			
			/*
			 for (int j = 0; j < ownedQueueSize; j++) {
						int ownedWaitingQueueSize = (((getThreadState(waitQueue.get(i))).ownedQueues).get(j)).waitQueue
								.size();
						int KticketSum = 0;
						for (int k = 0; k < ownedWaitingQueueSize; k++) {
							KticketSum += getThreadState(
									(((getThreadState(waitQueue.get(i))).ownedQueues).get(j).waitQueue
											.get(k))).priority;
						}
						ticketCount += KticketSum;
					}
					ticketSum[i] = ticketCount;
			  
			  
			 */
			
			
		}

		@Override
		public void waitForAccess(PriorityQueue waitQueue) {
			// implement me
			Lib.assertTrue(Machine.interrupt().disabled());
			// Add this thread to the PriorityQueue's waitQueue
			waitQueue.waitQueue.add(thread);
			
			ownedQueues.remove(waitQueue);
			
			// Set this ThreadState's associated PriorityQueue to the waitQueue
			// that this thread was added to
			threadQueue = waitQueue;
		}

		@Override
		public void acquire(PriorityQueue waitQueue) {
			Lib.assertTrue(Machine.interrupt().disabled());
			// Remove the thread from its associated PriorityQueue's waitQueue
			waitQueue.waitQueue.remove(thread);

			// Now that the associated thread has acquired access, it now owns this priority
			// queue (the one it previously waited on)
			ownedQueues.add(waitQueue);
			waitQueue.owner = this;

			// Set this thread's associated PriorityQueue reference to null;
			// This is the queue the thread is waiting on, now that it has acquired access
			// the thread is no longer waiting, the thread now owns the resource,
			// as is indicated in ownedQueues
			threadQueue = null;
		}

		public void noLongerOwn(PriorityQueue waitQueue) {
			ownedQueues.remove(waitQueue);
		}

	}
}
