package place.circuit.timing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import place.circuit.Circuit;
import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.architecture.DelayTables;
import place.circuit.architecture.PortType;
import place.circuit.block.AbstractBlock;
import place.circuit.block.GlobalBlock;
import place.circuit.block.LeafBlock;
import place.circuit.exceptions.PlacementException;
import place.circuit.pin.AbstractPin;
import place.circuit.pin.GlobalPin;
import place.circuit.pin.LeafPin;
import place.circuit.timing.TimingNode.Position;
import place.placers.simulatedannealing.Swap;
import place.util.Pair;

public class TimingGraph {

    private static String VIRTUAL_IO_CLOCK = "virtual-io-clock";

    private Circuit circuit;
    private DelayTables delayTables;

    // A map of clock domains, along with their unique id
    private Map<String, Integer> clockNamesToDomains = new HashMap<>();
    private int numClockDomains = 0;
    private int virtualIoClockDomain;

    private List<TimingNode> timingNodes = new ArrayList<>();
    
    private List<Double> clockDelays = new ArrayList<>();
    private List<TimingNode> rootNodes, leafNodes, interNodes;
    private List<TimingNode> arrivalTraversal, requiredTraversal;
    
    private List<TimingNode> affectedNodes = new ArrayList<>();

    private List<TimingEdge> timingEdges = new ArrayList<>();
    private List<TimingEdge> globalTimingEdges;

    
    private List<List<TimingEdge>> timingNets = new ArrayList<>();

    private double maxDelay;

    private double[] criticalityLookupTable = new double[21];
    
    //Tarjan's strongly connected components algorithm
    private int index;
    private Stack<TimingNode> stack;
    private List<SCC> scc;

    public TimingGraph(Circuit circuit) {
        this.circuit = circuit;
        this.delayTables = this.circuit.getArchitecture().getDelayTables();

        // Create the virtual io clock domain
        this.virtualIoClockDomain = 0;
        this.clockNamesToDomains.put(VIRTUAL_IO_CLOCK, this.virtualIoClockDomain);
        this.numClockDomains = 1;

        this.setCriticalityExponent(1);
    }

    /******************************************
     * These functions build the timing graph *
     ******************************************/

    public void build() {

        this.buildGraph();
        
        this.setRootAndLeafNodes();
        
        this.cutCombLoop();
        
        this.buildTraversals();
        
        this.setGlobalTimingEdges();
        
        
        
    }

    private void buildGraph() {
    	long start = System.nanoTime();
        List<Double> clockDelays = new ArrayList<Double>();

        // Create all timing nodes
        for(BlockType leafBlockType : BlockType.getLeafBlockTypes()) {
            boolean isClocked = leafBlockType.isClocked();
            for(AbstractBlock abstractBlock : this.circuit.getBlocks(leafBlockType)) {
                LeafBlock block = (LeafBlock) abstractBlock;
                boolean isConstantGenerator = isClocked ? false : this.isConstantGenerator(block);

                // Get the clock domain and clock setup time
                int clockDomain = -1;
                double clockDelay = 0;
                if(isClocked) {
                    Pair<Integer, Double> clockDomainAndDelay = this.getClockDomainAndDelay(block);
                    clockDomain = clockDomainAndDelay.getFirst();

                    // We don't include the clock setup time in the critical path delay,
                    // because VPR also doesn't does this for the critical path. When you
                    // let VPR print out the critical path the clock setup time IS included,
                    // but it isn't included in the stdout estimation.
                    clockDelay = clockDomainAndDelay.getSecond();
                } else if(isConstantGenerator) {
                    clockDomain = this.virtualIoClockDomain;
                }

                // If the block is clocked: create TimingNodes for the input pins
                if(isClocked) {
                    for(AbstractPin abstractPin : block.getInputPins()) {
                        if(abstractPin.getSource() != null) {

                            LeafPin inputPin = (LeafPin) abstractPin;
                            TimingNode node = new TimingNode(block, inputPin, Position.LEAF, clockDomain, clockDelay);
                            inputPin.setTimingNode(node);

                            clockDelays.add(0.0);
                            this.clockDelays.add(0.0);
                            this.timingNodes.add(node);
                        }
                    }
                }


                // Add the output pins of this timing graph root or intermediate node
                Position position = (isClocked || isConstantGenerator) ? Position.ROOT : Position.INTERMEDIATE;
                for(AbstractPin abstractPin : block.getOutputPins()) {
                    LeafPin outputPin = (LeafPin) abstractPin;
                    
                    if(outputPin.getNumSinks() > 0){
                        TimingNode node = new TimingNode(block, outputPin, position, clockDomain, clockDelay);
                        outputPin.setTimingNode(node);

                        this.timingNodes.add(node);

                        if(position == Position.ROOT) {
                            clockDelays.add(clockDelay + outputPin.getPortType().getSetupTime());
                            this.clockDelays.add(clockDelay + outputPin.getPortType().getSetupTime());
                        } else {
                            clockDelays.add(0.0);
                            this.clockDelays.add(0.0);

                        }
                        
                    }
                }
            }
        }
        
        
        for(BlockType SLLblocktype : BlockType.getDummyBlockTypes()) {
        	
        	for(AbstractBlock abstractBlock : this.circuit.getBlocks(SLLblocktype)) {

        		GlobalBlock block = (GlobalBlock) abstractBlock;
                double clockDelay = 0;
                int clockDomain = -1;
        		if(block.isSLLSink()) {

        			for(AbstractPin abstractPin : abstractBlock.getInputPins()) {
        				
        				if(abstractPin.getPortName().equals("datain") && (abstractPin.getIndex()==0)) {

        					GlobalPin inputPin = (GlobalPin) abstractPin ;

        					TimingNode node = new TimingNode(block, inputPin, Position.SLLNODE, clockDomain, clockDelay);
        					inputPin.setTimingNode(node);
        					clockDelays.add(0.0);
        					node.setSLLNodeasSink();
        					this.timingNodes.add(node);

        				}
        			}
        		}else if(block.isSLLSource()) {
        			for(AbstractPin abstractPin : block.getOutputPins()) {
                    	GlobalPin outputPin = (GlobalPin) abstractPin;
                    	
                        if(outputPin.getNumSinks() > 0){
                            TimingNode node = new TimingNode(block, outputPin, Position.SLLNODE, clockDomain, clockDelay);
                            abstractPin.setTimingNode(node);
                            node.setSLLNodeasSource();
                            this.timingNodes.add(node);
                            clockDelays.add(0.0);
                        }
        			}
        		}
        	}
        }

        

        
        int numNodes = this.timingNodes.size();
        for(int i = 0; i < numNodes; i++) {
            TimingNode node = this.timingNodes.get(i);

            if(!(node.getSLLnodestatus())) { //Not required for dummy blocks as they are at the highest level already
            	if(node.getPosition() != Position.LEAF && !this.clockNamesToDomains.containsKey(node.getPin().getOwner().getName())) {

            		this.traverseFromSource(node, clockDelays.get(i));
            	}
           }else
           {

        	   this.AddSLLTimingEdges(node, clockDelays.get(i));
           }

        }
        
        for(TimingNode node : this.timingNodes) {
            node.compact();
        }
        
        System.out.printf("Building graph took %.2fs\n\n", (System.nanoTime() - start) * 1e-9);
    }

    private boolean isConstantGenerator(LeafBlock block) {
        for(AbstractPin inputPin : block.getInputPins()) {
            if(inputPin.getSource() != null) {
                return false;
            }
        }

        return true;
    }


    private Pair<Integer, Double> getClockDomainAndDelay(LeafBlock block) {
        /**
         * This method should only be called for clocked blocks.
         */

        String clockName;
        double clockDelay = 0;

        // If the block is an input
        if(block.getGlobalParent().getCategory() == BlockCategory.IO) {
            clockName = VIRTUAL_IO_CLOCK;

        // If the block is a regular block
        } else{
            // A clocked leaf block has exactly 1 clock pin
            List<AbstractPin> clockPins = block.getClockPins();
            assert(clockPins.size() == 1);
            AbstractPin clockPin = clockPins.get(0);

            while(true) {
                AbstractPin sourcePin = clockPin.getSource();
                if(sourcePin == null) {
                    break;
                }

                clockDelay += sourcePin.getPortType().getDelay(clockPin.getPortType());
                clockPin = sourcePin;
            }

            clockName = clockPin.getOwner().getName();
            assert(!clockName.equals(VIRTUAL_IO_CLOCK));

            if(!this.clockNamesToDomains.containsKey(clockName)) {
                this.clockNamesToDomains.put(clockName, this.numClockDomains);
                this.numClockDomains++;
            }
        }

        return new Pair<Integer, Double>(this.clockNamesToDomains.get(clockName), clockDelay);
    }
  


    //The problem occurs because it is trying to get the global parent.
    private void traverseFromSource(TimingNode pathSourceNode, double clockDelay) {
    	GlobalBlock pathSourceBlock = pathSourceNode.getGlobalBlock();
    	LeafPin pathSourcePin = pathSourceNode.getPin();
        Map<GlobalBlock, List<TimingEdge>> sourceTimingNets = new HashMap<>();
        
        //System.out.print("\nThe pathsourcepin is " + pathSourcePin);

        Stack<TraversePair> todo = new Stack<>();
        todo.push(new TraversePair(pathSourcePin, clockDelay));

        while(!todo.empty()) {
            TraversePair traverseEntry = todo.pop();
            AbstractPin sourcePin = traverseEntry.pin;
            //System.out.print("\nThe sourcePin is " + sourcePin);
            double delay = traverseEntry.delay;

            AbstractBlock sourceBlock = sourcePin.getOwner();
            PortType sourcePortType = sourcePin.getPortType();
            if(this.isEndpin(sourceBlock, sourcePin, pathSourcePin)) {
                delay += sourcePortType.getSetupTime();
                TimingNode pathSinkNode = ((LeafPin) sourcePin).getTimingNode();
               // System.out.print("\nThis is true for the last pin " +sourceBlock.getName()+" " +  sourcePin + " the timing node is "+ pathSinkNode.toString());
                //System.out.print("\nThe delay added here is " + delay);
                // If pathSinkNode is null, this sinkPin doesn't have any sinks
                // so isn't used in the timing graph
                
                if(pathSinkNode != null) {
                    TimingEdge edge = pathSourceNode.addSink(pathSinkNode, delay, this.delayTables);
                    //System.out.print("\nThe path source node is " + pathSourceNode.toString() + " and the path sink node is " + pathSinkNode.toString() );
                    this.timingEdges.add(edge);
                    GlobalBlock pathSinkBlock = pathSinkNode.getGlobalBlock();
                    if(pathSinkBlock != pathSourceBlock) {
                        if(!sourceTimingNets.containsKey(pathSinkBlock)) {
                            sourceTimingNets.put(pathSinkBlock, new ArrayList<TimingEdge>());
                        }
                        sourceTimingNets.get(pathSinkBlock).add(edge);
                    }
                }

            } else if(this.isSLLEndpin(sourceBlock, sourcePin, pathSourcePin)) { 
            	//The dummy blocks dont have internal hierarchy and internal delay
            	GlobalBlock ParentBlock = (GlobalBlock) sourceBlock;
            	GlobalPin ParentPin = (GlobalPin) sourcePin;
            	int clockDomain = -1;
				
                 TimingNode pathSinkNode = ParentPin.getTimingNode();

                 if(pathSinkNode != null) {
                     TimingEdge edge = pathSourceNode.addSink(pathSinkNode, delay, this.delayTables);
                     this.timingEdges.add(edge);
                     GlobalBlock pathSinkBlock = pathSinkNode.getGlobalBlock();
                     if(pathSinkBlock != pathSourceBlock) {
                         if(!sourceTimingNets.containsKey(pathSinkBlock)) {
                             sourceTimingNets.put(pathSinkBlock, new ArrayList<TimingEdge>());
                         }
         
                         sourceTimingNets.get(pathSinkBlock).add(edge);
                     }
                 }

            }else {
            	
                List<AbstractPin> sinkPins;
                if(sourceBlock.isLeaf() && sourcePin != pathSourcePin) {
                    sinkPins = sourceBlock.getOutputPins();
                }else {
                		sinkPins = sourcePin.getSinks();
                }

                for(AbstractPin sinkPin : sinkPins) {
                	if(sinkPin != null) {
                    	if(sinkPin.getOwner().isSLLDummy()) {
                    		GlobalPin sinkSllPin = (GlobalPin) sinkPin;
                    		double sourceSinkDelay = sourcePortType.getDelay(sinkSllPin.getPortType());
                            
                            if(sourceSinkDelay >= 0.0){ // Only consider existing connections
                                double totalDelay = delay + sourceSinkDelay;
                                    todo.push(new TraversePair(sinkSllPin, totalDelay));
                            }
                    	}else {
                            double sourceSinkDelay = sourcePortType.getDelay(sinkPin.getPortType());
                            
                            if(sourceSinkDelay >= 0.0){ // Only consider existing connections
                                double totalDelay = delay + sourceSinkDelay;

                                // Loops around an unclocked block are rare, but not inexistent.
                                // The proper way to handle these is probably to add the loop
                                // delay to all other output pins of this block.
                                if(sinkPin != pathSourcePin) {
                                    todo.push(new TraversePair(sinkPin, totalDelay));
                                }
                                    
                            }
                        }                    	
                	}
            	}
            }
     

        }

        for(List<TimingEdge> timingNet : sourceTimingNets.values()) {
            this.timingNets.add(timingNet);
        }
    }

    //The problem occurs because it is trying to get the global parent.
    private void AddSLLTimingEdges(TimingNode pathSourceNode, double clockDelay) {
    	GlobalBlock pathSourceBlock = pathSourceNode.getGlobalBlock();
    	AbstractPin pathSourcePin = pathSourceNode.getgPin();
        Map<GlobalBlock, List<TimingEdge>> sourceTimingNets = new HashMap<>();

        Stack<TraversePair> todo = new Stack<>();
        todo.push(new TraversePair(pathSourcePin, clockDelay));

        while(!todo.empty()) {
            TraversePair traverseEntry = todo.pop();
            AbstractPin sourcePin = traverseEntry.pin;
            double delay = traverseEntry.delay;

            AbstractBlock sourceBlock = sourcePin.getOwner();
            PortType sourcePortType = sourcePin.getPortType();
            
            if(this.isEndpin(sourceBlock, sourcePin, pathSourcePin)) {
                delay += sourcePortType.getSetupTime();
                TimingNode pathSinkNode = ((LeafPin) sourcePin).getTimingNode();
                // If pathSinkNode is null, this sinkPin doesn't have any sinks
                // so isn't used in the timing graph
                if(pathSinkNode != null) {
                    TimingEdge edge = pathSourceNode.addSink(pathSinkNode, delay, this.delayTables);
                    this.timingEdges.add(edge);
                    GlobalBlock pathSinkBlock = pathSinkNode.getGlobalBlock();
                    if(pathSinkBlock != pathSourceBlock) {
                        if(!sourceTimingNets.containsKey(pathSinkBlock)) {
                            sourceTimingNets.put(pathSinkBlock, new ArrayList<TimingEdge>());
                        }
                        sourceTimingNets.get(pathSinkBlock).add(edge);
                    }
                }

            } else {
                List<AbstractPin> sinkPins;
                if(sourceBlock.isLeaf() && sourcePin != pathSourcePin) {
                    sinkPins = sourceBlock.getOutputPins();
                } else {
                    sinkPins = sourcePin.getSinks();
                }

                for(AbstractPin sinkPin : sinkPins) {
                    if(sinkPin != null) {
                        double sourceSinkDelay = sourcePortType.getDelay(sinkPin.getPortType());
                        
                        if(sourceSinkDelay >= 0.0){ // Only consider existing connections
                            double totalDelay = delay + sourceSinkDelay;

                            // Loops around an unclocked block are rare, but not inexistent.
                            // The proper way to handle these is probably to add the loop
                            // delay to all other output pins of this block.
                            if(sinkPin != pathSourcePin) {
                                todo.push(new TraversePair(sinkPin, totalDelay));
                            }
                        }
                    }
                }
            }
        }

        for(List<TimingEdge> timingNet : sourceTimingNets.values()) {
            this.timingNets.add(timingNet);
        }
    }
    private boolean isEndpin(AbstractBlock block, AbstractPin pin, AbstractPin pathSourcePin) {
        return block.isLeaf() && (pin != pathSourcePin) && (block.isClocked() && pin.isInput() || pin.isOutput());
    }
    
    private boolean isSLLEndpin(AbstractBlock block, AbstractPin pin, AbstractPin pathSourcePin) {
        return block.isSLLDummy() && (pin != pathSourcePin) && (pin.isInput() || pin.isOutput());
    }

    private class TraversePair {
        AbstractPin pin;
        double delay;

        TraversePair(AbstractPin pin, double delay) {
            this.pin = pin;
            this.delay = delay;
        }
    }

    private void setRootAndLeafNodes(){
    	this.rootNodes = new ArrayList<>();
    	this.leafNodes = new ArrayList<>();
    	this.interNodes = new ArrayList<>();
    	
        for(TimingNode timingNode:this.timingNodes){
        	if(timingNode.getPosition().equals(Position.ROOT)){

        		this.rootNodes.add(timingNode);
        	}else if(timingNode.getPosition().equals(Position.LEAF)){

        		this.leafNodes.add(timingNode);
        	}else if(timingNode.getPosition().equals(Position.SLLNODE)){

        		this.interNodes.add(timingNode);        		
        	}
        }
    }
    
    private void buildTraversals() {
    	long start = System.nanoTime();
    	this.arrivalTraversal = new ArrayList<>();
    	this.requiredTraversal = new ArrayList<>();
    	Set<Integer> added = new HashSet<>();

    	added.clear();
    	for(TimingNode leafNode : this.leafNodes) {
    		leafNode.recursiveArrivalTraversal(this.arrivalTraversal, added);
    	}

    	added.clear();
    	for(TimingNode interNodes : this.interNodes) {

    		interNodes.recursiveArrivalTraversal(this.arrivalTraversal, added);

    		
    	}

    	added.clear();
    	for(TimingNode rootNode : this.rootNodes) {

    		rootNode.recursiveRequiredTraversal(this.requiredTraversal, added);
    	}
    	added.clear();
    	for(TimingNode interNodes : this.interNodes) {
    		interNodes.recursiveRequiredTraversal(this.requiredTraversal, added);
    	}
    	System.out.printf("Build traversals took %.2fs\n\n", (System.nanoTime() - start) * 1e-9);
    }
    private void setGlobalTimingEdges() {
    	this.globalTimingEdges = new ArrayList<>();
    	for(TimingEdge edge : this.timingEdges) {
    		if(edge.getSource().getGlobalBlock().getIndex() != edge.getSink().getGlobalBlock().getIndex()) {
    			this.globalTimingEdges.add(edge);
    		}
    	}
    }

   
    public List<TimingNode> getTimingNodes() {
    	return this.timingNodes;
    }
    
    public List<List<TimingEdge>> getTimingNets() {
    	return this.timingNets;
    }
    
    public List<TimingNode> getArrivalTraversal(){
    	return this.arrivalTraversal;
    }
    public List<TimingNode> getRequiredTraversal(){
    	return this.requiredTraversal;
    }
    public List<Double> getClockDelays(){
    	return this.clockDelays;
    }
    
    public List<TimingEdge> getTimingEdges(){
    	return this.timingEdges;
    }
    
    public List<TimingEdge> getGlobalTimingEdges(){
    	return this.globalTimingEdges;
    }
    /****************************************************
     * Functionality to find combinational loops with   *
     * Tarjan's strongly connected components algorithm *
     ****************************************************/
    private void cutCombLoop(){

    	long start = System.nanoTime();

    	System.out.println("\nCut combinational loops iteratively");

    	int iteration = 0;
    	boolean finalIteration = false;
    	
    	while(!finalIteration){

    		int cutLoops = 0;
    		finalIteration = true;

    		//Initialize iteration
        	this.index = 0;
        	this.stack = new Stack<>();
        	this.scc = new ArrayList<>();
        	for(TimingNode v:this.timingNodes){
        		v.reset();
        	}

        	//Find SCC
        	for(TimingNode v:this.timingNodes){
        		if(v.undefined()){
        			strongConnect(v);
        		}
        	}

        	//Analyze SCC
        	for(SCC scc:this.scc){
        		if(scc.size > 1){
        			this.timingEdges.remove(scc.cutLoop());
        			finalIteration = false;
        			cutLoops++;
        		}
        	}
        	
        	System.out.println("\titeration " + iteration + " | " + cutLoops + " loops cut");
    	}

    	long end = System.nanoTime();
    	double time = (end - start) * 1e-9;

    	System.out.printf("\n\tcut loops took %.2f s\n\n", time);
    }
    private void strongConnect(TimingNode v){
    	v.setIndex(this.index);
    	v.setLowLink(this.index);
    	this.index++;

    	this.stack.add(v);
    	v.putOnStack();

    	for(TimingEdge e:v.getSinks()){
    		TimingNode w = e.getSink();
    		if(w.undefined()){
    			strongConnect(w);
    			
    			int lowLink = Math.min(v.getLowLink(), w.getLowLink());
    			v.setLowLink(lowLink);
    		}else if(w.onStack()){
    			int lowLink = Math.min(v.getLowLink(), w.getIndex());
    			v.setLowLink(lowLink);
    		}
    	}
    	
    	if(v.getLowLink() == v.getIndex()){
    		TimingNode w;
    		SCC scc = new SCC();
    		do{
    			w = this.stack.pop();
    			w.removeFromStack();
    			scc.addElement(w);
    		}while(w != v);
    		this.scc.add(scc);
    	}
    }
    
    public class SCC {
    	//Class for strongly connected component
    	List<TimingNode> elements;
    	int size;

    	SCC(){
    		this.elements = new ArrayList<>();
    		this.size = 0;
    	}

    	void addElement(TimingNode v){
    		this.elements.add(v);
    		this.size++;
    	}

    	TimingEdge cutLoop(){
    		TimingNode v = this.elements.get(this.elements.size()-1);

    		for(TimingEdge e:v.getSinks()){
    			TimingNode w = e.getSink();
    			if(w == this.elements.get(this.elements.size()-2)){
    	    		v.removeSink(e);
    	    		w.removeSource(e);

    	    		return e;
    			}
    		}
    		System.out.println("Edge not cut correctly");
    		return null;
    	}
    }

    /****************************************************************
     * These functions calculate the criticality of all connections *
     ****************************************************************/

    public void setCriticalityExponent(double criticalityExponent) {
        for(int i = 0; i <= 20; i++) {
            this.criticalityLookupTable[i] = Math.pow(i * 0.05, criticalityExponent);
        }
    }

    public double getMaxDelay() {
        return this.maxDelay * 1e9;
    }
    public double calculateMaxDelay(boolean calculateWireDelays) {
        if(calculateWireDelays) {
            this.calculateWireDelays();
        }

        this.calculateArrivalTimesAndCriticalities(false);
        return this.maxDelay;
    }
    public void calculateCriticalities(boolean calculateWireDelays) {
    	
        if(calculateWireDelays) {
            this.calculateWireDelays();
        }

        this.calculateArrivalTimesAndCriticalities(true);
    }
    public void calculateCriticalities(boolean calculateWireDelays, double sysMaxDelay) {
        if(calculateWireDelays) {
            this.calculateWireDelays();
        }

        this.calculateArrivalTimesAndCriticalities(true, sysMaxDelay);
    }
 
    //private void calculateArrivalTimesAndCriticalities(boolean calculateCriticalities, boolean firstUpdate) {
    private void calculateArrivalTimesAndCriticalities(boolean calculateCriticalities) {
        //GLOBAL MAX DELAY
    	this.maxDelay = 0;
    	//ARRIVAL TIME
        for(TimingNode rootNode: this.rootNodes){
        	rootNode.setArrivalTime(0);
        }

        for(TimingNode node : this.arrivalTraversal) {
        	if(!node.isSLLsource()) {
        		node.updateArrivalTime();
        	}
        }
        
        
        for(TimingNode leafNode: this.leafNodes){
        	this.maxDelay = Math.max(this.maxDelay, (leafNode.getArrivalTime() - leafNode.getClockDelay()));
        }

        if(calculateCriticalities) {
        	//REQUIRED TIME
        	for(TimingNode leafNode: this.leafNodes) {
        		leafNode.setRequiredTime(this.maxDelay + leafNode.getClockDelay());
        	}
            for(TimingNode node: this.requiredTraversal) {
            	if(!node.isSLLsink()) {
            		node.updateRequiredTime();
            	}
            }
            for(TimingEdge edge:this.globalTimingEdges){
            	double slack = 0;
            	if(edge.getSource().getSLLnodestatus()) {
            		slack = edge.getSink().getRequiredTime() - edge.getSource().getArrivalTime() - edge.getTotalDelay() - (this.circuit.getArchitecture().sllDelay);
            	}else {
            		slack = edge.getSink().getRequiredTime() - edge.getSource().getArrivalTime() - edge.getTotalDelay();
            	}
            	
        		slack = Math.min(slack,	this.maxDelay);
        		slack = Math.max(slack,	0);
                double val = (1 - slack/this.maxDelay) * 20;
                int i = Math.min(19, (int) val);
                double linearInterpolation = val - i;

                edge.setCriticality(
                        (1 - linearInterpolation) * this.criticalityLookupTable[i]
                        + linearInterpolation * this.criticalityLookupTable[i+1]);
            }
        }
    }
    private void calculateArrivalTimesAndCriticalities(boolean calculateCriticalities, double maxSysDelay) {
        //GLOBAL MAX DELAY
    	this.maxDelay = 0;
    	//ARRIVAL TIME
        for(TimingNode rootNode: this.rootNodes){
        	rootNode.setArrivalTime(0);
        }

        for(TimingNode node : this.arrivalTraversal) {

        	if(!node.isSLLsource()) {
        		node.updateArrivalTime();        		
        	}
        }
        
        
        for(TimingNode leafNode: this.leafNodes){        	
        	this.maxDelay = Math.max(this.maxDelay, (leafNode.getArrivalTime() - leafNode.getClockDelay()));
//        	this.maxDelay = Math.max(this.maxDelay, maxSysDelay);
        }
        if(calculateCriticalities) {
        	//REQUIRED TIME
        	for(TimingNode leafNode: this.leafNodes) {
        		leafNode.setRequiredTime(this.maxDelay + leafNode.getClockDelay());
        		
        		
        	}
            for(TimingNode node: this.requiredTraversal) {
            	if(!node.isSLLsink()) {
            		node.updateRequiredTime();
            	}
            }
            for(TimingEdge edge:this.globalTimingEdges){
            	double slack = 0;
            	slack = edge.getSink().getRequiredTime() - edge.getSource().getArrivalTime() - edge.getTotalDelay();


        		slack = Math.min(slack,	this.maxDelay);
        		slack = Math.max(slack,	0);

                double val = (1 - slack/this.maxDelay) * 20;
                int i = Math.min(19, (int) val);
                double linearInterpolation = val - i;

                edge.setCriticality(
                        (1 - linearInterpolation) * this.criticalityLookupTable[i]
                        + linearInterpolation * this.criticalityLookupTable[i+1]);
                
            }

        }
    }

    public void calculateWireDelays() {
    	for(TimingEdge edge:this.timingEdges){
    		edge.setWireDelay(edge.calculateWireDelay());
    	}
    }
    
    public String criticalPathToString() {
    	List<TimingNode> criticalPath = new ArrayList<>();
		TimingNode node = this.getEndNodeOfCriticalPath();
		criticalPath.add(node);
		while(!node.getSources().isEmpty()){
    		node = this.getSourceNodeOnCriticalPath(node);
    		criticalPath.add(node);
    	}
    	
    	int maxLen = 25;
    	for(TimingNode criticalNode:criticalPath){
    		if(criticalNode.toString().length() > maxLen){
    			maxLen = criticalNode.toString().length();
    		}
    	}
    	
    	System.out.println();
    	String delay = String.format("Critical path: %.3f ns", this.maxDelay * Math.pow(10, 9));
    	String result = String.format("%-" + maxLen + "s  %-3s %-3s  %-9s %-8s\n", delay, "x", "y", "Tarr (ns)", "LeafNode");
    	result += String.format("%-" + maxLen + "s..%-3s.%-3s..%-9s.%-8s\n","","","","","").replace(" ", "-").replace(".", " ");
    	for(TimingNode criticalNode:criticalPath){
    		result += this.printNode(criticalNode, maxLen);
    	}
    	return result;
    }
    private TimingNode getEndNodeOfCriticalPath(){
    	TimingNode endNode = null;
    	for(TimingNode leafNode: this.leafNodes){
    		if(compareDouble(leafNode.getArrivalTime(), this.maxDelay)){
    			if(endNode == null){
    				endNode = leafNode;
    			}else{
    				System.out.println("Warning: more than one end node has an arrival time equal to the critical path delay");
    			}
    		}
    	}
    	return endNode;
    }
    private TimingNode getSourceNodeOnCriticalPath(TimingNode sinkNode){
    	TimingNode sourceNode = null;
		for(TimingEdge edge: sinkNode.getSources()){
			if(this.compareDouble(edge.getSource().getArrivalTime(), sinkNode.getArrivalTime() - edge.getTotalDelay())){
				if(sourceNode == null){
					sourceNode = edge.getSource();
				}else{
					sourceNode = edge.getSource();
					System.out.println("Warning: more than one source node on the critical path");
	
				}
			}
		}
		return sourceNode;
    }
    private String printNode(TimingNode node, int maxLen){
    	String nodeInfo = node.toString();
    	int x = node.getGlobalBlock().getColumn();
    	int y = node.getGlobalBlock().getRow();
    	double delay = node.getArrivalTime() * Math.pow(10, 9);
    	int leafNode = node.getGlobalBlock().getLeafNode().getIndex();
    	
    	return String.format("%-" + maxLen + "s  %-3d %-3d  %-9s %-8d\n", nodeInfo, x, y, String.format("%.3f", delay), leafNode);
    }
    private boolean compareDouble(double var1, double var2){
    	return Math.abs(var1 - var2) < Math.pow(10, -12);
    }
    /*************************************************
     * Functions that facilitate simulated annealing *
     *************************************************/
    
    public double calculateTotalCost() {
    	double totalCost = 0;

    	for(List<TimingEdge> net : this.timingNets) {
    		
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
    
  

    public double calculateDeltaCost(Swap swap) {
    	double cost = 0;
    	
    	this.affectedNodes.clear();

    	// Switch the positions of the blocks
    	try {
    		swap.apply();
    	} catch(PlacementException e) {
    		e.printStackTrace();
    	}

    	int numBlocks = swap.getNumBlocks();
    	for(int i = 0; i < numBlocks; i++) {
    		GlobalBlock block1 = swap.getBlock1(i);
    		GlobalBlock block2 = swap.getBlock2(i);

    		if(block1 != null) {
    			cost += this.calculateDeltaCost(block1, block2);
    		}
    		if(block2 != null) {
    			cost += this.calculateDeltaCost(block2, block1);
    		}
    	}



    	// Put the blocks back in their original position
    	try {
    		swap.undoApply();
    	} catch(PlacementException e) {
    		e.printStackTrace();
    	}

    	return cost;
    }

    private double calculateDeltaCost(GlobalBlock block1, GlobalBlock block2) {
    	List<TimingNode> nodes1 = block1.getTimingNodes();
    	this.affectedNodes.addAll(nodes1);

    	double cost = 0;
    	for(TimingNode node : nodes1) {
    		cost += node.calculateDeltaCost(block2);
    	}

    	return cost;
    }

    public void pushThrough() {
    	for(TimingNode node : this.affectedNodes) {
    		node.pushThrough();
    	}
    }

    public void revert() {
    	// Do nothing
    }
    
    
    /******************This is to print all the arrival and required times ***********/
    public void printTimingInfo() {
		System.out.print("\n");
		System.out.print("Timing Node \t Arrival time \t Required time");
    	for(TimingNode timingNode:this.timingNodes){
    		System.out.print("\n");
    		System.out.print(timingNode + "\t" + timingNode.getArrivalTime() + "\t" + timingNode.getRequiredTime());
    		
    	}
    }
}
