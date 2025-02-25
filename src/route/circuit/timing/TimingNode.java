package route.circuit.timing;

import java.util.ArrayList;
import java.util.List;

import route.circuit.architecture.DelayTables;
import route.circuit.block.GlobalBlock;
import route.circuit.pin.AbstractPin;
import route.circuit.timing.TimingNode.Position;

public class TimingNode {

    public enum Position {ROOT, INTERMEDIATE, LEAF, C_SOURCE, C_SINK};

    private final GlobalBlock globalBlock;
    private final AbstractPin pin;
    
    private int numClockDomains;
    public int clockDomain;

    private final Position position;

    private final ArrayList<TimingEdge> sourceEdges = new ArrayList<>();
    private final ArrayList<TimingEdge> sinkEdges = new ArrayList<>();
    
    private int numSources = 0, numSinks = 0;

    private float arrivalTime, requiredTime;
    private boolean hasArrivalTime, hasRequiredTime;

    //Tarjan's strongly connected components algorithm
    private int index;
    private int lowLink;
    private boolean onStack;
    
    //Multi clock functionality
    private boolean[] hasClockDomainAsSource;
    private boolean[] hasClockDomainAsSink;
    private boolean hasSourceClockDomains = false;
    private boolean hasSinkClockDomains = false;
    
    private boolean sllNode = false;
   
    public final float clockDelay;

    TimingNode(GlobalBlock globalBlock, AbstractPin pin, Position position, int clockDomain, float clockDelay) {
        this.pin = pin;

        this.globalBlock = globalBlock;
        this.globalBlock.addTimingNode(this);

        this.position = position;
        
        this.clockDomain = clockDomain;
        
        this.clockDelay = clockDelay;
    }

    public GlobalBlock getGlobalBlock() {
        return this.globalBlock;
    }
    public AbstractPin getPin() {
        return this.pin;
    }
    public Position getPosition() {
        return this.position;
    }
    
    public int getClockDomain() {
    	return this.clockDomain;
    }
    
    public void setSLLNodeStatus() {
    	this.sllNode = true;
    }
    
    public boolean getSLLNodeStatus() {
    	return this.sllNode;
    }

    private void addSource(TimingNode source, TimingEdge edge) {
    	this.sourceEdges.add(edge);
    	this.numSources++;
    }
    TimingEdge addSink(TimingNode sink, float delay, DelayTables delayTables) {
        TimingEdge edge = new TimingEdge(delay, this, sink, delayTables);

        this.sinkEdges.add(edge);
        this.numSinks++;

        sink.addSource(this, edge);

        return edge;
    }

    void removeSource(TimingEdge sourceEdge) {
    	if(this.sourceEdges.contains(sourceEdge)){
    		this.sourceEdges.remove(sourceEdge);
    		this.numSources--;
    	}else{
    		System.err.println("This node does not contain source edge");
    	}
    }
    void removeSink(TimingEdge sinkEdge) {
    	if(this.sinkEdges.contains(sinkEdge)){
    		this.sinkEdges.remove(sinkEdge);
    		this.numSinks--;
    	}else{
    		System.err.println("This sink does not contain sink edge");
    	}
    }
    
    public List<TimingEdge> getSourceEdges() {
        return this.sourceEdges;
    }
   
    public List<TimingEdge> getSinkEdges() {
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
    
    void resetArrivalAndRequiredTime() {
        this.hasArrivalTime = false;
        this.hasRequiredTime = false;
    }
    
    //Arrival time
    void resetArrivalTime() {
        this.hasArrivalTime = false;
    }
    void setArrivalTime(float arrivalTime) {
    	this.arrivalTime = arrivalTime;
    	this.hasArrivalTime = true;
    }
    boolean hasArrivalTime() {
    	return this.hasArrivalTime;
    }
    public float getArrivalTime() {
    	return this.arrivalTime;
    }
	float recursiveArrivalTime(int sourceClockDomain) {
		if(this.hasArrivalTime()) {
			return this.getArrivalTime();
		} else {
			float maxArrivalTime = 0;
			for(TimingEdge edge:this.sourceEdges) {
				if(edge.getSource().hasClockDomainAsSource[sourceClockDomain]) {
					float localArrivalTime = edge.getSource().recursiveArrivalTime(sourceClockDomain) + edge.getTotalDelay();
					if(localArrivalTime > maxArrivalTime) {
						maxArrivalTime = localArrivalTime;
					}
				}
			}
			this.setArrivalTime(maxArrivalTime);
			return this.getArrivalTime();
		}
	}

	public void printPath() {
		float maxArrivalTime = 0;
		for(TimingEdge edge:this.sourceEdges) {
			maxArrivalTime = edge.getSource().getArrivalTime() + edge.getTotalDelay();
			System.out.print("\nThe source is " + edge.getSource() + " and the sink is " + edge.getSink() + " and the arrival time is " + edge.getSource().getArrivalTime() + " edge delay is " + edge.getTotalDelay());
			edge.getSource().printPath();
		}
	}
	
	//Required time
    void resetRequiredTime() {
    	this.hasRequiredTime = false;
    }
    void setRequiredTime(float requiredTime) {
    	this.requiredTime = requiredTime;
    	this.hasRequiredTime = true;
    }
    boolean hasRequiredTime() {
    	return this.hasRequiredTime;
    }
    float getRequiredTime() {
    	return this.requiredTime;
    }
    float recursiveRequiredTime(int sinkClockDomain) {
    	if(this.hasRequiredTime()) {
    		return this.getRequiredTime();
    	}else {
			float minRequiredTime = Integer.MAX_VALUE;
			for(TimingEdge edge:this.sinkEdges) {
				if(edge.getSink().hasClockDomainAsSink[sinkClockDomain]) {
					float localRequiredTime = edge.getSink().recursiveRequiredTime(sinkClockDomain) - edge.getTotalDelay();
					if(localRequiredTime < minRequiredTime) {
						minRequiredTime = localRequiredTime;
					}
				}
			}
			this.setRequiredTime(minRequiredTime);
			return this.getRequiredTime();
    	}
    }
   
   /****************************************************
    * Tarjan's strongly connected components algorithm *
    ****************************************************/
    public void reset(){
    	this.index = -1;
    	this.lowLink = -1;
    	this.onStack = false;
    }
    public boolean undefined(){
    	return this.index == -1;
    }
    public void setIndex(int index){
    	this.index = index;
    }
    public void setLowLink(int lowLink){
    	this.lowLink = lowLink;
    }
    public int getIndex(){
    	return this.index;
    }
    public int getLowLink(){
    	return this.lowLink;
    }
    public void putOnStack(){
    	this.onStack = true;
    }
    public void removeFromStack(){
    	this.onStack = false;
    }
    public boolean onStack(){
    	return this.onStack;
    }
    
    @Override
    public String toString() {
        return this.pin.toString();
    }
    
    //Multi clock functionality
    void setNumClockDomains(int numClockDomains) {
    	this.numClockDomains = numClockDomains;
    	
    	this.hasClockDomainAsSource = new boolean[numClockDomains];
    	this.hasClockDomainAsSink = new boolean[numClockDomains];
        
    	for(int clockDomain = 0; clockDomain < numClockDomains; clockDomain++) {
            this.hasClockDomainAsSource[clockDomain] = false;
            this.hasClockDomainAsSink[clockDomain] = false;
        }
    }
    
    public boolean[] setSourceClockDomains() {
    	if(this.hasSourceClockDomains) {
    		return this.hasClockDomainAsSource;
    		
    	} else if(this.position == Position.ROOT) {
    		this.hasClockDomainAsSource[this.getClockDomain()] = true;
    		this.hasSourceClockDomains = true;
    		
    		return this.hasClockDomainAsSource;
    		
    	} else {
			for(TimingEdge sourceEdge : this.sourceEdges) {
				TimingNode sourceNode = sourceEdge.getSource();
//				
				boolean[] sourceNodeClockDomains = sourceNode.setSourceClockDomains();
				
				for(int clockDomain = 0; clockDomain < this.numClockDomains; clockDomain++) {
					if(sourceNodeClockDomains[clockDomain]) {
						this.hasClockDomainAsSource[clockDomain] = true;						
					}
				}
			}
	    	this.hasSourceClockDomains = true;
	    	return this.hasClockDomainAsSource;
    	}
    }
    public boolean[] setSinkClockDomains() {
    	if(this.hasSinkClockDomains) {
 
    		return this.hasClockDomainAsSink;
    		
    	}else if(this.position == Position.LEAF) {
    		this.hasClockDomainAsSink[this.clockDomain] = true;
    		this.hasSinkClockDomains = true;
    		
    		return this.hasClockDomainAsSink;
    	} else {
    		
			for(TimingEdge sinkEdge : this.sinkEdges) {
				TimingNode sinkNode = sinkEdge.getSink();
				if(!sinkNode.getPin().getSLLSourceStatus()) {
					boolean[] sinkNodeClockDomains = sinkNode.setSinkClockDomains();
					
					for(int clockDomain = 0; clockDomain < this.numClockDomains; clockDomain++) {
						if(sinkNodeClockDomains[clockDomain]) {
							this.hasClockDomainAsSink[clockDomain] = true;
						}
					}
				}
			}
	    	this.hasSinkClockDomains = true;
	    	return this.hasClockDomainAsSink;
    	}
    }
    public boolean[] setSinkClockDomainsSystem() {
    	if(this.hasSinkClockDomains) {
    		return this.hasClockDomainAsSink;
    		
    	}else if(this.position == Position.LEAF) {
    		this.hasClockDomainAsSink[this.clockDomain] = true;
    		this.hasSinkClockDomains = true;
    		
    		return this.hasClockDomainAsSink;
    	} else {
    	
			for(TimingEdge sinkEdge : this.sinkEdges) {
				TimingNode sinkNode = sinkEdge.getSink();

					boolean[] sinkNodeClockDomains = sinkNode.setSinkClockDomainsSystem();
					for(int clockDomain = 0; clockDomain < this.numClockDomains; clockDomain++) {
						if(sinkNodeClockDomains[clockDomain]) {
							this.hasClockDomainAsSink[clockDomain] = true;
						}
					}
				}
	    	this.hasSinkClockDomains = true;
	    	return this.hasClockDomainAsSink;
    	}
    }
    
    public boolean hasClockDomainAsSink(int sinkClockDomain) {
    	return this.hasClockDomainAsSink[sinkClockDomain];
    }
    
    public boolean hasClockDomainAsSource(int sourceClockDomain) {
    	return this.hasClockDomainAsSource[sourceClockDomain];
    }
    
    void compact() {
        this.sourceEdges.trimToSize();
        this.sinkEdges.trimToSize();
    }
}
