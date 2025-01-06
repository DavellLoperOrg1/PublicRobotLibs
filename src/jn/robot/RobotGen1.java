package jn.robot;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.StringTokenizer;
import java.util.Vector;

// Robot Gen1 can do many things : follow a Mission, stay at a Position during some time, load/unload merchandise at locations
// subclasses of RobotGen1 add other capabilities (RobotFighter for ex)
public class RobotGen1 extends Robot
{

   // the mission
   private Mission theMission =  null;
   // where are we in the mission: which objective number in the mission list
   private int currentObjectiveNumber = 0; // the list of Objectives start at 0, so that's the first objective actually
   // status to know if the current goal is under work
   private boolean isWorkingOnGoal = false;

   // Specific to each kind of Objective:
   
   // Load/Unload a mass of material at a target Location
   // nominal mass (mass of the Robot without any load)
   private double nominalMass;
   // the total mass of the actual load(s) (the Robot can load several times)
   private double loadedMass;
   
   // Stay x ms at a Position
   // boolean telling if the method 'stayThereFor()' is called for the first time or not
   private boolean firstCallToStayThere = true;
   // startTime in ms
   private long startTimeToStayThere = 0;

   // constructor
   public RobotGen1(String pName, RobotWorld pRW, double pXcoord, double pYcoord)
   {
      // call parent Robot constructor
      super(pName,pRW,pXcoord,pYcoord);
      // specific to RobotGen1
      color = Color.blue;
      shape = "disk";
      size = 2;
      mass = 300;
      currentChargeLevel = 1000000; // great autonomy
      maximumChargeLevel = 1000000; // great autonomy
      //currentChargeLevel = 200000; // medium autonomy
      //maximumChargeLevel = 200000; // medium autonomy      
      currentShielding = 1200;
      maxShielding = 1200;
      reactionTimeFactor = 3;
      maxSpeedModulus = 1.0d;
      antiCollisionSystemIsActive = true;
      nominalMass = mass; // memorize the nominal mass before any loading: it's the robot mass
   }

   // Set the mission 
   public void setMission(Mission pMission)
   {
      theMission = pMission;
      // initialize the mission parameters
      currentObjectiveNumber = -1; // the list of Objectives start at 0, so that's the first objective actually. We set -1 on purpose, see below
      state = "OBJECTIVE DONE"; // We set "OBJECTIVE DONE" on purpose so that we start with the THINK stage and that will setup the robot for the first objective
   }

   // Think and act
   // So we have 2 main stages:
   // STAGE 1 : THINK, meaning if no active state is defined then determine it
   //   - Here we determine the state, the target and the speed modulus we want to use to reach the target
   // STAGE 2 : ACT, meaning execute all possible actions related to the state (so, a state has to be defined as a prereq obviously)
   //   - Check if the target was reached
   //     - if yes, do the actions related to the target (for ex load or unload mass, charge battery, etc)
   //     - if no, continue the journey to the target 
   //       - first determine the speed vector (will be used to create the movement in the calling thread)
   //       - override 1: check if target is Location its occupancy, if occupied, stop (speed = 0)
   //       - override 2: check it target is getting close, if so adapt speed (decrease speed to be sure we don't miss the target)
   //       - override 3: if anticollision system is activated, check for obstacle and escape if needed 
   public void thinkAndAct()
   {

      // STAGE 1: THINK. 
      // Determine where we are in the mission with the list of objectives
      // If we have finished the current objective, state = "OBJECTIVE DONE", and then we need to determine the next objective and new state
      // If we are still working on the current objective, state = "OBJECTIVE ONGOING" or "RBC ONGOING" and we go to STAGE 2: ACT
      // state should never been null

      // First make sure the Mission is coherent enough
      if (theMission==null || theMission.getMissionObjectives() == null || theMission.getMissionObjectives().size()==0)
      {
         logIt("That's not a mission! I will die!");
         die();
         return;
      }

      // Determine the next state/target
      if (state.equals("OBJECTIVE DONE")) 
      {
         // Steps for the RobotGen1: 
         // determine state, mais que vaut state? "GOING TO OBJECTIVE AS SCHEDULED"?
         // if autonomy is not sufficient, override (state can be changed to "RBC ONGOING" typically)
         // Important Note: here in STAGE 1, we only want to determine the state and associated speedModulus to reach the target, but not the speed vector (that causes the movement)
         // So we set the speed vector to 0 on purpose so that the speed vector is calculated and applied in STAGE 2 only (important because it takes care of collisions as well)
         
         // determine state : 
         // check what's the next objective in the Mission's list
         int totalNbrOfObjectives = theMission.getMissionObjectives().size();
         
         // was it the last objective in the list?
         if (currentObjectiveNumber==totalNbrOfObjectives-1) 
         {
            // is it a cycling mission?
            if (theMission.isCyclic())  // back to the first objective in the list
            {
               currentObjectiveNumber = -1; // we set this way because we setup for the next objective in the next block
            }
            else // mission is over
            {
               logIt("Mission is over since we accomplished all the objectives. Time to die!");
               state = "ALL DONE";
               // we die. todojn: see if must do better ? 'freeze'?
               die();
               return;
            }
         }
         
         // let's proceed to the next objective in the list
         // setup for the next objective
         currentObjectiveNumber++;
         state = "OBJECTIVE ONGOING";
         target = theMission.getMissionObjectives().elementAt(currentObjectiveNumber).getTheRobject();
         speedModulus = maxSpeedModulus; // we'll go full speed
         logIt("New objective number = " + currentObjectiveNumber + " State = " + state + ". Target is "+target.name);

         // Check autonomy: is target reachable or do we need to go to a RBC first?
         // Exceptionally, we need to calculate the speed vector here, because it's an input for the autonomy calculation
         // but at the end of STAGE 1 we'll reset the speed vector to 0
         direction = calculateDirectionFromR1toR2(this,target);
         speed = calculateSpeed(direction,speedModulus);
         Robject theCheckRobject = calculateIfTargetReachableOrNeedRBC();
         if (theCheckRobject!=target) // target is not reachable as is, need to go to a RBC
         {
            // ouch! no RBC reachable with the current autonomy
            if (theCheckRobject==null)
            {
               logIt("No RBC found in range with the current autonomy! Now decreasing to half speed to augment autonomy and hope we can reach the target "+target.name);
               // How to raise autonomy?
               // Here we decide to decrease speed to half of previous value (autonomy is ~ 1/(mV) so autonomy is then doubled... that will help!)
               // Note: so why note decrease more (take 25% for ex, or even 10% or 1% of current speed ???) 
               // Be careful: autonomy (distance) can then go to infinite, which is great ... but robot gets slower and slower so time to reach target becomes also infinite...
               // Once speedModulus is setup, what do we do? There are 2 main options :
               // 1) go to the target as initially set and hope it will work with this extra autonomy
               // 2) go to a RBC: the closest RBC typically, and from that it should be ok
               // 1) is more risky because we may run out of battery, but 2) can lead to be blocking a RBC because we can't reach the target anyway (so stay on the RBC)
               // We prefer 1) because blocking a RBC impacts all other Robots who want to access this RBC
               // And so so nothing to do since the state and the target are already set, we have just reduced the speed modulus
               speedModulus = speedModulus / 2;

               // if no RBC in this World then we cannot live as a Robot!   
               Vector<ResourceBatteryCharger> theListOfAllRBC = getListOfAllRBC();
               if (theListOfAllRBC.size()==0)
               {
                  logIt("There is no RBC in this World. A Robot cannot live in such a world, so we die!");
                  die();
                  return;
               }
            }
            else // ok found a RBC, that's the new target (we don't change the speedModulus here)
            {
               state = "RBC ONGOING";
               logIt("Changed state to RBC ONGOING");
               target = theCheckRobject;
               speedModulus = maxSpeedModulus; // we'll go full speed
               logIt("Proposed RBC found in range is this one: "+target.name+". Distance = "+calculateDistanceBetweenRobjects(this,target));
            }
         }
         // Now let's reset the speed vector to 0 just to make sure is no actual move before STAGE 2 is done
         speed[0] = 0;
         speed[1] = 0;
      }

      // STAGE 2: ACT. If we have a state other than "OBJECTIVE DONE", then do any action we can in context of that state
      if (!state.equals("OBJECTIVE DONE"))
      {
         // we should have a target
         // have we just reached the target? If so, act and change the state if needed
         if (targetIsReached())
         {
            // first stop the engine: speed = 0
            speedModulus = 0;
            speed[0] = 0;
            speed[1] = 0;

            // if we've reached the Objective then do the Goal - todojn
            if ("OBJECTIVE ONGOING".equals(state))
            {
               // work on the goal
               
               // when we just reached the Objective point we have not yet started to work on the Goal
               if (!isWorkingOnGoal)
               {
                  logIt("OK reached Objective. target = " + target.name + ". Now starting to work on the Goal");
                  isWorkingOnGoal = true;  
               }

               // work on the goal and check if we are done
               boolean weAreDone = workOnGoal(theMission.getMissionObjectives().elementAt(currentObjectiveNumber));

               
               // when we're done
               if (weAreDone)
               {
                  logIt("OK achieved Goal for objective/target " + target.name);
                  // we're done for the goal
                  isWorkingOnGoal = false;
                  // if Location, tell the Location we free occupancy
                  if (target instanceof Location)
                  {
                     ((Location) target).freeOccupancy(this);
                     logIt("OK freed occupancy for target " + target.name);
                  }
                  // set state to done
                  state = "OBJECTIVE DONE";
               }

            }

            // if we've reached the RBC then charge battery and check when it is fully charged (then reset state)
            if ("RBC ONGOING".equals(state))
            {
               // when we just reach the RBC we're not yet charging
               if (!isCharging)
               {
                  logIt("OK reached RBC "+target.name);
                  logIt("OK connected to RBC to charge. Current charge level = "+currentChargeLevel);
                  isCharging = true;
               }
               // charge
               currentChargeLevel = currentChargeLevel + chargeBattery((ResourceBatteryCharger)target);
               // is battery fully charged?
               if (currentChargeLevel >= maximumChargeLevel)
               {
                  currentChargeLevel = maximumChargeLevel;
                  logIt("OK battery is fully charged now. Disconnecting from RBC");
                  isCharging = false;
                  // tell the Location (it's a RBC) we free occupancy               
                  ((Location) target).freeOccupancy(this);
                  // set the state back to the current objective: so we do as if we just terminated the previous objective, this way we'll restart on the right obj
                  currentObjectiveNumber--;
                  state = "OBJECTIVE DONE";
               }
            }
         }
         else // target is not reached yet, continue to go to the target
         {
            // update speed vector with direction and speed modulus: that will make the movement (done in the thread Robot.run() method)
            direction = calculateDirectionFromR1toR2(this,target);
            speed = calculateSpeed(direction,speedModulus);

            // First override
            // check this about the target: if the target is a Location, we have to request Occupancy: if not granted then we stop (temporarily) to spare energy until granted
            if (target instanceof Location) 
            {
               // we request Occupancy when we're relatively close to the Location to avoid unnecessary locking of the place
               double distance = calculateDistanceBetweenRobjects(this, target);
               double reactionRange = (this.size+target.size)*2; // this must be greater than the range for collision checking
               if (distance <= reactionRange)
               {
                  // if we were already granted Occupancy, no need to ask again
                  Robot occupant = ((Location)target).getOccupant();
                  if (occupant != null && occupant.name.equals(this.name))
                  {
                     // we do nothing here since we already got Occupancy right
                  }
                  else // ok let's ask to be granted Occupance, if refused let's wait (stop to spare energy)
                  {
                     boolean canOccupateLocation = ((Location)target).requestOccupancy(this);
                     if (canOccupateLocation) // youpi, we're granted!
                     {
                        logIt("We were just granted Occupancy for our target location "+target.name+". Let's move!");
                     }
                     else 
                     {
                        logIt("We were not granted Occupancy for our target location "+target.name+". We stop until we get granted.");
                        speedModulus = 0;
                        speed[0] = 0;
                        speed[1] = 0;
                        // set the state back to the current objective: 
                        // so we do as if we just terminated the previous objective, this way we'll restart on the right obj and that wil redetermine target and speed
                        currentObjectiveNumber--;
                        state = "OBJECTIVE DONE";
                     }
                  }
               }

            }

            // Second override
            // check this about the target: do we need to adapt speed because we're approaching the target?
            adaptSpeedToTargetIfNeeded();

            // Third override
            // collision detection and escape maneuver
            // basic implementation for escape maneuver: 
            //   1 action that consists of changing speed (go full speed to the right or left side)
            //   so that will override the previous speed calculation if escape maneuver is needed
            if (antiCollisionSystemIsActive)
               adaptSpeedToAvoidCollisionIfNeeded();
         }            
      }

   }

   // To work on the goal of an objective 
   // returns true only when the Goal is totally achieved
   private boolean workOnGoal(Objective pObjective)
   {
      // Get the goal and extract the Verb and Quantity
      String theGoal = pObjective.getTheGoal();

      // The goal always starts with a verb delimited by a * : so we always have VERB* at the beginning of the goal
      // quite often, the goal string is VERB*QTY , and qty is a double or an int
      // but not always, the goal string can be VERB*PARAM1*PARAM2
      // So we extract the VERB anyway and we delegate to the corresponding method the rest of the parsing
      // The method name is the VERB in lower case
      StringTokenizer theST = new StringTokenizer(theGoal,"*");
      String theVerb = theST.nextToken();
      String theMethodName = theVerb.toLowerCase();
      try
      {
         Method theMethod = this.getClass().getMethod(theMethodName,StringTokenizer.class);
         boolean invokeResult = (boolean) theMethod.invoke(this, theST);
         return invokeResult;
      }
      catch (NoSuchMethodException theNSMexc)
      {
         logIt("The VERB is: " + theVerb + ". Could not find an associated method = "+theMethodName+". That's a big problem, so we die");
         die();
      }
      catch (Exception otherExc)
      {
         logIt("Problem encountered when invoking method = "+theMethodName+". That's a big problem, so we die. Exception msg = "+otherExc.getMessage());
         die();
      }

      // we're done 
      return true;
   }

   // verb = LOADMASS
   // load mass at location, that increases the total loaded mass, as well as the total mass
   // the robot can be loaded several times with different masses
   // always returns true because is done in 1 call
   public boolean loadmass(StringTokenizer pST)
   {
      String theQtyAsString = pST.nextToken();
      double theQty = Double.parseDouble(theQtyAsString);
      loadedMass = loadedMass + theQty; // total mass of the load(s)
      mass = nominalMass + loadedMass; // update the mass
      logIt("Just loaded this mass = " + theQty + ". Total mass of the robot = "+mass);
      return true;
   }

   // verb = UNLOADMASS 
   // unload the load at the destination, that decreases the total loaded mass, as well as the total mass
   // always returns true because is done in 1 call
   public boolean unloadmass(StringTokenizer pST)
   {
      String theQtyAsString = pST.nextToken();
      double theQty = Double.parseDouble(theQtyAsString);
      loadedMass = loadedMass - theQty; // total mass of the load(s)
      if (loadedMass < 0)
         logIt("loadedMass = "+loadedMass+". There's a problem...Perhaps the Mission is not coherent?");
      mass = nominalMass + loadedMass; // update the mass
      logIt("Just unloaded this mass = " + theQty + ". Total mass of the robot = "+mass);
      return true;
   }

   // verb = STAY
   // STAY*QTY "stay there for this number of ms"
   // returns true once the number of ms is passed, returns false until then
   public boolean stay(StringTokenizer pST)
   {
      String theQtyAsString = pST.nextToken();
      double theQty = Double.parseDouble(theQtyAsString);
      double pWaitTime = theQty;
      // if this is the first call
      if (firstCallToStayThere)
      {
         // set up the start time
         startTimeToStayThere = System.currentTimeMillis();
         // change the boolean
         firstCallToStayThere = false;
         logIt("Staying on this point " + target.name + " for "+pWaitTime+" ms.");
      }
      else
      {
         // have we passed the delay?
         long timeSpentSinceLastCall = System.currentTimeMillis() - startTimeToStayThere;
         if (timeSpentSinceLastCall > pWaitTime) // ok we're done
         {
            // reinit the boolean
            firstCallToStayThere = true;
            logIt("Finished staying on this point "+target.name);
            return true;
         }
      }

      return false;
   }   
}
