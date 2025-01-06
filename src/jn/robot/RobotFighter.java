package jn.robot;

import java.awt.Color;
import java.util.StringTokenizer;
import java.util.Vector;

// RobotFighter extends RobotGen1. Capabilities:
// ATTACK*THE-ENEMY*QTY: go to the target and attack the Robject named 'THE-ENEMY' by causing at least QTY damages to it
// WATCH*RANGE: go to the target and watch for any Robject coming within range. Attack them.
public class RobotFighter extends RobotGen1 implements RobotMissileLauncher
{

   // the loaded Missiles
   Vector<RobotMissile> theLoadedMissiles = new Vector<RobotMissile>();
   // missile counter to set unique name on each missile
   int missileCounter = 0;
   // the nbr of missiles that were launched during the last attack round
   int nbrOfMissilesLaunched = 0;
   // the number of feedbacks received from missiles launched during the last attack round
   int nbrOfFeedbacks = 0;
   // the enemy
   Robject theEnemyRobject = null;
   // the total damages to inflict to the enemy
   int theDamagesToInflict = 0;
   // the inflicted damages to the enemy so far
   int theInflictedDamages = 0;
   // position of the RobotFighter chest
   double[] theChestPosition = new double[2];
   // position of the RobotFighter left arm
   double[] theLeftArmPosition = new double[2];
   // position of the RobotFighter right arm
   double[] theRightArmPosition = new double[2];
   // (WATCH verb) boolean to tell if it is a new watch: meaning it's the first time we're going into the watch() method
   boolean isNewWatch = true;
   // (WATCH verb) boolean to tell if intruder(s) was/were detected
   boolean intruderDetected = false;
   // (WATCH verb) latest time where there was no trouble (no intruder detected)
   long latestQuietTime = 0;

   // constructor
   public RobotFighter(String pName, RobotWorld pRW, double pXcoord, double pYcoord)
   {
      // call parent RobotGen1 constructor
      super(pName,pRW,pXcoord,pYcoord);
      // specific to RobotFighter
      color = Color.blue;
      shape = "disk"; // special shape to identify fighters more easily
      mass = 1000; // a RobotFighter is able to carry 20 missiles so it's a heavy robot
      // components
      Color componentsColor = Color.BLUE;
      // chest component
      Vector<double[]> theChestPositionVector = new Vector<double[]>();
      theChestPositionVector.addElement(theChestPosition);
      RobotComponent chestComponent = new RobotComponent("chest","disk",0.5d,componentsColor,theChestPositionVector);
      theRobjectComponents.addElement(chestComponent);
      // left arm component
      Vector<double[]> theLeftArmPositionVector = new Vector<double[]>();
      theLeftArmPositionVector.addElement(theLeftArmPosition);
      RobotComponent leftArmComponent = new RobotComponent("leftArm","disk",0.5d,componentsColor,theLeftArmPositionVector);
      theRobjectComponents.addElement(leftArmComponent);
      // right arm component
      Vector<double[]> theRightArmPositionVector = new Vector<double[]>();
      theRightArmPositionVector.addElement(theRightArmPosition);
      RobotComponent rightArmComponent = new RobotComponent("rightArm","disk",0.5d,componentsColor,theRightArmPositionVector);
      theRobjectComponents.addElement(rightArmComponent);
   }

   // To update the positions of the components
   // For the RobotFighter: positions of the chest, left arm and right arm components
   // They point to : the enemy if it is defined, or to the target if it is defined
   protected void updateComponentsPositions()
   {

      // call super() to place super components
      super.updateComponentsPositions();

      // the chest and arms components must point to the enemy if it is defined, or the target if it is defined
      Robject theRobjectToPointTo = target;
      if (theEnemyRobject != null)
         theRobjectToPointTo = theEnemyRobject;
      if (theRobjectToPointTo == null)
         return;   

      // direction for the chest position
      double[] directionToPointTo = calculateDirectionFromR1toR2(this, theRobjectToPointTo);
      // direction for the right arm position
      double[] orthogonalRightDTE = new double[2];
      orthogonalRightDTE[0] = directionToPointTo[1];
      orthogonalRightDTE[1] = - directionToPointTo[0];
      // direction for the left arm position
      double[] orthogonalLeftDTE = new double[2];
      orthogonalLeftDTE[0] = - directionToPointTo[1];
      orthogonalLeftDTE[1] = directionToPointTo[0];

      double marginFactor = 1.1; // some margin to position the missiles on the RobotFighter
      RobotMissile theRM = new RobotMissile("dummy",this.theRW,0d,0d,0,0); // need a dummy missile to know its size
      
      // calculate the position for the chest
      theChestPosition[0] = this.position[0] + (this.size + theRM.size) / 2 * marginFactor * directionToPointTo[0];
      theChestPosition[1] = this.position[1] + (this.size + theRM.size) / 2 * marginFactor * directionToPointTo[1];

      // calculate the position for the left arm
      theLeftArmPosition[0] = this.position[0] + (this.size + theRM.size) / 2 * marginFactor * orthogonalLeftDTE[0];
      theLeftArmPosition[1] = this.position[1] + (this.size + theRM.size) / 2 * marginFactor * orthogonalLeftDTE[1];

      // calculate the position for the right arm
      theRightArmPosition[0] = this.position[0] + (this.size + theRM.size) / 2 * marginFactor * orthogonalRightDTE[0];
      theRightArmPosition[1] = this.position[1] + (this.size + theRM.size) / 2 * marginFactor * orthogonalRightDTE[1];      
   }

   // VERB = LOADMISSILES (LOADMISSILES*DAMAGEPOWER*BLASTRADIUS*QTY)
   // The RobotFighter must be loaded with RobotMissiles to be able to attack
   // Missiles are loaded at a Location of type ResourceMissileSupplier
   // The RobotFighter will be loaded with a max of QTY missiles with DAMAGEPOWER and BLASTRADIUS characteristics
   public boolean loadmissiles(StringTokenizer pST)
   {
      int theDamagePower = Integer.parseInt(pST.nextToken());
      double theBlastRadius = Double.parseDouble(pST.nextToken());
      int theQty = Integer.parseInt(pST.nextToken());
      // if the RobotFighter is laready loaed with some missiles we must take that into account (QTY missiles in total)
      int nbrOfAlreadyLoadedMissiles = theLoadedMissiles.size();
      theQty = theQty - nbrOfAlreadyLoadedMissiles;

      for (int i=0;i<theQty;i++)
      {
         missileCounter++;
         String theMissileName = this.name + "-M-" + missileCounter;
         RobotMissile theRM = ((ResourceMissileSupplier) target).getMissile(theMissileName, theDamagePower, theBlastRadius);
         theLoadedMissiles.addElement(theRM);
         // each loaded missile adds its mass to the Robot
         mass = mass + theRM.mass;
      }
      logIt("Loaded "+theQty+ " missiles. Total nbr of loaded missiles = "+theLoadedMissiles.size()+". DamagePower/BlastRadius = "+theDamagePower+" / "+theBlastRadius);
      return true;
   }

   // verb = ATTACK (ATTACK*THE-ENEMY*QTY)
   // "attack this Robject and cause qty damages"
   // returns true once the inflicted damages are >= QTY or there is no more weapons to attack the enemy, or there is no more enemy
   public boolean attack(StringTokenizer pST)
   {
      String theEnemyRobjectName = pST.nextToken();
      String theDamagesToInflictAsString = pST.nextToken();
      theDamagesToInflict = Integer.parseInt(theDamagesToInflictAsString);

      // Get the enemy Robject
      theEnemyRobject = theRW.getRobject(theEnemyRobjectName);
      if (theEnemyRobject==null)
      {
         logIt("Could not find a Robject named "+theEnemyRobjectName + ". So the attack is skipped.");
         return true;
      }

      // Attack the enemy
      boolean attackIsOver = attackTheEnemy();
      if (attackIsOver) // beware attack can be over for different reasons: enemy is destroyed, no more missiles
      {
         theEnemyRobject = null; // reset enemy
         return true;
      }
      
      return false;
   }  

   // verb = WATCH (WATCH*RANGE*DELAY)
   // "watch this area (disk centered on Target with radius = RANGE) and leave if nothing happened after DELAY ms"
   // "if Robot(s) are detected within RANGE, attack them (just attack the closest Robot at each iteration)"
   // returns true once the inflicted damages are >= QTY or there is no more weapons to attack the enemy, or there is no more enemy
   public boolean watch(StringTokenizer pST)
   {
      double range = Double.parseDouble(pST.nextToken());
      long delay = Long.parseLong(pST.nextToken());
      // While the RobotFighter is on watch it is displaying the range circle : we use a temporary component for that
      RobotComponent rangeCircleComponent = null;

      // If this is a new watch, initialize the latestQuietTime 
      if (isNewWatch)
      {
         logIt("Starting to watch within range = "+range+" m. Delay = "+delay+" ms.");
         latestQuietTime = System.currentTimeMillis();
         isNewWatch = false; // now it's not a new watch anymore

         // Let's create the temporary component for range circle 
         Vector<double[]> theRangeCirclePositionVector = new Vector<double[]>();
         theRangeCirclePositionVector.addElement(this.position);
         rangeCircleComponent = new RobotComponent("rangeCircle","circle",range*2,Color.orange,theRangeCirclePositionVector);
         theRobjectComponents.addElement(rangeCircleComponent);
      }

      // If we don't already have an enemy identified, let's look for it : any robot coming in range?
      if (theEnemyRobject == null)
      {
         // Any robot in range? We select on RobotGen1 type because we don't want to detect RobotMissiles
         Vector<Robject> robotsInRange = getTheListOfRobjectsWithinRange(this,range,"RobotGen1");
         int nbrOfIntruders = robotsInRange.size();

         if (nbrOfIntruders > 0) // Intruder(s) detected
         {
            intruderDetected = true;

            // list intruder(s) : todojn : remove
            /*
            for (int i=0;i<nbrOfIntruders;i++)
            {
               Robot currentRobot = (Robot) robotsInRange.elementAt(i);
               logIt("Detected this intruder Robot in range : "+currentRobot.name+". Distance = "+calculateDistanceBetweenRobjects(this,currentRobot));
            }
            */

            // define our enemy: it is the closest intruder
            theEnemyRobject = calculateTheClosestAmongTheList(this,robotsInRange);
            logIt("Our enemy is identified: "+theEnemyRobject.name);
            // we want to destroy the enemy and so the damages to inflict are equal to its shielding level
            theDamagesToInflict = theEnemyRobject.currentShielding;
         }
         else // No intruder detected
         {
            intruderDetected = false;
            //logIt("No intruder detected in range");
            // Have we reached the DELAY for a quiet period? If so this watch task is over
            if (System.currentTimeMillis() - latestQuietTime > delay)
            {
               logIt("WATCH : it has been quiet for longer than " + delay + "ms. So the WATCH task is over.");
               isNewWatch = true; // reset for the next possible WATCH
               theEnemyRobject = null; // reset enemy
               theInflictedDamages = 0; // reset inflicted damages
               theRobjectComponents.removeLast(); // delete the temporary component for range circle, it's the last element in the vector
               return true;
            }
         }
      }

      // If we have an identified enemy, attack it and check the status
      if (theEnemyRobject != null)
      {
         // if the enemy has left the range then we stop the attack (so we don't necessarily destroy the enemy, we just want it to be out of range)
         double distanceToEnemy = calculateDistanceBetweenRobjects(this,theEnemyRobject);
         if (distanceToEnemy > range)
         {
            theEnemyRobject = null; // reset enemy
            theInflictedDamages = 0; // reset inflicted damages
            return false; // go to the next watch round
         }            

         // Attack the enemy
         boolean attackIsOver = attackTheEnemy();

         // Reset the latestQuietTime to now as we just attacked an enemy
         latestQuietTime = System.currentTimeMillis(); 

         // Is attack over? Beware attack can be over for different reasons: enemy is destroyed, no more missiles
         if (attackIsOver) 
         {
            theEnemyRobject = null; // reset enemy
            theInflictedDamages = 0; // reset inflicted damages
            return false; // still have to finish our watch, go to next round
         }
      }
      
      // if we are here, the WATCH cycle must continue and we're going to the next round
      return false;
   }

   // Once the enemy is identified, attack it
   // returns true once the inflicted damages are >= damages to inflict or there is no more weapons to attack the enemy, or there is no more enemy
   public boolean attackTheEnemy()
   {
      // if the enemy is null we have a problem of consistency
      if (theEnemyRobject == null)
      {
         logIt("attackTheEnemy() method was invoked but theEnemyRobject is null: that's a bit strange...");
         return true;
      }

      // Before going into the (next) attack round, check if we received all feedback from missiles sent during the previous attack round
      if (nbrOfFeedbacks < nbrOfMissilesLaunched)
      {
         //logIt("nbrOfFeedbacks / nbrOfMissilesLaunched : "+nbrOfFeedbacks + " / " + nbrOfMissilesLaunched + ". Waiting for more feedback.");
         return false;
      }

      // Have we inflicted enough damages to the enemy? If so we're done with the attack!
      int remainingDamagesToInflict = theDamagesToInflict - theInflictedDamages;
      if (remainingDamagesToInflict <= 0)
      {
         logIt("We have inflicted " + theInflictedDamages + " / " + theDamagesToInflict + " damages to enemy " + theEnemyRobject.name + ". So the attack was successful!");
         return true;
      }
      
      // Check if we have at least 1 loaded missile, if not we cannot attack...
      // todojn : see if we can improve to go load more missiles in a warehouse (advanced....need to modify the mission dynamically)
      if (theLoadedMissiles.size()==0)
      {
         logIt("We don't have any remaining missile loaded. So the attack is abandoned.");
         return true;
      }
      
      // Restore counters for the next attack round
      nbrOfMissilesLaunched = 0;
      nbrOfFeedbacks = 0;

      // Preparation: select 1 to 3 missiles and position them before launch (on chest, on right arm, on left arm)
      int damagesCausedByASuccessfulMissile = theLoadedMissiles.elementAt(0).damagePower;
      int nbrOfMissilesToPrepare = Math.ceilDiv(remainingDamagesToInflict, damagesCausedByASuccessfulMissile); // prepare as many missiles as possible
      if (nbrOfMissilesToPrepare > 3) // can't go above 3 missiles during 1 attack round
         nbrOfMissilesToPrepare = 3;
      logIt("We want to prepare " + nbrOfMissilesToPrepare + " missiles. remainingDamagesToInflict = "+remainingDamagesToInflict);   
      RobotMissile[] theRMs = prepareToLaunchMissiles(nbrOfMissilesToPrepare);
      
      // Launch the missiles! And record how many were launched
      int nbrOfPreparedMissiles = theRMs.length;
      for (int i=0;i<nbrOfPreparedMissiles;i++)
      {
         theRMs[i].setMission(theEnemyRobject);
         theRMs[i].start();
         // Once launched the missile is not part of the Robot anymore and so we remove its mass
         mass = mass - theRMs[i].mass;
         logIt("Launched missile "+theRMs[i].name);
         // To avoid that missiles collide between them (happens because of symetry between the 2 arms) and also to avoid blast radius between missiles
         // we need to add a delay = blast radius / missile speed
         double marginFactor = 1.2; // necessary because the missile approaching to targetToDestroy decelerates and so need to compensate (20% margin here)
         long delay = (long) (marginFactor * 1000 * theRMs[i].blastRadius / theRMs[i].maxSpeedModulus); 
         logIt("Now wait " + delay + " ms."); 
         try {Thread.sleep(delay);} catch (Exception ex) {}
      }
      nbrOfMissilesLaunched = nbrOfPreparedMissiles;

      return false;      
   }

   // To prepare a missile before launching it
   // Prereq: the RobotFighter must be loaded with some missiles
   // The RobotFighter can launch the missiles from 3 places: from its chest, from its right arm, from its left arm
   // First, the RobotFighter orientates itself towards the enemy (its chest is pointing in the enemy's direction)
   // Second, the RobotFighter prepares 1 to 3 missiles (also checking if there are enough remaining) into the 3 places
   // If there is no loaded missile at the beginning, returns null
   private RobotMissile[] prepareToLaunchMissiles(int pNbrOfMissilesToPrepare)
   {
      // First: make sure there is at least 1 loaded missile remaining
      if (theLoadedMissiles.size()==0)
      {
          logIt("There is no more loaded missile, so we can't prepare any missile");  
          return null;
      }

      // The actual number of missiles that we will be able to prepare (if everything is fine it will be the intended number)
      int nbrOfMissilesToPrepare = pNbrOfMissilesToPrepare;
      // check if there are enough loaded missile compared to the number of missiles to prepare, reduce if needed
      int theNbrOfRemainingLoadedMissiles = theLoadedMissiles.size();
      if (theNbrOfRemainingLoadedMissiles < nbrOfMissilesToPrepare)
      {
          logIt("There is less loaded missiles than the number of missiles to prepare (" + nbrOfMissilesToPrepare + "). So we prepare only " + theNbrOfRemainingLoadedMissiles + " missiles.");  
          nbrOfMissilesToPrepare = theNbrOfRemainingLoadedMissiles;
      }

      // Initialize the list of RobotMissiles to prepare
      RobotMissile[] theRMs = new RobotMissile[nbrOfMissilesToPrepare];
      int countPreparedMissiles = -1; // 0 means first missile is prepared (index)
      // Update the positions of the chest and arms when pointing towards the enemy 
      // (need to invoke the method explicitely here to make sure chest and arms components are well positioned before firing missiles)
      updateComponentsPositions();

      // If we prepare 1 or 3 missiles, 1 will be on the RobotFighter's chest for sure
      if (nbrOfMissilesToPrepare==1 || nbrOfMissilesToPrepare==3)
      {
         RobotMissile theRMchest = theLoadedMissiles.elementAt(0); // we take the first available (no need to check if vector is empty, already done before)
         theLoadedMissiles.removeElementAt(0); // the missile is removed from the loaded missiles list
         theRMchest.setLauncherAndPosition(this,theChestPosition);
         countPreparedMissiles++;
         theRMs[countPreparedMissiles] = theRMchest;
      }

      // If we prepare 2 or 3 missiles, 2 of them will be on RobotFighter's arms (left and right)
      if (nbrOfMissilesToPrepare==2 || nbrOfMissilesToPrepare==3)
      {
         // prepare left arm
         RobotMissile theRMleft = theLoadedMissiles.elementAt(0); // we take the first available (no need to check if vector is empty, already done before)
         theLoadedMissiles.removeElementAt(0); // the missile is removed from the loaded missiles list
         theRMleft.setLauncherAndPosition(this,theLeftArmPosition);
         countPreparedMissiles++;
         theRMs[countPreparedMissiles] = theRMleft;
         // prepare right arm
         RobotMissile theRMright = theLoadedMissiles.elementAt(0); // we take the first available (no need to check if vector is empty, already done before)
         theLoadedMissiles.removeElementAt(0); // the missile is removed from the loaded missiles list
         theRMright.setLauncherAndPosition(this,theRightArmPosition);
         countPreparedMissiles++;
         theRMs[countPreparedMissiles] = theRMright;         
      }

      return theRMs;
   }

   // get feedback info from the missiles that were launched
   public void receiveMissileFeedback(String pRobotMissileName, boolean reachedTargetToDestroy, int pInflictedDamages)
   {
      //logIt("Got feedback from missile "+pRobotMissileName+". TargetToDestroy was hit / damages = "+reachedTargetToDestroy + " / " + pInflictedDamages);
      theInflictedDamages = theInflictedDamages + pInflictedDamages;
      nbrOfFeedbacks++;
   }   

}
