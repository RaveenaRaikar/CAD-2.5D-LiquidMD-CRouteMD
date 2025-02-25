package route.circuit.timing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import route.util.Pair;
import route.circuit.Circuit;
import route.circuit.architecture.BlockCategory;
import route.circuit.architecture.BlockType;
import route.circuit.architecture.PortType;
import route.circuit.block.AbstractBlock;
import route.circuit.block.GlobalBlock;
import route.circuit.block.LeafBlock;
import route.circuit.pin.AbstractPin;
import route.circuit.pin.GlobalPin;
import route.circuit.pin.LeafPin;
import route.circuit.resource.RouteNode;
import route.circuit.timing.TimingNode.Position;
import route.route.Connection;

public class TimingGraph {

    private static String VIRTUAL_IO_CLOCK = "virtual-io-clock";

    private Circuit circuit;

    // A map of clock domains, along with their unique id
    private Map<String, Integer> clockNamesToDomains = new HashMap<>();
    
    private Map<Integer, Integer> clockDomainFanout = new HashMap<>();
    private int numClockDomains = 0;
    private int virtualIoClockDomain;

    private List<TimingNode> sllTimingNodes = new ArrayList<>();
    private List<TimingNode> timingNodes = new ArrayList<>();
    private Map<Integer, List<TimingNode>> rootNodes, leafNodes;

    private List<TimingEdge> timingEdges  = new ArrayList<>();
    private List<List<TimingEdge>> timingNets = new ArrayList<>();

    //Multi-Clock Domain
    private String[] clockNames;
    private boolean clockDomainsSet;
    private boolean[][] includeClockDomain;
    private float[][] maxDelay;
    private float globalMaxDelay;
    
    //Tarjan's strongly connected components algorithm
    private int index;
    private Stack<TimingNode> stack;
    private List<SCC> scc;

    public TimingGraph(Circuit circuit) {
        this.circuit = circuit;

        // Create the virtual io clock domain
        this.virtualIoClockDomain = 0;
        this.clockNamesToDomains.put(VIRTUAL_IO_CLOCK, this.virtualIoClockDomain);
        this.clockDomainFanout.put(this.numClockDomains, 0);
        this.numClockDomains++;
        
        this.clockDomainsSet = false;
        
    }

    /******************************************
     * These functions build the timing graph *
     ******************************************/

    public void build() {
    	System.out.println("Build timing graph\n");
        this.buildGraph();

        this.cutCombLoop();
      
        this.setRootAndLeafNodes();
    
        this.setClockDomains();

    }
    
    public void initializeTiming() {
    	this.calculatePlacementEstimatedWireDelay();
    	this.calculateArrivalRequiredAndCriticality(1, 1);
    	System.out.println("-------------------------------------------------------------------------------");
    	System.out.println("|        Timing information (based on placement estimated wire delay)         |");
    	System.out.println("-------------------------------------------------------------------------------");
    	this.printDelays();
        System.out.println();
        System.out.println();
    }

    private void buildGraph() {
        List<Float> clockDelays = new ArrayList<Float>();

        // Create all timing nodes
        for(BlockType leafBlockType : BlockType.getLeafBlockTypes()) {
            boolean isClocked = leafBlockType.isClocked();
           
            for(AbstractBlock abstractBlock : this.circuit.getBlocks(leafBlockType)) {
                LeafBlock block = (LeafBlock) abstractBlock;
                    boolean isConstantGenerator = isClocked ? false : this.isConstantGenerator(block);
                    // Get the clock domain and clock setup time
                    int clockDomain = -1;
                    float clockDelay = 0;
                    if(isClocked && !block.getName().equals("open")) {
                        Pair<Integer, Float> clockDomainAndDelay = this.getClockDomainAndDelay(block);
                        clockDomain = clockDomainAndDelay.getFirst();
                        clockDelay = clockDomainAndDelay.getSecond();
	                    } else if(isConstantGenerator) {
	                        clockDomain = this.virtualIoClockDomain;
	                    }

                    // If the block is clocked: create TimingNodes for the input pins
                    if(isClocked) {
                    	for(AbstractPin abstractPin : block.getInputPins()) {
                    		if(abstractPin.getSource() != null) {
                            	//Find source of net and check if it is the source of a global net.
                            	AbstractPin source = abstractPin;
                            	while(source.getSource() != null) {
                            		source = source.getSource();
                            	}

                            	if(!this.isSourceOfGlobalNet(source)) {
                            		LeafPin inputPin = (LeafPin) abstractPin;	
                            		TimingNode node = new TimingNode(block.getGlobalParent(), inputPin, Position.LEAF, clockDomain, clockDelay);
                            		inputPin.setTimingNode(node);
                            		clockDelays.add(0f);
                            		this.timingNodes.add(node);
                            		
                            		this.clockDomainFanout.put(clockDomain, this.clockDomainFanout.get(clockDomain) + 1);
                            	}
                            }
                        }
                    }
                    
                    
                    // Add the output pins of this timing graph root or intermediate node
                    Position position = (isClocked || isConstantGenerator) ? Position.ROOT : Position.INTERMEDIATE;
                    for(AbstractPin abstractPin : block.getOutputPins()) {
                        LeafPin outputPin = (LeafPin) abstractPin;
                        if(outputPin.getNumSinks() > 0 && !this.isSourceOfClockNet(outputPin) && !this.isSourceOfGlobalNet(outputPin)) {
                        	TimingNode node = new TimingNode(block.getGlobalParent(), outputPin, position, clockDomain, clockDelay);
                        	outputPin.setTimingNode(node);

                        	this.timingNodes.add(node);
                        	if(position == Position.ROOT) {
                        		clockDelays.add(clockDelay + outputPin.getPortType().getSetupTime());
                        	} else {
                        		clockDelays.add(0f);
                        	}
                        }else if(outputPin.getNumSinks() > 0 && !this.isSourceOfClockNet(outputPin) && this.isSourceOfGlobalNet(outputPin)) {
                        	TimingNode node = new TimingNode(block.getGlobalParent(), outputPin, position, clockDomain, clockDelay);
                        	outputPin.setTimingNode(node);

                        	this.timingNodes.add(node);
                        	if(position == Position.ROOT) {
                        		clockDelays.add(clockDelay + outputPin.getPortType().getSetupTime());
                        	} else {
                        		clockDelays.add(0f);
                        		this.timingNodes.add(node);
                        		
                        		this.clockDomainFanout.put(clockDomain, this.clockDomainFanout.get(clockDomain) + 1);
                        	}
                        }
                    }
                }
        	}
//        }
        
        //Add timing nodes for the connections
        for(GlobalBlock globalBlock : this.circuit.getGlobalBlocks()) {
        	for(AbstractPin abstractPin : globalBlock.getOutputPins()) {
        		GlobalPin outputPin = (GlobalPin) abstractPin;
        		
        		AbstractPin current = outputPin;
        		while(current.getSource() != null) {
        			current = current.getSource();
        		}
        		
        		AbstractPin pathSource = current;

        		if(pathSource.hasTimingNode()) {
        			if(outputPin.getNumSinks() > 0) {
            			TimingNode node = new TimingNode(globalBlock, outputPin, Position.C_SOURCE, -1, 0);
            			outputPin.setTimingNode(node);

            			
            			this.timingNodes.add(node);  
            			
            			clockDelays.add(0f);
            		}
        		}
        		
        	}
        }
        for(GlobalBlock globalBlock : this.circuit.getGlobalBlocks()) {
        	for(AbstractPin abstractPin : globalBlock.getInputPins()) {
        		GlobalPin inputPin = (GlobalPin) abstractPin;
        		if(inputPin.getSource() != null) {
        			if(inputPin.getSource().hasTimingNode()) {
            			TimingNode node = new TimingNode(globalBlock, inputPin, Position.C_SINK, -1, 0);
            			inputPin.setTimingNode(node);
            			
            			this.timingNodes.add(node);  
            			
            			clockDelays.add(0f);
            		}
        		}
        	}
        }
        
        //Connect the timing nodes
        int numNodes = this.timingNodes.size();
        for(int i = 0; i < numNodes; i++) {
            TimingNode node = this.timingNodes.get(i);
            if(node.getPosition() != Position.LEAF && !this.isSourceOfClockNet(node)) {
                this.traverseFromSource(node, clockDelays.get(i));
            }
        }
        
        for(TimingNode node : this.timingNodes) {
            node.compact();
        }
        
        System.out.println("Clock domain fanout");
        for(int clockDomain = 0; clockDomain < this.numClockDomains; clockDomain++) {
        	System.out.println("   " + clockDomain + ": " + this.clockDomainFanout.get(clockDomain));
        }
        System.out.println();
    }
    
    private boolean isSourceOfClockNet(TimingNode node) {
    	return this.isSourceOfClockNet(node.getPin());
    }
    private boolean isSourceOfClockNet(AbstractPin pin) {
    	if(pin.getNumSinks() > 0) {
    		for(AbstractPin sink : pin.getSinks()) {
    			if(!isSourceOfClockNet(sink)) return false;
    		}
    	} else {
    		return pin.isClock();
    	}
    	
    	return true;
    }
    
    
    private boolean isSourceOfGlobalNet(AbstractPin pin) {
    	return this.circuit.getGlobalNetNames().contains(pin.getOwner().getName());
    }
    
    private boolean isConstantGenerator(LeafBlock block) {
        for(AbstractPin inputPin : block.getInputPins()) {
            if(inputPin.getSource() != null) {
                return false;
            }
        }

        return true;
    }

    private Pair<Integer, Float> getClockDomainAndDelay(LeafBlock block) {
        /**
         * This method should only be called for clocked blocks.
         */

        String clockName;
        float clockDelay = 0;

        // If the block is an input
        if(block.getGlobalParent().getCategory() == BlockCategory.IO) {
            clockName = VIRTUAL_IO_CLOCK;

        // If the block is a regular block
        } else{
        	clockName = "UNDIFINED";
        	
            // A clocked leaf block has exactly 1 clock pin
            List<AbstractPin> clockPins = block.getClockPins();
            assert(clockPins.size() == 1);
            AbstractPin clockPin = clockPins.get(0);
            while(true) {
                AbstractPin sourcePin = clockPin.getSource();
                if(sourcePin == null) {
                    break;
                }
                
                if(sourcePin.getOwner().isGlobal()) {
                	clockName = ((GlobalPin) sourcePin).getNetName();
                }
                
                clockDelay += sourcePin.getPortType().getDelay(clockPin.getPortType(), sourcePin.getOwner().getIndex(), clockPin.getOwner().getIndex(), sourcePin.getIndex(), clockPin.getIndex());
                clockPin = sourcePin;
            }
            
            if(clockName.equals("UNDIFINED")) {
            	System.err.println("Clock name is " + clockName);
            }
            
            assert(!clockName.equals(VIRTUAL_IO_CLOCK));

            if(!this.clockNamesToDomains.containsKey(clockName)) {
                this.clockNamesToDomains.put(clockName, this.numClockDomains);
                this.clockDomainFanout.put(this.numClockDomains, 0);
                this.numClockDomains++;
            }
        }
        return new Pair<Integer, Float>(this.clockNamesToDomains.get(clockName), clockDelay);
    }

    private void traverseFromSource(TimingNode pathSourceNode, float clockDelay) {

        GlobalBlock pathSourceBlock = pathSourceNode.getGlobalBlock();
        AbstractPin pathSourcePin = pathSourceNode.getPin();
        Map<GlobalBlock, List<TimingEdge>> sourceTimingNets = new HashMap<>();

        Stack<TraversePair> todo = new Stack<>();
        todo.push(new TraversePair(pathSourcePin, clockDelay));
        

        while(!todo.empty()) {
            TraversePair traverseEntry = todo.pop();
            AbstractPin sourcePin = traverseEntry.pin;
            float delay = traverseEntry.delay;
            
            AbstractBlock sourceBlock = sourcePin.getOwner();
            PortType sourcePortType = sourcePin.getPortType();
            
            
            if((sourcePin.hasTimingNode() && sourcePin != pathSourcePin) || this.isEndpin(sourceBlock, sourcePin, pathSourcePin)) {
                delay += sourcePortType.getSetupTime();
                TimingNode pathSinkNode = sourcePin.getTimingNode();
                // If pathSinkNode is null, this sinkPin doesn't have any sinks
                // so isn't used in the timing graph
                if(pathSinkNode != null) {
                    TimingEdge edge = pathSourceNode.addSink(pathSinkNode, delay, this.circuit.getArchitecture().getDelayTables());
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
                        float sourceSinkDelay = sourcePortType.getDelay(sinkPin.getPortType(), sourcePin.getOwner().getIndex(), sinkPin.getOwner().getIndex(), sourcePin.getIndex(), sinkPin.getIndex());

                        if(sourceSinkDelay >= 0.0){ // Only consider existing connections
                            float totalDelay = delay + sourceSinkDelay;

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

    private class TraversePair {
        AbstractPin pin;
        float delay;

        TraversePair(AbstractPin pin, float delay) {
            this.pin = pin;
            this.delay = delay;
        }
    }

    private void setRootAndLeafNodes(){
    	this.rootNodes = new HashMap<>();
    	this.leafNodes = new HashMap<>();
    
    	for(int clockDomain = 0; clockDomain < this.numClockDomains; clockDomain++) {
    		List<TimingNode> clockDomainRootNodes = new ArrayList<>();
    		List<TimingNode> clockDomainLeafNodes = new ArrayList<>();
    		
	        for (TimingNode timingNode:this.timingNodes) {
	        	if (timingNode.getClockDomain() == clockDomain) {
		        	if (timingNode.getPosition().equals(Position.ROOT)) {
		        		clockDomainRootNodes.add(timingNode);
		        	} else if (timingNode.getPosition().equals(Position.LEAF)) {
		        		clockDomainLeafNodes.add(timingNode);
		        	}
	        	}
	        }
	        System.out.print("\nClock Domain " + clockDomain + " has " + clockDomainRootNodes.size()+ " root nodes");
	        System.out.print("\nClock Domain " + clockDomain + " has " + clockDomainLeafNodes.size()+ " leef nodes");
	        this.rootNodes.put(clockDomain, clockDomainRootNodes);
	        this.leafNodes.put(clockDomain, clockDomainLeafNodes);
    	}
    }

    public Integer getNumClockDomains() {
    	return this.numClockDomains;
    }
    public Map<Integer, List<TimingNode>> getrootNodes(){
    	return this.rootNodes;
    }
    
    public Map<Integer, List<TimingNode>> getleafNodes(){
    	return this.leafNodes;
    }
    
    public Integer getClockDomainFanout(Integer clockDomain) {
    	if(this.clockDomainFanout.containsKey(clockDomain)){
    		return this.clockDomainFanout.get(clockDomain);
    	}else {
    		return 0;
    	}
    	
    }
    
    public Map<String, Integer> getClockNamesToDomains(){
    	return this.clockNamesToDomains;
    }
    
    /****************************************************
     * Functionality to find combinational loops with   *
     * Tarjan's strongly connected components algorithm *
     ****************************************************/
    private void cutCombLoop(){

    	long start = System.nanoTime();

    	System.out.println("Cut combinational loops iteratively");

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
    	System.out.printf("\n\tcut loops took %.2f s\n\n", (end - start) * 1e-9);
    }
    private void strongConnect(TimingNode v){
    	v.setIndex(this.index);
    	v.setLowLink(this.index);
    	this.index++;

    	this.stack.add(v);
    	v.putOnStack();

    	for(TimingEdge e:v.getSinkEdges()){
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

    		for(TimingEdge e:v.getSinkEdges()){
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
    public float getMaxDelay() {
        return 1e9f * this.globalMaxDelay;
    }

    public void calculatePlacementEstimatedWireDelay() {
    	for(TimingEdge edge : this.timingEdges) {
    		edge.calculatePlacementEstimatedWireDelay();
    	}
    }
    public void calculateActualWireDelay() {
    	//Set wire delay of the connections
    	for(Connection connection : this.circuit.getConnections()) {
    		float wireDelay = 0;
    		for(RouteNode routeNode : connection.routeNodes) {
    			wireDelay += routeNode.getDelay();
    		}
    		connection.setWireDelay(wireDelay);
    	}
    }

    public void calculateArrivalRequiredAndCriticality(float maxCriticality, float criticalityExponent) {
    	//Initialization
        this.globalMaxDelay = 0;
        
        for(Connection connection : this.circuit.getConnections()) {
        	connection.resetCriticality();
        }
        
        for(int sourceClockDomain = 0; sourceClockDomain < this.numClockDomains; sourceClockDomain++) {
        	for(int sinkClockDomain = 0; sinkClockDomain < this.numClockDomains; sinkClockDomain++) {
        		if(this.includeClockDomain(sourceClockDomain, sinkClockDomain)) {
        			float maxDelay = 0;
        			
        			for(TimingNode node : this.timingNodes) {
        				node.resetArrivalAndRequiredTime();
        			}
                	
        			List<TimingNode> clockDomainRootNodes = this.rootNodes.get(sourceClockDomain);
        			List<TimingNode> clockDomainLeafNodes = this.leafNodes.get(sinkClockDomain);
        			
        			//Arrival time
        			for(TimingNode rootNode: clockDomainRootNodes){
        				rootNode.setArrivalTime(0);
        			}
        			for(TimingNode leafNode: clockDomainLeafNodes){
        				leafNode.recursiveArrivalTime(sourceClockDomain);
        				
        				maxDelay = Math.max((leafNode.getArrivalTime() - leafNode.clockDelay), maxDelay);
        			}
                    
        			this.maxDelay[sourceClockDomain][sinkClockDomain] = maxDelay;
        			if(maxDelay > this.globalMaxDelay) {
        				this.globalMaxDelay = maxDelay;
        			}

        			//Required time
        			for(TimingNode leafNode: clockDomainLeafNodes) {
        				leafNode.setRequiredTime(maxDelay + leafNode.clockDelay);
        			}
        			for(TimingNode rootNode: clockDomainRootNodes) {
        				rootNode.recursiveRequiredTime(sinkClockDomain);
        			}
        			
        			//Criticality
        			for(Connection connection : this.circuit.getConnections()) {
        				connection.calculateCriticality(maxDelay, maxCriticality, criticalityExponent);
        			}
        		}
        	}
        }
    }
    
    public float calculateTotalCost() {
    	float totalCost = 0;

    	for(List<TimingEdge> net : this.timingNets) {
    		float netCost = 0;
    		for(TimingEdge edge : net) {
    			float cost = edge.getCost();
    			if(cost > netCost) {
    				netCost = cost;
    			}
    		}
 
    		totalCost += netCost;
    	}

    	return totalCost;
    }
    
    private void setClockDomains() {
    	//Set clock names
    	this.clockNames = new String[this.numClockDomains];
    	for(String clockDomainName : this.clockNamesToDomains.keySet()) {
    		this.clockNames[this.clockNamesToDomains.get(clockDomainName)] = clockDomainName;
    	}
    	
		for(TimingNode node : this.timingNodes) {
			node.setNumClockDomains(this.numClockDomains);
		}
		
		for(TimingNode node : this.timingNodes) {		
    		if(node.getPosition() == Position.LEAF) {
    	    	node.setSourceClockDomains();
    		} else if(node.getPosition() == Position.ROOT) {
    			if(!node.getPin().getSLLSourceStatus()) {

    				node.setSinkClockDomains();
    			}
        		
    		}
    	}
		
		this.includeClockDomain = new boolean[this.numClockDomains][this.numClockDomains];
		this.maxDelay = new float[this.numClockDomains][this.numClockDomains];
    	for(int sourceClockDomain = 0; sourceClockDomain < this.numClockDomains; sourceClockDomain++) {
    		for(int sinkClockDomain = 0; sinkClockDomain < this.numClockDomains; sinkClockDomain++) {
        		this.maxDelay[sourceClockDomain][sinkClockDomain] = -1;
        		this.includeClockDomain[sourceClockDomain][sinkClockDomain] = this.includeClockDomain(sourceClockDomain, sinkClockDomain);
    		}
    	}
    	
    	this.clockDomainsSet = true;
    }
    private boolean includeClockDomain(int sourceClockDomain, int sinkClockDomain) {
    	if(this.clockDomainsSet) {
    		return this.includeClockDomain[sourceClockDomain][sinkClockDomain];
    	} else {
    		if(!this.hasPaths(sourceClockDomain, sinkClockDomain)) {
        		return false;
        	} else {
        		return sourceClockDomain == 0 || sinkClockDomain == 0 || sourceClockDomain == sinkClockDomain;
        	}
    	}
    }
	private boolean hasPaths(int sourceClockDomain, int sinkClockDomain) {
		boolean hasPathsToSource = false;
		boolean hasPathsToSink = false;
		
		for (TimingNode leafNode : this.leafNodes.get(sinkClockDomain)) {
			if (leafNode.hasClockDomainAsSource(sourceClockDomain)) {
				hasPathsToSource = true;
				}
			}
		for (TimingNode rootNode : this.rootNodes.get(sourceClockDomain)) {
			if (rootNode.hasClockDomainAsSink(sinkClockDomain)) {
				hasPathsToSink = true;
			}
		}
		
		if(hasPathsToSource != hasPathsToSink) System.err.println("Has paths from leaf to source is not equal to has paths form sink to source");
		return hasPathsToSource;
	}
	private boolean isNetlistClockDomain(int sourceClockDomain, int sinkClockDomain) {
		return sourceClockDomain != 0 && sinkClockDomain != 0;
	}
    
    public void printDelays() {
		String maxDelayString;
    	for(int sourceClockDomain = 0; sourceClockDomain < this.numClockDomains; sourceClockDomain++) {
    		maxDelayString = this.maxDelay[sourceClockDomain][sourceClockDomain] > 0 ? String.format("%.3f", 1e9 * this.maxDelay[sourceClockDomain][sourceClockDomain]) : "---";
    		System.out.printf("%s to %s: %s\n", this.clockNames[sourceClockDomain], this.clockNames[sourceClockDomain], maxDelayString);

    		for(int sinkClockDomain = 0; sinkClockDomain < this.numClockDomains; sinkClockDomain++) {
    			if(sourceClockDomain != sinkClockDomain) {
        			maxDelayString = this.maxDelay[sourceClockDomain][sinkClockDomain] > 0 ? String.format("%.3f", 1e9 * this.maxDelay[sourceClockDomain][sinkClockDomain]) : "---";
        			System.out.printf("\t%s to %s: %s\n", this.clockNames[sourceClockDomain], this.clockNames[sinkClockDomain], maxDelayString);

    			}
    		}
    	}
    	System.out.println();
    	
    	for(int sourceClockDomain = 0; sourceClockDomain < this.numClockDomains; sourceClockDomain++) {
    		maxDelayString = this.maxDelay[sourceClockDomain][sourceClockDomain] > 0 ? String.format("%.3f", 1e9 * this.maxDelay[sourceClockDomain][sourceClockDomain]) : "---";
    		System.out.printf("%d to %d: %s\n", sourceClockDomain, sourceClockDomain, maxDelayString);
    		for(int sinkClockDomain = 0; sinkClockDomain < this.numClockDomains; sinkClockDomain++) {
    			if(sourceClockDomain != sinkClockDomain) {
        			maxDelayString = this.maxDelay[sourceClockDomain][sinkClockDomain] > 0 ? String.format("%.3f", 1e9 * this.maxDelay[sourceClockDomain][sinkClockDomain]) : "---";
        			System.out.printf("  %d to %d: %s\n", sourceClockDomain, sinkClockDomain, maxDelayString);
    			}
    		}
    	}
    	System.out.println();
		
		float geomeanPeriod = 1;
		float fanoutWeightedGeomeanPeriod = 0;
		int totalFanout = 0;
		int numValidClockDomains = 0;
		for(int sourceClockDomain = 0; sourceClockDomain < this.numClockDomains; sourceClockDomain++) {
    		for(int sinkClockDomain = 0; sinkClockDomain < this.numClockDomains; sinkClockDomain++) {
    			if(this.includeClockDomain(sourceClockDomain, sinkClockDomain) && this.isNetlistClockDomain(sourceClockDomain, sinkClockDomain)) {
					float maxDelay = this.maxDelay[sourceClockDomain][sinkClockDomain];
    				int fanout = this.clockDomainFanout.get(sinkClockDomain);
					
    				geomeanPeriod *= maxDelay;
					totalFanout += fanout;
					fanoutWeightedGeomeanPeriod += Math.log(maxDelay) * fanout;    
					
					numValidClockDomains++;
    			}
			}
		}
		
		geomeanPeriod = (float) Math.pow(geomeanPeriod, 1.0/numValidClockDomains);
		fanoutWeightedGeomeanPeriod = (float) Math.exp(fanoutWeightedGeomeanPeriod/totalFanout);

		System.out.printf("Max delay %.3f ns\n", 1e9 * this.globalMaxDelay);
		System.out.printf("Geometric mean intra-domain period: %.3f ns\n", 1e9 * geomeanPeriod);
		System.out.printf("Fanout-weighted geomean intra-domain period: %.3f ns\n", 1e9 * fanoutWeightedGeomeanPeriod);
		System.out.printf("Timing cost %.3e\n", this.calculateTotalCost());
		System.out.println("-------------------------------------------------------------------------------");
		System.out.println();
    }

    public String criticalPathToString() {
    	int sourceClockDomain = -1;
    	int sinkClockDomain = -1;
    	float maxDelay = 0;
    	
    	for(int i = 0; i < this.numClockDomains; i++) {
    		for(int j = 0; j < this.numClockDomains; j++) {
        		if(this.maxDelay[i][j] > maxDelay) {
        			maxDelay = this.maxDelay[i][j];
        			sourceClockDomain = i;
        			sinkClockDomain = j;
        		}
        	}
    	}
    	List<TimingNode> criticalPath = new ArrayList<>();
		TimingNode node = this.getEndNodeOfCriticalPath(sinkClockDomain);
		criticalPath.add(node);
		while(!node.getSourceEdges().isEmpty()){
    		node = this.getSourceNodeOnCriticalPath(node, sourceClockDomain);
    		criticalPath.add(node);
    	}
    	
    	int maxLen = 25;
    	for(TimingNode criticalNode:criticalPath){
    		if(criticalNode.toString().length() > maxLen){
    			maxLen = criticalNode.toString().length();
    		}
    	}
    	
    	String delay = String.format("Critical path: %.3f ns", this.globalMaxDelay * Math.pow(10, 9));
    	String result = String.format("%-" + maxLen + "s  %-3s %-3s  %-9s %-8s\n", delay, "x", "y", "Tarr (ns)", "LeafNode");
    	result += String.format("%-" + maxLen + "s..%-3s.%-3s..%-9s.%-8s\n","","","","","").replace(" ", "-").replace(".", " ");
    	for(TimingNode criticalNode:criticalPath){
    		result += this.printNode(criticalNode, maxLen);
    	}
    	return result;
    }
    private TimingNode getEndNodeOfCriticalPath(int sinkClockDomain){
    	for(TimingNode leafNode: this.leafNodes.get(sinkClockDomain)){
    		if(compareFloat(leafNode.getArrivalTime(), this.globalMaxDelay)){
    			return leafNode;
    		}
    	}
    	return null;
    }
    private TimingNode getSourceNodeOnCriticalPath(TimingNode sinkNode, int sourceClockDomain){
		for(TimingEdge edge: sinkNode.getSourceEdges()){
			if(this.compareFloat(edge.getSource().getArrivalTime(), sinkNode.getArrivalTime() - edge.getTotalDelay())){
				if(edge.getSource().hasClockDomainAsSource(sourceClockDomain)) {
					return edge.getSource();
				}
			}
		}
		return null;
    }
    
    
    public List<TimingNode> getTimingNodes() {
    	return this.timingNodes;
    }
    
    public List<TimingEdge> getTimingEdges() {
    	return this.timingEdges;
    }
    
    public List<TimingNode> getSlltimingNodes() {
    	return this.sllTimingNodes;
    }
    

    
    public void printTimingEdges() {
    	for(TimingEdge edge : this.timingEdges) {
    		TimingNode sourceNodePin = edge.getSource();
    		TimingNode sinkNodePin = edge.getSink();
    		
    		System.out.print("\nThe sink is " + sinkNodePin.getPin() + " and the arrival time is " + ((sinkNodePin.getArrivalTime() - sinkNodePin.clockDelay)*Math.pow(10, 9)));
    	}
    }
    
    public List<List<TimingEdge>> getTimingNets(){
    	return this.timingNets;
    }
    
    private String printNode(TimingNode node, int maxLen){
    	String nodeInfo = node.toString();
    	int x = node.getGlobalBlock().getColumn();
    	int y = node.getGlobalBlock().getRow();
    	double delay = node.getArrivalTime() * Math.pow(10, 9);
    	
    	return String.format("%-" + maxLen + "s  %-3d %-3d  %-9s\n", nodeInfo, x, y, String.format("%.3f", delay));
    }
    private boolean compareFloat(float var1, float var2){
    	return Math.abs(var1 - var2) < Math.pow(10, -12);
    }
}
