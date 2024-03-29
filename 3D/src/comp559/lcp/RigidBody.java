package comp559.lcp;
//Agnes Liu 260713093
import java.util.ArrayList;


import java.util.AbstractMap;
import java.util.HashMap;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import javax.vecmath.Color3f;
import javax.vecmath.Matrix3d;
import javax.vecmath.Matrix4d;

/**
 * Simple 2D rigid body based on image samples
 * @author kry
 */
public class RigidBody {

    /** Unique identifier for this body */
    public int index;
    /** storing lambda values of previous time step*/
    /** Variable to keep track of identifiers that can be given to rigid bodies */
    static public int nextIndex = 0;
//    public boolean isBox = false;
    public boolean isBox = true;
    /** Block approximation of geometry */
    ArrayList<Block> blocks;
    
    /** hashmap for in-contact body index*/
    HashMap<Point3d, Double[]> cHash =new HashMap<>();
    HashMap<Point3d, Double[]> nHash =new HashMap<>();
    HashMap<Point3d, Double[]> tHash1 = new HashMap<>();
    HashMap<Point3d, Double[]> tHash2 = new HashMap<>();
    
    /** Boundary blocks */
    ArrayList<Block> boundaryBlocks;
    ArrayList<Point3d> vertex;
    static float alpha;
    
    Color3f c = new Color3f();
    BVNode root;
    public Renderer rend= new Renderer();
    /** accumulator for forces acting on this body */
    Vector3d force = new Vector3d();
    
    /** accumulator for torques acting on this body */
    Vector3d torque = new Vector3d();
    Matrix3d torqhat = new Matrix3d();
    public int imw = 1000;
    public int imh = 1000;
    double massAngular;
    
    double massLinear;
        
    public boolean pinned;
    
    public ArrayList<Integer> bucketKey = new ArrayList<Integer>();
    public boolean sleep;
    /**
     * Transforms points in Body coordinates to World coordinates
     */
    RigidTransform transformB2W = new RigidTransform();
    
    /**
     * Transforms points in World coordinates to Body coordinates
     */
    RigidTransform transformW2B = new RigidTransform();
    
    /** linear velocity */
    public Vector3d v = new Vector3d();
    
    /** Position of center of mass in the world frame */
    public Point3d x = new Point3d();
    
    public Point3d r = new Point3d();
    
    /** initial position of center of mass in the world frame */
    Point3d x0 = new Point3d();
    
    /** orientation angle in radians */
    public Vector3d theta = new Vector3d();
    
    /**for gl drawing */
    public Vector3d rotAxis = new Vector3d();
    public double thed = 0.0;
    public double width;
    public double height;
    public double depth;
    
    /** angular velocity in radians per second */
    public Vector3d omega = new Vector3d();

    /** inverse of the linear mass, or zero if pinned */
    double minv;
    
    /** inverse of the angular mass, or zero if pinned */
    double jinv;
    
    /**
     * Creates a new rigid body from a collection of blocks
     * @param blocks
     * @param boundaryBlocks
     */
    public RigidBody( ArrayList<Block> blocks, ArrayList<Block> boundaryBlocks, int imw, int imh) {
    	this.imw = imw;
    	this.imh = imh;
        this.blocks = blocks;
        this.boundaryBlocks = boundaryBlocks;       
        // compute the mass and center of mass position        
        for ( Block b : blocks ) {
            double mass = b.getColourMass();
            massLinear += mass;            
            x0.x += b.j * mass;
            x0.y += b.i * mass; 
            x0.z += b.k * mass;
        }
        /**
         * added for load text
         */
        for ( Block b : boundaryBlocks ) {
            double mass = b.getColourMass();
            massLinear += mass;            
            x0.x += b.j * mass;
            x0.y += b.i * mass; 
        }
        
        x0.scale ( 1 / massLinear );
        // set block positions in world and body coordinates 
        for ( Block b : blocks ) {
            b.pB.x = b.j - x0.x;
            b.pB.y = b.i - x0.y;
            b.pB.z = b.k - x0.z;
        }
        /**
         * added for load text
         */
        for ( Block b : boundaryBlocks ) {
            b.pB.x = b.j - x0.x;
            b.pB.y = b.i - x0.y;
            b.pB.z = b.k - x0.z;
        }
        
        // compute the rotational inertia
        final Point3d zero = new Point3d(0,0,0);
        for ( Block b : blocks ) {
            double mass = b.getColourMass();
            massAngular += mass*b.pB.distanceSquared(zero);
        }
        /**
         * added for load text
         */
        for ( Block b : boundaryBlocks ) {
            double mass = b.getColourMass();
            massAngular += mass*b.pB.distanceSquared(zero);
        }
        // prevent zero angular inertia in the case of a single block
        if ( blocks.size() == 1 ) {
            Block b = blocks.get(0);
            double mass = b.getColourMass();
            massAngular = mass * (1+1+1)/12;
        }
        x.set(x0); 
        torqhat = new Matrix3d (0.0,-torque.z,torque.y,torque.z,0.0,-torque.x,-torque.y, torque.x,0.0);
        
        transformB2W.set( theta, x,torqhat);
        transformW2B.set( theta, x,torqhat);
        transformW2B.invert();
        //System.out.println("boundary blocks first pB "+boundaryBlocks.get(0).pB);
        
        root = new BVNode( boundaryBlocks, this );
        pinned = isAllBlueBlocks();
        
        if ( pinned ) {
            minv = 0;
            jinv = 0;
        } else {
            minv = 1/massLinear;
            jinv = 1/massAngular;
        }
        
        // set our index
        index = nextIndex++;
    }
    
    /**
     * Creates a copy of the provided rigid body 
     * @param body
     */
    public RigidBody( RigidBody body ) {
        blocks = body.blocks;
        boundaryBlocks = body.boundaryBlocks;
        massLinear = body.massLinear;
        massAngular = body.massAngular;
        x0.set( body.x0 );
        x.set( body.x );
        theta = body.theta;
        omega = body.omega;
        // we can share the blocks and boundary blocks...
        // no need to update them as they are in the correct body coordinates already        
        updateTransformations();
        // We do need our own bounding volumes!  can't share!
        root = new BVNode( boundaryBlocks, this );        
        pinned = body.pinned;
        sleep = body.sleep;
        minv = body.minv;
        jinv = body.jinv;
        // set our index
        index = nextIndex++;
    }
    /** vertex for drawing (and spatial hash?)
     */
    public void setMeas(ArrayList<Block> b) {
    	int xmin = 1000000;
    	int xmax = -100000;
    	int ymin = 1000000;
    	int ymax = -100000;	
    	int zmin = 1000000;
    	int zmax = -100000;
    	for (Block block :b) {
    		if (block.j>xmax) xmax = block.j;
    		if(block.j<xmin) xmin = block.j;
    		if(block.i>ymax) ymax = block.i;
    		if(block.i<ymin) ymin = block.i;
    		if(block.k>zmax) zmax = block.k;
    		if(block.k<zmin) zmin = block.k;
    	}
    	this.width = xmax-xmin;
    	this.height = ymax-ymin;
    	this.depth = zmax-zmin;
    }
    public ArrayList<Point3d> getVertex(ArrayList<Block> b){
    	ArrayList<Point3d> vert= new ArrayList<Point3d>();
    	int xmin = 1000000;
    	int xmax = -100000;
    	int ymin = 1000000;
    	int ymax = -100000;	
    	int zmin = 1000000;
    	int zmax = -100000;
    	for (Block block :b) {
    		if (block.j>xmax) xmax = block.j;
    		if(block.j<xmin) xmin = block.j;
    		if(block.i>ymax) ymax = block.i;
    		if(block.i<ymin) ymin = block.i;
    		if(block.k>zmax) zmax = block.k;
    		if(block.k<zmin) zmin = block.k;
    	}
    	vert.add(new Point3d(xmin,ymin,zmin));
    	vert.add(new Point3d(xmin,ymax,zmin));
    	vert.add(new Point3d(xmax,ymin,zmin));
    	vert.add(new Point3d(xmax,ymax,zmin));
    	vert.add(new Point3d(xmin,ymin,zmax));
    	vert.add(new Point3d(xmin,ymax,zmax));
    	vert.add(new Point3d(xmax,ymin,zmax));
    	vert.add(new Point3d(xmax,ymax,zmax));
    	return vert;
    }
    /**
     * Updates the B2W and W2B transformations
     */
    public void updateTransformations() {
        transformB2W.set( theta, x,torqhat);
        transformB2W.set( theta, x,torqhat );
        transformW2B.set( theta, x,torqhat );
        transformW2B.invert();
    }
    
    
    /**
     * Apply a contact force specified in world coordinates
     * @param contactPointW
     * @param contactForceW
     */
    public void applyContactForceW( Point3d contactPointW, Vector3d contactForceW ) {
        force.add( contactForceW );
        //obj1 TODO: Compute the torque applied to the body 
        //take z component of cross product: cz = rxfy-ryfx
        r.set(contactPointW.x - x.x, contactPointW.y - x.y,contactPointW.z -x.z);
        torque.cross(new Vector3d(r.x,r.y,r.z), force);
//        torque.set(  force.z * r.y - force.y * r.z,
//    			-force.z * r.x + force.x * r.z, 
//    			 force.y * r.x - force.x * r.y); 
        
        Vector3d tq = new Vector3d (torque);
        tq.normalize();
        torqhat.m01 = -tq.z;
        torqhat.m02 = tq.y;
        torqhat.m10 = tq.z;
        torqhat.m12 = -tq.x;
        torqhat.m20 = -tq.y;
        torqhat.m21 = tq.x; 

        
    }
    
    /**
     * Advances the body state using symplectic Euler, first integrating accumulated force and torque 
     * (which are then set to zero), and then updating position and angle.  The internal rigid transforms
     * are also updated. 
     * @param dt step size
     */
    public void advanceTime( double dt ) {
        if ( !pinned ) {            
            // TODO: fix here 
            omega.set(omega.x + dt*torque.x*jinv,
        			  omega.y + dt*torque.y*jinv, 
        			  omega.z + dt*torque.z*jinv);
        	theta.set(theta.x + dt*omega.x,
        			  theta.y + dt*omega.y,
        			  theta.z + dt*omega.z);

            // https://graphics.pixar.com/pbm2001/pdf/notesg.pdf
            v.x += 1.0 / massLinear * force.x * dt;
            v.y += 1.0 / massLinear * force.y * dt;
            v.z += 1.0 / massLinear * force.z * dt;
            x.x += v.x * dt;
            x.y += v.y * dt;
            x.z += v.z * dt;
//            rotAxis.set(torque);
            thed = transformB2W.getTheta(theta, torqhat);
            updateTransformations();
        }        
        force.set(0,0,0);
        torque.set(0,0,0);
    }
    
    /**
     * Computes the total kinetic energy of the body.
     * @return the total kinetic energy
     */
    public double getKineticEnergy() {
        return 0.5 * massLinear * v.lengthSquared() + 
        	   0.5 * massAngular * (omega.x * omega.x + omega.y * omega.y + omega.z * omega.z); 
    }
    
    /** 
     * Computes the velocity of the provided point provided in world coordinates due
     * to motion of this body.   
     * @param contactPointW
     * @param result the velocity
     */
    public void getSpatialVelocity( Point3d contactPointW, Vector3d result ) {
        result.sub( contactPointW, x );
        
        double xpart = -result.z*omega.y +  result.y*omega.z;
        double ypart =  result.z*omega.x + -result.x*omega.z;
        double zpart = -result.y*omega.x +  result.x*omega.y;
        
        result.set( xpart, ypart, zpart);
        result.add( v );
    }
    
    /**
     * Checks if all blocks are shades of blue
     * @return true if all blue
     */
    boolean isAllBlueBlocks() {
        for ( Block b : blocks ) {
            if ( ! (b.c.x == b.c.y && b.c.x < b.c.z) ) return false;
        }
        return true;
    }

    /**
     * Checks to see if the point intersects the body in its current position
     * @param pW
     * @return true if intersection
     */
    public boolean intersect( Point3d pW ) {
        if ( root.boundingDisc.isInDisc( pW ) ) {
            Point3d pB = new Point3d();
            transformW2B.transform( pW, pB );
            for ( Block b : blocks ) {
                if ( b.pB.distanceSquared( pB ) < Block.radius * Block.radius ) return true;
            }
        }
        return false;
    }
    
    /**
     * Resets this rigid body to its initial position and zero velocity, recomputes transforms
     */
    public void reset() {
        x.set(x0);        
        theta.set(0,0,0);
        v.set(0,0,0);
        omega.set(0,0,0);
        transformB2W.set( theta, x,torqhat);
        transformW2B.set( transformB2W );
        transformW2B.invert();
    }
    public void clearHashes() {
    	cHash.clear();
    	nHash.clear();
    	tHash1.clear();
    	tHash2.clear();
    }
    
    /** Map to keep track of display list IDs for drawing our rigid bodies efficiently */
    static private HashMap<ArrayList<Block>,Integer> mapBlocksToDisplayList = new HashMap<ArrayList<Block>,Integer>();
    
    /** display list ID for this rigid body */
    int myListID = -1;
    
    /**
     * Deletes all display lists.
     * This is called when clearing all rigid bodies from the simulation, or when display lists need to be updated due to 
     * changing transparency of the blocks.
     * @param gl
     */
    static public void clearDisplayLists( GL2 gl ) {
        for ( int id : mapBlocksToDisplayList.values() ) {
            gl.glDeleteLists(id, 1);
        }
        mapBlocksToDisplayList.clear();
    }
    
    /** 
     * Draws the blocks of a rigid body
     * @param drawable
     */
    public void display( GLAutoDrawable drawable ) {
    	System.out.println("displaying body"+this.index);
    	GL2 gl = drawable.getGL().getGL2();
    	GLU glu = new GLU();
    	float t = 1.0f;
    	if (!this.isBox) {
	        gl.glPushMatrix();
	        gl.glTranslated( x.x, x.y,x.z);
//	        System.out.println("torqhat is "+ this.torqhat.toString());
//	        System.out.println("theta value is "+thed);
//	        gl.glRotated(thed, rotAxis.x,rotAxis.y,rotAxis.z);
	        gl.glRotated(thed, torque.x,torque.y,torque.z);
	        if ( myListID == -1 ) {
	            Integer ID = mapBlocksToDisplayList.get(blocks);
	            if ( ID == null ) {
	                myListID = gl.glGenLists(1);
	                gl.glNewList( myListID, GL2.GL_COMPILE_AND_EXECUTE );
	                for ( Block b : blocks ) {
	                    b.display( drawable );
	                }
	                gl.glEndList();
	                mapBlocksToDisplayList.put( blocks, myListID );
	            } else {
	                myListID = ID;
	                gl.glCallList(myListID);
	            }
	        } else {
	            gl.glCallList(myListID);
	        }
	        gl.glPopMatrix();
    	}
    	else {
    		gl.glPushMatrix();
    		this.setMeas(this.boundaryBlocks);
    		this.c = this.blocks.get(0).c;
            rend.resize(drawable, imw*5, imh*5);
            rend.init(drawable);
//	        float offset = 1.0f;
//		    rend.t += offset;
	        rend.display(drawable,this, imw*5,imh*5);
	        gl.glPopMatrix();
    	}
    }
    
    static public double kineticEnergyThreshold = 1e-6;
    
    /**
     * Draws the center of mass position with a circle.  The circle will be 
     * drawn differently if the block is at rest (i.e., close to zero kinetic energy)
     * @param drawable
     */
    public void displayCOM( GLAutoDrawable drawable ) {
        GL2 gl = drawable.getGL().getGL2();
        if ( getKineticEnergy() > kineticEnergyThreshold ) {
            gl.glPointSize(8);
            gl.glColor3f(0,0,0.7f);
            gl.glBegin( GL.GL_POINTS );
            gl.glVertex3d(x.x, x.y,x.z);
            gl.glEnd();
            gl.glPointSize(4);
            gl.glColor3f(1,1,1);
            gl.glBegin( GL.GL_POINTS );
            gl.glVertex3d(x.x, x.y,x.z);
            gl.glEnd();
        } else {
            gl.glPointSize(8);
            gl.glColor3f(0,0,0.7f);
            gl.glBegin( GL.GL_POINTS );
            gl.glVertex3d(x.x, x.y,x.z);
            gl.glEnd();
            gl.glPointSize(4);
            gl.glColor3f(0,0,1);
            gl.glBegin( GL.GL_POINTS );
            gl.glVertex3d(x.x, x.y,x.z);
            gl.glEnd();
        }
    }
    
    
}