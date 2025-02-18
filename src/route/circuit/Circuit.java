package route.circuit;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import route.circuit.architecture.Architecture;
import route.circuit.architecture.BlockCategory;
import route.circuit.architecture.BlockType;
import route.circuit.block.AbstractBlock;
import route.circuit.block.GlobalBlock;
import route.circuit.pin.AbstractPin;
import route.circuit.pin.GlobalPin;
import route.circuit.resource.ResourceGraph;
import route.circuit.resource.RouteNode;
import route.circuit.timing.TimingGraph;
import route.circuit.timing.TimingNode;
import route.route.Connection;
import route.route.Net;

public class Circuit {
    private String name;
    private int width, height;

    private Architecture architecture;
    private TimingGraph timingGraph;

    private Set<String> globalNetNames;
    private Map<BlockType, List<AbstractBlock>> blocks;
    
	private List<Connection> connections;
	private List<Net> nets;

    private List<BlockType> globalBlockTypes;
    private List<GlobalBlock> globalBlockList = new ArrayList<GlobalBlock>();
    
    private Integer dieNum;
    private List<BlockType> columns;
    private Map<BlockType, List<Integer>> columnsPerBlockType;
    private List<List<List<Integer>>> nearbyColumns;
    
    public Circuit(String name, Architecture architecture, Map<BlockType, List<AbstractBlock>> blocks, int dieCounter) {
        this.name = name;
        this.architecture = architecture;
        this.dieNum = dieCounter;
        this.blocks = blocks;
        this.timingGraph = new TimingGraph(this);
    }
    
    public void initializeData() {
        this.loadBlocks();
        this.initializeGlobalNets();
        this.markConstantGenerators();
        this.markGlobalNetIOPins();

        for(List<AbstractBlock> blocksOfType : this.blocks.values()) {
            for(AbstractBlock block : blocksOfType) {
                block.compact();
            }
        }
    }
    
    public Integer getCurrentDie() {
    	return this.dieNum;
    }
    public void initializeTimingGraph() {
    	this.timingGraph.build();
    }

    

    
    private void initializeGlobalNets() {
    	this.globalNetNames = new HashSet<>();
    	
    	this.globalNetNames.add("vcc");
    	this.globalNetNames.add("gnd");
    	
    	BufferedReader br = null;
    	try {
			br = new BufferedReader(new FileReader(this.architecture.getSDCFile()));
			
			String line = null;
			while((line = br.readLine()) != null){
				line = line.trim();
				
				if(line.contains("create_clock") && !line.contains("-name") && line.contains("-period")) {
					line = line.replace("\n", "");
					line = line.replace("\t", "");
					while(line.contains("  ")) line = line.replace("  ", " ");
					
					line = line.replace("{ ", "{");
					line = line.replace(" }", "}");
					
					String globalNet = line.split(" ")[3];
					
					globalNet = globalNet.replace("\\\\", "\\");
					globalNet = globalNet.replace("\\|", "|");
					globalNet = globalNet.replace("\\[", "[");
					globalNet = globalNet.replace("\\]", "]");
					
					if(globalNet.charAt(0) == '{' && globalNet.charAt(globalNet.length() - 1) == '}') {
						globalNet = globalNet.substring(1, globalNet.length() - 1);
					}
					
					globalNet = globalNet.trim();
					
					this.globalNetNames.add(globalNet);
				}
			}
			
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    public Set<String> getGlobalNetNames() {
    	return this.globalNetNames;
    }
    
    public String stats(){
    	String s = new String();
    	s += "-------------------------------";
    	s += "\n";
    	s += "Type" + "\t" + "Col" + "\t" + "Loc" + "\t" + "Loc/Col";
    	s += "\n";
    	s += "-------------------------------";
    	s += "\n";
    	for(BlockType blockType:this.globalBlockTypes){
    		String columns = "-";
    		String columnHeight = "-";
    		if(this.columnsPerBlockType.get(blockType) != null){
    			columns = "" + (this.columnsPerBlockType.get(blockType).size()) + "";
    			columnHeight = "" + (this.height / blockType.getHeight()) + "";
    		}
        	s += blockType.getName() + "\t" + columns + "\t" + this.getCapacity(blockType) + "\t" + columnHeight + "\n";
        }
    	return s;
    }


    private void loadBlocks() {
        for(BlockType blockType : BlockType.getBlockTypes()) {
            if(!this.blocks.containsKey(blockType)) {
                this.blocks.put(blockType, new ArrayList<AbstractBlock>(0));
            }
        }

        this.globalBlockTypes = BlockType.getGlobalBlockTypes();

        for(BlockType blockType : this.globalBlockTypes) {
            @SuppressWarnings("unchecked")
            List<GlobalBlock> blocksOfType = (List<GlobalBlock>) (List<?>) this.blocks.get(blockType);
            this.globalBlockList.addAll(blocksOfType);
        }
        
        this.createColumns();
    }
    
    private void markGlobalNetIOPins() {
    	String netName;
    	for(GlobalBlock globalBlock : this.getGlobalBlocks()) {
    		for(AbstractPin inputPin : globalBlock.getInputPins()) {
    			GlobalPin sinkPin = (GlobalPin) inputPin;
    			netName = sinkPin.getNetName();
    			if(this.globalNetNames.contains(netName)) {
    				sinkPin.setGlobalNetPin();
    				if(sinkPin.toString().contains("attention^ADD~15972-0[0].datax[0]")) {
    					System.out.print("\nThis is also true");
    				}
    				
    			}
    		}
    		
        	for(AbstractPin outputPin : globalBlock.getOutputPins()) {
        		GlobalPin sourcePin = (GlobalPin) outputPin;
        		netName = sourcePin.getNetName();
    			if(this.globalNetNames.contains(netName)) {
    				sourcePin.setGlobalNetPin();
    				if(sourcePin.toString().contains("attention.qk_acc.Add_u17^ADD~6699-0[0].dataout[2]")) {
    					System.out.print("\nThis is true");
    				}
    			}
        	}
    		
    	}
    }
    
    private void markConstantGenerators() {
    	int constantCounter = 0;
    	try (BufferedReader reader = new BufferedReader(new FileReader(this.architecture.getBlifFile()))) {
 	            String line;
 	            StringBuilder currentNetName = null;

 	            while ((line = reader.readLine()) != null) {
 	                if (line.startsWith(".names")) {
 	                    Matcher matcher = Pattern.compile("\\.names\\s(\\S+)").matcher(line);
 	                    if (matcher.find()) {
 	                        currentNetName = new StringBuilder(matcher.group(1));
 	                    }
 	                } else if ((line.trim().equals("0") || line.trim().equals("1")) && currentNetName != null) {
 	                	this.globalNetNames.add(currentNetName.toString());
 	                	constantCounter++;
 	                    currentNetName = null;
 	                }
 	            }
 	        } catch (IOException e) {
 	            e.printStackTrace();
 	        }



    	for(GlobalBlock globalBlock : this.getGlobalBlocks()) {
    		BlockType clbType = BlockType.getBlockTypes(BlockCategory.CLB).get(0);
    		if(globalBlock.getType().equals(clbType)) {
	    		int numConn = 0;
	    		String netName = null;
	    		Boolean inputOpen = false;
	        	for(AbstractPin inputPin : globalBlock.getInputPins()) {
	        		GlobalPin sourcePin = (GlobalPin) inputPin;
	        		netName = sourcePin.getNetName();
	        		if(netName != null) {
	        			inputOpen = true;
	        		}
	        	}
	        	if(!inputOpen) {
		        	for(AbstractPin outputPin : globalBlock.getOutputPins()) {
		        		GlobalPin sourcePin = (GlobalPin) outputPin;
		        		if(sourcePin.getNumSinks() > 0) {
		        			netName = sourcePin.getNetName();
		        			numConn++;
		        		}
		        		
		        	}
		        	if(numConn == 1) {
		        		constantCounter++;
		        		this.globalNetNames.add(netName);
		        	}
	        	}

    	}
	}

    	System.out.print("\nConstant generator count is " + constantCounter + "\n");
    }
    
    private void createColumns() {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        BlockType clbType = BlockType.getBlockTypes(BlockCategory.CLB).get(0);
        List<BlockType> hardBlockTypes = BlockType.getBlockTypes(BlockCategory.HARDBLOCK);

        // Create a list of all global block types except the IO block type,
        // sorted by priority
        List<BlockType> blockTypes = new ArrayList<BlockType>();
        blockTypes.add(clbType);
        blockTypes.addAll(hardBlockTypes);

        Collections.sort(blockTypes, new Comparator<BlockType>() {
            @Override
            public int compare(BlockType b1, BlockType b2) {
                return Integer.compare(b2.getPriority(), b1.getPriority());
            }
        });


        this.calculateSize(ioType, blockTypes);

        // Fill some extra data containers to quickly calculate
        // often used data
        this.cacheColumns(ioType, blockTypes);
        this.cacheColumnsPerBlockType(blockTypes);
        this.cacheNearbyColumns();
    }
    
    /***************
     * CONNECTIONS *
     ***************/
    public int loadNetsAndConnections(int idCounter) {
    	short boundingBoxRange = 3; //Why is the bounding box restricted to 3?
    	
    	this.connections = new ArrayList<>();
    	this.nets = new ArrayList<>();
        
        int id = 0;
    	for(GlobalBlock globalBlock : this.getGlobalBlocks()) {
        	for(AbstractPin abstractSourcePin : globalBlock.getOutputPins()) {
        		GlobalPin sourcePin = (GlobalPin) abstractSourcePin;
        		
        		if(sourcePin.getNumSinks() > 0) {
        			String netName = sourcePin.getNetName();
            		
        			
        			if(!this.globalNetNames.contains(netName)) {
        				if(!sourcePin.getSLLGlobalPin()) {
	    	        		List<Connection> net = new ArrayList<>();
	    	        		for(AbstractPin abstractSinkPin : sourcePin.getSinks()) {
	    	        				GlobalPin sinkPin = (GlobalPin) abstractSinkPin;
	        	        			Connection c = new Connection(id, sourcePin, sinkPin, this.dieNum);
	        	        			this.connections.add(c);
	        	        			net.add(c);
	        	        			
	        	        			id++;
	    	        			}
	    	        		//The last parameter is for SLL fanout, which is calculated in CircuitSLL class and hence fixed to 0 here.
		        			this.nets.add(new Net(netName, net, boundingBoxRange, 0));
	        			}
        			}
        		}
        	}
    	}
    	return id;
    }
    
    public int maximumNetLength() {
    	int maximumNetLength = 0;
    	for(Net net : this.nets) {
    		int netLength = net.wireLength();
    		if(netLength > maximumNetLength) {
    			maximumNetLength = netLength;
    		}
    	}
    	return maximumNetLength;
    }
    
    public void printNetDelays() {
    	for(Net net : this.nets) {
    		float delay, maxDelay = 0;
    		
    		List<Connection> connsOfNets = net.getConnections();
    		for(Connection eachConnection : connsOfNets) {
    			TimingNode sinkNode = eachConnection.getSinkNode();
    			delay = (sinkNode.getArrivalTime() - sinkNode.clockDelay);
    			if(delay > maxDelay) {
    				maxDelay = delay;
    			}
    			
    		}
    		System.out.print("\nThe net name is " + net.getName() + " and the delay is " + maxDelay);
    	}
    }
    
    private void calculateSize(BlockType ioType, List<BlockType> blockTypes) {
        /**
         * Set the width and height, either fixed or automatically sized
         */
        if(this.architecture.isAutoSized()) {
            this.autoSize(ioType, blockTypes);
            System.out.println("Auto size: " +  this.width + "x" + this.height + "\n");
        } else {
            this.width = this.architecture.getWidth();
            this.height = this.architecture.getHeight();
            System.out.println("\nFixed size: " +  this.width + "x" + this.height + "\n");
        }
    }
    
    private void autoSize(BlockType ioType, List<BlockType> blockTypes) {
        int[] numColumnsPerType = new int[blockTypes.size()];

        boolean bigEnough = false;
        double autoRatio = this.architecture.getAutoRatio();
        int size = 0;
        this.width = size;
        this.height = size;
        int previousWidth;

        while(!bigEnough) {
            size += 1;
            previousWidth = this.width;
            if(autoRatio >= 1) {
                this.height = size;
                this.width = (int) Math.round(this.height * autoRatio);
            } else {
                this.width = size;
                this.height = (int) Math.round(this.width / autoRatio);
            }

            // If columns have been added: check which block type those columns contain
            for(int column = previousWidth + 1; column < this.width + 1; column++) {
                for(int blockTypeIndex = 0; blockTypeIndex < blockTypes.size(); blockTypeIndex++) {
                    BlockType blockType = blockTypes.get(blockTypeIndex);
                    int repeat = blockType.getRepeat();
                    int start = blockType.getStart();
                    if(column % repeat == start || repeat == -1 && column == start) {
                        numColumnsPerType[blockTypeIndex] += 1;
                        break;
                    }
                }
            }


            // Check if the architecture is large enough
            int ioCapacity = (this.width + this.height) * 2 * this.architecture.getIoCapacity();
            if(ioCapacity >= this.getBlocks(ioType).size()) {
                bigEnough = true;

                for(int blockTypeIndex = 0; blockTypeIndex < blockTypes.size(); blockTypeIndex++) {
                    BlockType blockType = blockTypes.get(blockTypeIndex);

                    int blocksPerColumn = this.height / blockType.getHeight();
                    int capacity = numColumnsPerType[blockTypeIndex] * blocksPerColumn;

                    if(capacity < this.blocks.get(blockType).size()) {
                        bigEnough = false;
                        break;
                    }
                }
            }
        }
    }
    
    private void cacheColumns(BlockType ioType, List<BlockType> blockTypes) {
        /**
         * Make a list that contains the block type of each column
         */
    	
        this.columns = new ArrayList<BlockType>(this.width+2);
        this.columns.add(null);
        this.columns.add(1, ioType);
        for(int column = 2; column < this.width - 2; column++) {
            for(BlockType blockType : blockTypes) {
                int repeat = blockType.getRepeat();
                int start = blockType.getStart();
                if(column % repeat == start || repeat == -1 && column == start) {
                    this.columns.add(blockType);
                    break;
                }
            }
        }
        this.columns.add(this.width - 2, ioType);
        this.columns.add(null);
    }
    private void cacheColumnsPerBlockType(List<BlockType> blockTypes) {
        /**
         *  For each block type: make a list of the columns that contain
         *  blocks of that type
         */

        this.columnsPerBlockType = new HashMap<BlockType, List<Integer>>();
        for(BlockType blockType : blockTypes) {
            this.columnsPerBlockType.put(blockType, new ArrayList<Integer>());
        }
        for(int column = 2; column < this.width - 2; column++) {
            this.columnsPerBlockType.get(this.columns.get(column)).add(column);
        }
    }

    private void cacheNearbyColumns() {
        /**
         * Given a column index and a distance, we want to quickly
         * find all the columns that are within [distance] of the
         * current column and that have the same block type.
         * nearbyColumns facilitates this.
         */

        this.nearbyColumns = new ArrayList<List<List<Integer>>>();
        this.nearbyColumns.add(null);
        int size = Math.max(this.width, this.height);

        // Loop through all the columns
        for(int column = 2; column < this.width - 1 ; column++) {
            BlockType columnType = this.columns.get(column);

            // previousNearbyColumns will contain all the column indexes
            // that are within a certain increasing distance to this
            // column, and that have the same block type.
            List<Integer> previousNearbyColumns = new ArrayList<>();
            previousNearbyColumns.add(column);

            // For each distance, nearbyColumnsPerDistance[distance] will
            // contain a list like previousNearbyColumns.
            List<List<Integer>> nearbyColumnsPerDistance = new ArrayList<>();
            nearbyColumnsPerDistance.add(previousNearbyColumns);

            // Loop through all the possible distances
            for(int distance = 1; distance < size; distance++) {
                List<Integer> newNearbyColumns = new ArrayList<>(previousNearbyColumns);

                // Add the column to the left and right, if they have the correct block type
                int left = column - distance;
                if(left >= 1 && this.columns.get(left).equals(columnType)) {
                    newNearbyColumns.add(left);
                }

                int right = column + distance;
                if(right <= this.width - 2 && this.columns.get(right).equals(columnType)) {
                    newNearbyColumns.add(right);
                }

                nearbyColumnsPerDistance.add(newNearbyColumns);
                previousNearbyColumns = newNearbyColumns;
            }

            this.nearbyColumns.add(nearbyColumnsPerDistance);
        }
    }

    public TimingGraph getTimingGraph() {
        return this.timingGraph;
    }

    public List<GlobalBlock> getGlobalBlocks() {
        return this.globalBlockList;
    }

    /*****************
     * Default stuff *
     *****************/
    public String getName() {
        return this.name;
    }
    public int getWidth() {
        return this.width;
    }
    public int getHeight() {
        return this.height;
    }

    public Architecture getArchitecture() {
        return this.architecture;
    }
    
    public BlockType getColumnType(int column) {
        return this.columns.get(column);
    }
    
    public int getNumGlobalBlocks() {
        return this.globalBlockList.size();
    }

    public List<BlockType> getGlobalBlockTypes() {
        return this.globalBlockTypes;
    }

    public Set<BlockType> getBlockTypes() {
        return this.blocks.keySet();
    }
    public List<AbstractBlock> getBlocks(BlockType blockType) {
        return this.blocks.get(blockType);
    }

    public int getCapacity(BlockType blockType) {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        if(blockType.equals(ioType)) {
            return (this.height + this.width) * 2;

        } else {
            int numColumns = this.columnsPerBlockType.get(blockType).size();
            int columnHeight = this.height / blockType.getHeight();

            return numColumns * columnHeight;
        }
    }

    public List<Integer> getColumnsPerBlockType(BlockType blockType) {
        return this.columnsPerBlockType.get(blockType);
    }
    
    public List<Connection> getConnections() {
    	return this.connections;
    }
    public List<Net> getNets() {
    	return this.nets;
    }
 
    @Override
    public String toString() {
        return this.getName();
    }
}
