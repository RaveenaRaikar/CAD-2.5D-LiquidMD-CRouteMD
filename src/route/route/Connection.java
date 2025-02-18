package route.route;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import route.circuit.pin.GlobalPin;
import route.circuit.resource.Opin;
import route.circuit.resource.ResourceGraph;
import route.circuit.resource.RouteNode;
import route.circuit.resource.InterposerSite;
import route.circuit.timing.TimingEdge;
import route.circuit.timing.TimingNode;


public class Connection implements Comparable<Connection>  {
	public final int id;//Unique ID number
    
	public final GlobalPin source;
	public final GlobalPin sink;
	
	private final TimingNode sourceTimingNode;
	private final TimingNode sinkTimingNode;
	private final TimingEdge timingEdge;
	private float criticality;
	
	public final int dieNum;
    public Net net;
    public final int boundingBox;
	
	public final String netName;
	
	public final RouteNode sourceRouteNode;
	public final RouteNode sinkRouteNode;
	
	private Queue<RouteNode> sllQueue;
	public int xCoordSLL, yCoordSLL;
	
	public final RouteNode sllWireNode;
	public boolean netTocheck = false;
	private boolean isCrossingSLL = false;
	public boolean touchSLL = false;
	public final List<RouteNode> routeNodes;
	
	public Connection(int id, GlobalPin source, GlobalPin sink, int dieNum) {
		this.id = id;
		this.source = source;
		String sourceName = null;
		if(this.source.getPortType().isEquivalent()) {
			sourceName = this.source.getPortName();
		}else {
			sourceName = this.source.getPortName() + "[" + this.source.getIndex() + "]";
		}
		this.sourceRouteNode = this.source.getOwner().getSiteInstance().getSource(sourceName);
		
		if(!source.hasTimingNode()) System.err.println(source + " => " + sink + " | Source " + source + " has no timing node");
		this.sourceTimingNode = this.source.getTimingNode();
		this.dieNum = dieNum;
		this.sink = sink;
		String sinkName = null;
		if(this.sink.getPortType().isEquivalent()) {
			sinkName = this.sink.getPortName();
		}else{
			sinkName = this.sink.getPortName() + "[" + this.sink.getIndex() + "]";
		}
		this.sinkRouteNode = this.sink.getOwner().getSiteInstance().getSink(sinkName);

		if(!sink.hasTimingNode()) System.out.println(source + " => " + sink + " | Sink " + sink + " has no timing node");
		this.sinkTimingNode = this.sink.getTimingNode();
		//Timing edge of the connection
		if(this.sinkTimingNode.getSourceEdges().size() != 1) {
			System.err.println("The connection should have only one edge => " + this.sinkTimingNode.getSourceEdges().size());
		}
		if(this.sourceTimingNode != this.sinkTimingNode.getSourceEdge(0).getSource()) {
			System.err.println("The source and sink are not connection by the same edge");
		}
		this.timingEdge = this.sinkTimingNode.getSourceEdge(0);
		
		//Bounding box
		this.boundingBox = this.calculateBoundingBox();
		this.sllWireNode = null;
		
		//Route nodes
		this.routeNodes = new ArrayList<>();
		
		//This queue will hold the route nodes for intermediate routing - source to sllwire;
		this.sllQueue = new LinkedList<>();
		//Net name
		this.netName = this.source.getNetName();
				
		this.net = null;
	}
	
	public Connection(int id, GlobalPin source, GlobalPin sink, int[] sllCoordinate, ResourceGraph rrg, int dieNum) {
		this.id = id;

		this.isCrossingSLL = true;
		this.xCoordSLL = sllCoordinate[0];
		this.yCoordSLL = sllCoordinate[1];
		//Source
		this.source = source;
		String sourceName = null;
		if(this.source.getPortType().isEquivalent()) {
			sourceName = this.source.getPortName();
		}else {
			sourceName = this.source.getPortName() + "[" + this.source.getIndex() + "]";
		}
		this.sourceRouteNode = this.source.getOwner().getSiteInstance().getSource(sourceName);
		
		if(!source.hasTimingNode()) System.err.println(source + " => " + sink + " | Source " + source + " has no timing node");
		this.sourceTimingNode = this.source.getTimingNode();
		this.dieNum = dieNum;
		//Sink
		this.sink = sink;
		String sinkName = null;
		if(this.sink.getPortType().isEquivalent()) {
			sinkName = this.sink.getPortName();
		}else{
			sinkName = this.sink.getPortName() + "[" + this.sink.getIndex() + "]";
		}
		this.sinkRouteNode = this.sink.getOwner().getSiteInstance().getSink(sinkName);

		if(!sink.hasTimingNode()) System.out.println(source + " => " + sink + " | Sink " + sink + " has no timing node");
		this.sinkTimingNode = this.sink.getTimingNode();
		//Timing edge of the connection
		if(this.sinkTimingNode.getSourceEdges().size() != 1) {
			System.err.println("The connection should have only one edge => " + this.sinkTimingNode.getSourceEdges().size());
		}
		if(this.sourceTimingNode != this.sinkTimingNode.getSourceEdge(0).getSource()) {
			System.err.println("The source and sink are not connection by the same edge");
		}
		this.timingEdge = this.sinkTimingNode.getSourceEdge(0);
		  

		InterposerSite sllSite = rrg.getInterposerSite(this.xCoordSLL, this.yCoordSLL);

		String dir;
		if(source.getOwner().getRow() < sink.getOwner().getRow()) {
			dir = "INC_DIR";
		}else {
			dir = "DEC_DIR";
		}
		
		this.sllWireNode = sllSite.getInterposerNode(dir);
		

		//Bounding box
		this.boundingBox = this.calculateBoundingBox();
		
		//Route nodes
		this.routeNodes = new ArrayList<>();
		
		//Net name
		this.netName = this.source.getNetName();
				
		this.net = null;
	}
	
	public void connTouchedSLL() {
		this.touchSLL = true;
	}
	public boolean getConnSLLStatus() {
		return this.touchSLL;
	}
	
	public void setNetToCheck() {
		this.netTocheck = true;
	}
	public boolean isCrossingSLL() {
		return this.isCrossingSLL;
	}
	//add another type of connection that only accepts 1 global pin input. In this case the other end of the connection depends on the 
	//the placer results.
	private int calculateBoundingBox() {
		int min_x, max_x, min_y, max_y;
		
		int sourceX = this.source.getOwner().getColumn();
		int sinkX = this.sink.getOwner().getColumn();
		if(sourceX < sinkX) {
			min_x = sourceX;
			max_x = sinkX;
		} else {
			min_x = sinkX;
			max_x = sourceX;
		}
		
		int sourceY = this.source.getOwner().getRow();
		int sinkY = this.sink.getOwner().getRow();
		if(sourceY < sinkY) {
			min_y = sourceY;
			max_y = sinkY;
		} else {
			min_y = sinkY;
			max_y = sourceY;
		}
		
		return (max_x - min_x + 1) + (max_y - min_y + 1);
	}
	
	public void setNet(Net net) {
		this.net = net;
	}

	public boolean isInBoundingBoxLimit(RouteNode node) {
		return node.xlow < this.net.x_max_b && node.xhigh > this.net.x_min_b && node.ylow < this.net.y_max_b && node.yhigh > this.net.y_min_b;
	}
	
	public void addRouteNode(RouteNode routeNode) {
		this.routeNodes.add(routeNode);
	}
	
	public void addNodesToSLL(RouteNode routeNode) {
		this.sllQueue.add(routeNode);
	}
	
	public Queue<RouteNode> getSLLQueue(){
		return this.sllQueue;
	}
	public void resetConnection() {
		this.routeNodes.clear();
	}
	
	public void setWireDelay(float wireDelay) {
		this.timingEdge.setWireDelay(wireDelay);
	}
	public void calculateCriticality(float maxDelay, float maxCriticality, float criticalityExponent) {
		this.timingEdge.calculateCriticality(maxDelay, maxCriticality, criticalityExponent);
		
		this.criticality = this.timingEdge.getCriticality();
	}
	public void setCriticality() {
		this.criticality = 1;
	}
	public void resetCriticality() {
		this.timingEdge.resetCriticality();
	}
	public TimingNode getSinkNode() {
		return this.sinkTimingNode;
	}
	
	public float getCriticality() {
		return this.criticality;
	}
	
	@Override
	public String toString() {
		return this.id + "_" + this.netName;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null) return false;
	    if (!(o instanceof Connection)) return false;
	   
	    Connection co = (Connection) o;
		if(this.id == co.id){
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		return this.id;
	}
	
	@Override
	public int compareTo(Connection other) {
		if(this.id > other.id) {
			return 1;
		} else {
			return -1;
		}
	}
	
	public boolean congested() {
		for(RouteNode rn : this.routeNodes){
			if(rn.overUsed()) {
				return true;
			}
		}
		return false;
	}
	
	public boolean illegal() {
		for(RouteNode rn : this.routeNodes){
			if(rn.illegal()) {
				return true;
			}
		}
		return false;
	}
	
	public Opin getOpin() {
		if(this.routeNodes.isEmpty()) {
			return null;
		} else {
			return (Opin) this.routeNodes.get(this.routeNodes.size() - 2);
		}
	}
	public int getManhattanDistance() {
		int horizontalDistance = Math.abs(this.source.getOwner().getColumn() - this.sink.getOwner().getColumn());
		int verticalDistance = Math.abs(this.source.getOwner().getRow() - this.sink.getOwner().getRow());
		int manhattanDistance = horizontalDistance + verticalDistance;
		
		return manhattanDistance;
	}
}
