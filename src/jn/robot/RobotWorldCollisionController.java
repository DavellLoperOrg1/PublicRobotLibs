package jn.robot;

import java.util.Vector;

// This a controller thread that checks collisions between Robjects
// It is launched by the RobotWorld
public class RobotWorldCollisionController implements Runnable
{

   // we need to know the robot world
   public RobotWorld theRW = null;
   // we check things every checkCycleTime. It should be < to the Robject.minimalCycleTime
   public static long checkCycleTime = 40; // 40 ms 
   // to display the latest Msg
   public String latestMsg = "nothing to declare";
   // constants
   // factor for damages due to collision
   private double collisionDamageFactor = 0.005d; // high damages
   //private double collisionDamageFactor = 0.0003d; // low damages
   // distance applied between 2 robjects after a collision happened
   private double deltaDistAfterCollision = 2*Robot.collisionMargin; // this param is aligned with the collisionMargin

   // constructor
   public RobotWorldCollisionController(RobotWorld pRW)
   {
      theRW = pRW;
   }

   // to run as a Thread
   public void go()
   {
      Thread theThread = new Thread(this,"RobotWorldCollisionController");
		theThread.start();
   }

   // control
   public void run()
   {

      long totalNbrOfCycles = 0; // the total number of cycles

      // infinite loop, each iteration is a cycle
      while (true)
      {

         totalNbrOfCycles++;

         // check collisions
         // we memorize the collision checks that are done between robots (Robots couples) to avoid rechecking (collision (R1,R2) = collision(R2,R1))
         Vector<String> theCheckedRobotCouples = new Vector<String>();
         // we must clone the list of Robjects because it can change during the processing (Robjects that are destroyed and removed from the list for ex)
         Vector<Robject> theRobjects = (Vector<Robject>) theRW.getListOfRobjects().clone();
         int theNbrOfRobjects = theRobjects.size();
         for (int i=0;i<theNbrOfRobjects;i++) // first loop
         {
            Robject theCurrentRobject = theRobjects.elementAt(i);
            // we only take care of robots in the first loop
            if (theCurrentRobject instanceof Robot)
            {
               Robot theCurrentRobot = (Robot) theCurrentRobject;

               // if the Robot is not alive (could happen if it was killed due to a previous collision check in the loop), discard
               if (!theCurrentRobot.isAlive)
                        continue;

               // second loop, we look at all the Robjects except the Robot itself obviously
               for (int j=0;j<theNbrOfRobjects;j++)
               {
                  if (j!=i) // do not check a collision between the Robot and itself!
                  {
                     Robject theRobjectToCheck = theRobjects.elementAt(j);

                     // if the Robject is not concrete (a Position for example) then it's not subject to collisions
                     if (!theRobjectToCheck.isConcrete)
                        continue;
                     
                     // if the theRobjectToCheck is a Robot, 
                     // check1: verify if the collision check has been already done for this couple, if not memorize it
                     // check2: verify if theRobjectToCheck is targeting the Robot. If so there is no collision, it's intentional
                     if (theRobjectToCheck instanceof Robot)
                     {
                        // If the current Robot couple (theCurrentRobot,theRobjectToCheck) has already been checked
                        // then it means we have an entry for the couple (theRobjectToCheck,theCurrentRobot) in the Vector
                        String checkEntry = theRobjectToCheck.name + "-" + theCurrentRobot.name;
                        if (theCheckedRobotCouples.contains(checkEntry))
                           continue;
                        else
                           theCheckedRobotCouples.addElement(theCurrentRobot.name + "-" + theRobjectToCheck.name);

                        // Manage the case when the theRobjectToCheck is targeting the Robot (could be a RobotMissile targeting theCurrentRobot, for ex)
                        // so if the Robot is targeted by theRobjectToCheck, the collision in 'intentional' in that case, and so we skip
                        if (((Robot)theRobjectToCheck).target !=null && theCurrentRobot.name.equals(((Robot)theRobjectToCheck).target.name))
                           continue;   
                     }

                     // Let's calculate the distance between the Robot and the Robject to check
                     double distance = Robject.calculateDistanceBetweenRobjects(theCurrentRobot, theRobjectToCheck);

                     // Manage the case when the theRobjectToCheck is the target of the Robot
                     // if the Robot targets theRobjectToCheck, it's normal it wants to reach it and so the collision in 'intentional' in that case
                     if (theCurrentRobot.target !=null && theRobjectToCheck.name.equals(theCurrentRobot.target.name))
                        continue;

                     // Manage the case when the theRobjectToCheck just was the target of the Robot (previousTarget)   
                     // Indeed: if the Robot just reached the target theRobjectToCheck and is now leaving it, it's normal it is still "in collision"
                     // So we ensure the Robot is actually leaving in the second case...but it's not that simple:
                     // the Robot may have stopped at 3cm of the target on the left and when it leaves it is at 2 cm on the right
                     // So the change of distance test is only valid if distance is not too small (precisely:  > minDistanceTarget)
                     double distanceChange = Robject.calculateChangeOfDistanceBetweenRobjects(theCurrentRobot,theRobjectToCheck); // >0 means getting away
                     boolean robotIsVeryCloseToPreviousTarget = (distance < theCurrentRobot.minDistanceTarget);
                     if (theCurrentRobot.previouslyReachedTarget !=null 
                        && theRobjectToCheck.name.equals(theCurrentRobot.previouslyReachedTarget.name) 
                        && (robotIsVeryCloseToPreviousTarget || distanceChange >= 0) )
                        continue;

                     // Considering all Robjects as circles (size = diameter), collision means: dist < (size1 + size2)/2 
                     double minDistance = (theCurrentRobot.size + theRobjectToCheck.size) / 2;
                     if (distance < minDistance) // collision
                     {
                        latestMsg = "Detected collision between : "+theCurrentRobot.name+" and "+theRobjectToCheck.name+ ". Distance = "+distance+" < "+minDistance;
                        logIt(latestMsg);
                        // damage effect
                        setDamagesEffect(theCurrentRobot,theRobjectToCheck);
                        // effect on Robjects speed and position
                        setMovementEffect(theCurrentRobot, theRobjectToCheck);
                        distance = Robject.calculateDistanceBetweenRobjects(theCurrentRobot, theRobjectToCheck);
                        logIt("After setMovement() : R1.position[0] = "+theCurrentRobot.position[0]);
                        logIt("After setMovement() : R1.position[1] = "+theCurrentRobot.position[1]);
                        logIt("After setMovement() : R2.position[0] = "+theRobjectToCheck.position[0]);
                        logIt("After setMovement() : R2.position[1] = "+theRobjectToCheck.position[1]);
                        logIt("After setMovement() : Distance(R1,R2) = "+distance);
                     }
                  }
               }
            }
         }

         // let sleep   
         try
         {
            Thread.sleep(checkCycleTime);
         }
         catch (InterruptedException iex) {}
 
         // log every 100 cycles = 4s
         //if (totalNbrOfCycles%100==0) logIt("I'm here");
      }
      // things to clean?

   }

   // set damages on the ramages due to collision
   // depends on repective speeds and mass
   private void setDamagesEffect(Robot pR1, Robject pR2)
   {
      // raisonnement : 
      // soit s le différentiel des vitesses : vecteur s = speed1 - speed2
      // s est pertinent car imaginons 2 objets allant dans le même sens (l'un rattrape l'autre) : les dommages sont bien moins importants que si les objets sont en frontal
      // On dit que les dommages sont proportionnels à (m1+m2)*s^2
      double[] diffSpeed = new double[2];
      diffSpeed[0] = pR1.speed[0] - pR2.speed[0]; 
      diffSpeed[1] = pR1.speed[1] - pR2.speed[1];
      double sp2 = diffSpeed[0]*diffSpeed[0] + diffSpeed[1]*diffSpeed[1];
      double massSum = pR1.mass +  pR2.mass;
      double energy = massSum * sp2;
      int damages = (int) (collisionDamageFactor * energy);
      // same damages apply to each Robject
      logIt("Damages due to collision on "+pR1.name+" = "+damages);
      pR1.currentShielding = pR1.currentShielding - damages;
      if (pR1.currentShielding <=0 )
      {
         pR1.die();
         logIt("Damages due to collision destroyed "+pR1.name);
      }
      logIt("Damages due to collision on "+pR2.name+" = "+damages);
      pR2.currentShielding = pR2.currentShielding - damages;
      if (pR2.currentShielding <=0 )
      {
         pR2.die();
         logIt("Damages due to collision destroyed "+pR2.name);
      }
   }

   // Set effect on Robjects movement (speed and position) due to collision
   // depends on respective speeds and mass (rebound, ...)
   private void setMovementEffect(Robot pR1, Robject pR2)
   {
      // no need to act if at least 1 of the 2 Robjects died because of previous damages
      if (!(pR1.isAlive && pR2.isAlive))
         return;

      // simplest implementation (not physical at all) is to set speeds to 0:
      pR1.speed[0] = 0;
      pR1.speed[1] = 0;
      pR2.speed[0] = 0;
      pR2.speed[1] = 0; 
      // but then the 2 Robjects remain in collision...so we need to move the 2 Robjects a little bit so that they are not in collision anymore   
      // we move the Robjects anti-proportionnaly to their mass and so that they are separated by (pR1.size + pR2.size) / 2 + deltaDistAfterCollision
      // first, memorize the previous position
      pR1.previousPosition[0] = pR1.position[0];
      pR1.previousPosition[1] = pR1.position[1];
      pR2.previousPosition[0] = pR1.position[0];
      pR2.previousPosition[1] = pR1.position[1];      
      // adapt positions
      double[] unitVectorFromR1toR2 = Robot.calculateDirectionFromR1toR2(pR1, pR2);
      double deltaDist = (pR1.size + pR2.size) / 2 - Robject.calculateDistanceBetweenRobjects(pR1, pR2) + deltaDistAfterCollision;
      double addDistR1 = pR2.mass / (pR1.mass + pR2.mass) * deltaDist;
      double addDistR2 = pR1.mass / (pR1.mass + pR2.mass) * deltaDist;
      pR1.position[0] = pR1.position[0] - unitVectorFromR1toR2[0] * addDistR1;
      pR1.position[1] = pR1.position[1] - unitVectorFromR1toR2[1] * addDistR1;
      pR2.position[0] = pR2.position[0] + unitVectorFromR1toR2[0] * addDistR2;
      pR2.position[1] = pR2.position[1] + unitVectorFromR1toR2[1] * addDistR2;
   }

	protected void logIt(String msg)
	{
		java.util.Date d = new java.util.Date();
		java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss:SSS");
		String dateString = formatter.format(d);
		System.out.println("[RobotWorldCollisionController][" + dateString + "]" + msg);
	}

}
