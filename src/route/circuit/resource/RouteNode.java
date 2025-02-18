package route.circuit.resource;

import java.nio.file.FileSystemNotFoundException;
import java.util.ArrayList;
import java.util.List;

import route.route.RouteNodeData;

public abstract class RouteNode implements Comparable<RouteNode> {
	protected final int index;//Unique index number
	
	public final short xlow, xhigh;
	public final short ylow, yhigh;
	public final float centerx, centery;
	public final short n;
	
	public final String direction;
	public float delay;
	public final float r;
	public final float c;
	
	public final float base_cost;
	public float ortho_base_cost;
	public float ortho_inv_length = 0;
	public float inv_lngth = 0;
	
	public boolean isOpin;
	
	public final RouteNodeType type;
	public final boolean isWire;
	public final short capacity;
	
	public int numChildren;
	public RouteNode[] children;
	public RouteSwitch[] switches;
	
	public boolean isSLL = false;
	public RouteNode interposerNode;

	public final IndexedData indexedData;
	public final RouteNodeData routeNodeData;
	
	public int currentIndex;
	public boolean target;
	
	public RouteNode(int index, int xlow, int xhigh, int ylow, int yhigh, int n, int capacity, RouteNodeType t, float r, float c, IndexedData indexedData, String direction, int numChildren) {
		this.index = index;
		
		this.isOpin = false;
		
		this.xlow = (short) xlow;
		this.xhigh = (short) xhigh;
		this.ylow = (short) ylow;
		this.yhigh = (short) yhigh;
		
		this.centerx = 0.5f * (this.xlow + this.xhigh);
		this.centery = 0.5f * (this.ylow + this.yhigh);
		
		this.indexedData = indexedData;
		this.routeNodeData = new RouteNodeData(this.index);
		
		this.n = (short) n;
		this.currentIndex = 0;
		this.type = t;
		if(this.type == RouteNodeType.CHANX || this.type == RouteNodeType.CHANY) {
			this.isWire = true;
		} else {
			this.isWire = false;
		}
		this.capacity = (short) capacity;
		
		this.r = r;
		this.c = c;
		this.delay = -1;
		
		this.interposerNode = null;
		if(this.isWire) {
			this.base_cost = this.indexedData.getBaseCost();
		} else if (this.type == RouteNodeType.OPIN) {
			this.base_cost = this.indexedData.getBaseCost();
		} else {
			this.base_cost = this.indexedData.getBaseCost();
		}
		
		this.numChildren = numChildren;
		this.children = new RouteNode[this.numChildren]; 
		this.switches = new RouteSwitch[this.numChildren]; 
		this.direction = direction;
		this.target = false;
	}
	
	public RouteNode(int index, int xlow, int xhigh, int ylow, int yhigh, int n, int capacity, RouteNodeType t, float r, float c, IndexedData indexedData, String direction) {
		this.index = index;
		
		this.isOpin = false;
		
		this.xlow = (short) xlow;
		this.xhigh = (short) xhigh;
		this.ylow = (short) ylow;
		this.yhigh = (short) yhigh;
		
		this.centerx = 0.5f * (this.xlow + this.xhigh);
		this.centery = 0.5f * (this.ylow + this.yhigh);
		
		this.indexedData = indexedData;
		this.routeNodeData = new RouteNodeData(this.index);
		
		this.n = (short) n;
		this.currentIndex = 0;
		this.type = t;
		if(this.type == RouteNodeType.CHANX || this.type == RouteNodeType.CHANY) {
			this.isWire = true;
		} else {
			this.isWire = false;
		}
		this.capacity = (short) capacity;
		
		this.r = r;
		this.c = c;
		this.delay = -1;
		
		this.interposerNode = null;
		if(this.isWire) {

			this.base_cost = this.indexedData.getBaseCost();
		} else if (this.type == RouteNodeType.OPIN) {

			this.base_cost = this.indexedData.getBaseCost();
		} else {
			this.base_cost = this.indexedData.getBaseCost();
		}
		this.direction = direction;
		this.target = false;
	}
	
	public void setNumChildren(int numChildren) {
		this.numChildren = numChildren;
		this.children = new RouteNode[this.numChildren];
		this.switches = new RouteSwitch[this.numChildren];
	}
	public void setChild(int index, RouteNode child) {
		this.children[index] = child;
		if(this.currentIndex <= this.numChildren) {
			this.currentIndex++;
		}else {
			System.err.print("\nExtra children!!");
		}
	}
	
	public void setNewChild(int index, RouteNode child) {
		this.children[index] = child;
	}
	public void removeChild(int index) {
		this.children[index] = null;
	}
	
	public void removeChild(RouteNode child) {
		for(int i = 0; i < this.numChildren; i++) {
			if(this.children[i] == child) {
				this.children[i] = null;
			}
		}
	}
	public boolean getSLLStatus() {
		return this.isSLL;
	}
	public void setAsSLL() {
		this.isSLL = true;
	}

	public void setSwitchType(int index, RouteSwitch routeSwitch) {
		this.switches[index] = routeSwitch;
	}
	

	
	public RouteNode[] getChildren() {
		return this.children;
	}
	
	public void setInterposerNode(RouteNode InterposerNode) {
		this.interposerNode = InterposerNode;
	}
	
	public RouteNode getInterposerNode() {
		return this.interposerNode;
	}
	public int getNumChildren() {
		return this.numChildren;
	}
	
	public int wireLength() {
		int length = this.xhigh - this.xlow + this.yhigh - this.ylow + 1;
		if(length <= 0) System.err.println("The length of wire with type " + this.type + " is equal to " + length + " the node id is " + this.index );
		
		return length;
	}
	
	@Override
	public int compareTo(RouteNode o) {
		int r = this.type.compareTo(o.type);
		if (this == o)
			return 0;
		else if (r < 0)
			return -1;
		else if (r > 0)
			return 1;
		else if(this.xlow < o.xlow)
			return -1;
		else if (this.xhigh > o.xhigh)
			return 1;
		else if (this.ylow < o.ylow)
			return -1;
		else if (this.yhigh > o.yhigh)
			return 1;
		else if (this.index < o.index)
			return -1;
		else if (this.index > o.index)
			return 1;
		else 
			return Long.valueOf(this.hashCode()).compareTo(Long.valueOf(o.hashCode()));
	}
	
	@Override
	public String toString() {
		
		String index = "" + this.index;
		while(index.length() < 10) index = "0" + index;
		
		String coordinate = "";
		if(this.xlow == this.xhigh && this.ylow == this.yhigh) {
			coordinate = "(" + this.xlow + "," + this.ylow + ")";
		} else {
			coordinate = "(" + this.xlow + "," + this.ylow + ") to (" + this.xhigh + "," + this.yhigh + ")";
		}
		
		StringBuilder s = new StringBuilder();
		s.append("RouteNode " + index + " ");
		s.append(String.format("%-11s", coordinate));
		s.append(String.format("ptc_num = %3d", this.n));
		s.append(", ");
		s.append(String.format("basecost = %.2e", this.base_cost));
		s.append(", ");
		s.append(String.format("capacity = %2d", this.capacity));
		s.append(", ");
		s.append(String.format("occupation = %2d ", this.routeNodeData.occupation));
		s.append(", ");
		s.append(String.format("num_unique_sources = %2d ", this.routeNodeData.numUniqueSources()));
		s.append(", ");
		s.append(String.format("num_unique_parents = %2d ", this.routeNodeData.numUniqueParents()));
		s.append(", ");
		s.append(String.format("type = %s", this.type));
		s.append(", ");
		s.append(String.format("direction = %s", this.direction));
		
		return s.toString();
	}
	
	public boolean overUsed() {
		return this.capacity < this.routeNodeData.occupation;
	}
	public boolean used() {
		return this.routeNodeData.occupation > 0;
	}
	public boolean illegal() {
		return this.capacity < this.routeNodeData.numUniqueParents();
	}
	
	public float getDelay() {
		return this.delay;
	}
	
	public int getOccupation() {
		return this.routeNodeData.occupation;
	}
	public void updatePresentCongestionPenalty(float pres_fac) {
		RouteNodeData data = this.routeNodeData;
		
		int occ = data.numUniqueSources();
		int cap = this.capacity;
		
		if (occ < cap) {
			data.pres_cost = 1;
		} else {
			data.pres_cost = 1 + (occ - cap + 1) * pres_fac;
		}

		data.occupation = occ;
	}
	
	public void setDelay(RouteSwitch drivingRouteSwitch) {
		if(this.type == RouteNodeType.SOURCE || this.type == RouteNodeType.SINK) {
			this.delay = 0;
		} else if(this.getSLLStatus()){
			float cload = this.c + (drivingRouteSwitch.cout/2);
			this.delay = (float) (drivingRouteSwitch.tdel + (cload * drivingRouteSwitch.r) + (cload * (0.5f * this.r)) + 1e-9);
		}else{
			
			this.delay = (float) (drivingRouteSwitch.tdel + ((this.c + drivingRouteSwitch.cout) * (drivingRouteSwitch.r + 0.5f * this.r)));

		}
	}
	
	public void setDelay() {
		if(this.getSLLStatus()) {
			this.delay = (float) (this.c * this.r * 0.5f + 1000e-12);
		}else {
			this.delay = this.c * this.r * 0.5f;
		}
		
	}
	
	@Override
	public int hashCode() {
		return this.index;
	}
	
	@Override 
	public boolean equals(Object other) {
		if (other == null) return false;
		if (other == this) return true;
		if (!(other instanceof RouteNode))return false;
		RouteNode routeNode = (RouteNode)other;
		return routeNode.index == this.index;
	}
}
