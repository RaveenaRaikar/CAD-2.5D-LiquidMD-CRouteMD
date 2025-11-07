package place.placers.analytical;

import place.circuit.Circuit;
import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.block.AbstractBlock;
import place.circuit.block.AbstractSite;
import place.circuit.block.GlobalBlock;
import place.circuit.block.IOSite;
import place.circuit.block.Macro;
import place.circuit.block.SLLNetBlocks;
import place.circuit.block.Site;
import place.circuit.exceptions.PlacementException;
import place.circuit.timing.TimingEdge;
import place.circuit.timing.TimingGraphSLL;
import place.circuit.timing.TimingNode;
import place.circuit.timing.TimingNode.Position;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.placers.Placer;
import place.placers.analytical.AnalyticalAndGradientPlacer.ParallelOptiLeg;
import place.visual.PlacementVisualizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import pack.util.ErrorLog;
import pack.util.Output;

public abstract class AnalyticalAndGradientPlacer extends Placer{

    protected List<List<BlockType>> blockTypes;
    protected List<List<Integer>> blockTypeIndexStarts;
    protected final Map<Integer,Map<GlobalBlock, NetBlock>> netBlocks;
    private Map<String, SLLNetBlocks> netToBlockSLL = new HashMap<>();
    protected int[] numIOBlocks, numMovableBlocks;
    protected int[] numSLLBlocks;
    protected StringBuilder localOutput = new StringBuilder();
//    protected boolean Global_fix;
//    protected boolean Seperate_fix;
    protected int syncStep;
    protected TimingGraphSLL timingGraphSLL;
    
    protected List<double[]> linearX, linearY;
    protected List<double[]> legalX, legalY;
    protected List<double[]> bestLinearX, bestLinearY;
    protected List<double[]> bestLegalX, bestLegalY;
    protected List<int[]> leafNode;
    protected List<int[]> heights;

    private double criticalityLearningRate;

    protected double[] linearCost;
    protected double[] legalCost;
    protected double[] timingCost;
    
    protected double[] currentCost, bestCost;
//    protected Map<String, Map<Integer, Integer>> SLLcounter;
    protected Map<String, List<BlockInfo>> SLLcounter;
    protected Map<String,Integer> sLLNodeList;

    private List<boolean[]> hasNets;
    private boolean[] hasNetstemp;
    protected int[] numRealNets, numRealConn;
    protected List<Net>[] nets;
    protected List<TimingNet>[] timingNets;

    private boolean[][] solveSeparate;
//    
//    private boolean[] dieSync;
    
    protected boolean hasHierarchyInformation;

    private static final String
        O_CRIT_LEARNING_RATE = "crit learning rate",
    	O_SYNC_STEP = "Synchronisation step",
    	O_FIX_GLOBAL = "fix global",
    	O_FIX_SEPERATE = "fix Seperate";
    	

    public static void initOptions(Options options) {
        options.add(
                O_CRIT_LEARNING_RATE,
                "criticality learning rate of the critical connections in sparce placement",
                new Double(0.7));
        options.add(
        		O_SYNC_STEP,
                "Synchronisation step for multithreading",
                new Integer(1));  
        options.add(O_FIX_GLOBAL,
        		"Fixed SLL blocks in global",
        		Boolean.FALSE);
        options.add(O_FIX_SEPERATE,
        		"Fixed SLL blocks in seperate",
        		Boolean.FALSE);
    }

    protected final static String
        T_INITIALIZE_DATA = "initialize data",
        T_UPDATE_CIRCUIT = "update circuit",
        T_BUILD_LINEAR = "build linear system",
        T_SOLVE_LINEAR = "solve linear system",
        T_CALCULATE_COST = "calculate cost",
        T_LEGALIZE = "legalize";


    public AnalyticalAndGradientPlacer(Circuit[] circuitDie, Options options, Random random, Logger logger, 
    		PlacementVisualizer[] visualizer, int TotalDies, int SLLrows, TimingGraphSLL timingGraphSLL, HashMap<String, SLLNetBlocks> netToBlockSLL) {	
		super(circuitDie, options, random, logger, visualizer, TotalDies, SLLrows, timingGraphSLL, netToBlockSLL);

//		this.Global_fix = options.getBoolean(O_FIX_GLOBAL);
//		this.Seperate_fix = options.getBoolean(O_FIX_SEPERATE);
        this.criticalityLearningRate = options.getDouble(O_CRIT_LEARNING_RATE);
        this.syncStep = options.getInteger(O_SYNC_STEP);
        this.hasHierarchyInformation = false;
        this.netBlocks = new HashMap<>();
        this.SLLcounter = new HashMap<>();
        this.sLLNodeList = new HashMap<>();
        this.timingGraphSLL = timingGraphSLL;
//        this.timingGraphSLL.recalculateSystemTimingGraph();
        this.netToBlockSLL = netToBlockSLL;
        
        boolean flag = false;
        //Since the hierarchy file will exist for both the dies, it is fine to consider only one die for evaluation.
        for(GlobalBlock block:this.circuitDie[0].getGlobalBlocks()){
        	if(block.hasLeafNode()) flag = true;
        }
        if(flag){
            for(GlobalBlock block:this.circuitDie[0].getGlobalBlocks()){
            	if(!block.hasLeafNode()){
            		ErrorLog.print("Liquid includes hierarchy information but global block " + block + " has no leaf node");
            	}
            }
            this.hasHierarchyInformation = true;
        }else{
        	this.hasHierarchyInformation = false;
        }
        

    }

    protected abstract boolean isTimingDriven();

    protected abstract void initializeIteration(int iteration, int dieCounter);
    protected abstract void solveLinear(int iteration, int dieCounter);
    protected abstract void solveLegal(int iteration, boolean isLastIteration, int dieCounter);
    protected abstract void solveLinear(BlockType category, int iteration, int dieCounter);
    protected abstract void solveLegal(BlockType category, boolean isLastIteration,int dieCounter);
    protected abstract void calculateCost(int iteration, int dieCount);
    //protected abstract void calculateCost(int iteration, int dieCount, double maxSysDelay);
    protected abstract boolean stopCondition(int iteration);
    protected abstract int numIterations();
    protected abstract void printLegalizationRuntime(int dieCounter);
    protected abstract void fixSLLblocks(BlockType category, int SLLrows);
    protected abstract void fixSLLblocksLiquidMD(BlockType category, int SLLrows);
    
    protected abstract void synchroniseSLLblocks(StringBuilder localOutput);
    protected abstract StringBuilder printStatistics(int iteration, double time, int dieCounter);



    @SuppressWarnings("unchecked")
	@Override
    public void initializeData() {
    	String initialiseData = "Placer Initialisation";
    	this.startSystemTimer(initialiseData);
        int dieCount = 0;
        // All the variables are in a loop with die count
        // Count the number of blocks
        // A macro counts as 1 block
        
        //numBlocks = new int[this.TotalDies];
        this.blockTypes = new ArrayList<>();
        this.blockTypeIndexStarts = new ArrayList<>();
        
        
        this.linearCost = new double[this.TotalDies];
        this.legalCost = new double[this.TotalDies];
        this.timingCost = new double[this.TotalDies];
        this.currentCost = new double[this.TotalDies];
        this.bestCost = new double[this.TotalDies];
        this.numIOBlocks = new int[this.TotalDies];
        
        this.nets = new List[this.TotalDies];
        this.timingNets = new List[this.TotalDies];;
        this.numRealNets = new int[this.TotalDies];
        this.numRealConn = new int[this.TotalDies];
        int numIterations = this.numIterations();
        this.solveSeparate = new boolean[this.TotalDies][numIterations];
//        this.dieSync = new boolean [numIterations];
        //this.SLLcounter =
        //this.netBlocks = new HashMap<>();
        
       // this.leafNode = new double[this.TotalDies][];
        
        
        this.linearX = new ArrayList<double[]>();
        this.linearY = new ArrayList<double[]>();
        this.legalX = new ArrayList<double[]>();
        this.legalY = new ArrayList<double[]>();
        this.bestLinearX = new ArrayList<double[]>();
        this.bestLinearY = new ArrayList<double[]>();
        this.bestLegalX = new ArrayList<double[]>();
        this.bestLegalY = new ArrayList<double[]>();
        this.leafNode = new ArrayList<int[]>();
        this.hasNets = new ArrayList<boolean[]>();
        this.heights = new ArrayList<int[]>();
        
        while(dieCount < this.TotalDies) {
        	 this.startTimer(T_INITIALIZE_DATA, dieCount);
        	int numBlocks = 0;   //internal variables dont need to be 2D
        	this.blockTypes.add(new ArrayList<>());
        	this.blockTypeIndexStarts.add(new ArrayList<>());
        
        	this.netBlocks.put(dieCount, new HashMap<>());
            
            
        	for(BlockType blockType : this.circuitDie[dieCount].getGlobalBlockTypes()) {
                numBlocks += this.circuitDie[dieCount].getBlocks(blockType).size();
            }
            for(Macro macro : this.circuitDie[dieCount].getMacros()) {
                numBlocks -= macro.getNumBlocks() - 1;
            }
            
            BlockType ioBlockType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
            BlockType sllBlockType = BlockType.getBlockTypes(BlockCategory.SLLDUMMY).get(0);
            this.blockTypes.get(dieCount).add(ioBlockType);
            for(BlockType blockType : this.circuitDie[dieCount].getGlobalBlockTypes()) {
                if(!blockType.equals(ioBlockType)) {
                	this.blockTypes.get(dieCount).add(blockType);
                }
            }
            
            this.linearCost[dieCount] = Double.NaN;
            this.legalCost[dieCount] = Double.NaN;
            this.timingCost[dieCount] = Double.NaN;
            
            this.currentCost[dieCount] = Double.MAX_VALUE;
            this.bestCost[dieCount] = Double.MAX_VALUE;

            // Add all global blocks, in the order of 'blockTypes'
            double [] linearXtemp = new double[numBlocks];
            double [] linearYtemp = new double[numBlocks];
            double [] legalXtemp = new double[numBlocks];
            double [] legalYtemp = new double[numBlocks];
            double [] bestLinearXtemp = new double[numBlocks];
            double [] bestLinearYtemp = new double[numBlocks];
            double [] bestLegalXtemp = new double[numBlocks];
            double [] bestLegalYtemp = new double[numBlocks];
            int [] leafNodetemp = new int[numBlocks];
            this.hasNetstemp = new boolean[numBlocks];
           
            //If the value of leafNode is equal to -1 then the node has no hierarchy leaf node
            Arrays.fill(leafNodetemp, -1);
            
           
           // this.heights = new int[this.TotalDies][numBlocks];
            
            int [] tempHeight = new int[numBlocks];
            Arrays.fill(tempHeight, 1);

            
            this.blockTypeIndexStarts.get(dieCount).add(0);
            List<GlobalBlock> macroBlocks = new ArrayList<>();
           
            
            
            int blockCounter = 0;
            for(BlockType blockType : this.circuitDie[dieCount].getGlobalBlockTypes()) {
                for(AbstractBlock abstractBlock : this.circuitDie[dieCount].getBlocks(blockType)) {
                    GlobalBlock block = (GlobalBlock) abstractBlock;
                	if (block.getSite().getdie() != dieCount) {
                	    System.out.println("Block " + abstractBlock.getName() + 
                	        " claims to belong to die " + block.getSite().getdie() + 
                	        " but is being iterated in die loop for die " + dieCount + " is of type " + block.getType() + " "  + System.identityHashCode(block));
                	}
                    // Blocks that are the first block of a macro (or that aren't
                    // in a macro) should get a movable position.
                    if(!block.isInMacro() || block.getMacroOffsetY() == 0) {
                        int column = block.getColumn();
                        int row = block.getRow();
                      
                        int height = block.isInMacro() ? block.getMacro().getHeight() : 1;
                        // The offset is measured in half blocks from the center of the macro
                        // For the legal position of macro's with an even number of blocks,
                        // the position of the macro is rounded down
                        float offset = (1 - height) / 2f;
                        linearXtemp[blockCounter] = column;
                        linearYtemp[blockCounter] = row - offset;
                        legalXtemp[blockCounter] = column;
                        legalYtemp[blockCounter] = row - offset;
                        tempHeight[blockCounter] = height;

                        if(this.hasHierarchyInformation){
                        	leafNodetemp[blockCounter] = block.getLeafNode().getIndex();
                        }
                        NetBlock netBlock = new NetBlock(blockCounter, offset, blockType);
                        this.netBlocks.get(dieCount).put(block, netBlock);
                        
                        block.netBlock = netBlock;

                        
                     // Check if the block is an SLL block
                        if (block.getType().equals(sllBlockType)) {
                            String blockname = block.getName();
                            boolean isSource = false;
				if(abstractBlock.isSLLSink()) {
                            	isSource = true;
                            }
                            

                            if (!this.SLLcounter.containsKey(blockname)) {
                                this.SLLcounter.put(blockname, new ArrayList<>());
                            }

                            List<BlockInfo> dieList = this.SLLcounter.get(blockname);
                            BlockInfo blockData = new BlockInfo(dieCount, blockCounter, isSource);
//                            System.out.print("\nTHe block is " + blockname + " with counter " + blockCounter + " on die " + dieCount + " is source " + abstractBlock.isSLLSource() + " the sink is " + abstractBlock.isSLLSink() );
                            if (isSource) {
                            	// Check if a source block already exists
                                boolean sourceExists = dieList.stream().anyMatch(info -> info.isSource);
                                if (sourceExists) {
                                    System.err.println("\nWarning: Source block already exists for " + blockname + "! Duplicate source found on die " + dieCount);
                                    // You can also throw an exception or skip adding if desired
                                    // throw new RuntimeException("Duplicate source block for " + blockname);
                                } else {
//                                	System.out.print("\nAdded for source");
                                    dieList.add(0, new BlockInfo(dieCount, blockCounter, true));
                                }                            	
                            } else {
                                // Append non-source blocks at the end
                                dieList.add(blockData);
                            }

                            this.SLLcounter.put(blockname, dieList);
                        }
                        
                        
                        blockCounter++;
                    // The position of other blocks will be calculated
                    // using the macro source.
                    } else {
                        macroBlocks.add(block);
                    }

                }
                
                this.blockTypeIndexStarts.get(dieCount).add(blockCounter);
            }
            
            this.heights.add(tempHeight);
            this.leafNode.add(leafNodetemp);
            this.linearX.add(linearXtemp);
            this.linearY.add(linearYtemp);
            this.legalX.add(legalXtemp);
            this.legalY.add(legalYtemp);
            this.bestLinearX.add(bestLinearXtemp);
            this.bestLinearY.add(bestLinearYtemp);
            this.bestLegalX.add(bestLegalXtemp);
            this.bestLegalY.add(bestLegalYtemp);
            this.hasNets.add(this.hasNetstemp);

            for(GlobalBlock block : macroBlocks) {
                GlobalBlock macroSource = block.getMacro().getBlock(0);
                int sourceIndex = this.netBlocks.get(dieCount).get(macroSource).blockIndex;
                int macroHeight = block.getMacro().getHeight();
                int offset = (1 - macroHeight) / 2 + block.getMacroOffsetY();

                this.netBlocks.get(dieCount).put(block, new NetBlock(sourceIndex, offset, macroSource.getType()));
                blockCounter++;
            }

            this.numIOBlocks[dieCount] = this.blockTypeIndexStarts.get(dieCount).get(1);
            // Add all nets
            // A net is simply a list of unique block indexes
            // If the algorithm is timing driven, we also store all the blocks in
            // a net (duplicates are allowed) and the corresponding timing edge
            this.nets[dieCount] = new ArrayList<Net>(); 
            this.timingNets[dieCount] = new ArrayList<TimingNet>();


            /* For each global output pin, build the net that has that pin as
             * its source. We build the following data structures:
             *   - uniqueBlockIndexes: a list of the global blocks in the net
             *     in no particular order. Duplicates are removed.
             *   - blockIndexes: a list of the blocks in the net. Duplicates
             *     are allowed if a block is connected multiple times to the
             *     same net. blockIndexes[0] is the net source.
             *   - timingEdges: the timing edges that correspond to the blocks
             *     in blockIndexes. The edge at timingEdges[i] corresponds to
             *     the block at blockIndexes[i + 1].
             */

            // Loop through all leaf blocks //Timing nodes are only the output pins
            // Get all the blocks which act as the source to a net and then add the net between the block and its sink. the Sink can exist in the block
            // if its an intermediate block
            
            for(GlobalBlock sourceGlobalBlock : this.circuitDie[dieCount].getGlobalBlocks()) {
            	
                NetBlock sourceBlock = this.netBlocks.get(dieCount).get(sourceGlobalBlock);
                for(TimingNode timingNode : sourceGlobalBlock.getTimingNodes()) {
                    if(timingNode.getPosition() != Position.LEAF) {
                        this.addNet(sourceBlock, timingNode, dieCount);
                    }
               }
            }
            this.hasNets.set(dieCount, this.hasNetstemp);
            
           // 
            this.numRealNets[dieCount] = this.nets[dieCount].size();
            System.out.print("\nThe number of real nets for die " + dieCount + " is " + this.numRealNets[dieCount] + "\n");
            this.numRealConn[dieCount] = 0;
            for(Net net:this.nets[dieCount]){
            	this.numRealConn[dieCount] += net.blocks.length - 1;
            }
            
            for(NetBlock block : this.netBlocks.get(dieCount).values()) {
                if(!this.hasNets.get(dieCount)[block.blockIndex]) {
                   this.addDummyNet(block,dieCount);
                }
            }
            
            System.out.print("\nThe number of total nets for die " + dieCount + " is " + this.nets[dieCount].size() + "\n");

            double averageCLBusuage = (this.circuitDie[0].ratioUsedCLB() + this.circuitDie[1].ratioUsedCLB())/2;
//            Arrays.fill(this.dieSync, false);
           // if(this.circuitDie[dieCount].ratioUsedCLB() > 0.8) {
            int counter = this.syncStep;
            if(averageCLBusuage > 0.8) {  

                double nextFunctionValue = 0;

                double priority = 0.75, fequency = 0.3, min = 5;
                
            	StringBuilder recalculationsString = new StringBuilder();
                for(int i = 0; i < numIterations; i++) {
                    double functionValue = Math.pow((1. * i) / numIterations, 1. / priority);
                    if(functionValue >= nextFunctionValue) {
                        nextFunctionValue += 1.0 / (fequency * numIterations);
                        if(i > min){
                        	this.solveSeparate[dieCount][i] = true;

                        	recalculationsString.append("|");
                        }else{
                        	this.solveSeparate[dieCount][i] = false;

                        	recalculationsString.append(".");
                        }
                    } else {
                    	this.solveSeparate[dieCount][i] = false;

                        recalculationsString.append(".");
                    }
//                    if(!this.Seperate_fix) {
//                        if((i%2==0) ) {
//                          	 if ((i == 2 || counter == 0) && (i>0)) {
//                          		this.dieSync[i] =true;
//                           	counter = this.syncStep - 1;
//                          	 }else {
//                          		counter--;
//                          	}
//                          }else if(i%2==1) {
//                        	  if(!this.Global_fix) {
//                        		  this.dieSync[i] = true;
//                        	  }
//                          }
//                    }else if(!this.Global_fix) {
//                    	 this.dieSync[i] = true;
//                    }
                    
                    
                }
                System.out.println("Solve separate: " + recalculationsString + " for die " + dieCount +"\n");
            //Separate solving sparse designs
                
            //Solves seperately for odd / last iteration.
            } else {


                StringBuilder recalculationsString = new StringBuilder();
                for(int i = 0; i < numIterations; i++) {
                	if(i%2 == 1 || i == numIterations - 1){
                		this.solveSeparate[dieCount][i] = true;

                		recalculationsString.append("|");
//                    	  if(!this.Global_fix) {
//                    		  this.dieSync[i] = true;
//                    	  }
                		
                	}else{
                		this.solveSeparate[dieCount][i] = false;
//                        if(!this.Seperate_fix) {
//                            if((i%2==0) ) {
//                              	 if ((i == 2 || counter == 0) && (i>0)) {
//                              		this.dieSync[i] =true;
//                               	counter = this.syncStep - 1;
//                              	 }else {
//                              		counter--;
//                              	}
//                              }else if(i%2==1) {
//                            	  if(!this.Global_fix) {
//                            		  this.dieSync[i] = true;
//                            	  }
//                              }
//                        }
                		recalculationsString.append(".");
                	}
                }
                this.logger.println("\nSolve separate: " + recalculationsString + "\n");
            }

            this.stopTimer(T_INITIALIZE_DATA, dieCount);
            
            dieCount++;
        }

        
       this.stopandPrintSystemTimer(initialiseData);
    }
    
    private void addDummyNet(NetBlock sourceBlock, int dieCount) {
        // These dummy nets are needed for the analytical
        // placer. If they are not added, diagonal elements
        // exist in the matrix that are equal to 0, which
        // makes the matrix unsolvable.
        Net net = new Net(sourceBlock);
        this.nets[dieCount].add(net);
    }

    private void addNet(NetBlock sourceBlock, TimingNode sourceNode, int dieCount) {
    	boolean isSLLEnabled = false;
        int numSinks = sourceNode.getNumSinks();
        GlobalBlock tempBlock = sourceNode.getGlobalBlock();
 
        TimingNet timingNet = new TimingNet(sourceBlock, numSinks);
        if(tempBlock.isSLLDummy()) {
        	isSLLEnabled = true;
        }

        boolean allFixed = this.isFixed(sourceBlock.blockIndex, dieCount);;
        for(int sinkIndex = 0; sinkIndex < numSinks; sinkIndex++) {
            GlobalBlock sinkGlobalBlock = sourceNode.getSinkEdge(sinkIndex).getSink().getGlobalBlock();
            if(sinkGlobalBlock.isSLLDummy()) {
            	isSLLEnabled = true;
            	this.sLLNodeList.put(sinkGlobalBlock.getName(), dieCount);
            }
            NetBlock sinkBlock = this.netBlocks.get(dieCount).get(sinkGlobalBlock);
            if(allFixed) {
                allFixed = this.isFixed(sinkBlock.blockIndex, dieCount);
            }


            TimingEdge timingEdge = sourceNode.getSinkEdge(sinkIndex);
            if(timingEdge != null) {
            	timingNet.sinks[sinkIndex] = new TimingNetBlock(sinkBlock, timingEdge, this.criticalityLearningRate);
            }
            
        }

        if(allFixed) {
            return;
        }
        Net net = new Net(timingNet);


        //TODO HOW CAN I MAKE THE COSTCALCULATOR ACCURATE
        /* Don't add nets which connect only one global block.
         * Due to this, the WLD costcalculator is not entirely
         * accurate, but that doesn't matter, because we use
         * the same (inaccurate) costcalculator to calculate
         * both the linear and legal cost, so the deviation
         * cancels out.
         */
        int numUniqueBlocks = net.blocks.length;
        if(numUniqueBlocks > 1) {
    		this.nets[dieCount].add(net);

    		for(NetBlock block : net.blocks) {
    			this.hasNetstemp[block.blockIndex] = true;
    			}
        	if(this.isTimingDriven()) {
        		this.timingNets[dieCount].add(timingNet);
        		}
    	}else if(numUniqueBlocks == 1 && isSLLEnabled) {
    		this.nets[dieCount].add(net);

      		for(NetBlock block : net.blocks) {
      			this.hasNetstemp[block.blockIndex] = true;
      		}
	      	if(this.isTimingDriven()) {
	      		this.timingNets[dieCount].add(timingNet);
	      	}

        }
    }
    
    @Override
    protected void doPlacement() throws PlacementException {
    	

        int iteration = 0;
        boolean isLastIteration = false;

    	String placetimer = "Placement took";
    	this.startSystemTimer(placetimer);
        System.out.print("\n");

    	String sllLegal = "SLL legalization took";
    	this.startSystemTimer(sllLegal);
        for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.SLLDUMMY)){
            this.fixSLLblocksLiquidMD(blockType, this.SLLrows); 
           }
        this.stopandPrintSystemTimer(sllLegal);
        while(!isLastIteration) {
            double timerBegin = System.nanoTime();
            StringBuilder output = new StringBuilder();
            int dieCounter = this.TotalDies;
            
            
            ExecutorService executor = Executors.newFixedThreadPool(this.TotalDies);
            for (int i = 0; i < dieCounter; i++) { 
                    Runnable worker = new ParallelOptiLeg(iteration,isLastIteration,timerBegin,i);  
                    executor.execute(worker);//calling execute method of ExecutorService  

              }  
            executor.shutdown();  
  	      try {
	    	    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
	    	} catch (InterruptedException e) {
	    	    e.printStackTrace();
	    	}

            isLastIteration = this.stopCondition(iteration);
            
            iteration++;

        }
    	
    	//************************************************
       this.stopandPrintSystemTimer(placetimer);
       System.out.print(this.localOutput);

        int dieCounter = 0;
    	String legaltimer = "Legalisation routine took";
    	this.startSystemTimer(legaltimer);
        while(dieCounter < this.TotalDies) {
        	//System.out.print("The die number is " + dieCounter + "\n");
            //////////// Final legalization of the LABs ////////////
            //Set up the temporary arrays.
            double [] linearXtemp = this.linearX.get(dieCounter);
            double [] linearYtemp = this.linearY.get(dieCounter);
            double [] legalXtemp = this.legalX.get(dieCounter);
            double [] legalYtemp = this.legalY.get(dieCounter);

            double [] bestLinearXtemp = this.bestLinearX.get(dieCounter);
            double [] bestLinearYtemp = this.bestLinearY.get(dieCounter);
            double [] bestLegalXtemp = this.bestLegalX.get(dieCounter);
            double [] bestLegalYtemp = this.bestLegalY.get(dieCounter);

    		for(int i = 0; i < linearXtemp.length; i++){
    			linearXtemp[i] = bestLinearXtemp[i];
    			linearYtemp[i] = bestLinearYtemp[i];
    		}
    		for(int i = 0; i < legalXtemp.length; i++){
    			legalXtemp[i] = bestLegalXtemp[i];
    			legalYtemp[i] = bestLegalYtemp[i];
    		}

    		this.linearX.set(dieCounter, linearXtemp);
    		this.linearY.set(dieCounter, linearYtemp);
    		this.legalX.set(dieCounter, legalXtemp);
    		this.legalY.set(dieCounter, legalYtemp);
    		
        	for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.CLB)){
            	this.solveLegal(blockType, isLastIteration, dieCounter);
            }
        	dieCounter++;
        }

        this.logger.println();

        
        String circuitUpdate = "Circuit update routine took";
		this.startSystemTimer(circuitUpdate);
//		ExecutorService executor = Executors.newFixedThreadPool(this.TotalDies);
//		for (int i = 0; i < dieCounter; i++) {  
//		    Runnable worker = new parallelCircuitUpdate(i);  
//		    executor.execute(worker);//calling execute method of ExecutorService  
//		  }  
//		executor.shutdown();  
//	      try {
//		    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
//		} catch (InterruptedException e) {
//		    e.printStackTrace();
//		}
	      
        this.updateCircuit();
		this.stopandPrintSystemTimer(circuitUpdate);
       
    }
    
    private StringBuilder diePlacement(int iteration, boolean isLastIteration,double timerBegin, int dieCounter) {
    	 //Initialise the iterations on both the dies 
    	this.initializeIteration(iteration, dieCounter);
        if(this.solveSeparate[dieCounter][iteration]){

        	for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.CLB)){
	
                this.solveLinear(blockType, iteration, dieCounter);
            	this.solveLegal(blockType, isLastIteration, dieCounter);
            }
            for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.HARDBLOCK)){
                this.solveLinear(blockType, iteration, dieCounter);
            	this.solveLegal(blockType, isLastIteration, dieCounter);
//
            }
//            if(!this.Seperate_fix) {
//            	//this.localOutput.append("\n SLL is optimised\n");
//    		    for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.SLLDUMMY)){
//    		        this.solveLinear(blockType, iteration, dieCounter); 
//    		        this.solveLegal(blockType, isLastIteration, dieCounter);
//    		    }
//            }

            for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.IO)){
                this.solveLinear(blockType, iteration, dieCounter); 
            	this.solveLegal(blockType, isLastIteration, dieCounter);
            }

        }else if (!this.solveSeparate[dieCounter][iteration]){

        	this.solveLinear(iteration,dieCounter);
        	this.solveLegal(iteration, isLastIteration,dieCounter);
        }  	
        this.calculateCost(iteration,dieCounter);
        this.addLinearPlacement(iteration,dieCounter);
        this.addLegalPlacement(iteration,dieCounter);
        
        double timerEnd = System.nanoTime();
        double time = (timerEnd - timerBegin) * 1e-9;
        this.printStatistics(iteration, time, dieCounter);

        return this.printStatistics(iteration, time, dieCounter);
        
        //this.localOutput.append();
    }
    private void addLinearPlacement(int iteration, int dieNumber){
        this.visualizer[dieNumber].addPlacement(
                String.format("iteration %d: linear", iteration),
                this.netBlocks.get(dieNumber), this.linearX.get(dieNumber), this.linearY.get(dieNumber),
                this.linearCost[dieNumber]);
    }
    private void addLegalPlacement(int iteration, int dieNumber){
        this.visualizer[dieNumber].addPlacement(
                String.format("iteration %d: legal", iteration),
                this.netBlocks.get(dieNumber), this.legalX.get(dieNumber), this.legalY.get(dieNumber),
                this.legalCost[dieNumber]);
    }

    
    private void updateCircuitPerDie(int dieCounter) throws PlacementException {
    	this.startTimer(T_UPDATE_CIRCUIT, dieCounter);
		double [] legalXtemp = this.legalX.get(dieCounter);
		double [] legalYtemp = this.legalY.get(dieCounter);
		
//        for(int i=0; i<legalYtemp.length; i++) {
//        	System.out.print("\nThe index is " + i + " legalX is " + legalXtemp[i] + " legalY is " + legalYtemp[i]);
//        }
        
		// Clear all previous locations
        for(GlobalBlock block : this.netBlocks.get(dieCounter).keySet()) {
            block.removeSite();
        }
        
        //System.out.print("\nThe size of the legalXtemp is " + legalXtemp.length + "\n");

        // Update locations
        for(Map.Entry<GlobalBlock, NetBlock> blockEntry : this.netBlocks.get(dieCounter).entrySet()) {
        	
            GlobalBlock block = blockEntry.getKey();
            //System.out.print("\nThe die number is " + dieCounter );
//            System.out.print("\nThe block is " + block.getName());
            NetBlock netBlock = blockEntry.getValue();
           // System.out.print("\nThe netblock is " + netBlock.blockIndex);
            int index = netBlock.blockIndex;
            int offset = (int) Math.ceil(netBlock.offset);

            int column = (int)Math.round(legalXtemp[index]);
            int row = (int)Math.round(legalYtemp[index] + offset);

            if(block.getCategory() != BlockCategory.IO) {
            	if(block.getCategory() == BlockCategory.SLLDUMMY) {
//            	   this.logger.print("\n the block is " + block.getName() + "  " + column + " " +row + " " + dieCounter );
            		AbstractSite virtualSite = this.circuitDie[dieCounter].getVirtualSite(dieCounter, column, row, true);	
//            		System.out.print("\n The site is marked" + virtualSite);
                    block.setSite(virtualSite);
                   // }
                   // 
                    
            	}else {
//                	this.logger.print("\n the block is " + block.getName() + " and type " + block.getType() + column + " " +row + " " + dieCounter );
                    Site site = (Site) this.circuitDie[dieCounter].getSite(dieCounter, column, row, true);
//                   System.out.print("\nThe site is " + site);
                    block.setSite(site);    
            	}
            }else{
//            	this.logger.print("\n or else the block is " + block.getName() + "  " + column + " " +row + " for die " + dieCounter + "\n");
                IOSite site = (IOSite) this.circuitDie[dieCounter].getSite(dieCounter, column, row, true);
                block.setSite(site);
            }
        }
        this.circuitDie[dieCounter].getTimingGraph().calculateCriticalities(true);
        this.stopTimer(T_UPDATE_CIRCUIT, dieCounter);
    }
    protected void updateCircuit() throws PlacementException {
       	
    	int dieCounter = 0; 
    	
    	while(dieCounter < this.TotalDies) {
    		this.startTimer(T_UPDATE_CIRCUIT, dieCounter);
    		double [] legalXtemp = this.legalX.get(dieCounter);
    		double [] legalYtemp = this.legalY.get(dieCounter);
    		
    		// Clear all previous locations
            for(GlobalBlock block : this.netBlocks.get(dieCounter).keySet()) {
                block.removeSite();
            }
            

            // Update locations
            for(Map.Entry<GlobalBlock, NetBlock> blockEntry : this.netBlocks.get(dieCounter).entrySet()) {
            	
                GlobalBlock block = blockEntry.getKey();

                NetBlock netBlock = blockEntry.getValue();

                int index = netBlock.blockIndex;
                int offset = (int) Math.ceil(netBlock.offset);

                int column = (int)Math.round(legalXtemp[index]);
                int row = (int)Math.round(legalYtemp[index] + offset);

                if(block.getCategory() != BlockCategory.IO) {
                	if(block.getCategory() == BlockCategory.SLLDUMMY) {
                		AbstractSite virtualSite = this.circuitDie[dieCounter].getVirtualSite(dieCounter, column, row, true);	
                        block.setSite(virtualSite);

                	}else {
	                    Site site = (Site) this.circuitDie[dieCounter].getSite(dieCounter, column, row, true);

	                    block.setSite(site);    
                	}
                }else{
 
                    IOSite site = (IOSite) this.circuitDie[dieCounter].getSite(dieCounter, column, row, true);
                    block.setSite(site);
                }
            }
            this.circuitDie[dieCounter].getTimingGraph().calculateCriticalities(true);
            this.stopTimer(T_UPDATE_CIRCUIT, dieCounter);
            dieCounter++;
            
    	}
        
    }


    private boolean isFixed(int blockIndex, int dieCounter) {
        return blockIndex < this.numIOBlocks[dieCounter];
    }


    public static double getWeight(int size) {
        switch (size) {
            case 1:
            case 2:
            case 3:  return 1;
            case 4:  return 1.0828;
            case 5:  return 1.1536;
            case 6:  return 1.2206;
            case 7:  return 1.2823;
            case 8:  return 1.3385;
            case 9:  return 1.3991;
            case 10: return 1.4493;
            case 11:
            case 12:
            case 13:
            case 14:
            case 15: return (size-10) * (1.6899-1.4493) / 5 + 1.4493;
            case 16:
            case 17:
            case 18:
            case 19:
            case 20: return (size-15) * (1.8924-1.6899) / 5 + 1.6899;
            case 21:
            case 22:
            case 23:
            case 24:
            case 25: return (size-20) * (2.0743-1.8924) / 5 + 1.8924;
            case 26:
            case 27:
            case 28:
            case 29:
            case 30: return (size-25) * (2.2334-2.0743) / 5 + 2.0743;
            case 31:
            case 32:
            case 33:
            case 34:
            case 35: return (size-30) * (2.3895-2.2334) / 5 + 2.2334;
            case 36:
            case 37:
            case 38:
            case 39:
            case 40: return (size-35) * (2.5356-2.3895) / 5 + 2.3895;
            case 41:
            case 42:
            case 43:
            case 44:
            case 45: return (size-40) * (2.6625-2.5356) / 5 + 2.5356;
            case 46:
            case 47:
            case 48:
            case 49:
            case 50: return (size-45) * (2.7933-2.6625) / 5 + 2.6625;
            default: return (size-50) * 0.02616 + 2.7933;
        }
    }


    public class NetBlock {
        final int blockIndex;
        final float offset;

        final BlockType blockType;

        NetBlock(int blockIndex, float offset, BlockType blockType) {
            this.blockIndex = blockIndex;
            this.offset = offset;

            this.blockType = blockType;
        }

        NetBlock(TimingNetBlock timingNetBlock) {
            this(timingNetBlock.blockIndex, timingNetBlock.offset, timingNetBlock.blockType);
        }

        public int getBlockIndex() {
            return this.blockIndex;
        }
        public float getOffset() {
            return this.offset;
        }

        @Override
        public boolean equals(Object otherObject) {
            if(!(otherObject instanceof NetBlock)) {
                return false;
            } else {
                return this.equals((NetBlock) otherObject);
            }
        }

        private boolean equals(NetBlock otherNetBlock) {
            return this.blockIndex == otherNetBlock.blockIndex && this.offset == otherNetBlock.offset;
        }

        @Override
        public int hashCode() {
            return 31 * this.blockIndex + (int) (2 * this.offset);
        }
    }

    class TimingNetBlock {
        final int blockIndex;
        final float offset;

        final TimingEdge timingEdge;

        final BlockType blockType;

        double criticality, criticalityLearningRate;

        TimingNetBlock(int blockIndex, float offset, TimingEdge timingEdge, double criticalityLearningRate, BlockType blockType) {
            this.blockIndex = blockIndex;
            this.offset = offset;
            this.timingEdge = timingEdge;

            this.criticality = 0.0;
            this.criticalityLearningRate = criticalityLearningRate;

            this.blockType = blockType;
        }

        TimingNetBlock(NetBlock block, TimingEdge timingEdge, double criticalityLearningRate) {
            this(block.blockIndex, block.offset, timingEdge, criticalityLearningRate, block.blockType);
        }

        void updateCriticality(){
        	this.criticality = this.criticality * (1 - this.criticalityLearningRate) + this.timingEdge.getCriticality() * this.criticalityLearningRate;
        }
    }

    class Net {
        final NetBlock[] blocks;

        Net(NetBlock block) {
            this.blocks = new NetBlock[2];
            this.blocks[0] = block;
            this.blocks[1] = block;
        }

        Net(TimingNet timingNet) {
            Set<NetBlock> netBlocks = new HashSet<>();
            netBlocks.add(timingNet.source);
            for(TimingNetBlock timingNetBlock : timingNet.sinks) {
                netBlocks.add(new NetBlock(timingNetBlock));
            }

            this.blocks = new NetBlock[netBlocks.size()];
            netBlocks.toArray(this.blocks);
        }
    }

    class TimingNet {
        final NetBlock source;
        final TimingNetBlock[] sinks;

        TimingNet(NetBlock source, int numSinks) {
            this.source = source;
            this.sinks = new TimingNetBlock[numSinks];
        }
    }

    class CritConn{
    	final int sourceIndex, sinkIndex;
    	final float sourceOffset, sinkOffset;
    	final double weight;

    	CritConn(int sourceIndex, int sinkIndex, float sourceOffset, float sinkOffset, double weight) {
    		this.sourceIndex = sourceIndex;
    		this.sinkIndex = sinkIndex;

    		this.sourceOffset = sourceOffset;
    		this.sinkOffset = sinkOffset;

    		this.weight = weight;
    	}
    }
    
    class ParallelOptiLeg implements Runnable{
    	int iteration;
    	boolean isLastIteration;
    	double timerBegin;
    	int dieCounter;
    	StringBuilder output = new StringBuilder();
    	public ParallelOptiLeg(int iteration, boolean isLastIteration, double timerBegin, int dieCounter) {
    		this.iteration = iteration;
    		this.isLastIteration = isLastIteration;
    		this.timerBegin = timerBegin;
    		this.dieCounter = dieCounter;
    	}
    	@Override
    	public void run() {
    		this.output = diePlacement(this.iteration, this.isLastIteration, this.timerBegin, this.dieCounter);
    		System.out.println(this.output);
    	}
    	
    	
    }
    
 
    
    
    class parallelCircuitUpdate implements Runnable{
    	int dieCounter;
    	StringBuilder output = new StringBuilder();
    	public parallelCircuitUpdate(int dieCounter) {
    		this.dieCounter = dieCounter;
    	}
    	@Override
    	public void run() {
    		//System.out.print("\nThe die running is " + this.dieCounter)
    		//System.out.println(Thread.currentThread().getName() + " Start");  
    		try {
				updateCircuitPerDie(this.dieCounter);
			} catch (PlacementException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		//System.out.println(Thread.currentThread().getName()+" (End)");
//    		System.out.println(this.output);
    	}
    	
    	
    }
    

    
    class BlockInfo {
        private int dieIndex;
        private int blockCounter;
        private boolean isSource;
        private int x, y;

        BlockInfo(int dieIndex, int blockCounter, boolean isSource) {
            this.dieIndex = dieIndex;
            this.blockCounter = blockCounter;
            this.isSource = isSource;
        }
        
        public int getDieID() {
        	return this.dieIndex;
        }
        public void setXY(int x, int y) {
        	this.x = x;
        	this.y = y;
        }
        public int getX() {
        	return this.x;

        }
        
        public int getY() {
        	return this.y;
        }
        
        public int getBlockCounter() {
        	return this.blockCounter;
        }
    }

}
