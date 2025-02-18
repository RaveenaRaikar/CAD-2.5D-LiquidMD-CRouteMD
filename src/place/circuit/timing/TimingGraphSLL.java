package place.circuit.timing;

import place.circuit.timing.TimingGraph;
import place.circuit.timing.TimingGraph.SCC;
import place.circuit.timing.TimingNode.Position;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import place.circuit.Circuit;
import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.DelayTables;

public class TimingGraphSLL {
	private Circuit[] circuitdie;
	private TimingGraph[] timingGraphdie;
	private List<TimingNode> allTimingNodes = new ArrayList<>();
	private List<TimingEdge> allTimingEdges = new ArrayList<>();
	private List<List<TimingEdge>> allTimingNets = new ArrayList<>();
	private List<TimingNode> allRootNodes, allLeafNodes, allSLLNodes;
	private List<TimingNode> allArrivalTraversal, allRequiredTraversal;
    private DelayTables delayTables;
    private List<TimingEdge> allGlobalTimingEdges;
    private double[] criticalityLookupTable = new double[21];
    private int totaldie;
    private double totalMaxDelay;
    
    private double sllDelay;
    
	public TimingGraphSLL(Circuit[] circuitdie, int totDie) {
		this.circuitdie = circuitdie;
		this.totaldie = totDie;
		
		this.setCriticalityExponent(1);
		
	}
	//transfer functions from timing graph
	
    public void build() {

        this.buildSystemGraph();
        
        this.setRootAndLeafNodes();
        
        this.buildTraversals();
        
        this.setGlobalTimingEdges();
        
        this.calculateArrivalTimesAndCriticalities(false);

        
        
    }
    private void buildSystemGraph() {

    	long start = System.nanoTime();

    	this.delayTables = this.circuitdie[0].getArchitecture().getDelayTables();
    	this.sllDelay = this.circuitdie[0].getArchitecture().sllDelay;


    	this.timingGraphdie = new TimingGraph[this.totaldie];
    	List<TimingNode> sllSourceNodes = new ArrayList<>(); 
    	List<TimingNode> sllSinkNodes = new ArrayList<>(); 
    	
    	
    	for(int i = 0; i < this.totaldie; i ++) {
    		this.timingGraphdie[i] = this.circuitdie[i].getTimingGraph();
    		this.allTimingNodes.addAll(this.timingGraphdie[i].getTimingNodes());
    		this.allTimingEdges.addAll(this.timingGraphdie[i].getTimingEdges());
    		this.allTimingNets.addAll(this.timingGraphdie[i].getTimingNets());
    	}

    	
    	long nextStart = System.nanoTime();
    	//Next traverse from source to build the final graph
    	int numNodes = this.allTimingNodes.size();
    	//Find the datain or the source pins
        for(int i = 0; i < numNodes; i++) {
            TimingNode node = this.allTimingNodes.get(i);
            if (node.getGlobalBlock().isSLLDummy()) {
            	if(node.getgPin().getPortName().contains("datain")) {

            		sllSourceNodes.add(node);
            	}else if(node.getgPin().getPortName().contains("dataout")) {
            		sllSinkNodes.add(node);
            	}
            }



        }

        int delay = 0;


        for(int j = 0; j < sllSourceNodes.size(); j++) {
        	TimingNode Source = sllSourceNodes.get(j);
        	for(int i = 0; i < sllSinkNodes.size(); i++) {
        		TimingNode tempnode = sllSinkNodes.get(i);
        		if((tempnode.getGlobalBlock().getName().equals(Source.getGlobalBlock().getName())) && (tempnode.getgPin().getPortName().contains("dataout")) ) {
        			TimingNode Sink = tempnode;
 
        			TimingEdge SLLedge = Source.addSink(Sink, delay, this.delayTables, true);
        			this.allTimingEdges.add(SLLedge);

                }
        	}
        }

        System.out.printf("Building system graph took %.2fs\n\n", (System.nanoTime() - start) * 1e-9);
    }

    
    private void setRootAndLeafNodes(){

    	this.allRootNodes = new ArrayList<>();
    	this.allLeafNodes = new ArrayList<>();
    	this.allSLLNodes = new ArrayList<>();
    	
        for(TimingNode timingNode:this.allTimingNodes){
        	if(timingNode.getPosition().equals(Position.ROOT)){
        		this.allRootNodes.add(timingNode);
        	}else if(timingNode.getPosition().equals(Position.LEAF)){
        		this.allLeafNodes.add(timingNode);
        	}else {
        		this.allSLLNodes.add(timingNode);
        	}
        }
    }
    
    public void setCriticalityExponent(double criticalityExponent) {
        for(int i = 0; i <= 20; i++) {
            this.criticalityLookupTable[i] = Math.pow(i * 0.05, criticalityExponent);
        }
    }


    private void buildTraversals() {

    	this.allArrivalTraversal = new ArrayList<>();
    	this.allRequiredTraversal = new ArrayList<>();
    	Set<Integer> added = new HashSet<>();

    	added.clear();
    	for(TimingNode leafNode : this.allLeafNodes) {
    		leafNode.recursiveArrivalTraversal(this.allArrivalTraversal, added);
    	}
    	added.clear();
    	for(TimingNode interNodes : this.allSLLNodes) {

    		interNodes.recursiveArrivalTraversal(this.allArrivalTraversal, added);

    		
    	}
    	added.clear();
    	for(TimingNode rootNode : this.allRootNodes) {

    		rootNode.recursiveRequiredTraversal(this.allRequiredTraversal, added);
    	}
    	added.clear();
    	for(TimingNode interNodes : this.allSLLNodes) {

    		interNodes.recursiveRequiredTraversal(this.allRequiredTraversal, added);
    	}
  
    }
    private void setGlobalTimingEdges() {

    	this.allGlobalTimingEdges = new ArrayList<>();
    	for(int i = 0; i < this.totaldie; i ++) {
    		this.timingGraphdie[i] = this.circuitdie[i].getTimingGraph();
    		this.allGlobalTimingEdges.addAll(this.timingGraphdie[i].getGlobalTimingEdges());
    	}
    	for(TimingEdge edge:this.allTimingEdges) {
    		if(edge.isSLLedge()) {
    			this.allGlobalTimingEdges.add(edge);
    		}
    	}

    }
    
    public double getTotalMaxDelay() {
        return this.totalMaxDelay * 1e9;
    }

    public void recalculateTimingGraph() {
        this.calculateAllWireDelays();
       
        this.calculateArrivalTimesAndCriticalities(false);
    }
    
    public void recalculateSystemTimingGraph() {

        this.setSLLdelay();
        this.calculateArrivalTimesAndCriticalities(true);
    }
    
    
    public void setSLLdelay() {
    	for(TimingEdge edge:this.allTimingEdges){
    		if(edge.isSLLedge()) {
    			edge.setFixedDelay(this.sllDelay);

    		}
    	}
    }
    public void calculateAllWireDelays() {

    	
    	for(TimingEdge edge:this.allTimingEdges){
    		if(edge.isSLLedge()) {

    			edge.setFixedDelay(this.sllDelay);
    		}
    		else {
    			
    			edge.setWireDelay(edge.calculateWireDelay());
    		}

    	}
    }

    
    private void calculateArrivalTimesAndCriticalities(boolean calculateCriticalities) {
        //GLOBAL MAX DELAY
    	this.totalMaxDelay = 0;

    	//ARRIVAL TIME
        for(TimingNode rootNode: this.allRootNodes){
        	rootNode.setArrivalTime(0);
        }
       
        for(TimingNode node : this.allArrivalTraversal) {
        	node.updateArrivalTime();
        }

        for(TimingNode leafNode: this.allLeafNodes){
        	this.totalMaxDelay = Math.max(this.totalMaxDelay, (leafNode.getArrivalTime() - leafNode.getClockDelay()));
        	

        }
        ///For the node coming as the input from the SLL, check whether the SLL delay is counted
        if(calculateCriticalities) {
        	//REQUIRED TIME
        	for(TimingNode leafNode: this.allLeafNodes) {
        		leafNode.setRequiredTime(this.totalMaxDelay + leafNode.getClockDelay());
        	}
            for(TimingNode node: this.allRequiredTraversal) {

            	if(node.isSLLsink()) {

            		node.updateSLLRequiredTime();
            	}else {
            		node.updateRequiredTime();
            	}
 
            }
            for(TimingEdge edge:this.allGlobalTimingEdges){

            	
            	double slack = edge.getSink().getRequiredTime() - edge.getSource().getArrivalTime() - edge.getTotalDelay();

            	slack = Math.min(slack,	this.totalMaxDelay);
            	slack = Math.max(slack,	0);
            	
                double val = (1 - slack/this.totalMaxDelay) * 20;
                int i = Math.min(19, (int) val);
                double linearInterpolation = val - i;

                edge.setCriticality(
                        (1 - linearInterpolation) * this.criticalityLookupTable[i]
                        + linearInterpolation * this.criticalityLookupTable[i+1]);

            }
        }
    }

    public double calculateSystemTotalCost() {
    	double totalCost = 0;

    	for(List<TimingEdge> net : this.allTimingNets) {

    		double netCost = 0;
    		for(TimingEdge edge : net) {
    			double cost = edge.getActualCost();
    			if(cost > netCost) {
    				netCost = cost;
    			}
    		}
 
    		totalCost += netCost;

    	}

    	return totalCost;
    }
    
    public double calculateSLLtimingCost() {
    	double totalCost = 0;


    	double netCost = 0;
    	for(TimingEdge edge : this.allTimingEdges) {
        		if(edge.isSLLedge()) {
	    			double cost = edge.getSLLCost();

	    			if(cost > netCost) {
	    				netCost = cost;
	    			}
    		}
 
    		totalCost += netCost;
    	}

    	return totalCost;
    }
    /******************This is to print all the arrival and required times ***********/
    public void printSystemTimingInfo() {
		System.out.print("\n");
		System.out.print("Timing Node \t Arrival time \t Required time");
    	for(TimingNode timingNode:this.allTimingNodes){
    			System.out.print("\n");
    			System.out.print(timingNode + "\t" + timingNode.getArrivalTime() + "\t" + timingNode.getRequiredTime());
    	
    		
    		
    	}
    }
}
