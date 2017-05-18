package view.drawables;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;

public class Ball extends Drawable{

	private static int LEFT = -1;		//gibt die Drehrichtung von Ball im Orbit um einen Node an.
	private static int RIGHT = 1;		//je nach Richtung werden all Drehungen mit -1/+1 multipliziert -> keine Abfragen mehr
	
	private static double radius = 1;	//Radius fuer Ball, eig Groesse wird mit setScale() gemacht
	private double speed;				//Geschwindigkeit in Pixeln, mit der sich Ball pro Frame vorbewegt
	private int fWidth;

	private boolean isInOrbit;			//ist Leertaste gedrueckt und bald wird ein Node umkreist?
	private boolean isSpinning;			//Ball dreht sich um einen Node (in seinem Orbit)?
	private boolean hasCrashed;			//Ball kolliediert nach Spielregeln mit anderem Objekt?

	private Node lastNode;				//haelt fest, um welchen Node sich Ball gerade dreht/zuletzt drehte
	private int spinDirection;			//Vorfaktor, der bestimmt, ob Ball nach links/rechts dreht
	private double spinRadius;			//Radius von Ball zu seinem Node in einem Orbit
	private double spinAddition;		//Grad, um die sich Ball jedes Mal in einem Orbit drehen wird
	
	//wenn Ball ausserhalb Spielfelds einen Orbit leavt, soll Player  Schutzzeit zum neu Orbit Joinen bekommen
	private Long crashTimer;				//wann wurde Orbit geleavt, zum Vergleichen bei kommenden Crashes
	private int crashBuffer;				//Intervall zum neu Joinen
	
	public Ball(int layer, Color color, int fWidth) {

		super(createShape(), layer);
		setColor(color);
		setPos(fWidth/2, 0);
		setRotation(Math.PI * 3/2);
		setScale(fWidth/40.0, fWidth/40);
	
		this.fWidth = fWidth;
		
		speed = 0;
		isInOrbit = false;
		isSpinning = false;
		hasCrashed = false;
		lastNode = null;
		crashTimer = new Long(0);
		crashBuffer = 150;
	}

	private static Area createShape() {
		Area shape = 	 new Area(new Ellipse2D.Double	(-radius, -radius, 2*radius, 2*radius));
		shape.subtract	(new Area(new Ellipse2D.Double	(-radius*2/3, -radius*2/3, radius * 4/3, radius * 4/3)));
		shape.add		(new Area(new Ellipse2D.Double	(-radius/3, -radius/3, radius*2/3, radius*2/3)));
		shape.add		(new Area(new Rectangle2D.Double(-radius, -radius, radius, 2*radius)));
		shape.subtract	(new Area(new Rectangle2D.Double(-radius, -radius, radius*2/3, 2*radius)));
		return shape;
		
		/*Area shape = 	 new Area(new Ellipse2D.Double	(-6, -6, 12, 12));
		shape.subtract	(new Area(new Ellipse2D.Double	(-4, -4,  8, 8)));
		shape.add		(new Area(new Ellipse2D.Double	(-2, -2,  4, 4)));
		shape.add		(new Area(new Rectangle2D.Double(-6, -6,  6, 12)));
		shape.subtract	(new Area(new Rectangle2D.Double(-6, -6,  4, 12)));
		return shape;*/
	}
	
	public double getSpeed() {return speed;}
	public Node getNode() {return lastNode;}

	public void setSpeed(double speed) {this.speed = speed;}

	public boolean isInOrbit() {return isInOrbit;}
	public boolean isSpinning() {return isSpinning;}
	public boolean hasCrashed() {return hasCrashed;}

	public double getSpinRadius() {
		if(isSpinning)
			return spinRadius;
		else
			return 0;
	}
	
	/* Ball muss irgendwie abschaetzen, welcher naechste Node zum andocken ist
	 * dazu werden Diszanz zu Node(hypotenuse) & Richtung zu Node(alpha) verglichen; 50:50 Einfluss
	 * wenn Ball vertikal zu Node staende(opposite), duerften sie auch sich nicht beruehren
	 */
	public void enterOrbit(ArrayList<Node> visibleNodes) {
		
		if(isInOrbit == true || visibleNodes.isEmpty())
			return;
		
		ArrayList<Node> availableNodes = new ArrayList<>();
		double alpha, adjacent, opposite, hypotenuse, proportion;
		double crashDistance = fWidth;
		
		double maxCatchDist = fWidth;
		double maxSpinRadius = fWidth;
		
		//finde heraus, welche Nodes erreichbar sind
		for(Node n : visibleNodes) {
			
			//Blickwinkel zu Node
			alpha = getInternalAngle(getAngleTo(n.getPos()) - getRotation());
			//Distanz dazu
			hypotenuse = getPos().distance(n.getPos());
			//Distanz zu Node wenn parralel dazu
			opposite = Math.abs(Math.sin(alpha)) * hypotenuse;
			//Distanz geradeaus bis parralel zu Node
			adjacent = Math.abs(Math.cos(alpha)) * hypotenuse;
			//tada ein rechtwinkliges Dreieck
			
			//ist Node zu weit weg? dann ist er auch nicht crash-relevant
			if(hypotenuse > maxCatchDist || opposite > maxSpinRadius)
				continue;
			
			//wuerde Ball beim Umkreisen nicht crashen?
			if(opposite > radius*getScale().getX() + n.getRadius())
				availableNodes.add(n);
			//wenn doch, halte die kleinste Crash-Distanz fest
			else if(adjacent < crashDistance) {
				n.setColor(Color.RED);
				crashDistance = adjacent;
			}
		}

		Node nextNode = null;
		double nextProportion = 2*fWidth;	//max
		
		//fische den naehesten Node raus
		for(Node n : availableNodes) {
			
			alpha = getInternalAngle(getAngleTo(n.getPos()) - getRotation());
			hypotenuse = getPos().distance(n.getPos());
			adjacent = Math.cos(alpha) * hypotenuse;

			//ist der Weg zum Node frei von anderen Nodes?
			if(adjacent > crashDistance)
				continue;
			
			//vergleiche Blickrichtung mit Distanz; 50:50 Gewichtung
			proportion = hypotenuse + alpha * (fWidth / Math.PI);

			if(lastNode == null || proportion < nextProportion) {
				nextNode = n;
				nextProportion = proportion;
			}
		}
		
		if(nextNode == null)
			if(lastNode != null && lastNode.isVisible() && getPos().distance(lastNode.getPos()) < fWidth/4) {
				nextNode = lastNode;
				System.out.println("weellll");
			}else
				return;
		
		//teste, ob Node lings/rechts von Ball ist -> setzte Drehrichtung
		double angleToNode = getAngleTo(nextNode.getPos()) - getRotation();
		
		if(angleToNode > -Math.PI && angleToNode < 0 ||
		   angleToNode >  Math.PI && angleToNode < 2*Math.PI)
			spinDirection = LEFT;
		else
			spinDirection = RIGHT;
		
		lastNode = nextNode;
		isInOrbit = true;
	}

	public void leaveOrbit(ArrayList<Drawable> solids) {
		isInOrbit = false;
		isSpinning = false;
		lastNode.disconnect();
		
		//wenn Ball ausserhalb Spielfelds Orbit leavt, wird crashTimer geupdated
		if(getPos().getX() < 40 - radius/2 || getPos().getX() > fWidth - 40)		//40 ist die Dicke der Mauer, hab grad keine Variable dafuer
			crashTimer = System.currentTimeMillis();
	}
	
	public void update(ArrayList<Drawable> solids) {
		
		if(isCrashing(solids)) {
			hasCrashed = true;
			return;
		}
		
		if(isInOrbit && !isSpinning) {
			
			double angleToNode = getAngleTo(lastNode.getPos()) - getRotation();

			if(angleToNode > -Math.PI * 5/2 && angleToNode < -Math.PI * 3/2 ||
			   angleToNode > -Math.PI / 2	&& angleToNode <  Math.PI/2 ||
			   angleToNode >  Math.PI * 3/2 && angleToNode <  Math.PI * 5/2)
				return;
			
			isSpinning  = true;
			spinRadius = getPos().distance(lastNode.getPos());
			
			//cos Alpha(Winkel zum Node) = Hypotenuse(spinRadius) / Gegenkathete(speed/2)
			//180� - Alpha, da sich Ball nur um Gegenwinkel von berechnetem Winkel dreht
			//Ball kommt schraeg aus einer Richtung, geht schraeg in andere Richtung -> 2xAlpha
			spinAddition = Math.PI - 2*(Math.acos(speed/2 / spinRadius));
			setRotation(getAngleTo(lastNode.getPos()) - spinDirection * (Math.PI/2));
			//da sich Ball immer nur um spinAddition drehen wird, muss Ausgangsposition wie spinAddition/2 zu Node sein
			rotate(-spinDirection * spinAddition/2);
			//drehe Node auf Winkelgeschwindigkeit von Ball an
			lastNode.setSpinSpeed(spinDirection * 2*Math.asin(speed/2 / spinRadius));
			lastNode.connect();
		}
	}
	
	public void move2() {
		
		if(isSpinning)
			spin();
		else
			move();
	}
	
	//bewegt Ball um angegebene Distanz in Blickrichtung (rotation)
	private void move() {
		setPos(getPos().getX() + Math.cos(getRotation()) * speed,
			   getPos().getY() + Math.sin(getRotation()) * speed);
	}
	
	//koordiniert die Bewegung in Node Orbit
	private void spin() {
		//drehe Ball minimal im Kreis: je nach Drehrichtung (*1/*-1)
		rotate(spinAddition * spinDirection);
		move();
	}
	
	//ueberpruefe, ob nach Spielregeln Crash vorliegt
	private boolean isCrashing(ArrayList<Drawable> solids) {
		
		//gehe all soliden Objekte durch
		for(Drawable d : solids) {
			//nur wenn es Ueberschneidungen der Shapes gibt
			if(!intersects(d.getShape()))
				continue;
			//wenn Ball geschuetzt crashe trotzdem an allen Nodes ausser dem Umkreisten
			if(d instanceof Node && !d.equals(lastNode))
				return true;
			//andere Objekte sind, wenn Ball in Orbit, nicht wichtig
			if(isInOrbit())
				continue;
			//wenn Ball nicht geschuetzt ist -> alles crash
			if(crashTimer + crashBuffer < System.currentTimeMillis())
				return true;
		}	
		
		//wenn nicht in einem Orbit und Schutzzeit abgelaufen oder Ball fliegt nach nur nach unten
		if(!isInOrbit && crashTimer + crashBuffer < System.currentTimeMillis())
			//und Ball ausserhalb des Spielfelds -> crash
			if(getPos().getX() < -40 + radius/2 || 
			   getPos().getX() > fWidth + 40 - radius/2 ||
			   getPos().getY() > 2*fWidth)
				return true;
		
		return false;
	}
	
	//ueberpruefe ob Ball anderes Shape schneidet -> BOOOM!
	private boolean intersects(Shape shapeB) {
		Area areaA = new Area(getShape());
		areaA.intersect(new Area(shapeB));
		
		return !areaA.isEmpty();
	}
	
	//berechnet Winkel von eigenem Mittelpunkt zu 2. Punkt
	private double getAngleTo(Point2D p2) {
		double deltaX = p2.getX() - getPos().getX();
		double deltaY = p2.getY() - getPos().getY();
		double angle = Math.atan(deltaY / deltaX);
		
		if(deltaX < 0)			//Tangens-Korrektur
			angle += Math.PI;
		else if(deltaY < 0)
			angle += 2*Math.PI;
		
		return angle;
	}
		
		//mache aus Parameter einen Winkel >0� und <180� wie ein Innenwinkel ines Dreiecks
	private double getInternalAngle(double angle) {
		double internalAngle = angle;
		
		if(angle < 0)
			internalAngle *= -1;
		if(angle > Math.PI)
			internalAngle = 2*Math.PI - internalAngle;
		
		return internalAngle;
	}
}
