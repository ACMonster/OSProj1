package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
    static BoatGrader bg;
    static Lock Olock, Mlock;
    static int numOA, numOC, numMA, numMC;
    static boolean needORower, needORider, arrivedM;
    static Condition OChild, OAdult, MChild;
    static Communicator communicator;
    
    public static void selfTest()
    {
	BoatGrader b = new BoatGrader();
	
	// System.out.println("\n ***Testing Boats with only 2 children***");
	// begin(0, 2, b);

	// System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
 // 	begin(1, 2, b);

 	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
 	begin(3, 3, b);

 		// System.out.println("\n ***Testing Boats with 20 children, 20 adults***");
 		// begin(20, 20, b);

    }

    public static void begin( int adults, int children, BoatGrader b )
    {
	// Store the externally generated autograder in a class
	// variable to be accessible by children.
	bg = b;

		// Instantiate global variables here
		Olock = new Lock();
		Mlock = new Lock();
		numOA = numOC = numMA = numMC = 0;
		needORower = needORider = true;
		arrivedM = false;
		OChild = new Condition(Olock);
		OAdult = new Condition(Olock);
		MChild = new Condition(Mlock);
		communicator = new Communicator();
		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		Runnable adultRun = new Runnable() {
		    public void run() {
	                AdultItinerary();
	            }
        };
        Runnable childRun = new Runnable() {
        	public void run() {
        		ChildItinerary();
        	}
        };
        for (int i = 0; i < adults; ++ i)
        {
	        KThread t = new KThread(adultRun);
	        t.setName("Adult thread " + i);
	        t.fork();
	    }
	    for (int i = 0; i < children; ++ i)
        {
	        KThread t = new KThread(childRun);
	        t.setName("Child thread " + i);
	        t.fork();
	    }
	    while (communicator.listen() != adults + children);
    }

    static void AdultItinerary()
    {
	bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE. 

	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
		Olock.acquire();
		numOA ++;
		OAdult.sleep();
		numOA --;
		Olock.release();
		bg.AdultRowToMolokai();
		Mlock.acquire();
		numMA ++;
		MChild.wake();
		Mlock.release();
    }

    static void ChildItinerary()
    {
	bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE. 

		Olock.acquire();
		int myPlace = 0; // 0 for Oahu, 1 for Molokai
		while (true)
		{
			if (myPlace == 0)
			{
				while (!needORider || needORower && numOC == 0)
				{
					numOC ++;
					OChild.sleep();
					numOC --;
				}
				if (needORower)
				{
					needORower = false;
					OChild.wake();

					bg.ChildRowToMolokai();
					Olock.release();
				}
				else
				{
					needORider = false;
					Olock.release();

					bg.ChildRideToMolokai();
				}
				Mlock.acquire();
				myPlace = 1;
				if (arrivedM)
					MChild.wake();
				else
					arrivedM = true;
				numMC ++;
				MChild.sleep();
				numMC --;
			}
			else
			{
				arrivedM = false;
				Mlock.release();

				bg.ChildRowToOahu();

				Olock.acquire();
				myPlace = 0;
				if (numOC > 0)
				{
					needORider = true;
					OChild.wake();

					bg.ChildRowToMolokai();
					Olock.release();

					Mlock.acquire();
					myPlace = 1;
					if (arrivedM)
						MChild.wake();
					else
						arrivedM = true;
					numMC ++;
					MChild.sleep();
					numMC --;
				}
				else if (numOA == 0)
				{
					Olock.release();

					bg.ChildRowToMolokai();

					Mlock.acquire();
					myPlace = 1;
					communicator.speak(numMC + numMA + 1);
					MChild.wake();
					numMC ++;
					MChild.sleep();
					numMC --;
				}
				else
				{
					OAdult.wake();
					numOC ++;
					OChild.sleep();
					numOC --;
				}
			}
		}
    }

    static void SampleItinerary()
    {
	// Please note that this isn't a valid solution (you can't fit
	// all of them on the boat). Please also note that you may not
	// have a single thread calculate a solution and then just play
	// it back at the autograder -- you will be caught.
	System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
	bg.AdultRowToMolokai();
	bg.ChildRideToMolokai();
	bg.AdultRideToMolokai();
	bg.ChildRideToMolokai();
    }
    
}
