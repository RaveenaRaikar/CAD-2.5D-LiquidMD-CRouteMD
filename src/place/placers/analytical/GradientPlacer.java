package place.placers.analytical;

import place.circuit.Circuit;
import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.block.AbstractBlock;
import place.circuit.block.GlobalBlock;
import place.circuit.exceptions.BlockTypeException;
import place.circuit.pin.AbstractPin;
import place.circuit.timing.TimingGraphSLL;
import place.circuit.timing.TimingNode;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.visual.PlacementVisualizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


public abstract class GradientPlacer extends AnalyticalAndGradientPlacer {

    private static final String
        O_ANCHOR_WEIGHT_EXPONENT = "anchor weight exponent",
        O_ANCHOR_WEIGHT_STOP = "anchor weight stop",

        O_LEARNING_RATE_START = "learning rate start",
        O_LEARNING_RATE_STOP = "learning rate stop",

        O_MAX_CONN_LENGTH_RATIO = "max conn length ratio",
        O_MAX_CONN_LENGTH = "max conn length",

        O_BETA1 = "beta1",
        O_BETA2 = "beta2",
        O_EPS = "eps",

        O_OUTER_EFFORT_LEVEL_SPARSE = "outer effort level sparse",
        O_OUTER_EFFORT_LEVEL_DENSE = "outer effort level dense",
        
        O_INNER_EFFORT_LEVEL_START = "inner effort level start",
        O_INNER_EFFORT_LEVEL_STOP = "inner effort level stop",
        
        /////////////////////////
        // Parameters to sweep //
        /////////////////////////
        O_INTERPOLATION_FACTOR = "interpolation",
        O_CLUSTER_SCALING_FACTOR = "cluster scaling factor",
        O_SPREAD_BLOCK_ITERATIONS = "spread block iterations",
        		
        O_STEP_SIZE_START = "step size start",
        O_STEP_SIZE_STOP = "step size stop";

    @SuppressWarnings("deprecation")
	public static void initOptions(Options options) {
        AnalyticalAndGradientPlacer.initOptions(options);

        options.add(
                O_ANCHOR_WEIGHT_EXPONENT,
                "anchor weight exponent",
                new Double(2));
        options.add(
                O_ANCHOR_WEIGHT_STOP,
                "anchor weight at which the placement is finished (max: 1)",
                new Double(0.85));

        options.add(
                O_LEARNING_RATE_START,
                "ratio of distance to optimal position that is moved",
                new Double(1));
        options.add(
                O_LEARNING_RATE_STOP,
                "ratio of distance to optimal position that is moved",
                new Double(0.2));
        
        options.add(
                O_MAX_CONN_LENGTH_RATIO,
                "maximum connection length as a ratio of the circuit width",
                new Double(0.25));
        options.add(
                O_MAX_CONN_LENGTH,
                "maximum connection length",
                new Integer(30));

        options.add(
                O_BETA1,
                "adam gradient descent beta1 parameter",
                new Double(0.9));
        options.add(
                O_BETA2,
                "adam gradient descent beta2 parameter",
                new Double(0.999));
        options.add(
                O_EPS,
                "adam gradient descent eps parameter",
                new Double(10e-10));
        
        options.add(
                O_OUTER_EFFORT_LEVEL_SPARSE,
                "number of solve-legalize iterations for sparse designs",
                new Integer(15));
        options.add(
                O_OUTER_EFFORT_LEVEL_DENSE,
                "number of solve-legalize iterations for dense designs",
                new Integer(40));
        
        options.add(
                O_INNER_EFFORT_LEVEL_START,
                "number of gradient steps to take in each outer iteration in the beginning",
                new Integer(200));
        
        options.add(
                O_INNER_EFFORT_LEVEL_STOP,
                "number of gradient steps to take in each outer iteration at the end",
                new Integer(50));
        
        options.add(
                O_INTERPOLATION_FACTOR,
                "the interpolation between linear and legal solution as starting point for detailed legalization",
                new Double(0.5));
        options.add(
                O_CLUSTER_SCALING_FACTOR,
                "the force of the inter-cluster spreading is scaled to avoid large forces",
                new Double(0.75));
        options.add(
        		O_SPREAD_BLOCK_ITERATIONS,
                "the number of independent block spreading iterations",
                new Integer(250));
        options.add(
                O_STEP_SIZE_START,
                "initial step size in gradient cluster legalizer",
                new Double(0.2));
        options.add(
                O_STEP_SIZE_STOP,
                "final step size in gradient cluster legalizer",
                new Double(0.05));
    }

    protected double[] anchorWeight;
    protected final double anchorWeightStop, anchorWeightExponent;
    protected StringBuilder localOutput;
    private double[] maxConnectionLength;
    protected double[] learningRate, learningRateMultiplier;
    private final double beta1, beta2, eps;

    protected int numIterations;
    protected int[] effortLevel;
    protected final int effortLevelStart, effortLevelStop;

    // Only used by GradientPlacerTD
    protected double tradeOff; 
    protected List<List<CritConn>> criticalConnections;

    private CostCalculator[] costCalculator;
    private SllLegalizer sllLegalizer;

    protected Legalizer[] legalizer;
    protected LinearSolverGradient[] solver;

    private List<Map<BlockType, boolean[]>> netMap;
    private List<boolean[]> allTrue;
    private List<int[]> netStarts;
    private List<int[]> netEnds;
    private List<int[]> netBlockIndexes;
    private List<float[]> netBlockOffsets;
    private int[] netBlockSize;

    protected List<boolean[]> fixed;
   // protected List<boolean[]> sllFixed;
    private List<double[]> coordinatesX;
    private List<double[]> coordinatesY;

    public GradientPlacer(
            Circuit[] circuitDie,
            Options options,
            Random random,
            Logger logger,
            PlacementVisualizer[] visualizer,
            int TotalDies,
            int SLLrows,
            TimingGraphSLL timingGraphSLL){

		super(circuitDie, options, random, logger, visualizer, TotalDies,SLLrows, timingGraphSLL);
        this.anchorWeight = new double[this.TotalDies];
        this.criticalConnections = new ArrayList<>();
        this.anchorWeightExponent = this.options.getDouble(O_ANCHOR_WEIGHT_EXPONENT);
        this.anchorWeightStop = this.options.getDouble(O_ANCHOR_WEIGHT_STOP);

    	this.effortLevelStart = this.options.getInteger(O_INNER_EFFORT_LEVEL_START);
    	this.effortLevelStop = this.options.getInteger(O_INNER_EFFORT_LEVEL_STOP);
    	this.effortLevel = new int[this.TotalDies];
    	this.maxConnectionLength = new double[this.TotalDies];
    	this.learningRate = new double[this.TotalDies];
    	this.learningRateMultiplier = new double[this.TotalDies];
    	int dieCount = 0;


    	double averageCLBusuage = (this.circuitDie[0].ratioUsedCLB() + this.circuitDie[1].ratioUsedCLB())/2;
    	while(dieCount < this.TotalDies) {
    		System.out.print("\n THe ratio of used CLBs is " + this.circuitDie[dieCount].ratioUsedCLB());

	    	if(averageCLBusuage > 0.8) {
	    		this.numIterations = this.options.getInteger(O_OUTER_EFFORT_LEVEL_DENSE) + 1;

	    	} else {
	    		this.numIterations = this.options.getInteger(O_OUTER_EFFORT_LEVEL_SPARSE) + 1;
	    	}
	    	if(averageCLBusuage < 0.4) {

	        	this.maxConnectionLength[dieCount] = this.circuitDie[dieCount].getWidth() * this.options.getDouble(O_MAX_CONN_LENGTH_RATIO);
	        } else {
	        	this.maxConnectionLength[dieCount] = this.options.getInteger(O_MAX_CONN_LENGTH);
	        }

	        this.criticalConnections.add(new ArrayList<>());
	        this.anchorWeight[dieCount] = 0.0;
	        this.effortLevel[dieCount] = this.effortLevelStart;
	        this.learningRate[dieCount] = this.options.getDouble(O_LEARNING_RATE_START);
	        this.learningRateMultiplier[dieCount] = Math.pow(this.options.getDouble(O_LEARNING_RATE_STOP) / this.options.getDouble(O_LEARNING_RATE_START), 1.0 / (this.numIterations - 1.0));

	        dieCount++;
    	}

        this.beta1 = this.options.getDouble(O_BETA1);
        this.beta2 = this.options.getDouble(O_BETA2);
        this.eps = this.options.getDouble(O_EPS);

    }

    protected abstract void initializeIteration(int iteration, int dieCounter);
    protected abstract void calculateTimingCost(int dieCounter);

    @Override
    public void initializeData() {
        super.initializeData();

        
        this.legalizer = new Legalizer[this.TotalDies];
        this.solver = new LinearSolverGradient[this.TotalDies];
        this.costCalculator = new CostCalculator[this.TotalDies];
        
        
        this.allTrue = new ArrayList<boolean[]>();
        this.netMap = new ArrayList<Map<BlockType, boolean[]>>();
        this.netStarts = new ArrayList<int[]>();
        this.netEnds = new ArrayList<int[]>();
        this.netBlockIndexes = new ArrayList<int[]>();
        this.netBlockOffsets = new ArrayList<float[]>();
        this.fixed = new ArrayList<boolean[]>();
        this.coordinatesX = new ArrayList<double[]>();
        this.coordinatesY = new ArrayList<double[]>();
        this.netBlockSize = new int[this.TotalDies];
        
        
        int dieCount = 0;

        while(dieCount < this.TotalDies) {

        	this.startTimer(T_INITIALIZE_DATA, dieCount);

        	if(!this.hasHierarchyInformation){
            	this.legalizer[dieCount] = new HeapLegalizer(
            			this.circuitDie[dieCount],
            			this.blockTypes.get(dieCount),
            			this.blockTypeIndexStarts.get(dieCount),
            			this.numIterations,
            			this.linearX.get(dieCount),
            			this.linearY.get(dieCount),
            			this.legalX.get(dieCount),
            			this.legalY.get(dieCount),
            			this.heights.get(dieCount),
            			this.leafNode.get(dieCount),
            			this.visualizer[dieCount],
            			this.nets[dieCount],
            			this.netBlocks.get(dieCount),
            			this.logger);
            	this.legalizer[dieCount].addSetting("anneal_quality", 0.1,  0.001);
        	}else {
	        double widthFactor = Math.pow((1.0 * this.circuitDie[dieCount].getWidth()) / 100.0, 1.3);
	        this.logger.println("------------------");
	        this.logger.println("Circuit width: " + this.circuitDie[dieCount].getWidth());
	        this.logger.println("Width factor: " + String.format("%.2f", widthFactor));
	        this.logger.println("------------------\n");
	        
	        this.legalizer[dieCount] = new GradientLegalizer(
	                this.circuitDie[dieCount],
	                this.blockTypes.get(dieCount),
	                this.blockTypeIndexStarts.get(dieCount),
	                this.numIterations,
	                this.linearX.get(dieCount),
	                this.linearY.get(dieCount),
	                this.legalX.get(dieCount),
	                this.legalY.get(dieCount),
	                this.heights.get(dieCount),
	                this.leafNode.get(dieCount),
	                this.visualizer[dieCount],
	                this.nets[dieCount],
	                this.netBlocks.get(dieCount),
	                this.logger);
	        this.legalizer[dieCount].addSetting(
	        		"anneal_quality", 
	        		0.1,
	        		0.001);
	        this.legalizer[dieCount].addSetting(
	        		"step_size", 
	        		widthFactor * this.options.getDouble(O_STEP_SIZE_START),  
	        		widthFactor * this.options.getDouble(O_STEP_SIZE_STOP));
	        this.legalizer[dieCount].addSetting(
	        		"interpolation", 
	        		this.options.getDouble(O_INTERPOLATION_FACTOR));
	        this.legalizer[dieCount].addSetting(
	        		"cluster_scaling",
	        		this.options.getDouble(O_CLUSTER_SCALING_FACTOR));
	        this.legalizer[dieCount].addSetting(
	        		"block_spreading",
	        		this.options.getInteger(O_SPREAD_BLOCK_ITERATIONS));
       }
            // Juggling with objects is too slow (I profiled this,
            // the speedup is around 40%)
            // Build some arrays of primitive types
            int netBlockSize = 0;
            for(int i = 0; i < this.numRealNets[dieCount]; i++) {
                netBlockSize += this.nets[dieCount].get(i).blocks.length;
            }

            this.netBlockSize[dieCount] = netBlockSize;

            
            boolean [] allTruetemp = new boolean[this.numRealNets[dieCount]];
            Arrays.fill(allTruetemp, true);
            this.allTrue.add(allTruetemp);
            
            Map<BlockType, boolean[]> netMapTemp = new HashMap<>();

            for(BlockType blockType:this.blockTypes.get(dieCount)){
            	netMapTemp.put(blockType, new boolean[this.numRealNets[dieCount]]);
            	Arrays.fill(netMapTemp.get(blockType), false);
            }
            
            this.netMap.add(netMapTemp);
            
            int [] netStartstemp = new int[this.numRealNets[dieCount]];
            int [] netEndstemp = new int[this.numRealNets[dieCount]];
            int [] netBlockIndexestemp = new int[netBlockSize];
            float [] netBlockOffsetstemp = new float[netBlockSize];
            int netBlockCounter = 0;
            for(int netCounter = 0; netCounter < this.numRealNets[dieCount]; netCounter++) {
            	netStartstemp[netCounter] = netBlockCounter;

            	Net net = this.nets[dieCount].get(netCounter);

                for(NetBlock block : net.blocks) {
                	netBlockIndexestemp[netBlockCounter] = block.blockIndex;
                	netBlockOffsetstemp[netBlockCounter] = block.offset;

                    netBlockCounter++;

                    netMapTemp.get(block.blockType)[netCounter] = true;
                }

                netEndstemp[netCounter] = netBlockCounter;
            }

            this.netStarts.add(netStartstemp);
            this.netEnds.add(netEndstemp);
            this.netBlockIndexes.add(netBlockIndexestemp);
            this.netBlockOffsets.add(netBlockOffsetstemp);
            this.netMap.set(dieCount, netMapTemp);

            boolean [] fixedtemp = new boolean[this.linearX.get(dieCount).length];

            this.fixed.add(fixedtemp);


            double [] coordinatesXtemp = new double[this.linearX.get(dieCount).length];
            double [] coordinatesYtemp = new double[this.linearY.get(dieCount).length];
            
            this.coordinatesX.add(coordinatesXtemp);
            this.coordinatesY.add(coordinatesYtemp);


            this.solver[dieCount] = new LinearSolverGradient(
                    this.coordinatesX.get(dieCount),
                    this.coordinatesY.get(dieCount),
                    this.netBlockIndexes.get(dieCount),
                    this.netBlockOffsets.get(dieCount),
                    this.maxConnectionLength[dieCount],
                    this.fixed.get(dieCount),
                    this.beta1, 
                    this.beta2, 
                    this.eps);

            this.costCalculator[dieCount] = new CostCalculator(this.nets[dieCount]);

            
            this.stopTimer(T_INITIALIZE_DATA, dieCount);
            dieCount++;
        }
        
        this.sllLegalizer = new SllLegalizer(
        		this.linearX, 
        		this.linearY, 
        		this.legalX, 
        		this.legalY, 
        		this.SLLcounter,
        		this.sLLNodeList,
        		this.circuitDie[0].getWidth(),
        		this.circuitDie[0].getHeight(),
        		this.SLLrows);
    }
    
    int isValidXlocation(int Xloc, int direction) {
    	//This is a temporary solution to avoid placing blocks on the hardblock columns
    	int RAMstart = 2;
    	int DSPstart = 6;
    	int columnPeriod = 16;
    	
    	int startX = Xloc;
    	int FinalXloc = 0;
    	
    	if((startX == RAMstart) || (startX == DSPstart)) {
    		FinalXloc = startX + direction;
    	}else if((startX - RAMstart)%columnPeriod == 0) {
    		FinalXloc = startX + direction;
    	}else if((startX - DSPstart)%columnPeriod == 0) {
    		FinalXloc = startX + direction;
    	}else {

    		FinalXloc = startX;
    	}

    	return FinalXloc;
    }

    @Override
    protected void fixSLLblocks(BlockType solveType, int SLLrowsCount) {
    	//Check if the solvetype is dummy SLL block
    	//Get the list of SLL blocks/nets
    	// Compare the names and get the blocks on both the dies.
    	//Set the legal and linear arrays for the block counter to the specific(but Random)
    	//locations.

    	int XCounter = 0, YCounter = 0; 
    	int SLLRows = SLLrowsCount-1;

    	int width = this.circuitDie[0].getWidth();
    	int height = this.circuitDie[0].getHeight();
    	

    	
    	int Xloc = 0,Yloc = 0;
    	int XlastLeft = width, XlastRight = width;
    	int direction = 0;
    	boolean isRight = false;
    	boolean isXdirection = true;
		
		for(String block: this.SLLcounter.keySet())
    	{		
			if(isXdirection) {
				if(isRight) {
					direction = 1;				
					Xloc = XlastRight + 1; 
					Xloc = isValidXlocation(Xloc,direction);
					XlastRight = Xloc;
					isRight = false;
				}else {
					direction = - 1;
					Xloc = XlastLeft - 1;
					Xloc = isValidXlocation(Xloc,direction);
					XlastLeft = Xloc;
					isRight = true;
				}
				isXdirection = false;
			}
			
			Yloc = height - YCounter - 1;
    		List<Integer> blockcounter = this.SLLcounter.get(block);

    		for(int dieCounter = 0; dieCounter < this.TotalDies ; dieCounter++) {
    			this.legalX.get(dieCounter)[blockcounter.get(dieCounter)] = Xloc;
    			this.linearX.get(dieCounter)[blockcounter.get(dieCounter)] = Xloc;
    			if (dieCounter == 0) {
    				this.legalY.get(dieCounter)[blockcounter.get(dieCounter)] = Yloc;
    				this.linearY.get(dieCounter)[blockcounter.get(dieCounter)] = Yloc;
    			}else {
    				this.legalY.get(dieCounter)[blockcounter.get(dieCounter)] = SLLRows - YCounter;
    				this.linearY.get(dieCounter)[blockcounter.get(dieCounter)] = SLLRows - YCounter;
    			}
    		}
    		YCounter++;
    		if(YCounter==SLLRows) {
    			YCounter = 0;
    			isXdirection = true;
    			if(!isRight) {
    				XCounter++;
    			}
    		}
    	}
    }

    @Override
    protected void synchroniseSLLblocks(StringBuilder localOutput) {

    	this.sllLegalizer.legaliseSLLblock(localOutput);
    	for(int dieCounter = 0; dieCounter<this.TotalDies; dieCounter++) {
    		 double [] legalXtemp = this.sllLegalizer.legalX.get(dieCounter);
             double [] legalYtemp = this.sllLegalizer.legalY.get(dieCounter);
             for(int i = 0; i < legalXtemp.length; i++){
            	 this.legalX.get(dieCounter)[i] = legalXtemp[i];
            	 this.legalY.get(dieCounter)[i] = legalYtemp[i];
     		}
    	}

    }
    @Override
    protected void solveLinear(int iteration, int dieNum) {
    	boolean [] fixedtemp = this.fixed.get(dieNum);
    	Arrays.fill(fixedtemp, false);
    	this.fixed.set(dieNum, fixedtemp);
    	
    	//Fix the position of all IO blocks in the array
		for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.IO)){
			this.fixBlockType(blockType, dieNum);
		}
		if(this.Global_fix) {

			for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.SLLDUMMY)){
				this.fixBlockType(blockType, dieNum);
			}	
		}
		this.doSolveLinear(this.allTrue.get(dieNum),dieNum);
    }

    
    //Treat blocks the same way as normal routine
    //Fix the block positions in global optimisation
    //Allow blocks to be movable in global but do not optimise in seperate
    //Allow blocks to be movable in seperate optimisation 
    @Override
    protected void solveLinear(BlockType solveType, int iteration, int dieNum) {
    	boolean [] fixedtemp = this.fixed.get(dieNum);
    	Arrays.fill(fixedtemp, false);
    	this.fixed.set(dieNum, fixedtemp);
    	
		for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.IO)){
			if(!blockType.equals(solveType)) {
				this.fixBlockType(blockType, dieNum);
			}
		}
		
		for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.SLLDUMMY)){
			if(!blockType.equals(solveType)){
				this.fixBlockType(blockType, dieNum);
			}
		}

    	for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.CLB)){
    		if(!blockType.equals(solveType)){
    			this.fixBlockType(blockType, dieNum);
    		}
    	}
    	for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.HARDBLOCK)){
    		if(!blockType.equals(solveType)){
    			this.fixBlockType(blockType, dieNum);
    		}
    	}
		this.doSolveLinear(this.netMap.get(dieNum).get(solveType), dieNum);


    }

    private void fixBlockType(BlockType fixBlockType, int dieNum){
    	//Iterate through all the blocks in the map and process the blocks of the specific block type
    	// Identify the block based on the index(counter) and fix the position as true
    	
    	boolean [] fixedtemp = this.fixed.get(dieNum);

    	for(GlobalBlock block:this.netBlocks.get(dieNum).keySet()){
    	
    		if(block.getType().equals(fixBlockType)){
    			int blockIndex = this.netBlocks.get(dieNum).get(block).getBlockIndex();
    			fixedtemp[blockIndex] = true;
    		}
    	}

    	this.fixed.set(dieNum, fixedtemp);
    }
    

    private void doSolveLinear(boolean[] processNets, int dieNum){
    	
    	//System.out.print("\nThe die count in solve linear is " +dieNum + "\n" );
    	//Temp arrays
    	double [] legalXtemp = this.legalX.get(dieNum);
    	double [] legalYtemp = this.legalY.get(dieNum);
    	double [] linearXtemp = this.linearX.get(dieNum);
    	double [] linearYtemp = this.linearY.get(dieNum); 
    	double [] coordinatesXtemp = this.coordinatesX.get(dieNum);
    	double [] coordinatesYtemp = this.coordinatesY.get(dieNum);

		for(int i = 0; i < this.linearX.get(dieNum).length; i++){
			//IO & SLL blocks are fixed positions
			if(this.fixed.get(dieNum)[i]){
				//assign legal positions for all fixed positions and linear positions for others
				coordinatesXtemp[i] = legalXtemp[i];
				coordinatesYtemp[i] = legalYtemp[i];
			}else{
				coordinatesXtemp[i] = linearXtemp[i];
				coordinatesYtemp[i] = linearYtemp[i];
			}
		}
        
		this.coordinatesX.set(dieNum, coordinatesXtemp);
		this.coordinatesY.set(dieNum, coordinatesYtemp);
		
        for(int i = 0; i < this.effortLevel[dieNum]; i++) {
            this.solveLinearIteration(processNets, dieNum);

        }
        
		for(int i = 0; i < this.linearX.get(dieNum).length; i++){
			if(!this.fixed.get(dieNum)[i]){
				linearXtemp[i] = coordinatesXtemp[i];
				linearYtemp[i] = coordinatesYtemp[i];
			}
		}
		
		this.linearX.set(dieNum, linearXtemp);
		this.linearY.set(dieNum, linearYtemp);
    }

    /*
     * Build and solve the linear system ==> recalculates linearX and linearY
     * If it is the first time we solve the linear system ==> don't take pseudonets into account
     */
    

    protected void solveLinearIteration(boolean[] processNets, int dieNum) {
        this.startTimer(T_BUILD_LINEAR, dieNum);

        // Set value of alpha and reset the solver
        this.solver[dieNum].initializeIteration(this.anchorWeight[dieNum], this.learningRate[dieNum]);

        // Process nets
        this.processNets(processNets, dieNum);

        // Add pseudo connections
        if(this.anchorWeight[dieNum]!= 0.0) {
            // this.legalX and this.legalY store the solution with the lowest cost
            // For anchors, the last (possibly suboptimal) solution usually works better
            this.solver[dieNum].addPseudoConnections(this.legalX.get(dieNum), this.legalY.get(dieNum));
        }

        this.stopTimer(T_BUILD_LINEAR, dieNum);

        // Solve and save result
        this.startTimer(T_SOLVE_LINEAR,dieNum);
        this.solver[dieNum].solve();
        this.stopTimer(T_SOLVE_LINEAR, dieNum);
    }

    protected void processNets(boolean[] processNets, int dieNum) {

    	int numNets = this.netEnds.get(dieNum).length;

    	for(int netIndex = 0; netIndex < numNets; netIndex++) {
    		if(processNets[netIndex]){

    			this.solver[dieNum].processNet(this.netStarts.get(dieNum)[netIndex], this.netEnds.get(dieNum)[netIndex]);
    		}
    	}
    }

    @Override
    protected void solveLegal(int iteration, boolean isLastIteration, int dieNum) {
        this.startTimer(T_LEGALIZE, dieNum);
        for(BlockType legalizeType:BlockType.getBlockTypes(BlockCategory.CLB)){
        	this.legalizer[dieNum].legalize(legalizeType, isLastIteration);
        }
        for(BlockType legalizeType:BlockType.getBlockTypes(BlockCategory.HARDBLOCK)){
        	this.legalizer[dieNum].legalize(legalizeType, isLastIteration);
        }

        for(BlockType legalizeType:BlockType.getBlockTypes(BlockCategory.IO)){
        	this.legalizer[dieNum].legalize(legalizeType, isLastIteration);
        }

            for(BlockType legalizeType:BlockType.getBlockTypes(BlockCategory.SLLDUMMY)){

           	  this.legalizer[dieNum].legalize(legalizeType, isLastIteration);
            }
        

        this.stopTimer(T_LEGALIZE, dieNum);
    }

    @Override
    protected void solveLegal(BlockType legalizeType, boolean lastIteration, int dieNum) {
        this.startTimer(T_LEGALIZE, dieNum);
        this.legalizer[dieNum].legalize(legalizeType, lastIteration);
        this.stopTimer(T_LEGALIZE, dieNum);
    }
    

    protected void updateSLLPositions(int dieCounter) {
    	//From the block list find the SLL blocks.
    	//Connection B1-D1. SLL block is acting as the sink. Get source block position and update the array.
    	//Connection D2-Bi. SLL block is acting as the source. Set to XY contributing to minimum BBox.
    	
    	
    	for(GlobalBlock block:this.netBlocks.get(dieCounter).keySet()){
        	
    		if(block.isSLLDummy()){
    			if(block.isSLLSink()) {
    				int sllIndex = this.netBlocks.get(dieCounter).get(block).getBlockIndex();
    				int blockIndex = 0;
    				for(AbstractPin datain: block.getInputPins()) {
    					if(datain.getSource() != null) {
    						GlobalBlock sourceBlock = (GlobalBlock) datain.getSource().getOwner();

    						 blockIndex = this.netBlocks.get(dieCounter).get(sourceBlock).getBlockIndex();
    					}
    				}

    				double Xcord = this.legalX.get(dieCounter)[blockIndex];
    				double Ycord = this.legalY.get(dieCounter)[blockIndex];
    				this.legalX.get(dieCounter)[sllIndex] = Xcord;
    				this.legalY.get(dieCounter)[sllIndex] = Ycord;
    			}else if(block.isSLLSource()) {
    				int sllIndex = this.netBlocks.get(dieCounter).get(block).getBlockIndex();
    			}
    		}
    	}
    	
    	
    }
    
    @Override
    protected void calculateCost(int iteration, int dieNumber){
    	this.startTimer(T_UPDATE_CIRCUIT, dieNumber);

    	this.linearCost[dieNumber] = this.costCalculator[dieNumber].calculate(this.linearX.get(dieNumber), this.linearY.get(dieNumber));
    	this.legalCost[dieNumber] = this.costCalculator[dieNumber].calculate(this.legalX.get(dieNumber), this.legalY.get(dieNumber));
    	
    	this.currentCost[dieNumber] = this.legalCost[dieNumber];
   
    	if(this.isTimingDriven()){
    		this.calculateTimingCost(dieNumber);
    		this.currentCost[dieNumber] *= this.timingCost[dieNumber];
    	}
    	
    	
    	//Setup temp arrays
    	
    	double [] bestllinearXtemp = this.bestLinearX.get(dieNumber);
    	double [] bestllinearYtemp = this.bestLinearY.get(dieNumber);
    	double [] bestLegalXtemp = this.bestLegalX.get(dieNumber);
    	double [] bestLegalYtemp = this.bestLegalY.get(dieNumber);
    	//Save minimum cost for dense designs

    		if(this.currentCost[dieNumber] < this.bestCost[dieNumber]){
        		this.bestCost[dieNumber] = this.currentCost[dieNumber];
        		for(int i = 0; i < this.linearX.get(dieNumber).length; i++){
        			bestllinearXtemp[i] = this.linearX.get(dieNumber)[i];
        			bestllinearYtemp[i] = this.linearY.get(dieNumber)[i];
        			
        			bestLegalXtemp[i] = this.legalX.get(dieNumber)[i];
        			bestLegalYtemp[i] = this.legalY.get(dieNumber)[i];
        		}
        	}
    		
    	this.bestLinearX.set(dieNumber, bestllinearXtemp);
    	this.bestLinearY.set(dieNumber, bestllinearYtemp);
    	this.bestLegalX.set(dieNumber, bestLegalXtemp);
    	this.bestLegalY.set(dieNumber,bestLegalYtemp);

    	this.stopTimer(T_UPDATE_CIRCUIT, dieNumber);
    }

    @Override
    protected void addStatTitles(List<String> titles) {
    	
    	int dieCount = 0;
        titles.add("it");
        titles.add("effort level");
        titles.add("stepsize");
        titles.add("anchor");
        titles.add("max conn length");

        //Wirelength cost
        titles.add("BB linear");
        titles.add("BB legal");

        //Timing cost
        if(this.isTimingDriven()){
        	titles.add("max delay");
        }
        
        titles.add("best");

        titles.add("time (ms)");
        titles.add("crit conn");
    	
    	while(dieCount < this.TotalDies) {
            
            for(String setting:this.legalizer[dieCount].getLegalizerSetting()){
            	titles.add(setting);
            }
            dieCount++;
    	}

    }

    @Override
    protected StringBuilder printStatistics(int iteration, double time , int dieCounter) {
    	StringBuilder localOutput = new StringBuilder();
        List<String> stats = new ArrayList<>();

        stats.add(Integer.toString(iteration));
        stats.add(Integer.toString(this.effortLevel[dieCounter]));
        stats.add(String.format("%.3f", this.learningRate[dieCounter]));
        stats.add(String.format("%.3f", this.anchorWeight[dieCounter]));
        stats.add(String.format("%.1f", this.maxConnectionLength[dieCounter]));

        //Wirelength cost
        stats.add(String.format("%.0f", this.linearCost[dieCounter]));
        stats.add(String.format("%.0f", this.legalCost[dieCounter]));

        //Timing cost
        if(this.isTimingDriven()){
        	stats.add(String.format("%.4g", this.timingCost[dieCounter]));
        }
        
        stats.add(this.currentCost[dieCounter] == this.bestCost[dieCounter] ? "yes" : "");

        stats.add(String.format("%.0f", time*Math.pow(10, 3)));
        stats.add(String.format("%d", this.criticalConnections.size()));
        
        stats.add("Null");
        for(String setting:this.legalizer[dieCounter].getLegalizerSetting()){
        	stats.add(String.format("%.3f", this.legalizer[dieCounter].getSettingValue(setting)));
        }
        localOutput.append(printStats(stats.toArray(new String[0])));
        return localOutput;

    }

    @Override
    protected int numIterations() {
    	return this.numIterations;
    }

    @Override
    protected boolean stopCondition(int iteration) {

    	return iteration + 1 >= this.numIterations;

    
    }
    
    @Override
    public void printLegalizationRuntime(int dieNum){
    	this.legalizer[dieNum].printLegalizationRuntime();
    }
}
