package place.circuit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import place.circuit.architecture.Architecture;
import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.block.AbstractBlock;
import place.circuit.block.AbstractSite;
import place.circuit.block.GlobalBlock;
import place.circuit.block.IOSite;
import place.circuit.block.Macro;
import place.circuit.block.Site;
import place.circuit.block.VirtualSite;
import place.circuit.pin.AbstractPin;
import place.circuit.pin.GlobalPin;
import place.circuit.timing.TimingGraph;
import place.circuit.timing.TimingGraphSLL;
import java.io.*;
import java.util.*;

public class Circuit {

    private String name;
    private transient int width, height;
    private int dienum;  //Current die number
    private int totaldie; //To know the total dies in the system
    private int sllRows;
    private Architecture architecture;

    private TimingGraph timingGraph;


    private Map<BlockType, List<AbstractBlock>> blocks;

    private List<BlockType> globalBlockTypes;
    private List<GlobalBlock> globalBlockList = new ArrayList<GlobalBlock>();
    private List<Macro> macros = new ArrayList<Macro>();

    private List<BlockType> columns;
    private Map<BlockType, List<Integer>> columnsPerBlockType;
    private List<List<List<Integer>>> nearbyColumns;

    private AbstractSite[][][] sites;
    private AbstractSite[][][] virtualSites;


    public Circuit(String name, Architecture architecture, Map<BlockType, List<AbstractBlock>> blocks) {
        this.name = name;
        this.architecture = architecture;

        this.blocks = blocks;

        this.timingGraph = new TimingGraph(this);
    }

    public Circuit(String name, Architecture architecture, Map<BlockType, List<AbstractBlock>> blocks, int totdie, int dieNum, int SLLrows) {
        this.name = name;
        this.architecture = architecture;
        this.totaldie = totdie;
        this.blocks = blocks;
        this.dienum = dieNum;
        this.sllRows = SLLrows;
        this.timingGraph = new TimingGraph(this);
        
        
        this.width = this.architecture.getWidth();
        this.height = this.architecture.getHeight();
    	this.sites = new AbstractSite[this.totaldie][this.width][this.height];
    	this.virtualSites = new AbstractSite[this.totaldie][this.width][this.height];
        
    }

    public void initializeData(int dieNumber) {
        this.loadBlocks(dieNumber);

        this.timingGraph.build();

        for(List<AbstractBlock> blocksOfType : this.blocks.values()) {
        	//
            for(AbstractBlock block : blocksOfType) {
                block.compact();
            }
        }
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


    private void loadBlocks(int dieNumber) {
    	//Adding the blocks from the architecture
        for(BlockType blockType : BlockType.getBlockTypes()) {
            if(!this.blocks.containsKey(blockType)) {
                this.blocks.put(blockType, new ArrayList<AbstractBlock>(0));
            }
        }
        
        
        //Add SLL DUMMY as a block type
        this.globalBlockTypes = BlockType.getGlobalBlockTypes();

        for(BlockType blockType : this.globalBlockTypes) {
            List<GlobalBlock> blocksOfType = (List<GlobalBlock>)(List<?>) this.blocks.get(blockType);
            this.globalBlockList.addAll(blocksOfType);
        }

        this.loadMacros();

        this.createColumns();
        this.createSites(dieNumber);
    }

    private void loadMacros() {
        for(BlockType blockType : this.globalBlockTypes) {
        	if(!blockType.getName().equals("EMPTY")) {
                // Skip block types that don't have a carry chain
                if(blockType.getCarryFromPort() == null) {
                    continue;
                }
                
                for(AbstractBlock abstractBlock : this.blocks.get(blockType)) {
                    GlobalBlock block = (GlobalBlock) abstractBlock;
                    GlobalPin carryIn = block.getCarryIn();
                    GlobalPin carryOut = block.getCarryOut();
                    if(carryIn.getSource() == null && carryOut.getNumSinks() > 0) {
                        List<GlobalBlock> macroBlocks = new ArrayList<>();
                        macroBlocks.add(block);

                        while(carryOut.getNumSinks() != 0) {
                            block = carryOut.getSink(0).getOwner();
                            carryOut = block.getCarryOut();
                            macroBlocks.add(block);
                        }

                        Macro macro = new Macro(macroBlocks);
                        this.macros.add(macro);
                    }
                }
        	}

        }
    }


    private void createColumns() {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        BlockType clbType = BlockType.getBlockTypes(BlockCategory.CLB).get(0);
        BlockType emptyType = BlockType.getBlockTypes(BlockCategory.EMPTY).get(0);
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
        this.cacheColumns(ioType, emptyType, blockTypes);
        this.cacheColumnsPerBlockType(blockTypes);
        this.cacheNearbyColumns();
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
            System.out.println("Fixed size: " +  this.width + "x" + this.height + "\n");
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
            int ioCapacity = (this.width +(2* this.height)) * this.architecture.getIoCapacity();
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
    
    private void cacheColumns(BlockType ioType, BlockType emptyType, List<BlockType> blockTypes) {
        /**
         * Make a list that contains the block type of each column
         */
    	
        this.columns = new ArrayList<BlockType>(this.width);
        this.columns.add(emptyType);
        this.columns.add(ioType);
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
        this.columns.add(ioType);
        this.columns.add(emptyType);
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
        for(int column = 2; column < this.width - 2 ; column++) {
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
        for(int column = 0; column < this.width; column++) {
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
                if(right < this.width - 1) {
	                if(right <= this.width - 2 && this.columns.get(right).equals(columnType)) {
	                    newNearbyColumns.add(right);
	                }

                nearbyColumnsPerDistance.add(newNearbyColumns);
                previousNearbyColumns = newNearbyColumns;
                }
            }

            this.nearbyColumns.add(nearbyColumnsPerDistance);
        }
    }

    private void createSites(int dieNumber) {


        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        BlockType emptyType = BlockType.getBlockTypes(BlockCategory.EMPTY).get(0);
        BlockType SllType = BlockType.getBlockTypes(BlockCategory.SLLDUMMY).get(0);
        int ioCapacity = this.architecture.getIoCapacity();
	     for (int i = 0; i < this.height ; i++) {
	         this.sites[this.dienum][0][i] = new Site(this.dienum, 0, i, emptyType);
	         this.sites[this.dienum][this.width - 1][i] = new Site(this.dienum, this.width - 1, i, emptyType);
	     }
	     // Rows for EMPTY
	     if (dieNumber == 0) {
	         for (int i = 0; i < this.width; i++) {
	             this.sites[this.dienum][i][0] = new Site(this.dienum, i, 0, emptyType);
	         }
	     } else if (dieNumber == 1) {
	         for (int i = 0; i < this.width; i++) {
	             this.sites[this.dienum][i][this.height - 1] = new Site(this.dienum, i, this.height - 1, emptyType);
	         }
	     }
	     
        
	     // Columns for IOs
	     for (int i = 1; i < this.height - 1; i++) {
	         this.sites[this.dienum][1][i] = new IOSite(this.dienum, 1, i, ioType, ioCapacity);
	         this.sites[this.dienum][this.width - 2][i] = new IOSite(this.dienum, this.width - 2, i, ioType, ioCapacity);
	     }
	
	     // Rows for IOs
	     if (dieNumber == 0) {
	         for (int i = 1; i < this.width - 1; i++) {
	             this.sites[this.dienum][i][1] = new IOSite(this.dienum, i, 1, ioType, ioCapacity);
	         }
	     } else if (dieNumber == 1) {
	         for (int i = 1; i < this.width - 1; i++) {
	             this.sites[this.dienum][i][this.height - 2] = new IOSite(this.dienum, i, this.height - 2, ioType, ioCapacity);
	         }
	     }
	
	     for (int column = 2; column < this.width - 2; column++) {
	         BlockType blockType = this.columns.get(column);
	         int blockHeight = blockType.getHeight();
	         for (int row = 2; row < this.height - 1 - blockHeight; row += blockHeight) {
	             this.sites[this.dienum][column][row] = new Site(this.dienum, column, row, blockType);
	         }
	     }
	
	     // For a SLL region
	     for (int column = 1; column < this.width - 1; column++) {
	         int startRow = (dieNumber == 0) ? this.height + 1 - this.sllRows : 0;
	         int endRow = (dieNumber == 0) ? this.height : this.sllRows;
	
	         for (int row = startRow; row < endRow; row++) {
	             this.virtualSites[this.dienum][column][row] = new VirtualSite(this.dienum, column, row, SllType);
	         }
	     }

    }




    /*************************
     * Timing graph wrapping *
     *************************/
    public TimingGraph getTimingGraph() {
        return this.timingGraph;
    }

    public void recalculateTimingGraph() {

        this.timingGraph.calculateCriticalities(true);
    }
    public double getMaxDelay() {
        return this.timingGraph.getMaxDelay();
    }
    public double getTotalTimingCost() {
        return this.timingGraph.calculateTotalCost();
    }
    public void getTimingInfo() {
        this.timingGraph.printTimingInfo();
    }
    

    public List<GlobalBlock> getGlobalBlocks() {
        return this.globalBlockList;
    }


    public List<Macro> getMacros() {
        return this.macros;
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
    public int getCurrentDie() {
        return this.dienum;
    }
    public int getTotalDie() {
        return this.totaldie;
    }
    
    public int getSLLrows() {
    	return this.sllRows;
    }
    
    public Architecture getArchitecture() {
        return this.architecture;
    }


    public BlockType getColumnType(int column) {
        return this.columns.get(column);
    }

    /**
     * Return the site at coordinate (x, y). If allowNull is false,
     * return the site that overlaps coordinate (x, y) but possibly
     * doesn't start at that position.
     */
    public AbstractSite getSite(int dieNum, int column, int row) {
        return this.getSite(dieNum, column, row, false);
    }
    public AbstractSite getSite(int dieNum, int column, int row, boolean allowNull) {
        if(allowNull) {
            return this.sites[dieNum][column][row];

        } else {
            AbstractSite site = null;
            int topY = row;

            while(site == null) {
                site = this.sites[dieNum][column][topY];
                if(topY == 1) {
                	topY ++; 
                }else {
                	topY--;
                }
                
            }

            return site;
        }
    }
    public AbstractSite getVirtualSite(int dieNum, int column, int row) {
        return this.getVirtualSite(dieNum, column, row, false);
    }
    public AbstractSite getVirtualSite(int dieNum, int column, int row, boolean allowNull) {
        if(allowNull) {
            return this.virtualSites[dieNum][column][row];

        } else {
            AbstractSite site = null;
            int topY = row;
            while(site == null) {
                site = this.virtualSites[dieNum][column][topY];
                topY--;
            }

            return site;
        }
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
        BlockType sllDummy = BlockType.getBlockTypes(BlockCategory.SLLDUMMY).get(0);
        BlockType emptyType = BlockType.getBlockTypes(BlockCategory.EMPTY).get(0);
        if(blockType.equals(ioType)) {
        	//Multi-die architecture has 2 columns and one row
            return ((2*(this.height-1)) + this.width-1);

        } else if(blockType.equals(sllDummy)) {
        	return this.width;
        }else if(blockType.equals(emptyType)) {
        	return (this.width + this.height)*2;
        }else{
	            int numColumns = this.columnsPerBlockType.get(blockType).size();
	            int columnHeight = this.height / blockType.getHeight();

	            return numColumns * columnHeight;
        }
    }


    public List<AbstractSite> getSites(BlockType blockType, int dieNumber) {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        BlockType emptyType = BlockType.getBlockTypes(BlockCategory.EMPTY).get(0);
        List<AbstractSite> sites = null;
        int ioCapacity = this.architecture.getIoCapacity();
        if (blockType.equals(ioType)) {
            int totalSites = (this.width - 1 * (this.height - 1)) * ioCapacity;
            sites = new ArrayList<>(totalSites);
            for (int n = 0; n < ioCapacity; n++) {
                for (int i = 1; i < this.height - 1; i++) {
                    sites.add(this.sites[dieNumber][1][i]);
                    sites.add(this.sites[dieNumber][this.width - 2][i]);
                }
                for (int i = 1; i < this.width - 1; i++) {
                    if (dieNumber == 0) {
                        sites.add(this.sites[dieNumber][i][1]);
                    } else {
                        sites.add(this.sites[dieNumber][i][this.height - 2]);
                    }
                }
            }
        } else if(!blockType.equals(emptyType)) {
            List<Integer> columns = this.columnsPerBlockType.get(blockType);
            int blockHeight = blockType.getHeight();
            sites = new ArrayList<>(columns.size() * (this.height - 2));

            for (Integer column : columns) {
                for (int row = 2 ; row < this.height - 2 - blockHeight; row += blockHeight) {

                        sites.add(this.sites[dieNumber][column][row]);

                }
            }
        }


        return sites; 
    }
    
    public List<AbstractSite> getVirtualSites(BlockType blockType, int dieNumber) {
    	BlockType slltype = BlockType.getBlockTypes(BlockCategory.SLLDUMMY).get(0);
        List<AbstractSite> virtualSites = new ArrayList<AbstractSite>((this.width-1) * this.sllRows);
        int blockHeight = 1; 
        if(blockType.equals(slltype)){
        	for(int column = 1; column < this.width - 1; column++) {
   	         int startRow = (dieNumber == 0) ? this.height - 1 - this.sllRows : 0;
   	         int endRow = (dieNumber == 0) ? this.height - 1 : this.sllRows;
   	
   	         for (int row = startRow; row < endRow; row++) {
         		if(!(this.virtualSites[dieNumber][column][row] == null)) {
        			virtualSites.add(this.virtualSites[dieNumber][column][row]);
        		}
   	         }
        	}
        }

		return virtualSites;
    }
    public List<Integer> getColumnsPerBlockType(BlockType blockType) {
        return this.columnsPerBlockType.get(blockType);
    }


    public GlobalBlock getRandomBlock(Random random) {
        int index = random.nextInt(this.globalBlockList.size());
        return this.globalBlockList.get(index);
    }

    public AbstractSite getRandomSite(
            BlockType blockType,
            int column,
            int columnDistance,
            int minRow,
            int maxRow,
            Random random) {

        int blockHeight = blockType.getHeight();
        int blockRepeat = blockType.getRepeat();

        // Get a random row
        int minRowIndex = (int) Math.ceil((minRow - 1.0) / blockHeight);
        int maxRowIndex = (maxRow - 1) / blockHeight;

        if(maxRowIndex == minRowIndex && columnDistance < blockRepeat) {
            return null;
        }

        // Get a random column
        List<Integer> candidateColumns = this.nearbyColumns.get(column).get(columnDistance);
        int randomColumn = candidateColumns.get(random.nextInt(candidateColumns.size()));

        // Get a random row
        int randomRow = 1 + blockHeight * (minRowIndex + random.nextInt(maxRowIndex + 1 - minRowIndex));

        // Return the site found at the random row and column
        return this.getSite(this.dienum, randomColumn, randomRow, false);
    }


    public double ratioUsedCLB(){
        int numCLB = 0;
        int numCLBPos = 0;
        for(BlockType clbType:BlockType.getBlockTypes(BlockCategory.CLB)){
        	numCLB += this.getBlocks(clbType).size();
        	numCLBPos += this.getColumnsPerBlockType(clbType).size() * this.getHeight();
        }
        double ratio = (double)numCLB / numCLBPos;
        
        return ratio;
    }

    @Override
    public String toString() {
        return this.getName();
    }
    
    public void numOutputPins() {
    	int numOutPins = 0;
    	
    	for(BlockType ioType:BlockType.getBlockTypes(BlockCategory.IO)){
    		for(AbstractBlock block:this.getBlocks(ioType)) {
    			for(AbstractPin pin : block.getOutputPins()) {
    				if(pin.getNumSinks()>=1) {
    					numOutPins++;
    				}
                }
    		}
        }
    	System.out.print("\nThe number of input pins is " + numOutPins);
    	
    	for(BlockType clbType:BlockType.getBlockTypes(BlockCategory.CLB)){
    		for(AbstractBlock block:this.getBlocks(clbType)) {
    			for(AbstractPin pin : block.getOutputPins()) {
    				if(pin.getNumSinks()>=1) {
    					numOutPins++;
    				}
                }
    		}
        }
    	System.out.print("\nThe number of CLB output pins is " + numOutPins);
    	
    	for(BlockType hardType:BlockType.getBlockTypes(BlockCategory.HARDBLOCK)){
    		for(AbstractBlock block:this.getBlocks(hardType)) {
    			for(AbstractPin pin : block.getOutputPins()) {
    				if(pin.getNumSinks()>=1) {
    					numOutPins++;
    				}
                }
    		}
        }
    	System.out.print("\nThe number of hard block output pins is " + numOutPins);
    	
    	for(BlockType sllType:BlockType.getBlockTypes(BlockCategory.SLLDUMMY)){
    		for(AbstractBlock block:this.getBlocks(sllType)) {
    			for(AbstractPin pin : block.getOutputPins()) {
    				if(pin.getNumSinks()>=1) {
    					numOutPins++;
    				}
                }
    		}
        }
    	System.out.print("\nThe number of slltype block output pins is " + numOutPins +"\n");

    }

}
