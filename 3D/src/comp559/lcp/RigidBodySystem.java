package comp559.lcp;
//Agnes Liu 260713093 
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import mintools.parameters.BooleanParameter;
import mintools.parameters.DoubleParameter;
import mintools.swing.CollapsiblePanel;
import mintools.swing.VerticalFlowPanel;

/**
 * Maintains a list of RigidBody objects, and provides methods for collision processing and numerical integration
 * @author kry
 */
public class RigidBodySystem {
	public double imageWidth = 0.0;
	public double imageHeight =0.0;
	public String name ="";
//	broad phase
	public HashMap <Integer, ArrayList<RigidBody>> SpHash = new HashMap<Integer, ArrayList<RigidBody>>();
    public int numPerRow = 0;
    public int width = 0;
    public int[] SHvisitID = new int [1];
    
    public double simulationTime = 0;
    
	public ArrayList<RigidBody> bodies = new ArrayList<RigidBody>();
    
	public CollisionProcessor collisionProcessor = new CollisionProcessor(bodies,SpHash);
    
    public MouseSpringForce mouseSpring;
    
    BooleanParameter useGravity = new BooleanParameter( "enable gravity", true );
    
    DoubleParameter gravityAmount = new DoubleParameter( "gravitational constant", 1, -20, 20 );
    
    DoubleParameter gravityAngle = new DoubleParameter( "gravity angle", 90, 0, 360 );
    
    /**
     * Creates a new rigid body system
     */
    public RigidBodySystem() {
        /* do nothing */
    }
    
    /**
     * Adds a rigid body to the system 
     * @param body
     */
    public void add( RigidBody body ) {
        bodies.add( body );
    }
    public void SHinit(double w, double h) {
    	imageWidth = w;
    	imageHeight =h;
    	numPerRow = 3;
        width = (int)Math.ceil(imageWidth/numPerRow);
        SHvisitID = new int [numPerRow*numPerRow*100];
    }
    /**
     * Applies a small random acceleration to all bodies
     */
    public void jiggle() {
        final Random rand = new Random();
        for ( RigidBody b : bodies ) {
            if ( b.pinned ) continue;
            b.omega.x+=rand.nextDouble()*2-1;
            b.omega.y+=rand.nextDouble()*2-1;
            b.omega.z+=rand.nextDouble()*2-1;
            b.v.x += rand.nextDouble()*2-1;
            b.v.y += rand.nextDouble()*2-1;
            b.v.z += rand.nextDouble()*2-1;
        }
    }
    
    /** 
     * Time in seconds to advance the system
     */
    public double computeTime;
    
    /**
     * Total time in seconds for computation since last reset
     */
    public double totalAccumulatedComputeTime;
    
    /**
     * Advances the state of all rigid bodies
     * @param dt time step
     */
    public void advanceTime( double dt ) {
        long now = System.nanoTime();        
        mouseSpring.apply();
        // apply gravity to all bodies
        if ( useGravity.getValue() ) {
            Vector3d force = new Vector3d();
            for ( RigidBody b : bodies ) {
                double theta = gravityAngle.getValue() / 180.0 * Math.PI;
                force.set( Math.cos( theta ), Math.sin(theta),0 );
                force.scale( b.massLinear * gravityAmount.getValue() );
                // gravity goes directly into the accumulator!  no torque!
                b.force.add( force );
            }
        }
        
        if ( processCollisions.getValue() ) {
            // process collisions, given the current time step
        	collisionProcessor.SpHash = this.SpHash;
            collisionProcessor.processCollisions( dt );
        }
        // advance the system by the given time step
        for ( RigidBody b : bodies ) {
            b.advanceTime(dt);
	//         we identify its cell and add to the SpHash 
            if(collisionProcessor.SpatialHash.getValue()) {
            	int key = 0;
            	if(b.pinned) {
	        		for(Block bl : b.boundaryBlocks) {
	        			key = (int)(Math.floor(bl.j/width) + Math.floor(((bl.i-1)/width*numPerRow)));
	//        			if(SHvisitID[key]!=collisionProcessor.visitID) {
	//        				SpHash.remove(key);
	        			if (SpHash.containsKey(key)) {
		    				if(!SpHash.get(key).contains(b))
			    				SpHash.get(key).add(b);
			    		}else {
			    			ArrayList<RigidBody> temp = new ArrayList <RigidBody>();
			    			temp.add(b);
			    			SpHash.put(key,temp);
			    		}
			    		if(!b.bucketKey.contains(key))
			    			b.bucketKey.add(key);
	//		    		SHvisitID[key]=collisionProcessor.visitID;
	        		}
	        	/**if you wanna run factory, please comment this else block
	        	 * **
	        	 */
            	}else {
        			key = (int)(Math.floor(b.x.x/width) + Math.floor(((b.x.y-1)/width*numPerRow)));
//        			if(SHvisitID[key]!=collisionProcessor.visitID) {
//        				SpHash.remove(key);
        			if (SpHash.containsKey(key)) {
	    				if(!SpHash.get(key).contains(b))
		    				SpHash.get(key).add(b);
		    		}else {
		    			ArrayList<RigidBody> temp = new ArrayList <RigidBody>();
		    			temp.add(b);
		    			SpHash.put(key,temp);
		    		}
		    		if(!b.bucketKey.contains(key))
		    			b.bucketKey.add(key);
//		    		SHvisitID[key]=collisionProcessor.visitID;
            	}

	    	}
        }
        
        computeTime = (System.nanoTime() - now) / 1e9;
        simulationTime += dt;
        totalAccumulatedComputeTime += computeTime;
    }
    
    /**
     * Finds the body which has a block that intersects the provided point.
     * @param p
     * @return a body containing the given point
     */
    public RigidBody pickBody( Point3d p ) {
        for ( RigidBody body : bodies ) {
            if ( body.intersect( p ) ) {
                return body;
            }
        }
        return null;
    }
    
    /**
     * Removes a rigid body from the system
     * @param body
     */
    public void remove( RigidBody body ) {
        bodies.remove(body);
    }
    
    /** 
     * Resets the position of all bodies, and sets all velocities to zero
     */
    public void reset() {
        for ( RigidBody b : bodies ) {
            b.reset();
        }
        simulationTime = 0;
        collisionProcessor.reset();
        totalAccumulatedComputeTime = 0;        
    }
    
    /**
     * Removes all bodies from the system
     */
    public void clear() {
        bodies.clear();
        RigidBody.nextIndex = 0;
        reset();
    }
    
    /**
     * Draws all rigid bodies
     * @param drawable
     */
    public void display( GLAutoDrawable drawable ) {
        GL2 gl = drawable.getGL().getGL2();
        if ( Block.alpha != (float) (double) transparency.getValue()) {
            Block.alpha = (float) (double) transparency.getValue();
            // gross... need to rebuild display lists for the currently set transparency
            // which is potentially slow and bad news (memory thrashing) for openGL if we do this excessively!
            RigidBody.clearDisplayLists( gl );
            for ( RigidBody b : bodies ) {
                b.myListID = -1;
            }
        }        
        if ( drawBodies.getValue() ) {
            for ( RigidBody b : bodies ) {
            	System.out.println("b4");
                b.display( drawable );
                System.out.println("after");
            }
        }
        gl.glLineWidth(1);
        if ( drawBoundingVolumes.getValue() ) {
            for ( RigidBody b : bodies ) {
                b.root.boundingDisc.display(drawable);
            }
        }
        if ( drawAllBoundingVolumes.getValue() ) {
            for ( RigidBody b : bodies ) {
                b.root.display( drawable );
            }
        }        
        if ( drawBoundingVolumesUsed.getValue() ) {
            for ( RigidBody b : bodies ) {
                b.root.displayVisitBoundary( drawable, collisionProcessor.visitID );
            }
        }
        if ( drawContactGraph.getValue() ) {
            for ( Contact c : collisionProcessor.contacts ) {
                c.displayConnection(drawable);
            }
        }
        
        if ( drawContacts.getValue() ) {
            for ( Contact c : collisionProcessor.contacts ) {
                c.display(drawable);
            }
        }
        if ( drawCOMs.getValue() ) {
            for ( RigidBody b : bodies ) {
                b.displayCOM(drawable);
            }
        }        
    }

    private DoubleParameter transparency = new DoubleParameter("body block transparency", 1, 0, 1 );
    private BooleanParameter drawBodies = new BooleanParameter( "draw bodies", true );
    private BooleanParameter drawBoundingVolumes = new BooleanParameter( "draw root bounding volumes", false );
    private BooleanParameter drawAllBoundingVolumes = new BooleanParameter( "draw ALL bounding volumes", false );
    private BooleanParameter drawBoundingVolumesUsed = new BooleanParameter( "draw bounding volumes used", false );
    private BooleanParameter drawCOMs = new BooleanParameter( "draw center of mass positions", false );
    private BooleanParameter drawContacts = new BooleanParameter( "draw contact locations", false );
    private BooleanParameter drawContactGraph = new BooleanParameter( "draw contact graph", false );
    private BooleanParameter processCollisions = new BooleanParameter( "process collisions", true );
    
    /**
     * @return control panel for the system
     */
    public JPanel getControls() {
        VerticalFlowPanel vfp = new VerticalFlowPanel();
        vfp.setBorder( new TitledBorder("Rigid Body System Controls" ));
        
        VerticalFlowPanel vfpv = new VerticalFlowPanel();
        vfpv.setBorder( new TitledBorder("viewing controls") );
        vfpv.add( drawBodies.getControls() );
        vfpv.add( transparency.getSliderControls(false));
        vfpv.add( drawBoundingVolumes.getControls() );
        vfpv.add( drawAllBoundingVolumes.getControls() );
        vfpv.add( drawBoundingVolumesUsed.getControls() );
        vfpv.add( drawCOMs.getControls() );
        vfpv.add( drawContacts.getControls() );
        vfpv.add( drawContactGraph.getControls() );
        
        CollapsiblePanel cp = new CollapsiblePanel(vfpv.getPanel());
        cp.collapse();
        vfp.add( cp );
        
        vfp.add( processCollisions.getControls() );
        vfp.add( collisionProcessor.getControls() );
        
        vfp.add( useGravity.getControls() );
        vfp.add( gravityAmount.getSliderControls(false) );
        vfp.add( gravityAngle.getSliderControls(false) );
        return vfp.getPanel();
    }
    
}
