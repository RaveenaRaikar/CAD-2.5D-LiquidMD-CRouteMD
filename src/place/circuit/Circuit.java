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
    private Map<BlockType, List<AbstractBlock>> sllblocks;

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

    public Circuit(String name, Architecture architecture, Map<BlockType, List<AbstractBlock>> blocks, int totdie, int dienum, int SLLrows) {
        this.name = name;
        this.architecture = architecture;
        this.totaldie = totdie;
        this.blocks = blocks;
        this.dienum = dienum;
        this.sllRows = SLLrows;
        this.timingGraph = new TimingGraph(this);
        
        
        this.width = this.architecture.getWidth();
        this.height = this.architecture.getHeight();
    	this.sites = new AbstractSite[this.totaldie][this.width][this.height];
    	this.virtualSites = new AbstractSite[this.totaldie][this.width][this.height];
        
    }

    public void initializeData() {
        this.loadBlocks();

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


    private void loadBlocks() {
    	//Adding the blocks from the architecture
        for(BlockType blockType : BlockType.getBlockTypes()) {
            if(!this.blocks.containsKey(blockType)) {
                this.blocks.put(blockType, new ArrayList<AbstractBlock>(0));
            }
        }
        
        
        //Add SLL DUMMY as a block type
        this.globalBlockTypes = BlockType.getGlobalBlockTypes();

        for(BlockType blockType : this.globalBlockTypes) {
            @SuppressWarnings("unchecked")
			List<GlobalBlock> blocksOfType = (List<GlobalBlock>)(List<?>) this.blocks.get(blockType);
            this.globalBlockList.addAll(blocksOfType);
        }

        this.loadMacros();

        this.createColumns();
        this.createSites(this.dienum);
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
        final int W = this.width; // note: this is width/2 in 2x2
        final boolean twoByTwo  = (this.architecture.archCols == 2);
        final int dieId = this.dienum;
        final boolean rightHalf = twoByTwo && (dieId == 1 || dieId == 3);

        // Indices
        final int L_SENT = 0, L_IO = 1, R_IO = W - 2, R_SENT = W - 1;

        // Pre-size and fill with empty
        this.columns = new ArrayList<>(java.util.Collections.nCopies(W + 2, emptyType));

        // Place IOs/sentinels
        if (twoByTwo) {
            if (rightHalf) {
                this.columns.set(R_IO, ioType);
                this.columns.set(R_SENT, emptyType);
            } else {
                this.columns.set(L_SENT, emptyType);
                this.columns.set(L_IO, ioType);
            }
        } else {
            this.columns.set(L_SENT, emptyType);
            this.columns.set(L_IO, ioType);
            this.columns.set(R_IO, ioType);
            this.columns.set(R_SENT, emptyType);
        }

        // Interior range and base shift for pattern math
        final int start = twoByTwo ? (rightHalf ? 0 : 2) : 2;
        final int end   = twoByTwo ? (rightHalf ? R_IO : W) : R_IO; // exclusive
        final int base  = rightHalf ? W : 0;

        // Fill interior columns
        for (int c = start; c < end; c++) {
            final int n = base + c; // virtual/global column index
            BlockType chosen = null;

            for (BlockType bt : blockTypes) {
                int repeat = bt.getRepeat();
                int startAt = bt.getStart();

                if ((repeat == -1 && n == startAt) ||
                    (repeat > 0 && n >= startAt && (n - startAt) % repeat == 0)) {
                    chosen = bt;
                    break;
                }
            }

            if (chosen != null) this.columns.set(c, chosen);
        }
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

    //Configurations and the numbering
//    ----------            -------------------
//    |        |            |        |        |
//    |  die 0 |            |  die 0 |  die 1 |
//    |        |            |        |        |
//    ----------            -------------------
//    |        |            |        |        |
//    |  die 1 |            |  die 2 |  die 3 |
//    |        |            |        |        |
//    ----------            -------------------
//    |        |
//    |  die 2 |
//    |        |
//    ----------
//    |        |
//    |  die 3 |
//    |        |
//    ----------
//    
    
    public void createSites(int dieIndex) {
    	
    	if(this.architecture.archCols == 1) {
    		this.createSitesSingleCol(dieIndex);
    		
    	}else if(this.architecture.archCols == 2) {
    		this.createSitesMultCol(dieIndex);
    	}else {
    		System.err.print("\nThis configuration is not supported");
    	}
    }



    public void createSitesSingleCol(int dieIndex) {
    	BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        BlockType emptyType = BlockType.getBlockTypes(BlockCategory.EMPTY).get(0);
        BlockType sllType = BlockType.getBlockTypes(BlockCategory.SLLDUMMY).get(0);
        int ioCapacity = this.architecture.getIoCapacity();
//        System.out.print("\nThe dieNumber is " + dieIndex);
        // Add EMPTY to left and right edges
        for (int row = 0; row < this.height; row++) {
            this.sites[dieIndex][0][row] = new Site(dieIndex, 0, row, emptyType);
            this.sites[dieIndex][this.width - 1][row] = new Site(dieIndex, this.width - 1, row, emptyType);
        }

        // Add IO to left and right (inside the EMPTY)
        for (int row = 1; row < this.height - 1; row++) {
            this.sites[dieIndex][1][row] = new IOSite(dieIndex, 1, row, ioType, ioCapacity);
            this.sites[dieIndex][this.width - 2][row] = new IOSite(dieIndex, this.width - 2, row, ioType, ioCapacity);
        }

        // Bottom die: add EMPTY + IO on bottom row
        if (dieIndex == 0) {
            for (int col = 0; col < this.width; col++) {
                this.sites[dieIndex][col][0] = new Site(dieIndex, col, 0, emptyType);
            }
            for (int col = 1; col < this.width - 1; col++) {
                this.sites[dieIndex][col][1] = new IOSite(dieIndex, col, 1, ioType, ioCapacity);
            }
        }

        // Top die: add EMPTY + IO on top row
        if (dieIndex == this.totaldie - 1) {
            for (int col = 0; col < this.width; col++) {
                this.sites[dieIndex][col][this.height - 1] = new Site(dieIndex, col, this.height - 1, emptyType);
            }
            for (int col = 1; col < this.width - 1; col++) {
                this.sites[dieIndex][col][this.height - 2] = new IOSite(dieIndex, col, this.height - 2, ioType, ioCapacity);
            }
        }

        // Add functional block sites (core region)
        for (int column = 2; column < this.width - 2; column++) {
            BlockType blockType = this.columns.get(column);
            int blockHeight = blockType.getHeight();
            for (int row = 2; row <= this.height - 2 - blockHeight; row += blockHeight) {
//            	if(dieIndex == 3) {
//            		System.out.print("\nSite added at " + row + " column: " + column);
//            	}
                this.sites[dieIndex][column][row] = new Site(dieIndex, column, row, blockType);
            }
        }
        
        if(dieIndex == 0 || dieIndex == (this.totaldie -1)) {
        	if(dieIndex == 0) {
                for (int col = 1; col < this.width - 2; col++) {
                    for (int row = this.height - this.sllRows - 1; row < this.height; row++) {
                        this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
                    }
                }
        	}else {
                for (int col = 1; col < this.width - 2; col++) {
                    for (int row = 0; row < this.sllRows; row++) {
                        this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
                    }
                }
        	}
        }else {
        	//middle region, add at both.
        	if(height > (2*this.sllRows)) {
        		for (int col = 1; col < this.width - 2; col++) {
                    for (int row = this.height - this.sllRows - 1; row < this.height; row++) {
                        this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
                    }
                }
        		
        		for (int col = 1; col < this.width - 2; col++) {
                    for (int row = 0; row < this.sllRows; row++) {
                        this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
                    }
                }
        		
        	}else {
        		for (int col = 1; col < this.width - 2; col++) {
                    for (int row = 0; row < this.height; row++) {
                        this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
                    }
                }
        	}
        	
        }
    }
    
//  ----------            -------------------
//  |        |            |        |        |
//  |  die 0 |            |  die 0 |  die 1 |
//  |        |            |        |        |
//  ----------            -------------------
//  |        |            |        |        |
//  |  die 1 |            |  die 2 |  die 3 |
//  |        |            |        |        |
//  ----------            -------------------
//  |        |
//  |  die 2 |
//  |        |
//  ----------
//  |        |
//  |  die 3 |
//  |        |
//  ----------
    
    public void createSitesMultCol(int dieIndex) {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        BlockType emptyType = BlockType.getBlockTypes(BlockCategory.EMPTY).get(0);
        BlockType sllType = BlockType.getBlockTypes(BlockCategory.SLLDUMMY).get(0);
        int ioCapacity = this.architecture.getIoCapacity();

        //Add IO and empty at left and right edges
        if(dieIndex == 0 || dieIndex == 2) {
            for (int row = 0; row < this.height; row++) {
                this.sites[dieIndex][0][row] = new Site(dieIndex, 0, row, emptyType);
            }
            for (int row = 1; row < this.height - 1; row++) {
                this.sites[dieIndex][1][row] = new IOSite(dieIndex, 1, row, ioType, ioCapacity);
            }
        }else if(dieIndex == 1 || dieIndex == 3) {
            for (int row = 0; row < this.height; row++) {
                this.sites[dieIndex][this.width - 1][row] = new Site(dieIndex, this.width - 1, row, emptyType);
            }
            for (int row = 1; row < this.height - 1; row++) {
                this.sites[dieIndex][this.width - 2][row] = new IOSite(dieIndex, this.width - 2, row, ioType, ioCapacity);
            }
        }
        
        // Bottom/Top rows for dies
        if(dieIndex == 0 || dieIndex == 1) {
            for (int col = 0; col < this.width; col++) {
                this.sites[dieIndex][col][0] = new Site(dieIndex, col, 0, emptyType);
            }
            for (int col = 1; col < this.width - 1; col++) {
                this.sites[dieIndex][col][1] = new IOSite(dieIndex, col, 1, ioType, ioCapacity);
            }
        }else if(dieIndex == 2 || dieIndex == 3) {
            for (int col = 0; col < this.width; col++) {
                this.sites[dieIndex][col][this.height - 1] = new Site(dieIndex, col, this.height - 1, emptyType);
            }
            for (int col = 1; col < this.width - 1; col++) {
                this.sites[dieIndex][col][this.height - 2] = new IOSite(dieIndex, col, this.height - 2, ioType, ioCapacity);
            }
        }


        // Core block site placement
        for (int col = 2; col < this.width - 2; col++) {
            BlockType blockType = this.columns.get(col);
            int blockHeight = blockType.getHeight();
            for (int row = 2; row <= this.height - 2 - blockHeight; row += blockHeight) {
//            	System.out.print("\nThe block added at column " + col + " and row " + row + " is " + blockType);
                this.sites[dieIndex][col][row] = new Site(dieIndex, col, row, blockType);
            }
        }

        System.out.print("\nThe height is " + (this.height - this.sllRows));
        //Bottom and top rows
        if(dieIndex == 0 || dieIndex == 1) {
        	for (int col = 1; col < this.width - 1; col++) {
                for (int row = this.height - this.sllRows - 1; row < this.height; row++) {
                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
                }
            }
        }
        if(dieIndex == 2 || dieIndex == 3) {
            for (int col = 1; col < this.width - 1; col++) {
                for (int row = 0; row < this.sllRows; row++) {
                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
                }
            }
        }
        
        //side rows
        if (dieIndex == 0 || dieIndex == 2) {
            for (int col = this.width - this.sllRows - 1; col < this.width; col++) {
                for (int row = 1; row < this.height - 1; row++) {
                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
                }
            }
        }

        if (dieIndex == 1 || dieIndex == 3) {
            for (int col = 1; col < this.sllRows; col++) {
                for (int row = 1; row < this.height; row++) {
                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
                }
            }
        }

    }

    
//    public void createSitesMultCol(int dieIndex) {
//    	BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
//        BlockType emptyType = BlockType.getBlockTypes(BlockCategory.EMPTY).get(0);
//        BlockType sllType = BlockType.getBlockTypes(BlockCategory.SLLDUMMY).get(0);
//        int ioCapacity = this.architecture.getIoCapacity();
////        System.out.print("\nThe dieNumber is " + dieIndex);
//        // Add EMPTY to left and right edges
//        for (int row = 0; row < this.height; row++) {
//            this.sites[dieIndex][0][row] = new Site(dieIndex, 0, row, emptyType);
//            this.sites[dieIndex][this.width - 1][row] = new Site(dieIndex, this.width - 1, row, emptyType);
//        }
//
//        // Add IO to left and right (inside the EMPTY)
//        for (int row = 1; row < this.height - 1; row++) {
//            this.sites[dieIndex][1][row] = new IOSite(dieIndex, 1, row, ioType, ioCapacity);
//            this.sites[dieIndex][this.width - 2][row] = new IOSite(dieIndex, this.width - 2, row, ioType, ioCapacity);
//        }
//
//        // Bottom die: add EMPTY + IO on bottom row
//        if (dieIndex == 0) {
//        	for (int row = 0; row < this.height; row++) {
//                this.sites[dieIndex][0][row] = new Site(dieIndex, 0, row, emptyType);
////                this.sites[dieIndex][this.width - 1][row] = new Site(dieIndex, this.width - 1, row, emptyType);
//            }
//
//            // Add IO to left and right (inside the EMPTY)
//            for (int row = 1; row < this.height - 1; row++) {
//                this.sites[dieIndex][1][row] = new IOSite(dieIndex, 1, row, ioType, ioCapacity);
////                this.sites[dieIndex][this.width - 2][row] = new IOSite(dieIndex, this.width - 2, row, ioType, ioCapacity);
//            }
//        	
//            for (int col = 0; col < this.width; col++) {
//                this.sites[dieIndex][col][0] = new Site(dieIndex, col, 0, emptyType);
//            }
//            for (int col = 1; col < this.width - 1; col++) {
//                this.sites[dieIndex][col][1] = new IOSite(dieIndex, col, 1, ioType, ioCapacity);
//            }
//            
//            for (int column = 2; column < this.width - 2; column++) {
//                BlockType blockType = this.columns.get(column);
//                int blockHeight = blockType.getHeight();
//                for (int row = 2; row < this.height - 1 - blockHeight; row += blockHeight) {
////                	if(dieIndex == 3) {
////                		System.out.print("\nSite added at " + row + " column: " + column);
////                	}
//                    this.sites[dieIndex][column][row] = new Site(dieIndex, column, row, blockType);
//                }
//            }
//            
//    		for (int col = 1; col < this.width - 1; col++) {
//    			for (int row = this.height - this.sllRows; row < this.height; row++) {
//                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
//                }
//            }
//    		
//    		for (int col = this.width - this.sllRows; col < this.width; col++) {
//    			for (int row = 1; row < this.height - 1; row++) {
//                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
//                }
//            }
//    		           
//        }else if (dieIndex == 1) {
//        	for (int row = 0; row < this.height; row++) {
//                this.sites[dieIndex][this.width - 1][row] = new Site(dieIndex, 0, row, emptyType);
////                this.sites[dieIndex][this.width - 1][row] = new Site(dieIndex, this.width - 1, row, emptyType);
//            }
//
//            // Add IO to left and right (inside the EMPTY)
//            for (int row = 1; row < this.height - 1; row++) {
//                this.sites[dieIndex][this.width - 2][row] = new IOSite(dieIndex, 1, row, ioType, ioCapacity);
////                this.sites[dieIndex][this.width - 2][row] = new IOSite(dieIndex, this.width - 2, row, ioType, ioCapacity);
//            }
//        	
//            for (int col = 0; col < this.width; col++) {
//                this.sites[dieIndex][col][0] = new Site(dieIndex, col, 0, emptyType);
//            }
//            for (int col = 1; col < this.width - 1; col++) {
//                this.sites[dieIndex][col][1] = new IOSite(dieIndex, col, 1, ioType, ioCapacity);
//            }
//            
//            for (int column = 2; column < this.width - 2; column++) {
//                BlockType blockType = this.columns.get(column);
//                int blockHeight = blockType.getHeight();
//                for (int row = 2; row < this.height - 1 - blockHeight; row += blockHeight) {
////                	if(dieIndex == 3) {
////                		System.out.print("\nSite added at " + row + " column: " + column);
////                	}
//                    this.sites[dieIndex][column][row] = new Site(dieIndex, column, row, blockType);
//                }
//            }
//            
//    		for (int col = 1; col < this.width - 1; col++) {
//    			for (int row = this.height - this.sllRows; row < this.height; row++) {
//                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
//                }
//            }
//    		
//   		
//    		for (int col = 1; col < this.sllRows; col++) {
//                for (int row = 1; row < this.height; row++) {
//                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
//                }
//            }
//        }else if (dieIndex == 2) {
//        	for (int row = 0; row < this.height; row++) {
//                this.sites[dieIndex][0][row] = new Site(dieIndex, 0, row, emptyType);
////                this.sites[dieIndex][this.width - 1][row] = new Site(dieIndex, this.width - 1, row, emptyType);
//            }
//
//            // Add IO to left and right (inside the EMPTY)
//            for (int row = 1; row < this.height - 1; row++) {
//                this.sites[dieIndex][1][row] = new IOSite(dieIndex, 1, row, ioType, ioCapacity);
////                this.sites[dieIndex][this.width - 2][row] = new IOSite(dieIndex, this.width - 2, row, ioType, ioCapacity);
//            }
//        	
//            for (int col = 0; col < this.width; col++) {
//                this.sites[dieIndex][col][this.height - 1] = new Site(dieIndex, col, 0, emptyType);
//            }
//            for (int col = 1; col < this.width - 1; col++) {
//                this.sites[dieIndex][col][this.height - 2] = new IOSite(dieIndex, col, 1, ioType, ioCapacity);
//            }
//            
//             
//            
//            for (int column = 2; column < this.width - 2; column++) {
//                BlockType blockType = this.columns.get(column);
//                int blockHeight = blockType.getHeight();
//                for (int row = 2; row < this.height - 1 - blockHeight; row += blockHeight) {
//
//                    this.sites[dieIndex][column][row] = new Site(dieIndex, column, row, blockType);
//                }
//            }
//            
//    		for (int col = 1; col < this.width - 1; col++) {
//    			for (int row = 1; row < this.sllRows; row++) {
//                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
//                }
//            }
//    		
//    		for (int col = this.width - this.sllRows; col < this.width; col++) {
//    			for (int row = 1; row < this.height - 1; row++) {
//                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
//                }
//            }
//    		
//        }else if (dieIndex == 3) {
//        	for (int row = 0; row < this.height; row++) {
//                this.sites[dieIndex][0][row] = new Site(dieIndex, 0, row, emptyType);
//
//            }
//
//            // Add IO to left and right (inside the EMPTY)
//            for (int row = 1; row < this.height - 1; row++) {
//                this.sites[dieIndex][1][row] = new IOSite(dieIndex, 1, row, ioType, ioCapacity);
//
//            }
//        	
//            for (int col = 0; col < this.width; col++) {
//                this.sites[dieIndex][col][this.height - 1] = new Site(dieIndex, col, 0, emptyType);
//            }
//            for (int col = 1; col < this.width - 1; col++) {
//                this.sites[dieIndex][col][this.height - 2] = new IOSite(dieIndex, col, 1, ioType, ioCapacity);
//            }
//            
//            for (int column = 2; column < this.width - 2; column++) {
//                BlockType blockType = this.columns.get(column);
//                int blockHeight = blockType.getHeight();
//                for (int row = 2; row < this.height - 1 - blockHeight; row += blockHeight) {
//                    this.sites[dieIndex][column][row] = new Site(dieIndex, column, row, blockType);
//                }
//            }
//            
//    		for (int col = 1; col < this.sllRows; col++) {
//                for (int row = 1; row < this.height; row++) {
//                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
//                }
//            }
//    		for (int col = 1; col < this.width - 1; col++) {
//    			for (int row = 1; row < this.sllRows; row++) {
//                    this.virtualSites[dieIndex][col][row] = new VirtualSite(dieIndex, col, row, sllType);
//                }
//            }
//    		
//
//        }
//    }

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
    
    public void setDieNum(int newDieNum) {
        this.dienum = newDieNum;
        // Recalculate or adjust properties dependent on dieNum
//        updateCircuitForDieNumber(newDieNum);
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
        List<AbstractSite> sites = new ArrayList<>();

        int ioCapacity = this.architecture.getIoCapacity();

        if (blockType.equals(ioType)) {
            // IO block: Handle across multiple dies dynamically
            int totalSites = (this.width - 1) * (this.height - 1) * ioCapacity;
            for (int n = 0; n < ioCapacity; n++) {
                // Handle IO sites for each die number
                for (int i = 1; i < this.height - 1; i++) {
                    // Add left and right edge IO sites for each die
                    sites.add(this.sites[dieNumber][1][i]); // Left side
                    sites.add(this.sites[dieNumber][this.width - 2][i]); // Right side
                }
                for (int i = 1; i < this.width - 1; i++) {
                    // Handle top and bottom edge IO sites for the first and last dies
                    if (dieNumber == 0) {
                        sites.add(this.sites[dieNumber][i][1]); // Bottom side for die 0
                    } else if (dieNumber == this.totaldie - 1) {
                        sites.add(this.sites[dieNumber][i][this.height - 2]); // Top side for the last die
                    }
                }
            }
        } else if (!blockType.equals(emptyType)) {
            // Non-IO block types
            List<Integer> columns = this.columnsPerBlockType.get(blockType);
            int blockHeight = blockType.getHeight();

            // Calculate number of sites needed for the block type across the dies
            sites = new ArrayList<>(columns.size() * (this.height - 2));

            for (Integer column : columns) {
                for (int row = 2; row < this.height - 2 - blockHeight; row += blockHeight) {
                    // Add block sites for each die dynamically
                    sites.add(this.sites[dieNumber][column][row]);
                }
            }
        }

        return sites;
    }

//    }
    
    public List<AbstractSite> getVirtualSites(BlockType blockType, int dieNumber) {
    	if(this.architecture.archCols == 1) {
    		return this.getVirtualSitesSingleCol(blockType, dieNumber);
    	}else if(this.architecture.archCols == 2) {
    		return this.getVirtualSitesMultCol(blockType, dieNumber);
    		
    	}else {
    		System.out.print("\nThe configuration is not supported");
    		return null;
    	}
    
    }
    
    public List<AbstractSite> getVirtualSitesSingleCol(BlockType blockType, int dieNumber) {
    	BlockType slltype = BlockType.getBlockTypes(BlockCategory.SLLDUMMY).get(0);
        List<AbstractSite> virtualSites = new ArrayList<AbstractSite>((this.width-1) * this.sllRows);
        int blockHeight = 1; 
        if(blockType.equals(slltype)){
            if(dieNumber == 0 || dieNumber == (this.totaldie -1)) {
            	if(dieNumber == 0) {
                    for (int col = 1; col < this.width - 1; col++) {
                        for (int row = this.height - this.sllRows; row < this.height; row++) {
                        	if(!(this.virtualSites[dieNumber][col][row] == null)) {
                    			virtualSites.add(this.virtualSites[dieNumber][col][row]);
                    		}
                        }
                    }
            	}else {
                    for (int col = 1; col < this.width - 1; col++) {
                        for (int row = 0; row < this.sllRows; row++) {
                        	if(!(this.virtualSites[dieNumber][col][row] == null)) {
                    			virtualSites.add(this.virtualSites[dieNumber][col][row]);
                    		}
                        }
                    }
            	}
            }else {
            	//middle region, add at both.
            	if(height > (2*this.sllRows)) {
            		for (int col = 1; col < this.width - 1; col++) {
                        for (int row = this.height - this.sllRows; row < this.height; row++) {
                        	if(!(this.virtualSites[dieNumber][col][row] == null)) {
                    			virtualSites.add(this.virtualSites[dieNumber][col][row]);
                    		}
                        }
                    }
            		
            		for (int col = 1; col < this.width - 1; col++) {
                        for (int row = 0; row < this.sllRows; row++) {
                        	if(!(this.virtualSites[dieNumber][col][row] == null)) {
                    			virtualSites.add(this.virtualSites[dieNumber][col][row]);
                    		}
                        }
                    }
            		
            	}else {
            		for (int col = 1; col < this.width - 1; col++) {
                        for (int row = 0; row < this.height; row++) {
                        	if(!(this.virtualSites[dieNumber][col][row] == null)) {
                    			virtualSites.add(this.virtualSites[dieNumber][col][row]);
                    		}
                        }
                    }
            	}
            	
            }	
   
        }
		return virtualSites;
    }
    
    public List<AbstractSite> getVirtualSitesMultCol(BlockType blockType, int dieNumber) {
    	BlockType slltype = BlockType.getBlockTypes(BlockCategory.SLLDUMMY).get(0);
        List<AbstractSite> virtualSites = new ArrayList<AbstractSite>((this.width-1) * this.sllRows * 2);
        if(blockType.equals(slltype)){
        	if(dieNumber == 0 || dieNumber == 2) {
        		for(int row = 1; row < this.height; row++) {
        			for(int col = this.width - this.sllRows; col < this.width - 1; col ++) {
        				if(!(this.virtualSites[dieNumber][col][row] == null)) {
                			virtualSites.add(this.virtualSites[dieNumber][col][row]);
                		}
        			}
        		}
        	}
        	
        	if(dieNumber == 1 || dieNumber == 3) {
        		for(int row = 0; row < this.height; row++) {
        			for(int col = 1; col < this.sllRows; col ++) {
        				if(!(this.virtualSites[dieNumber][col][row] == null)) {
                			virtualSites.add(this.virtualSites[dieNumber][col][row]);
                		}
        			}
        		}
        	}
        	
        	
        	if(dieNumber == 0 || dieNumber == 1) {
                for (int col = 1; col < this.width - 1; col++) {
                    for (int row = this.height - this.sllRows; row < this.height; row++) {
                    	if(!(this.virtualSites[dieNumber][col][row] == null)) {
                			virtualSites.add(this.virtualSites[dieNumber][col][row]);
                		}
                    }
                }
        	}
        	
        	if(dieNumber == 2 || dieNumber == 3) {
                for (int col = 1; col < this.width - 1; col++) {
                    for (int row = 0; row < this.sllRows; row++) {
                    	if(!(this.virtualSites[dieNumber][col][row] == null)) {
                			virtualSites.add(this.virtualSites[dieNumber][col][row]);
                		}
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
