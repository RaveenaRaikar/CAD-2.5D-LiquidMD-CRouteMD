package place.circuit.timing;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


import place.circuit.architecture.DelayTables;
import place.circuit.block.AbstractBlock;
import place.circuit.block.GlobalBlock;
import place.circuit.block.LeafBlock;
import place.circuit.pin.AbstractPin;
import place.circuit.pin.GlobalPin;
import place.circuit.pin.LeafPin;
import place.circuit.timing.TimingNode.Position;

public class TimingNode {

    public enum Position {ROOT, INTERMEDIATE, LEAF, SLLNODE};

    private LeafBlock block;
    private GlobalBlock globalBlock;
    private LeafPin pin;
    private AbstractPin gpin;
    private Position position;

    private final ArrayList<TimingEdge> sourceEdges = new ArrayList<>();
    private final ArrayList<TimingEdge> sinkEdges = new ArrayList<>();
    private final ArrayList<TimingEdge> SLLsinkEdges = new ArrayList<>();
    private int numSources = 0, numSinks = 0;

    private double arrivalTime, requiredTime;
    private double sllSLack;

    //Tarjan's strongly connected components algorithm
    private int index;
    private int lowLink;
    private boolean onStack;
    
    private int clockDomain;
    private double clockDelay;
    private boolean SLLNode = false;
    private boolean SLLsink = false;
    private boolean SLLsource = false;
    
    TimingNode(LeafBlock block, LeafPin pin, Position position, int clockDomain, double clockDelay) {
        this.block = block;
        this.pin = pin;

        this.globalBlock = block.getGlobalParent();
        this.globalBlock.addTimingNode(this);

        this.position = position;
        
        this.clockDomain = clockDomain;
        this.clockDelay = clockDelay;

    }
    TimingNode(GlobalBlock block, GlobalPin pin, Position position, int clockDomain, double clockDelay) {

        this.gpin = pin;

        this.globalBlock = (GlobalBlock) block;
        this.globalBlock.addTimingNode(this);

        this.position = position;
        
        this.clockDomain = clockDomain;
        this.clockDelay = clockDelay;
        this.SLLNode = true;
    }
    void compact() {
    	this.sourceEdges.trimToSize();
        this.sinkEdges.trimToSize();
    }

    public LeafBlock getBlock() {
        return this.block;
    }
    public GlobalBlock getGlobalBlock() {
        return this.globalBlock;
    }
    public LeafPin getPin() {
        return this.pin;
    }
    public boolean getSLLnodestatus() {
    	return this.SLLNode;
    }
    public AbstractPin getgPin() {
        return this.gpin;
    }
    public Position getPosition() {
        return this.position;
    }
    
   

    private void addSource(TimingNode source, TimingEdge edge) {

        this.sourceEdges.add(edge);
        this.numSources++;
    }
    TimingEdge addSink(TimingNode sink, double delay, DelayTables delayTables) {
        TimingEdge edge = new TimingEdge(delay, this, sink, delayTables);

        this.sinkEdges.add(edge);
        this.numSinks++;

        sink.addSource(this, edge);

        return edge;
    }
    TimingEdge addSink(TimingNode sink, double delay, DelayTables delayTables, boolean isSLL) {
        TimingEdge edge = new TimingEdge(delay, this, sink, delayTables, isSLL);

        this.SLLsinkEdges.add(edge);


        sink.addSource(this, edge);

        return edge;
    }

    void removeSource(TimingEdge source){
    	if(this.sourceEdges.contains(source)){

    		this.sourceEdges.remove(source);
    		this.numSources--;
    	}else{
    		System.out.println("This node does not contain source edge");
    	}
    }
    void removeSink(TimingEdge sink){
    	if(this.sinkEdges.contains(sink)){
    		this.sinkEdges.remove(sink);
    		this.numSinks--;
    	}else{
    		System.out.println("This sink does not contain sink edge");
    	}
    }

    public List<TimingEdge> getSources() {
        return this.sourceEdges;
    }
    public List<TimingEdge> getSinks() {
        return this.sinkEdges;
    }

    public int getNumSources() {
        return this.numSources;
    }
    public int getNumSinks() {
        return this.numSinks;
    }

    public TimingEdge getSourceEdge(int sourceIndex) {
        return this.sourceEdges.get(sourceIndex);
    }
    public TimingEdge getSinkEdge(int sinkIndex) {
        return this.sinkEdges.get(sinkIndex);
    }

    void setSLLNodeasSink() {
		this.SLLsink = true;
		this.SLLsource = false;
    }
    void setSLLNodeasSource() {
		this.SLLsink = false;
		this.SLLsource = true;
    }
    
    boolean isSLLsink() {
    	return this.SLLsink;
    }
    boolean isSLLsource() {
    	return this.SLLsource;
    }
    //Arrival time	
    void setArrivalTime(double value) {
    	this.arrivalTime = value;
    }
    double getArrivalTime() {
    	return this.arrivalTime;
    }
    

    ///IF the edge source is an SLL node
    void updateArrivalTime() {
		this.arrivalTime = Double.MIN_VALUE;
		for(TimingEdge edge:this.sourceEdges) {
			//
			double localArrivalTime = edge.getSource().arrivalTime + edge.getTotalDelay();

			if(localArrivalTime > this.arrivalTime) {
				this.arrivalTime = localArrivalTime;
			}
		}

	}

	void recursiveArrivalTraversal(List<TimingNode> traversal, Set<Integer> added) {
		if(this.position.equals(Position.ROOT)) return;
		
		for(TimingEdge edge:this.sourceEdges) {
			if(!added.contains(edge.getSource().index)) {

				edge.getSource().recursiveArrivalTraversal(traversal, added);
			}
		}
	    added.add(this.index);
		traversal.add(this);
	}

	//Required time
    void setRequiredTime(double value) {
    	this.requiredTime = value;
    }
    double getRequiredTime() {
    	return this.requiredTime;
    }
    void updateRequiredTime() {
		this.requiredTime = Double.MAX_VALUE;
		


		for(TimingEdge edge:this.sinkEdges) {
			double localRequiredTime = edge.getSink().requiredTime - edge.getTotalDelay();

			if(localRequiredTime < this.requiredTime) {
				this.requiredTime = localRequiredTime;
			}
		}
    }
    
    void updateRequiredTimeSetCriticalEdge() {
		this.requiredTime = Double.MAX_VALUE;
		for(TimingEdge edge:this.sinkEdges) {
			double localRequiredTime = edge.getSink().requiredTime - edge.getTotalDelay();
			if(localRequiredTime < this.requiredTime) {
				this.requiredTime = localRequiredTime;
			}
		}
    }
    void setSLLslack(double slack) {
    	this.sllSLack = slack;
    }
    void updateSLLRequiredTime() {
		this.requiredTime = Double.MAX_VALUE;
		

		for(TimingEdge edge:this.SLLsinkEdges) {
			double localRequiredTime = edge.getSink().requiredTime - edge.getTotalDelay();

			if(localRequiredTime < this.requiredTime) {
				this.requiredTime = localRequiredTime;
			}
		}
    }
    
    
	void recursiveRequiredTraversal(List<TimingNode> traversal, Set<Integer> added) {
		if(this.position.equals(Position.LEAF)) return;
		
		for(TimingEdge edge:this.sinkEdges) {
			if(!added.contains(edge.getSink().index)) {
				edge.getSink().recursiveRequiredTraversal(traversal, added);
			}
		}
	    added.add(this.index);
		traversal.add(this);
	}
    
    public int getClockDomain() {
    	return this.clockDomain;
    }
    public double getClockDelay() {
    	return this.clockDelay;
    }

   /****************************************************
    * Tarjan's strongly connected components algorithm *
    ****************************************************/
    void reset(){
    	this.index = -1;
    	this.lowLink = -1;
    	this.onStack = false;
    }
    boolean undefined(){
    	return this.index == -1;
    }
    void setIndex(int index){
    	this.index = index;
    }
    void setLowLink(int lowLink){
    	this.lowLink = lowLink;
    }
    int getIndex(){
    	return this.index;
    }
    int getLowLink(){
    	return this.lowLink;
    }
    void putOnStack(){
    	this.onStack = true;
    }
    void removeFromStack(){
    	this.onStack = false;
    }
    boolean onStack(){
    	return this.onStack;
    }


    /*************************************************
     * Functions that facilitate simulated annealing *
     *************************************************/

    double calculateDeltaCost(GlobalBlock otherBlock) {
    	/*
    	 * When this method is called, we assume that this block and
    	 * the block with which this block will be swapped, already
    	 * have their positions updated (temporarily).
    	 */
    	double cost = 0;

    	for(int sinkIndex = 0; sinkIndex < this.numSinks; sinkIndex++) {
    		TimingEdge edge = this.sinkEdges.get(sinkIndex);
    		TimingNode sink = edge.getSink();

    		cost += this.calculateDeltaCost(sink, edge);
    	}

    	for(int sourceIndex = 0; sourceIndex < this.numSources; sourceIndex++) {
    		TimingEdge edge = this.sourceEdges.get(sourceIndex);
    		TimingNode source = edge.getSource();

    		if(source.globalBlock != otherBlock) {
    			cost += this.calculateDeltaCost(source, edge);
    		}
    	}

    	return cost;
    }
    private double calculateDeltaCost(TimingNode otherNode, TimingEdge edge){
    	if(otherNode.globalBlock == this.globalBlock){
    		edge.resetStagedDelay();
    		return 0;
    	}else{
    		edge.setStagedWireDelay(edge.calculateWireDelay());
    		return edge.getDeltaCost();
    	}
    }

    void pushThrough(){
    	for(TimingEdge edge : this.sinkEdges){
    		edge.pushThrough();
    	}
    	for(TimingEdge edge : this.sourceEdges){
    		edge.pushThrough();
    	}
    }


    @Override
    public String toString() {
    	if(!this.SLLNode) {
    		return this.pin.toString();
    	}else
    	{
    		return this.gpin.toString();
    	}
    }

}
