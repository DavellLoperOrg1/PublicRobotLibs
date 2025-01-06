package jn.robot;

import java.awt.Color;
import java.util.Vector;

// This kind of robot carries goods from source location (warehouse for ex) to destination location (delivery point)
// Mission: go to source loc and load, carry load to dest loc and unload, then repeat indefinitely.
// The actual mass of the truck depends on its load: total mass = mass of truck alone + mass of load
public class RobotTruck extends Robot
{

   // the source location
   private Location theSourceLoc = null;
   // the destination location
   private Location theDestinationLoc = null;
   // the mass to load at the source location
   private double massToLoad;
   // the nominal mass (mass without any load)
   private double nominalMass;
   // the total mass of the actual load(s) (the truck can be loaded several times)
   private double loadedMass;


   // constructor
   public RobotTruck(String pName, RobotWorld pRW, double pXcoord, double pYcoord)
   {
      // call parent Robot constructor
      super(pName,pRW,pXcoord,pYcoord);
      // specific to RobotTruck
      color = Color.BLUE;
      shape = "disk";
      size = 2;
      mass = 1000;
      currentChargeLevel = 1000000; // great autonomy
      maximumChargeLevel = 1000000; // great autonomy
      //currentChargeLevel = 200000; // medium autonomy
      //maximumChargeLevel = 200000; // medium autonomy      
      currentShielding = 500;
      maxShielding = 500;
      reactionTimeFactor = 3;
      maxSpeedModulus = 1.0d;
      antiCollisionSystemIsActive = true;
   }

   // Set the mission 
   // register the source (warehouse) to pick merchandise from and the destination to deliver it
   // register the mass of the merchandise to pick up 
   public void setMission(Location pSourceLoc, Location pDestinationLoc, double pMassToLoad)
   {
      // parameters specific to the RobotTruck
      nominalMass = mass; // memorize the nominal mass before any loading: it's the truck's mass
      theSourceLoc = pSourceLoc;
      theDestinationLoc = pDestinationLoc;
      massToLoad = pMassToLoad;
   }

   // Think and act
   // So we have 2 main stages:
   // STAGE 1 : THINK, meaning if no state is defined yet then determine it
   //   - Here we determine the state, the target and the speed modulus we want to use to reach the target
   // STAGE 2 : ACT, meaning execute all possible actions related to the state (so, a state has to be defined as a prereq obviously)
   //   - Check if the target was reached
   //     - if yes, do the actions related to the target (for ex load or unload mass, charge battery, etc)
   //     - if no, continue the journey to the target 
   //       - first determine the speed vector (will be used to create the movement in the calling thread)
   //       - override 1: check if target is Location its occupancy, if occupied, stop (speed = 0)
   //       - override 2: check it target is apporaching close, if so adapt speed (decrease speed to be sure we don't miss the target)
   //       - override 3: if anticollision system is activated, cehck for obstacle and escape idf needed (adapt speed to go righ or left typically) 
   public void thinkAndAct()
   {

      // STAGE 1: THINK. If we don't have a state, we need to determine it
      if (state == null) 
      {
         // Steps for the robot truck:
         // if we are not loaded, then we need to go to the source loc and state = "GOTOSOURCE"
         // if we are loaded, then we need to go to the dest loc and state = "GOTODESTINATION"
         // if autonomy is not sufficient, override (state can be changed to "GOTORBC" typically)
         // Important Note: here in STAGE 1, we only want to determine the state and associated speedModulus to reach the target, but not the speed vector (that causes the movement)
         // So we set the speed vector to 0 on purpose so that the speed vector is calculated and applied in STAGE 2 only (important because it takes care of collisions as well)
         
         // if the source and dest loc are not defined, well it's not normal
         if (theSourceLoc == null || theDestinationLoc == null)
         {
            logIt("The source or dest location is not defined, that's not normal. Have you set the mission properly? I can't do anything so let's die.");
            isAlive = false;
            return;
         }

         // go to source location
         if (loadedMass < massToLoad)
         {
            target = theSourceLoc;
            state = "GOTOSOURCE";
            speedModulus = maxSpeedModulus; // we'll go full speed
            logIt("changed state to GOTOSOURCE, target is "+target.name);
         }
      
         // go to dest location
         if (loadedMass == massToLoad)
         {
            target = theDestinationLoc;
            state = "GOTODESTINATION";
            speedModulus = maxSpeedModulus; // we'll go full speed here, but we could/should calculate speed depending on mass, to optimize autonomy...
            logIt("changed state to GOTODESTINATION, target is "+target.name);
         }
         
         // this should not happen
         if (loadedMass > massToLoad)
         {
            // that should not happen...
            logIt("loadedMass > massToLoad: that's not normal at all. Let's die");
            die();
         }

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
               }
               
               /*
               // This section consists of looking for the closest RBC (option 2), but that may lead us to remain blocked on a RBC, which is not good
                // what's the closest RBC?
                ResourceBatteryCharger theClosestRBC = (ResourceBatteryCharger) calculateTheClosestAmongTheList(this, theListOfAllRBC);
                target = theClosestRBC;
                state = "GOTORBC";
                logIt("changed state to GOTORBC");
                logIt("Here is the closest RBC: "+target.name+". Distance = "+calculateDistanceBetweenRobjects(this,target));
               */
            }
            else // ok found a RBC, that's the new target (we don't change the speedModulus here)
            {
               state = "GOTORBC";
               logIt("changed state to GOTORBC");
               target = theCheckRobject;
               speedModulus = maxSpeedModulus; // we'll go full speed
               logIt("Proposed RBC found in range is this one: "+target.name+". Distance = "+calculateDistanceBetweenRobjects(this,target));
            }
         }
         // Now let's reset the speed vector to 0 just to make sure is no actual move before STAGE 1 is done
         speed[0] = 0;
         speed[1] = 0;
      }

      // STAGE 2: ACT. If we have a state, then do any action we can in context of that sate
      if (state != null)
      {
         // we should have a target
         // have we just reached the target? If so, act and change the state if needed
         if (targetIsReached())
         {
            // first stop the engine: speed = 0
            speedModulus = 0;
            speed[0] = 0;
            speed[1] = 0;

            // if we've reached the source loc then load and reset state
            if ("GOTOSOURCE".equals(state))
            {
               // load the mass
               load(massToLoad);
               // tell the Location we free occupancy
               ((Location) target).freeOccupancy(this);
               logIt("OK reached SOURCE LOC " + target.name + " and loaded the mass: "+massToLoad);
               state = null;
            }
            // if we've reached the target loc then unload and reset state     
            if ("GOTODESTINATION".equals(state))
            {
               unload(massToLoad);
               // tell the Location we free occupancy               
               ((Location) target).freeOccupancy(this);               
               logIt("OK reached DESTINATION LOC " + target.name + " and unloaded the mass: "+massToLoad);
               state = null;
            }
            // if we've reached the RBC then charge battery and check when it is fully charged (then reset state)
            if ("GOTORBC".equals(state))
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
                  // tell the Location we free occupancy               
                  ((Location) target).freeOccupancy(this);
                  state = null;
               }
            }
         }
         else // continue to go to the target
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
                        state = null; // reset the state, it will be redefined in the next thread call, which will allow to restart (or to remain stopped)
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

   // load at a warehouse (source location), that increases the total loaded mass, as well as the total mass
   // the truck can be loaded several times with different masses
   public void load(double pLoadMass)
   {
      loadedMass = loadedMass + pLoadMass; // total mass of the load(s)
      mass = nominalMass + loadedMass; // update the mass
   }

   // unload the load at the destination, that brings mass back to nominal
   public void unload(double pLoadMass)
   {
      loadedMass = loadedMass - pLoadMass; // total mass of the load(s)
      mass = nominalMass + loadedMass; // update the mass
   }
}
