package route.circuit.resource;

import java.util.ArrayList;

public class InterposerSite {
	private final int column, row;
	private ArrayList<Chany> sllNodes;
    public InterposerSite(int column, int row) {
    	this.column = column;
        this.row = row;
    	this.sllNodes = new ArrayList<>();
    }
    
    public int getColumn() {
        return this.column;
    }
    public int getRow() {
        return this.row;
    }
    
    public boolean addInterposerNode(Chany sllNode) {
    	this.sllNodes.add(sllNode);
    	return true;
    }
	
    public RouteNode getInterposerNode(String dir){
    	for(Chany sllNode: this.sllNodes) {
    		if(sllNode.direction.equals(dir)) {
    			return (RouteNode) sllNode;
    		}
    	}
    	return null;
    }
    
    public String toString() {
    	return "[" + this.column + "," + this.row + "]\t";
    }
}
