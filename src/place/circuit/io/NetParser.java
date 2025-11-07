package place.circuit.io;

import place.circuit.Circuit;
import place.circuit.architecture.Architecture;
import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.architecture.PortType;
import place.circuit.block.AbstractBlock;

import place.circuit.block.GlobalBlock;
import place.circuit.block.LeafBlock;
import place.circuit.block.LocalBlock;
import place.circuit.block.SLLNetBlocks;
import place.circuit.pin.AbstractPin;

import place.circuit.pin.GlobalPin;


import java.io.*;
import java.util.*;


public class NetParser {


    private Architecture architecture;
    private String circuitName;
    private BufferedReader reader;
    private int dieNum;
    private int TotDie;
    private int SLLRows;

    private Map<BlockType, List<AbstractBlock>> blocks;
    private Map<String, AbstractBlock> SLLDummyBlocks;
    private List<AbstractBlock> localSLLBlocks;
    private Map<String, SLLNetBlocks> netToBlockSLL;
    private LinkedList<AbstractBlock> blockStack;
    private Stack<TupleBlockMap> inputsStack;
    private Stack<Map<String, String>> outputsStack;
    private Stack<Map<String, String>> clocksStack;

    
    private Integer SllBlockNum; //This is required to give an index to the dummy blocks

    
    private ArrayList<String> SLLNets;
    private HashMap<String, sllNet> sllNetInfo;
    private Map<String, AbstractPin> sourcePins;

    private enum PortDirection {INPUT, OUTPUT, CLOCK};
    private PortDirection currentPortType;
    
    // TO add info to the AddedsllBlocks MAP : There are 3 cases that exist
    //Case 1: SLL net is the input to the block. Add the dummy block with output pin 
    //Case 2: SLL net is the output of the block.  Add the dummy block with input pin 
    //Case 3: The SLL net originates and terminates at this block. In this case we need to create the dummy block with the input pin only 
    //as the net may be connected to a block on the other die. If input pin is added, replace it with output pin


    public NetParser(Architecture architecture, String circuitName, File file,int totDie, int dieNum, int sllRows) throws FileNotFoundException {
        this.architecture = architecture;
        this.circuitName = circuitName;
        this.reader = new BufferedReader(new FileReader(file));

        this.TotDie = totDie;
        this.SLLRows = sllRows;
    }


    public Circuit parse(int dieCounter) throws IOException {
        // A list of all the blocks in the circuit
    	this.dieNum = dieCounter;
        this.blocks = new HashMap<BlockType, List<AbstractBlock>>();
        this.SLLDummyBlocks = new HashMap<String, AbstractBlock>();
        this.localSLLBlocks = new ArrayList<AbstractBlock>();
        this.SLLNets = new ArrayList<String>();
        this.sllNetInfo = new HashMap<String, sllNet>();
        this.netToBlockSLL = new HashMap<String, SLLNetBlocks>();
        this.SllBlockNum = 0;
        // blockStack is a stack that contains the current block hierarchy.
        // It is used to find the parent of a block. outputsStack contains
        // the outputs of these blocks. This is necessary because the outputs
        // of a block can only be processed after all the childs have been
        // processed.
        this.blockStack = new LinkedList<AbstractBlock>();
        this.inputsStack = new Stack<TupleBlockMap>();

        this.outputsStack = new Stack<Map<String, String>>();  
        this.clocksStack = new Stack<Map<String, String>>();


        // sourcePins contains the names of the outputs of leaf blocks and
        // the corresponding output pins. It is needed to be able to create
        // global nets: at the time: only the name of the bottom-level source
        // block is given for these nets.
        this.sourcePins = new HashMap<String, AbstractPin>();
        long start = System.nanoTime();
        Boolean isSLL = false;
        String [] SLLNets;
        String line, multiLine = "";

        while ((line = this.reader.readLine()) != null) {

            String trimmedLine = line.trim();

            // Add the current line to the multiLine
            if(multiLine.length() > 0) {
                multiLine += " ";
            }
            multiLine += trimmedLine;

            if(!this.isCompleteLine(multiLine)) {
                continue;
            }
        	if(multiLine.contains("<SLLs")){
 
            }else if(multiLine.contains("</SLLs")) {
         
            	isSLL = true;
            }
        	
        	if(isSLL){
            	SLLNets = multiLine.split(" ");
            	for(String SLLConn:SLLNets) {
            		if(!SLLConn.contains("SLL")) {
            			this.SLLNets.add(SLLConn);
            			sllNet newSLL = new sllNet(SLLConn,null,new ArrayList<AbstractPin>());
            			this.sllNetInfo.put(SLLConn, newSLL);
            			this.netToBlockSLL.put(SLLConn, new SLLNetBlocks(SLLConn));
            		}
            	}
            	isSLL = false;
            }else{
		        String lineStart = multiLine.substring(0, 5);

		        switch(lineStart) {
		        case "<inpu":
		            this.processInputLine(multiLine);
		            break;
		
		
		        case "<outp":
		            this.processOutputLine(multiLine);
		            break;
		
		        case "<cloc":
		            this.processClockLine(multiLine);
		            break;
		
		
		        case "<port":
		            this.processPortLine(multiLine);
		            break;
		
		
		        case "<bloc":
		            if(!multiLine.substring(multiLine.length() - 2).equals("/>")) {
		                this.processBlockLine(multiLine);
		               
		            } else {
		                this.processBlockLine(multiLine);
		                this.processBlockEndLine();
		            }
		            break;
		
		
		        case "</blo":
		        	
		            this.processBlockEndLine();
		            break;
		        }
        	}
            multiLine = "";
        }

        System.out.println("\nThe count of SLL connections are " + this.SLLNets.size());
        System.out.print("\nThe dieCounter is " + dieCounter);
 
        this.addSLLBlocks();

        Circuit circuit = new Circuit(this.circuitName, this.architecture, this.blocks, this.TotDie, dieCounter, this.SLLRows);
        circuit.initializeData();
        return circuit;
    }


    private boolean isCompleteLine(String line) {
        int lineLength = line.length();

        // The line is empty
        if(lineLength == 0) {
            return false;
        }


        // The line doesn't end with a ">" character
        if(!line.substring(lineLength - 1).equals(">")) {
            return false;
        }

        // The line is a port line, but not all ports are on this line
        if(lineLength >= 7
                && line.substring(0, 5).equals("<port")
                && !line.substring(lineLength - 7).equals("</port>")) {
            return false;
        }

        return true;
    }


    @SuppressWarnings("unused")
    private void processInputLine(String line) {
        this.currentPortType = PortDirection.INPUT;
    }

    @SuppressWarnings("unused")
    private void processOutputLine(String line) {
        this.currentPortType = PortDirection.OUTPUT;
    }

    @SuppressWarnings("unused")
    private void processClockLine(String line) {
        this.currentPortType = PortDirection.CLOCK;
    }


    private void processPortLine(String line) {

        // This is a clock port
        if(this.currentPortType == null) {
            return;
        }

        int nameStart = 12;
        int nameEnd = line.indexOf("\"", 12);
        String name = line.substring(nameStart, nameEnd);

        int portsStart = nameEnd + 2;
        int portsEnd = line.length() - 7;
        String ports = line.substring(portsStart, portsEnd);

        switch(this.currentPortType) {
            case INPUT:
                this.inputsStack.peek().getMap().put(name, ports);
                break;

            case OUTPUT:
                this.outputsStack.peek().put(name, ports);
                break;

            case CLOCK:
                this.clocksStack.peek().put(name, ports);
                break;
        }
    }


    private void processBlockLine(String line) {

        int nameStart = 13;
        int nameEnd = line.indexOf("\"", nameStart);
        String name = line.substring(nameStart, nameEnd);

        int typeStart = nameEnd + 12;
        int typeEnd = line.indexOf("[", typeStart);
        String type = line.substring(typeStart, typeEnd);

        // Ignore the top-level block
        if(type.equals("FPGA_packed_netlist")) {
            return;
        }


        int indexStart = typeEnd + 1;
        int indexEnd = line.indexOf("]", indexStart);
        int index = Integer.parseInt(line.substring(indexStart, indexEnd));


        int modeStart = indexEnd + 9;
        int modeEnd = line.indexOf("\"", modeStart); //line.length() - 2;
        String mode = modeStart < modeEnd ? line.substring(modeStart, modeEnd) : null;


        BlockType parentBlockType = this.blockStack.isEmpty() ? null : this.blockStack.peek().getType();
        if (mode != null && mode.equals("default")){
            mode = type;
        }
        if (mode == null && name.equals("open")){
            mode = "X"; // == don't care; The mode for an open block might not be specified, then give a don't care to the blocktype initializer.
        }
        BlockType blockType = new BlockType(parentBlockType, type, mode);


        AbstractBlock newBlock;
        if(blockType.isGlobal()) {
            newBlock = new GlobalBlock(name, blockType, index,false);

        } else {
            AbstractBlock parent = this.blockStack.peek();

            //IF the block type is LUT or FF then it is considered as a leaf block.
            if(blockType.isLeaf()) {
                GlobalBlock globalParent = (GlobalBlock) this.blockStack.peekLast();
                newBlock = new LeafBlock(name, blockType, index, parent, globalParent);

            } else {
                newBlock = new LocalBlock(name, blockType, index, parent);
            }
            
            ///TO MARK BLOCKS AS SLL BLOCKS
            GlobalBlock globalParent = (GlobalBlock) this.blockStack.peekLast();
            if(this.SLLNets.contains(name)) {
            	globalParent.setSLLStatus(true);

            }else {
            	globalParent.setSLLStatus(false);
            }

            
        	
        }
        this.blockStack.push(newBlock);
        this.inputsStack.push(new TupleBlockMap(newBlock));
        this.outputsStack.push(new HashMap<String, String>());
        this.clocksStack.push(new HashMap<String, String>());


        if(!this.blocks.containsKey(blockType)) {
            BlockType emptyModeType = new BlockType(parentBlockType, blockType.getName());
            this.blocks.put(emptyModeType, new ArrayList<AbstractBlock>());
        }
        this.blocks.get(blockType).add(newBlock);
    }

    private void CreateSLLDummyBlock(String SLLNet, AbstractPin SLLPin) {

    	String SLLConn = SLLNet;

    	AbstractPin ALBPin = SLLPin;
    	
    	String BlockName = "Dummy_" + SLLConn;
    	BlockType SLLBlocktype = new BlockType(null,"DummyAlb");
    	AbstractBlock dummyBlock = null;
    	int sourcePinIndex = 0;
    	AbstractPin SourcePin,TerminalPin = null;
    	AbstractPin globalSourcePin = null;
    	SLLNetBlocks sllBlockInfo = this.netToBlockSLL.get(SLLNet);
    	
    	
    	if(this.SLLDummyBlocks.containsKey(BlockName)) {
   
    		dummyBlock = this.SLLDummyBlocks.get(BlockName);

    		if(ALBPin.isInput()) {

    			if(!dummyBlock.isSLLSink()) {
	    			String sourcePortName = "dataout";
	    			PortType dummyPortType = new PortType(dummyBlock.getType(), sourcePortName);
	    			TerminalPin = dummyBlock.getPin(dummyPortType, sourcePinIndex);
	        		TerminalPin.addSink(ALBPin);
	        		ALBPin.setSource(TerminalPin);
	    
	        		GlobalPin netSource = (GlobalPin)TerminalPin;

	        		netSource.setNetName(SLLConn);
	        		GlobalPin netSink = (GlobalPin)ALBPin;
	        		netSink.setNetName(SLLConn);
	        		dummyBlock.setSLLsource(true);
	        		sllBlockInfo.addDummySource(netSource);
	        		sllBlockInfo.addSinkPins(netSink);
	        		netSink.getOwner().isSLLSink = true;
	        
        		}
    		}else if(ALBPin.isOutput()) {
    			globalSourcePin = GetGlobalPin(SLLConn);

	    			  			
    			String sourcePortName = "datain";
    			PortType dummyPortType = new PortType(dummyBlock.getType(), sourcePortName);
    			SourcePin = dummyBlock.getPin(dummyPortType, sourcePinIndex);
    			SourcePin.setSource(globalSourcePin);
    			globalSourcePin.addSink(SourcePin);

    			GlobalPin netSource = (GlobalPin) globalSourcePin;
    			netSource.setNetName(SLLConn);
    			GlobalPin netSink = (GlobalPin) SourcePin;
    			netSink.setNetName(SLLConn);
    			dummyBlock.setSLLsink(true);
    			dummyBlock.setSLLsource(false); //Since the block is assigned as the sink, it cannot be the source anymore.
    			sllBlockInfo.addDummySink(netSink);
    			sllBlockInfo.setSourcePin(netSource);
    			netSource.getOwner().isSLLSource = true;
	
    		}
    	}else {
    		dummyBlock = new GlobalBlock(BlockName, SLLBlocktype, this.SllBlockNum, true);
    		
    		if(ALBPin.isInput()) {
    			if(!dummyBlock.isSLLSink()) {
	    			String sourcePortName = "dataout";
	        		PortType dummyPortType = new PortType(dummyBlock.getType(), sourcePortName);
	        		TerminalPin = dummyBlock.getPin(dummyPortType, sourcePinIndex);
	        		TerminalPin.addSink(ALBPin);
	        		ALBPin.setSource(TerminalPin);

	        		GlobalPin netSource = (GlobalPin)TerminalPin;
	        		netSource.setNetName(SLLConn);
	        		GlobalPin netSink = (GlobalPin) ALBPin;
	        		netSink.setNetName(SLLConn);

        			dummyBlock.setSLLsource(true);
	        		sllBlockInfo.addDummySource(netSource);
	        		sllBlockInfo.addSinkPins(netSink);
	        		netSink.getOwner().isSLLSink = true;
        		}
    		}else if(ALBPin.isOutput()) {
    			
    			globalSourcePin = GetGlobalPin(SLLConn);        		
    			   			
    			String sourcePortName = "datain";

    			PortType dummyPortType = new PortType(dummyBlock.getType(), sourcePortName);

    			SourcePin = dummyBlock.getPin(dummyPortType, sourcePinIndex);
    			globalSourcePin.addSink(SourcePin);
    			SourcePin.setSource(globalSourcePin);

    			
    			GlobalPin netSource = (GlobalPin) globalSourcePin;
    			netSource.setNetName(SLLConn);
    			GlobalPin netSink = (GlobalPin) SourcePin;
    			netSink.setNetName(SLLConn);
    			dummyBlock.setSLLsink(true);
    			dummyBlock.setSLLsource(false); //Since the block is assigned as the sink, it cannot be the source anymore.
    			sllBlockInfo.addDummySink(netSink);
    			sllBlockInfo.setSourcePin(netSource);
    			netSource.getOwner().isSLLSource = true;
    		}
    		this.SllBlockNum++;
    		
    	}
    	
    	this.SLLDummyBlocks.put(BlockName, dummyBlock);
    	
    	//Clean up
    }
    
    private AbstractPin GetGlobalPin(String SLLConn) {
		Stack<AbstractPin> TempPins = new Stack<>();
		TempPins.add(this.sourcePins.get(SLLConn));
		AbstractPin globalSourcePin = null;
		 while(true) {
              AbstractPin sourcePin = TempPins.pop();
              AbstractBlock parent = sourcePin.getOwner().getParent();

              if(parent == null) {
                  globalSourcePin = sourcePin;
                  break;
              }
            int numSinks = sourcePin.getNumSinks();
              for(int i = 0; i < numSinks; i++) {
                  AbstractPin pin = sourcePin.getSink(i);
                  if(pin.getOwner() == parent) {
                  	TempPins.add(pin);
                  }
              }
		 }
		 
		 return globalSourcePin;
    }
    
    private void addSLLBlocks() {
    	

    	for(String sllnets:this.sllNetInfo.keySet()) {

    		sllNet sllNetInfo = this.sllNetInfo.get(sllnets);
    		if(sllNetInfo.getSLLsource() != null) {

    			this.CreateSLLDummyBlock(sllnets, sllNetInfo.getSLLsource());
    		}else {
    			List<AbstractPin> sllOutpins = sllNetInfo.getSLLsinks();

    			for(int i = 0; i < sllOutpins.size(); i++) {
    				
    				this.CreateSLLDummyBlock(sllnets, sllOutpins.get(i));
    			}
    		}

    		
    	}
    	
    	
    	
    	//Get the blocks in the dummy map.
    	for(String Dummy : this.SLLDummyBlocks.keySet()) {
    		AbstractBlock NewBlock = this.SLLDummyBlocks.get(Dummy);
    		
            if(!this.blocks.containsKey(NewBlock.getType())) {
                this.blocks.put(NewBlock.getType(), new ArrayList<AbstractBlock>());
            }
            this.blocks.get(NewBlock.getType()).add(NewBlock);

    	}
    }
    private void processBlockEndLine() {
    	int maxIndex = 0; 
        // If the stack is empty: this is the top-level block
        // All that is left to do is process all the inputs of
        // the global blocks
        if(this.blockStack.size() == 0) {
            while(this.inputsStack.size() > 0) {
                TupleBlockMap globalTuple = this.inputsStack.pop();
                AbstractBlock globalBlock = globalTuple.getBlock();
                
                //NEED TO ENSURE THE MAX INDEX IS TAKEN TO DEFINE THE SLL NUMBER
                maxIndex= globalBlock.getIndex();
                if(this.SllBlockNum < maxIndex) {
                	this.SllBlockNum = maxIndex + 1;
                }
                Map<String, String> inputs = globalTuple.getMap();
                processPortsHashMap(globalBlock, inputs);

                Map<String, String> clocks = this.clocksStack.pop();
                processPortsHashMap(globalBlock, clocks);
                
                //Updating the pin map for blocks with SLL as the output. This is required because previously only
                //the leaf block is processed at end block map. 
                

            }

        // This is a regular block, global, local or leaf
        } else {
            // Remove this block and its outputs from the stacks
            AbstractBlock block = this.blockStack.pop();
            Map<String, String> outputs = this.outputsStack.pop();
            processPortsHashMap(block, outputs);

            // Process the inputs of all the children of this block, but
            // not of this block itself. This is because the inputs may
            // come from sibling blocks that haven't been parsed yet.
            while(this.inputsStack.peek().getBlock() != block) {
                TupleBlockMap childTuple = this.inputsStack.pop();
                AbstractBlock childBlock = childTuple.getBlock();

                Map<String, String> inputs = childTuple.getMap();
                processPortsHashMap(childBlock, inputs);

                Map<String, String> clocks = this.clocksStack.pop();
                processPortsHashMap(childBlock, clocks);
            }
        }
    }

    private void processPortsHashMap(AbstractBlock block, Map<String, String> ports) {
        for(Map.Entry<String, String> portEntry : ports.entrySet()) {
            String portName = portEntry.getKey();
            PortType portType = new PortType(block.getType(), portName);
            List<AbstractPin> pins = block.getPins(portType);
            String nets = portEntry.getValue();
            this.addNets(pins, nets);
        }
    }


    private void addNets(List<AbstractPin> sinkPins, String netsString) {

    	
        String[] nets = netsString.trim().split("\\s+");

        for(int sinkPinIndex = 0; sinkPinIndex < nets.length; sinkPinIndex++) {
            AbstractPin sinkPin = sinkPins.get(sinkPinIndex);
            String net = nets[sinkPinIndex];

            this.addNet(sinkPin, net);
        }
        
    }



    
    @SuppressWarnings("unused")
	private void addNet(AbstractPin sinkPin, String net) {
        if(net.equals("open")) {
            return;
        }
        ArrayList<AbstractPin> tempList = new ArrayList<>();
        AbstractBlock sinkBlock = sinkPin.getOwner();
        int separator = net.lastIndexOf("-&");

        if(separator != -1) {
            int pinIndexEnd = separator - 1;
            int pinIndexStart = net.lastIndexOf("[", pinIndexEnd) + 1;
            int sourcePinIndex = Integer.parseInt(net.substring(pinIndexStart, pinIndexEnd));

            int portEnd = pinIndexStart - 1;
            int portStart = net.lastIndexOf(".", portEnd) + 1;
            String sourcePortName = net.substring(portStart, portEnd);


            int blockIndexEnd = portStart - 2;
            int blockIndexStart = portStart;
            int sourceBlockIndex = -1;

            if(net.charAt(blockIndexEnd) == ']') {
                blockIndexStart = net.lastIndexOf("[", blockIndexEnd) + 1;
                sourceBlockIndex = Integer.parseInt(net.substring(blockIndexStart, blockIndexEnd));
            }

            int typeEnd = blockIndexStart - 1;
            int typeStart = 0;
            String sourceBlockName = net.substring(typeStart, typeEnd);

            //BlockType sourceBlockType = new BlockType(sourceBlockName);
            //PortType sourcePortType = new PortType(sourceBlockType, sourcePortName);

            // Determine the source block
            AbstractBlock sourceBlock;

            // The net is incident to an input port. It has an input port of the parent block as source.
            if(sourceBlockIndex == -1) {
                sourceBlock = ((LocalBlock) sinkBlock).getParent();

            // The net is incident to an input port. It has a sibling's output port as source.
            } else if(sinkPin.isInput()) {
                AbstractBlock parent = ((LocalBlock) sinkBlock).getParent();
                BlockType sourceBlockType = new BlockType(parent.getType(), sourceBlockName);
                sourceBlock = parent.getChild(sourceBlockType, sourceBlockIndex);

            // The net is incident to an output port. It has an input port of itself as source
            } else if(sinkBlock.getType().getName().equals(sourceBlockName)) {
                sourceBlock = sinkBlock;

            // The net is incident to an output port. It has a child's output port as source
            } else {
                BlockType sourceBlockType = new BlockType(sinkBlock.getType(), sourceBlockName);
                sourceBlock = sinkBlock.getChild(sourceBlockType, sourceBlockIndex);
            }
            
            PortType sourcePortType = new PortType(sourceBlock.getType(), sourcePortName);
            AbstractPin sourcePin = sourceBlock.getPin(sourcePortType, sourcePinIndex);
            sourcePin.addSink(sinkPin);
            sinkPin.setSource(sourcePin);

        // The current block is a leaf block. We can add a reference from the net name to
        // the correct pin in this block, so that we can add the todo-nets later.
        } else if(sinkPin.isOutput()) {
            this.sourcePins.put(net, sinkPin);
            
            sllNet tempNetInfo = this.sllNetInfo.get(net);
            if(this.SLLNets.contains(net)) { 
            
            	if(sinkBlock.isLeaf()) {
            		AbstractBlock parent = sinkBlock.getParent();
            		while(!parent.isGlobal()) {//Check if the block is global block
            			 parent = parent.getParent();
           
            		}

            		parent.setSLLStatus(true);

           			tempNetInfo.setSLLsource(sinkPin);
    				
            	}
            
            }
            
        } else {
            String sourceName = net;
            sllNet tempNetInfo = this.sllNetInfo.get(net);
            if(this.SLLNets.contains(sourceName)) {              
            	if(sinkPin.isInput()){ //This is for the blocks that have SLLs as the input
            		
            		//Get global block and set the source?
            		if(sinkBlock.isGlobal()) {
            			AbstractBlock globalParent = sinkBlock;
            			globalParent.setSLLStatus(true);

            			

            		}   
            		tempNetInfo.addSLLsink(sinkPin);
	            }
            }else {
	            Stack<AbstractPin> sourcePins = new Stack<>();
	            sourcePins.add(this.sourcePins.get(sourceName));
	
	            AbstractPin globalSourcePin = null;
	            while(true) {
	                AbstractPin sourcePin = sourcePins.pop();
	                AbstractBlock parent = sourcePin.getOwner().getParent();
	
	                if(parent == null) {
	                    globalSourcePin = sourcePin;
	                    break;
	                }
	
	                int numSinks = sourcePin.getNumSinks();
	                for(int i = 0; i < numSinks; i++) {
	                    AbstractPin pin = sourcePin.getSink(i);
	                    if(pin.getOwner() == parent) {
	                        sourcePins.add(pin);
	                    }
	                }
	            }
	
	            globalSourcePin.addSink(sinkPin);
	            sinkPin.setSource(globalSourcePin);
	            
	            GlobalPin netSource = (GlobalPin) globalSourcePin;
	            netSource.setNetName(net);
	            GlobalPin netSink = (GlobalPin) sinkPin;
	            netSink.setNetName(net);
        }
	  }
    }
    public Map<String, SLLNetBlocks> getSllNetInfo(){
    	return this.netToBlockSLL;
    }
    class sllNet{
    	String netname;
    	AbstractPin sourceSLL;
    	List<AbstractPin> sinkSLL;
    	sllNet(String netname, AbstractPin sourceSLL, List<AbstractPin> sinkSLL){
    		this.netname = netname;
    		this.sourceSLL = sourceSLL;
    		this.sinkSLL = sinkSLL;
    	}
    	AbstractPin getSLLsource() {
    		return this.sourceSLL;
    	}
    	
    	List<AbstractPin> getSLLsinks(){
    		return this.sinkSLL;
    	}
    	void setSLLsource(AbstractPin sourceSLL) {
    		this.sourceSLL = sourceSLL;
    	}
    	void addSLLsink(AbstractPin sinkSLL) {
    		if(!this.sinkSLL.contains(sinkSLL)) {
    			this.sinkSLL.add(sinkSLL);
    		}
    		
    	}
    }

}
