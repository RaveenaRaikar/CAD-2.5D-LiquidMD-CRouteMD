package route.circuit.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;


import route.circuit.Circuit;
import route.circuit.architecture.Architecture;
import route.circuit.architecture.BlockType;
import route.circuit.architecture.PortType;
import route.circuit.block.AbstractBlock;
import route.circuit.block.GlobalBlock;
import route.circuit.block.LeafBlock;
import route.circuit.block.LocalBlock;
import route.circuit.pin.AbstractPin;
import route.circuit.pin.GlobalPin;
import route.circuit.resource.ResourceGraph;

public class NetParser {

    //private Circuit circuit;
    private Architecture architecture;
    private ResourceGraph resourceGraph;
    private String circuitName;
    private BufferedReader reader;
    private ArrayList<String> SLLNets;
    private Map<BlockType, List<AbstractBlock>> blocks;
    private Map<AbstractPin, String> pinToNetMap;
    private HashMap<String, SllNetData> localSLLMap;
    // blockStack is a LinkedList because we want to be able to peekLast()
    private LinkedList<AbstractBlock> blockStack;
    private Stack<TupleBlockMap> inputsStack;
    private Stack<Map<String, String>> outputsStack;
    private Stack<Map<String, String>> clocksStack;
    private int dieNumber;

    private Map<String, AbstractPin> sourcePins;

    private enum PortDirection {INPUT, OUTPUT, CLOCK};
    private PortDirection currentPortType;


    public NetParser(Architecture architecture, String circuitName, File file, ResourceGraph resourceGraph) throws FileNotFoundException {
        this.architecture = architecture;
        this.circuitName = circuitName;
        this.resourceGraph = resourceGraph;
        this.reader = new BufferedReader(new FileReader(file));
    }


    public Circuit parse(int dieCounter, HashMap<String, SllNetData> sllMap) throws IOException {
        // A list of all the blocks in the circuit
        this.blocks = new HashMap<BlockType, List<AbstractBlock>>();
        Boolean isSLL = false;
        String [] SLLNets;
        // blockStack is a stack that contains the current block hierarchy.
        // It is used to find the parent of a block. outputsStack contains
        // the outputs of these blocks. This is necessary because the outputs
        // of a block can only be processed after all the childs have been
        // processed.
        this.blockStack = new LinkedList<AbstractBlock>();
        this.inputsStack = new Stack<TupleBlockMap>();
        this.outputsStack = new Stack<Map<String, String>>();
        this.clocksStack = new Stack<Map<String, String>>();
        this.localSLLMap = sllMap;
        this.SLLNets = new ArrayList<String>();
        this.pinToNetMap = new HashMap<AbstractPin, String>();
        // sourcePins contains the names of the outputs of leaf blocks and
        // the corresponding output pins. It is needed to be able to create
        // global nets: at the time: only the name of the bottom-level source
        // block is given for these nets.
        this.sourcePins = new HashMap<String, AbstractPin>();
        this.dieNumber = dieCounter;

        String line, multiLine = "";

        while ((line = this.reader.readLine()) != null) {
            String trimmedLine = line.trim();
            //System.out.print("\nThe trimmed line " + trimmedLine);
            // Add the current line to the multiLine
            if(multiLine.length() > 0) {
                multiLine += " ";
            }
            multiLine += trimmedLine;

            if(!this.isCompleteLine(multiLine)) {
            	//System.out.print("\nThis is true");
                continue;
            }

        	if(multiLine.contains("<SLLs")){
            	//System.out.print(multiLine);
            	//isSLL = true;
            }else if(multiLine.contains("</SLLs")) {
            	//System.out.print(multiLine);
            	isSLL = true;
            }

        	if(isSLL){
            	SLLNets = multiLine.split(" ");
            	for(String SLLConn:SLLNets) {
            		
//            		if(!(SLLConn.equals("gnd") || SLLConn.equals("vcc"))) {
//            			System.out.print("\nThe sll con is " + SLLConn);
	            		if(!SLLConn.contains("SLL")) {
	            			this.SLLNets.add(SLLConn);
	            			SllNetData newSLL = new SllNetData(SLLConn);
	            			if(!this.localSLLMap.containsKey(SLLConn)) {
	            				this.localSLLMap.put(SLLConn, newSLL);
	            			}
	            			
	//            			this.sllNetInfo.put(SLLConn, newSLL);
	            			//this.sllNetInfo.put(SLLConn, new HashMap<Integer,List<AbstractPin>>());
	            			//System.out.print("\n\tThe SLL connection is " + SLLConn + "\n");
	            			}
            		}
//            	}
            	isSLL = false;
            }else{
                String lineStart = multiLine.substring(0, 6);
                //System.out.print("\nThe line is " + multiLine);
                switch(lineStart) {
                case "<input":
                    this.processInputLine(multiLine);
                    break;


                case "<outpu":
                    this.processOutputLine(multiLine);
                    break;

                case "<clock":
                    this.processClockLine(multiLine);
                    break;
                    
    	        case "<port_":
    	        	//System.out.print("\nThis is true here");
    	            this.processPortRotationLine(multiLine);
    	            break;
    	            

    	        case "<port ":
                    this.processPortLine(multiLine);
                    break;


                case "<block":
                    if(!multiLine.substring(multiLine.length() - 2).equals("/>")) {
                        this.processBlockLine(multiLine);
                    } else {
                        this.processBlockLine(multiLine);
                        this.processBlockEndLine();
                    }
                    break;


                case "</bloc":
//                	System.out.print("\nIS this true");
                    this.processBlockEndLine();
                    break;
                }
            }


            multiLine = "";
        }
        
        this.processSLLBlocks();
        //Mark the global pin of the SLL blocks.
        Circuit circuit = new Circuit(this.circuitName, this.architecture, this.blocks, dieCounter);
        
        circuit.initializeData();

        return circuit;
    }

    
    private boolean isCompleteLine(String line) {
        int lineLength = line.length();
        //System.out.print("the last character is "+ line.substring(lineLength - 7));
        // The line is empty
        if(lineLength == 0) {
            return false;
        }


        // The line doesn't end with a ">" character
        if(!line.substring(lineLength - 1).equals(">")) {
        	//System.out.print("\nIs this true");
            return false;
        }

        // The line is a port line, but not all ports are on this line
        if(lineLength >= 7
                && line.substring(0, 6).equals("<port ")
                && !line.substring(lineLength - 7).equals("</port>")) {
        	//System.out.print("\nThis condition is true");
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
    private void processPortRotationLine(String line) {
    	//do nothing
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
        //int modeEnd = line.length() - 2;
        int modeEnd = line.indexOf("\"", modeStart); //line.length() - 2;
        //System.out.print("\nThe mode start is " + modeStart + " and end is " + modeEnd );
        String mode = modeStart < modeEnd ? line.substring(modeStart, modeEnd) : null;
//        System.out.print("\nThe mode is " + mode + " and the name is " + name);
        Boolean wireBlock = false;
        BlockType parentBlockType = this.blockStack.isEmpty() ? null : this.blockStack.peek().getType();
        if (mode != null && mode.equals("default")){
            mode = type;
        }
        if (mode == null && name.equals("open")){
            mode = "X"; // == don't care; The mode for an open block might not be specified, then give a don't care to the blocktype initializer.
        } else if(name.equals("open")) {
        	if(mode.equals("wire")) {
        		wireBlock = true;
        	}
        	
        }
        
        
        //if(mode != "X") {
            BlockType blockType = new BlockType(parentBlockType, type, mode);

            
            AbstractBlock newBlock;
            if(blockType.isGlobal()) {
                newBlock = new GlobalBlock(name, blockType, index, this.dieNumber);

            } else {
                AbstractBlock parent = this.blockStack.peek();

                if(blockType.isLeaf()) {
                    GlobalBlock globalParent = (GlobalBlock) this.blockStack.peekLast();
                    newBlock = new LeafBlock(name, blockType, index, parent, globalParent, wireBlock);

                } else {
                    newBlock = new LocalBlock(name, blockType, index, parent);
                }
            }
//            if(newBlock.getName().contains("attention.Q.u_dual_port_ram^out2~1023")) {
//            	System.out.print("\nThe blocktype is " + newBlock.getType() + " and category is " + newBlock.getCategory());
//            }
//            if(newBlock.getName().contains("attention.Q.u_dual_port_ram^out2~538")) {
//            	System.out.print("\nThe blocktype now is " + newBlock.getType() + " and category is " + newBlock.getCategory());
//            }
//            System.out.print("\nThe block added is " + newBlock.getName());
            this.blockStack.push(newBlock);
            this.inputsStack.push(new TupleBlockMap(newBlock));
            this.outputsStack.push(new HashMap<String, String>());
            this.clocksStack.push(new HashMap<String, String>());

            if(!this.blocks.containsKey(blockType)) {
                BlockType emptyModeType = new BlockType(parentBlockType, blockType.getName());
                this.blocks.put(emptyModeType, new ArrayList<AbstractBlock>());
            }
            this.blocks.get(blockType).add(newBlock);
       // }
    }

    private void processBlockEndLine() {
        // If the stack is empty: this is the top-level block
        // All that is left to do is process all the inputs of
        // the global blocks
    	
        if(this.blockStack.size() == 0) {
            while(this.inputsStack.size() > 0) {
                TupleBlockMap globalTuple = this.inputsStack.pop();
                AbstractBlock globalBlock = globalTuple.getBlock();

                Map<String, String> inputs = globalTuple.getMap();
                processPortsHashMap(globalBlock, inputs);

                Map<String, String> clocks = this.clocksStack.pop();
                processPortsHashMap(globalBlock, clocks);
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
            this.addNets(block, pins, nets);
        }
    }
    
    private void addNets(AbstractBlock block, List<AbstractPin> sinkPins, String netsString) {
        //TODO: no regex?
        String[] nets = netsString.trim().split("\\s+");
        int numOutputConns = 0;
        for(int sinkPinIndex = 0; sinkPinIndex < nets.length; sinkPinIndex++) {
            AbstractPin sinkPin = sinkPins.get(sinkPinIndex);
            String net = nets[sinkPinIndex];

            this.addNet(sinkPin, net);
        }

    }

    
    private void processSLLBlocks() {
    	for(BlockType blocktype: this.blocks.keySet()) {
    		if(blocktype.isGlobal()) {
    			List<AbstractBlock> blocksList = this.blocks.get(blocktype);
    			for(AbstractBlock block : blocksList) {
    				if(block.getSLLSourceBlock()) {
//    					System.out.print("\nThe block is " + block.getName());
    					for(AbstractPin abstractPin : block.getOutputPins()) {
    						GlobalPin outputPin = (GlobalPin) abstractPin;
    		        		
    		        		AbstractPin current = outputPin;
    		        		while(current.getSource() != null) {
    		        			current = current.getSource();
    		        		}
    		        		
    		        		AbstractPin pathSource = current;
    		        		if(pathSource.getSLLSourceStatus()) {
    		        			String netname = this.pinToNetMap.get(pathSource);
    		        			
    		        			outputPin.setNetName(netname);
    		        			this.localSLLMap.get(netname).setSLLsourceGlobalPin(outputPin);
//    		        			System.out.print("\nThe output pin is " + outputPin + " and netname is " + netname);
    		        		}
    		        		
    					}
    				}
    			}
    			
    		}
    	}
    }
    private void addNet(AbstractPin sinkPin, String net) {
        if(net.equals("open")) {
//        	System.out.print("\nThe net is " + net);
            return;
        }

        AbstractBlock sinkBlock = sinkPin.getOwner();
        int separator = net.lastIndexOf("-&gt");
        

        
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
//            System.out.print("\nThe source pin is " + sourcePin + " and the sink is " + sinkPin);

        // The current block is a leaf block. We can add a reference from the net name to
        // the correct pin in this block, so that we can add the todo-nets later.
        } else if(sinkPin.isOutput()) {
            this.sourcePins.put(net, sinkPin);

            //This is to check if the block is the source of the SLL net.
            if(this.SLLNets.contains(net)) {
            	SllNetData sllNet = this.localSLLMap.get(net);
            	AbstractBlock parent = sinkBlock.getParent();
            	while(!parent.isGlobal()) {
            		parent = parent.getParent();
            	}
            	sllNet.setSLLsourceBlock(parent);
            	parent.setSLLSourceBlock();
            	sllNet.setSLLsourceLeafPin(sinkPin);
            	this.pinToNetMap.put(sinkPin, net);
            	sinkPin.setSLLsourceStatus();
            }
        // The input net that we want to add has a leaf block as its (indirect) source.
        // Finding the source block for the net is a bit tricky, because we have to trickle
        // up through the hierarchy from the referenced block.
        } else {
            String sourceName = net;
//            System.out.print("\nThe net is "+ net);
            
            //The nets that are partitioned/connected through the SLL have the source block in a different circuit which
            //will create problems for the net. Hence we make the connection in a different way.
            
            if(this.SLLNets.contains(sourceName)) {
            	SllNetData sllNet = this.localSLLMap.get(net);
            	if(sinkPin.isInput()) {
            		if(sinkBlock.isGlobal()) {
            			AbstractBlock globalParent = sinkBlock;
            			sllNet.addSinkBlock(globalParent, sinkPin);
            			globalParent.setSLLSinkBlock();
            			GlobalPin sinkGlobal = (GlobalPin) sinkPin;
            			sinkGlobal.setNetName(net);
            			sinkPin.setSLLSinkStatus();
            		}
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
                    //System.out.print("\nThe pin in outerloop is " + pin.toString());
                   // System.out.print("\nThe pin owner is " + pin.getOwner().getName() + " " + pin.getOwner().getType());
                 //   System.out.print("\npin.getOwner().getParent() is " + pin.getOwner().getParent().getName());
                    if(pin.getOwner() == parent) {
                    	//System.out.print("\nThe pin is " + pin.toString());
                        sourcePins.add(pin);
                        
                    }else if(pin.getPortName().contains("f7f8")) {
                        //Check if pin owner is same as parent
                        //This is if the pin connects to another block in same Alb
                          //This means the pin carries to the next BLE via the mux,
                          //need to do push followed by pop.
                    	AbstractPin temppin = pin;
                    	while(true) {
                    		int tempsinks = temppin.getNumSinks();
                    		for(int j =0; j < tempsinks ; j++) {
                    			AbstractPin temppinSink = temppin.getSink(i);
                    			//while(true) {
                    			//System.out.print("\nThe sink is " + temppinSink.toString() + "port type is " + temppinSink.getPortType() + " " + temppinSink.getPortName());
                    			//}

                    			//if(temppinSink.getPortName().co)
                    			temppin = temppinSink;
                    		//	System.out.print("\nThe temppin is " + temppin.toString());
                    		}
                    		//This is added because the architecture has a "false" connection:
                    		//FMUX 9 is driven from the input of all F7F8in instead of indivdual mux outputs.
                    		//hence one of the ble pins is driven high unnecessarily.
                			if(temppin.getOwner().isLeaf()) {
                				System.out.print("F7F8 attempting to connect to the leaf node.\n");
                				break;
                			}
                    		if(temppin.getOwner().getParent() == parent) {
                    			//System.out.print("\nThe pin is " + pin.toString());
                    			sourcePins.add(temppin);
                    			break;
                    		}
                    	}
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

}
//  if(this.SLLNets.contains(net)) {
//	SllNetData sllNet = this.localSLLMap.get(net);
//	//check if sinkPin is input or output.
//	//if input (port data in)
//		//This is a sink block. add the block to the map
//	if(sinkPin.isInput()) {
//		GlobalPin netSink = (GlobalPin) sinkPin;
//		GlobalBlock sinkSLLBlk = netSink.getOwner();
//		sllNet.addSinkBlock(sinkSLLBlk, netSink);
//		
//	}
////	else if(sinkPin.isOutput()) {
////		AbstractPin parentPin = sinkPin;
////		System.out.print("\nthe sinkPin is " + sinkPin);
////		while(true){
////			parentPin = parentPin.getSource();
////			System.out.print("\nthe parent is " + parentPin);
////			if(parentPin.getOwner().getParent() == null) {
////				break;
////			}
////		}
////		GlobalPin netSllSource = (GlobalPin) parentPin;
////		GlobalBlock sourceSllBlock = netSllSource.getOwner();
////		sllNet.setSLLsource(sourceSllBlock, netSllSource);
////		
////	}
//	//if output(port is dataout/ this is the source)
//		//This is the source block, we only need the output global pin.
//}
