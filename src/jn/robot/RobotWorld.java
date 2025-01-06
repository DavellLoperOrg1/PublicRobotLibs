package jn.robot;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Vector;

// todos
// 1) Gestion des collisions et tests : 
//      bilan actuel : algo left-right est optimal pour trous de souris passants mais mène au crash (effet entonnoir) pour trou de souris trop petit
//      solution 1 : passer en always right pour les obstacles fixes, de type Location donc : OK ça améliore beaucoup mais pas 100% suffisant : 
//                   en effet il y a encore des cas où on tombe dans blocage/effet entonnoir/crash   (cf test1b de World1b)
//      solution 2 : savoir réagir à un trou de souris trop petit avec algo left-right ou always right
//                     basique = NON MARCHE PAS! augmenter suffisamment reactionToCollisionRangeFactor , ce qui permettrait d'éviter en amont dans tous les cas? 
//      --> meilleure solution a priori : always right + détecter trou de souris trop petit grâce à O1O2 (cas de obstacle et 'sous-obstacle'). TODOJN
//            --> Attention cela ne traite pas le cas d'une oscillation entre 2 obstacles : mais always right évite le pb je pense
//            --> OK on peut détecter mais comment réagir? ou mieux l'anticiper? utilisation algo escape sophistiqué avec Positions?
//      note à revérifier pas sûr que ça reste : pb algo backwards qui oscille (factorCollisionReactionRange = 1.2d à 1.4d) 
//    - test scenario avec obstacles fixes (locations) et passer dans un trou de souris : test1a et test1b
//    - test scenario avec obstacles fixes (locations) où ça pourrait osciller gauche-droite entre 2 obstacles : test1a et test1b
//    - test scenario avec obstacles mobiles : 
//      - entre 2 robots avec R1 et R2 qui arrivent en frontal : test 2a
//      - entre 2 robots avec R1 et R2 qui arrivent en oblique : test 2b
//      - même chose mais avec un 2nd obstacle fixe qui empêche le escape et mène à un stop infini : test2a
//      - entre robots et missiles : test3a 
//   - Ajout fonctionnalité Zone (polygones et cercles) TODOJN
//      - gérer collisions avec Zones dans Collision Controller d'abord : pas évident...
//      - puis algo évitement Zone dans Robot : algo escape sophistiqué avec un state 'escape' dédié et des Positions?
// 2) Robots de combat : TODOJN
//   - défenses anti missiles : boucliers physiques, leurres, antimissiles?
//   - lasers ou rayons = missiles rapides?
//   - mode combat : 
//     - basique, en restant statique sur la target et avec envoi missiles : 
//       - offensif simple faisable a priori avec Objective = "ATTACK*ENNEMI*100" : aller à la target et attaquer le Robject ENNEMI en infligeant 100 damages
//         --> a priori peu utilisable pour des Robjects mobiles, mais OK pour attaquer un Robject fixe comme une Location 
//       - défensif simple faisable a priori avec Objective = "WATCH*range" : on lance des missiles sur le(s) Robjects(s) dont la distance est < range
//         --> semble suffisant pour une tour de contrôle qui protège un périmètre autour de la target
//         --> notion de camp pour ne pas tirer sur ses amis...
//     - évolué : 
//       - a priori besoin d'un nouveau state/target(s) car l'ennemi n'est pas la target habituelle : le mode combat s'ajoute au mode habituel
//         - par ex : on peut continuer sa patrouille tout en attaquant un ennemi, savoir gérer le mode 'poursuite', savoir gérer ennemis multiples, etc
//         - comment étendre le Think&Act() actuel? (héritage...)
//       - rester à distance fixe de l'ennemi : mode 'poursuite' (corps à corps, missiles à distance réduite), gérer corps à corps versus algo anti-collision
//       - savoir déterminer un ennemi dynamiquement (range/périmètre ok, mais cas où un Robot ennemi attaque une Location hors périmètre, ou si on est soi-même attaqué...)
//     - mode combat consomme de l'energie? suivant les actions (corps à corps, missile, etc) et gérer la recharge d'énergie alors (go to RBC si energie < 20% par ex)
//     - recharger des munitions : prévoir Location de ce type et où/comment le gère t on?
// 3) Divers : 
//   - notion de 'detection range' : pour l'instant les Robots voient tout


// the World of Robots
public class RobotWorld 
{

    private Vector<Robject> theListOfRobjects = new Vector<Robject>();
    private RobotWorldCollisionController theRWCC;

    // to activate the CollisionController
    public void activateCollisionController()
    {
        // create and launch the CollisionController
        theRWCC = new RobotWorldCollisionController(this);
        theRWCC.go();
    }
    
    protected Vector<Robject> getListOfRobjects()
    {
        return theListOfRobjects;
    }

    // register the Robject (Robot or Resource)
    protected synchronized void registerRobject(Robject pRobject)
    {
        theListOfRobjects.add(pRobject);
        logIt("registerRobject(), welcome " + pRobject.name + "."); 
    }

    // register the Robject (Robot or Resource)
    protected synchronized void unregisterRobject(Robject pRobject)
    {
        theListOfRobjects.remove(pRobject);  
        logIt("unregisterRobject(), goodbye " + pRobject.name + ". Nbr of Robjects in the World = "+theListOfRobjects.size());  
    }

    // get a specific Robject by its name (to check if it is still registered for ex)
    // returns null if the Robject is not listed
    protected Robject getRobject(String pRobjectName)
    {
        int listSize = theListOfRobjects.size();
        for (int i=0;i<listSize;i++)
        {
            Robject currentRobject = theListOfRobjects.elementAt(i);
            if (pRobjectName.equals(currentRobject.name))
                return currentRobject; 
        }
        return null;
    }

    // to be implemented by subclasses
    // here we create the Robjects that are part of this World (robots, resources, ...)
    public void createRobjects()
    {     
    }

    // Display objects
    // X0,Y0 : graphical coordinates of the origin O (typically the center of the graphical window)
    // facteur...: scale factor along X and Y
    public synchronized void displayRobjects(Graphics2D g2, int X0, int Y0, double facteurEchelleX, double facteurEchelleY, Font pFont)
    {
        // Display info about CollisionController
        g2.setPaint(Color.BLACK);
        g2.drawString(theRWCC.latestMsg,50,50);

        // Draw the Robjects and their components
        int nbrOfRobjects = getListOfRobjects().size();
        for (int i=0;i<nbrOfRobjects;i++)
        {
            // get the current Robject
            Robject currentRobject = getListOfRobjects().elementAt(i);

            // draw the current Robject and print its information
            drawRobject(currentRobject,g2,X0,Y0,facteurEchelleX,facteurEchelleY,pFont);
 
            // if the current Robject has visible components, draw them
            int nbrOfComponents = currentRobject.theRobjectComponents.size();
            if (nbrOfComponents > 0)
            {
                for (int j=0;j<nbrOfComponents;j++)
                {
                    RobotComponent theCurrentRC = currentRobject.theRobjectComponents.elementAt(j);
                    drawRobjectComponent(theCurrentRC,g2,X0,Y0,facteurEchelleX,facteurEchelleY,pFont);
                }
            }
        }
    }

    // to draw a Robject and print its info
    public void drawRobject(Robject pRobject, Graphics2D g2, int X0, int Y0, double facteurEchelleX, double facteurEchelleY, Font pFont)
    {
        // calculate the graphical position of the Robject
        double x = pRobject.position[0];
        double y = pRobject.position[1];
        int X = X0 + (int) Math.round(x * facteurEchelleX);
        int Y = Y0 - (int) Math.round(y * facteurEchelleY);

        // draw the Robject according to its color, shape and size
        g2.setPaint(pRobject.color);
        String shape = pRobject.shape;
        double size = pRobject.size;
        int graphicalSize = (int) Math.round(size * facteurEchelleX);

        // if shape is disk, fill an oval
        if ("disk".equalsIgnoreCase(shape))
        {
            int ovalSize = graphicalSize;
            // need to compensate to center the circle since X,Y is a corner of a rectangle here
             g2.fillOval(X-ovalSize/2,Y-ovalSize/2,ovalSize,ovalSize);
        }

        // if shape is disk2, fill 1 oval and draw 1 empty oval around
        if ("disk2".equalsIgnoreCase(shape))
        {
            int filledOvalSize = graphicalSize;
            // need to compensate to center the circle since X,Y is a corner of a rectangle here
            g2.fillOval(X-filledOvalSize/2,Y-filledOvalSize/2,filledOvalSize,filledOvalSize);
            int graphicalSizeForEmptyOval = (int) Math.round(size * facteurEchelleX * 1.2);
            int emptyOvalSize = graphicalSizeForEmptyOval;
            // need to compensate to center the circle since X,Y is a corner of a rectangle here
            g2.drawOval(X-emptyOvalSize/2,Y-emptyOvalSize/2,emptyOvalSize,emptyOvalSize);
        }

        // if shape is circle, draw an oval
        if ("circle".equalsIgnoreCase(shape))
        {
            int ovalSize = graphicalSize;
            // need to compensate to center the circle since X,Y is a corner of a rectangle here
            g2.drawOval(X-ovalSize/2,Y-ovalSize/2,ovalSize,ovalSize);
        }

        // if shape is square, draw a square
        if ("square".equalsIgnoreCase(shape))
        {
            int squareSize = graphicalSize;
            // need to compensate to center the circle since X,Y is a corner of a rectangle here
            g2.drawRect(X-squareSize/2,Y-squareSize/2,squareSize,squareSize);
        }
        
        // display main info for the Robject: the name
        String mainInfos = pRobject.name;
        int rightshift = (int) (pRobject.size * facteurEchelleX / 2d) + 1;
        g2.drawString(mainInfos,X+rightshift,Y);
        int downshift = pFont.getSize(); //10;
        // display relevant infos for robots
        if (pRobject instanceof Robot)
        {
            // battery level info
            double batteryLevelAsDouble = (double) ((Robot)pRobject).currentChargeLevel / (double) ((Robot)pRobject).maximumChargeLevel;
            int percentageBatteryLevel = (int) (batteryLevelAsDouble*100);
            String batteryInfo = "b = "+ percentageBatteryLevel + "%";
            // shielding info
            double shieldingLevelAsDouble = (double) pRobject.currentShielding / (double) pRobject.maxShielding;
            int percentageShieldingLevel = (int) (shieldingLevelAsDouble*100);
            String shieldingInfo = "s = " + percentageShieldingLevel + "%";
            // target info
            String targetInfo = "t = null";
            Robot theRobot = (Robot) pRobject;
            if (theRobot.target != null) 
                targetInfo = "t = " + theRobot.target.name;                
            // speed modulus info
            double[] theSpeed = pRobject.speed;
            double speedModulus = Math.sqrt(theSpeed[0]*theSpeed[0]+theSpeed[1]*theSpeed[1]);
            String speedInfo = "sp = " + numberRoundedTo(speedModulus,2);                
            // mass info
            String massInfo = "m = " + (int) pRobject.mass;
            // display infos
            g2.drawString(shieldingInfo,X+rightshift,Y+1*downshift);
            g2.drawString(batteryInfo,X+rightshift,Y+2*downshift);
            g2.drawString(targetInfo,X+rightshift,Y+3*downshift);
            g2.drawString(speedInfo,X+rightshift,Y+4*downshift);
            if (pRobject instanceof RobotTruck || pRobject instanceof RobotGen1)
                g2.drawString(massInfo,X+rightshift,Y+5*downshift);
            
        }
        if (pRobject instanceof Location)
        {
            // shielding info
            double shieldingLevelAsDouble = (double) pRobject.currentShielding / (double) pRobject.maxShielding;
            int percentageShieldingLevel = (int) (shieldingLevelAsDouble*100);
            String shieldingInfo = "s = " + percentageShieldingLevel + "%";
            // display infos
            g2.drawString(shieldingInfo,X+rightshift,Y+1*downshift);                
        }        
    }

    // to draw a Robject component
    public void drawRobjectComponent(RobotComponent pRobotComponent, Graphics2D g2, int X0, int Y0, double facteurEchelleX, double facteurEchelleY, Font pFont)
    {
        RobotComponent theCurrentComponent = pRobotComponent;
        String componentName = theCurrentComponent.name;
        String componentShape = theCurrentComponent.shape;
        double componentSize = theCurrentComponent.size;
        Color componentColor = theCurrentComponent.color;
        g2.setPaint(componentColor);

        // if shape is disk, fill an oval 
        if ("disk".equalsIgnoreCase(componentShape))
        {
            double componentx = theCurrentComponent.positions.elementAt(0)[0];
            double componenty = theCurrentComponent.positions.elementAt(0)[1];
            int componentX = X0 + (int) Math.round(componentx * facteurEchelleX);
            int componentY = Y0 - (int) Math.round(componenty * facteurEchelleY);
            int componentGraphicalSize = (int) Math.round(componentSize * facteurEchelleX);
            int componentOvalSize = componentGraphicalSize;
            // need to compensate to center the circle since X,Y is a corner of a rectangle here
            g2.fillOval(componentX-componentOvalSize/2,componentY-componentOvalSize/2,componentOvalSize,componentOvalSize);
        }

        // if shape is circle, draw an oval
        if ("circle".equalsIgnoreCase(componentShape))
        {
            double componentx = theCurrentComponent.positions.elementAt(0)[0];
            double componenty = theCurrentComponent.positions.elementAt(0)[1];
            int componentX = X0 + (int) Math.round(componentx * facteurEchelleX);
            int componentY = Y0 - (int) Math.round(componenty * facteurEchelleY);
            int componentGraphicalSize = (int) Math.round(componentSize * facteurEchelleX);
            int componentOvalSize = componentGraphicalSize;
            // need to compensate to center the circle since X,Y is a corner of a rectangle here
            g2.drawOval(componentX-componentOvalSize/2,componentY-componentOvalSize/2,componentOvalSize,componentOvalSize);
        }

        // if shape is segment, draw a line (segment)
        if ("segment".equalsIgnoreCase(componentShape))
        {
            // first tip of the segment
            double segmentTip1x = theCurrentComponent.positions.elementAt(0)[0];
            double segmentTip1y = theCurrentComponent.positions.elementAt(0)[1];
            int segmentTip1X = X0 + (int) Math.round(segmentTip1x * facteurEchelleX);
            int segmentTip1Y = Y0 - (int) Math.round(segmentTip1y * facteurEchelleY);
            // second tip of the segment
            double segmentTip2x = theCurrentComponent.positions.elementAt(1)[0];
            double segmentTip2y = theCurrentComponent.positions.elementAt(1)[1];
            int segmentTip2X = X0 + (int) Math.round(segmentTip2x * facteurEchelleX);
            int segmentTip2Y = Y0 - (int) Math.round(segmentTip2y * facteurEchelleY);            
            g2.drawLine(segmentTip1X,segmentTip1Y,segmentTip2X,segmentTip2Y);
        }         
    }

    // displays the number rounded to the number of decimals
	public static double numberRoundedTo(double pDouble, int pNumDecimal)
	{
		double toReturn = pDouble;
		long utilArrondi = (long) Math.pow(10, pNumDecimal); // 100 gives 2 decimals, 10000 gives 4 decimals
		toReturn = Math.round(toReturn * utilArrondi);// rounded to the right decimal
		return toReturn / utilArrondi;
	}

	// displays the number as a percentage, rounded to the number of decimals
	public static double percentageRoundedTo(double pDouble, int pNumDecimal)
	{
		return numberRoundedTo(100d*pDouble, pNumDecimal);
	}

	private static void logIt(String msg)
	{
		java.util.Date d = new java.util.Date();
		java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss:SSS");
		String dateString = formatter.format(d);
		System.out.println("[RobotWorld][" + dateString + "]" + msg);
	}
}
