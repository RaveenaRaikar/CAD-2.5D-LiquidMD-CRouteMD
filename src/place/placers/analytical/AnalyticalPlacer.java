package place.placers.analytical;

import place.circuit.Circuit;
import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.block.GlobalBlock;
import place.circuit.block.SLLNetBlocks;
import place.circuit.timing.TimingGraphSLL;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.visual.PlacementVisualizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public abstract class AnalyticalPlacer extends AnalyticalAndGradientPlacer {

    private static final double EPSILON = 0.005;

    private static final String
    	O_ANCHOR_WEIGHT = "anchor weight",
    	O_ANCHOR_WEIGHT_MULTIPLIER = "anchor weight multiplier",

    	O_OUTER_EFFORT_LEVEL = "outer effort level";

    public static void initOptions(Options options) {
        AnalyticalAndGradientPlacer.initOptions(options);

        options.add(
                O_ANCHOR_WEIGHT,
                "starting anchor weight",
                new Double(0.2));

        options.add(
                O_ANCHOR_WEIGHT_MULTIPLIER,
                "anchor weight multiplier",
                new Double(1.1));

        options.add(
                O_OUTER_EFFORT_LEVEL,
                "number of solve-legalize iterations",
                new Integer(40));
    }

    protected double[] anchorWeight;
    protected final double anchorWeightMultiplier;

    private double[] latestCost, minCost;

    protected int numIterations;

    // This is only used by AnalyticalPlacerTD
    protected double tradeOff;
    protected List<CritConn>[] criticalConnections;

    protected CostCalculator[] costCalculator;

    protected Legalizer[] legalizer;
    protected LinearSolverAnalytical[] solver;

    private Map<BlockType, boolean[]>[] netMap;
    private boolean[][] allTrue;

    protected boolean[][] fixed;
    private double[][] coordinatesX;
    private double[][] coordinatesY;
    protected TimingGraphSLL timingGraphSys;

    public AnalyticalPlacer(
    		Circuit[] circuitDie, 
    		Options options, 
    		Random random, 
    		Logger logger, 
    		PlacementVisualizer[] visualizer,
    		int TotalDies,
    		int SLLrows,
    		TimingGraphSLL timingGraphSys,
    		HashMap<String, SLLNetBlocks> netToBlockSLL){

        super(circuitDie, options, random, logger, visualizer, TotalDies, SLLrows, timingGraphSys, netToBlockSLL);

        this.anchorWeight[TotalDies] = this.options.getDouble(O_ANCHOR_WEIGHT);
        this.anchorWeightMultiplier = this.options.getDouble(O_ANCHOR_WEIGHT_MULTIPLIER);

        this.numIterations = this.options.getInteger(O_OUTER_EFFORT_LEVEL) + 1;

        this.latestCost[TotalDies] = Double.MAX_VALUE;
        this.minCost[TotalDies] = Double.MAX_VALUE;

        this.criticalConnections[TotalDies] = new ArrayList<>();
        this.timingGraphSys =timingGraphSys;
    }

    protected abstract void initializeIteration(int iteration, int dieNumber);
    protected abstract void calculateTimingCost(int dieNumber);

    @Override
    public void initializeData() {
        super.initializeData();

        

        int dieCounter = 0;
        
        while(dieCounter < this.TotalDies) {
        	//Legalizer
        	this.startTimer(T_INITIALIZE_DATA, dieCounter);
            this.legalizer[dieCounter] = new HeapLegalizer(
                    this.circuitDie[dieCounter],
                    this.blockTypes.get(dieCounter),
                    this.blockTypeIndexStarts.get(dieCounter),
                    this.numIterations,
                    this.linearX.get(dieCounter),
                    this.linearY.get(dieCounter),
                    this.legalX.get(dieCounter),
                    this.legalY.get(dieCounter),
                    this.heights.get(dieCounter),
                    this.leafNode.get(dieCounter),
                    this.visualizer[dieCounter],
                    this.nets[dieCounter],
                    this.netBlocks.get(dieCounter),
                    this.logger);
            this.legalizer[dieCounter].addSetting("anneal_quality", 0.1, 0.001);
            
            //Make a list of all the nets for each blockType
            this.allTrue[dieCounter] = new boolean[this.numRealNets[dieCounter]];
            Arrays.fill(this.allTrue[dieCounter], true);

            this.netMap[dieCounter] = new HashMap<>();
            for(BlockType blockType:BlockType.getBlockTypes(BlockCategory.CLB)){
            	this.netMap[dieCounter].put(blockType, new boolean[this.numRealNets[dieCounter]]);
            	Arrays.fill(this.netMap[dieCounter].get(blockType), false);
            }
            for(BlockType blockType:BlockType.getBlockTypes(BlockCategory.HARDBLOCK)){
            	this.netMap[dieCounter].put(blockType, new boolean[this.numRealNets[dieCounter]]);
            	Arrays.fill(this.netMap[dieCounter].get(blockType), false);
            }
            for(BlockType blockType:BlockType.getBlockTypes(BlockCategory.IO)){
            	this.netMap[dieCounter].put(blockType, new boolean[this.numRealNets[dieCounter]]);
            	Arrays.fill(this.netMap[dieCounter].get(blockType), false);
            }

            for(int netCounter = 0; netCounter < this.numRealNets[dieCounter]; netCounter++) {
                Net net = this.nets[dieCounter].get(netCounter);
                for(NetBlock block : net.blocks) {
                    this.netMap[dieCounter].get(block.blockType)[netCounter] = true;
                }
            }
            
            this.fixed[dieCounter] = new boolean[this.legalX.get(dieCounter).length];

        	this.coordinatesX[dieCounter] = new double[this.legalX.get(dieCounter).length];
        	this.coordinatesY[dieCounter] = new double[this.legalY.get(dieCounter).length];

        	this.costCalculator[dieCounter] = new CostCalculator(this.nets[dieCounter]);
        	
        	this.stopTimer(T_INITIALIZE_DATA, dieCounter);
        	dieCounter++;
        }



        
    }

    @Override
    protected void solveLinear(int iteration, int dieNumber) {
    	Arrays.fill(this.fixed[dieNumber], false);
		for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.IO)){
			this.fixBlockType(blockType,dieNumber);
		}

    	this.doSolveLinear(this.allTrue[dieNumber], iteration, dieNumber);
    }

    @Override
    protected void solveLinear(BlockType solveType, int iteration, int dieNumber) {
    	Arrays.fill(this.fixed[dieNumber], false);
		for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.IO)){
			this.fixBlockType(blockType, dieNumber);
		}

    	for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.CLB)){
    		if(!blockType.equals(solveType)){
    			this.fixBlockType(blockType,dieNumber);
    		}
    	}
    	for(BlockType blockType : BlockType.getBlockTypes(BlockCategory.HARDBLOCK)){
    		if(!blockType.equals(solveType)){
    			this.fixBlockType(blockType, dieNumber);
    		}
    	}

    	this.doSolveLinear(this.netMap[dieNumber].get(solveType), iteration, dieNumber);
    }

    private void fixBlockType(BlockType fixBlockType, int dieNumber){
    	for(GlobalBlock block:this.netBlocks.get(dieNumber).keySet()){
    		if(block.getType().equals(fixBlockType)){
    			int blockIndex = this.netBlocks.get(dieNumber).get(block).getBlockIndex();
    			this.fixed[dieNumber][blockIndex] = true;
    		}
    	}
    }

    protected void doSolveLinear(boolean[] processNets, int iteration, int dieNumber){
		for(int i = 0; i < this.legalX.get(dieNumber).length; i++){
			if(this.fixed[dieNumber][i]){
				this.coordinatesX[dieNumber][i] = this.legalX.get(dieNumber)[i];
				this.coordinatesY[dieNumber][i] = this.legalY.get(dieNumber)[i];
			}else{
				this.coordinatesX[dieNumber][i] = this.linearX.get(dieNumber)[i];
				this.coordinatesY[dieNumber][i] = this.linearY.get(dieNumber)[i];
			}
		}

    	int innerIterations = iteration == 0 ? 5 : 1;
        for(int i = 0; i < innerIterations; i++) {

            this.solver[dieNumber] = new LinearSolverAnalytical(
                    this.coordinatesX[dieNumber],
                    this.coordinatesY[dieNumber],
                    this.anchorWeight[dieNumber],
                    AnalyticalPlacer.EPSILON,
                    this.fixed[dieNumber]);
            this.solveLinearIteration(processNets, iteration, dieNumber);
        }

		for(int i = 0; i < this.legalX.get(dieNumber).length; i++){
			if(!this.fixed[dieNumber][i]){
				this.linearX.get(dieNumber)[i] = this.coordinatesX[dieNumber][i];
				this.linearY.get(dieNumber)[i] = this.coordinatesY[dieNumber][i];
			}
		}
    }
    protected void solveLinearIteration(boolean[] processNets, int iteration, int dieNumber) {

        this.startTimer(T_BUILD_LINEAR, dieNumber);

        // Add connections between blocks that are connected by a net
        this.processNetsWLD(processNets, dieNumber);
        
        this.processNetsTD(dieNumber);

        // Add pseudo connections
        if(iteration > 0) {
            // this.legalX and this.legalY store the solution with the lowest cost
            // For anchors, the last (possibly suboptimal) solution usually works better
            this.solver[dieNumber].addPseudoConnections(this.legalX.get(dieNumber), this.legalY.get(dieNumber));
        }

        this.stopTimer(T_BUILD_LINEAR, dieNumber);

        // Solve and save result
        this.startTimer(T_SOLVE_LINEAR, dieNumber);
        this.solver[dieNumber].solve();
        this.stopTimer(T_SOLVE_LINEAR, dieNumber);
    }

    protected void processNetsWLD(boolean[] processNet, int dieNumber) {
        for(Net net : this.nets[dieNumber]){
            this.solver[dieNumber].processNetWLD(net);
        }
    }
    protected void processNetsTD(int dieNumber) {
        for(CritConn critConn:this.criticalConnections[dieNumber]){
        	this.solver[dieNumber].processNetTD(critConn);
        }
    }

    @Override
    protected void solveLegal(int iteration, boolean isLastIteration, int dieNumber) {
        this.startTimer(T_LEGALIZE, dieNumber);
       // this.logger.print("This is true");
        for(BlockType legalizeType:BlockType.getBlockTypes(BlockCategory.CLB)){
        	this.legalizer[dieNumber].legalize(legalizeType, isLastIteration);
        }
        for(BlockType legalizeType:BlockType.getBlockTypes(BlockCategory.HARDBLOCK)){
        	this.legalizer[dieNumber].legalize(legalizeType, isLastIteration);
        }
        for(BlockType legalizeType:BlockType.getBlockTypes(BlockCategory.IO)){
        	this.legalizer[dieNumber].legalize(legalizeType, isLastIteration);
        }
        this.stopTimer(T_LEGALIZE, dieNumber);
    }

    @Override
    protected void solveLegal(BlockType legalizeType, boolean isLastIteration, int dieNumber) {
        this.startTimer(T_LEGALIZE, dieNumber);
        
        this.legalizer[dieNumber].legalize(legalizeType, isLastIteration);
        this.stopTimer(T_LEGALIZE, dieNumber);
    }
    
    @Override
    protected void calculateCost(int iteration, int dieNumber){
    	this.startTimer(T_UPDATE_CIRCUIT, dieNumber);
    	
    	this.linearCost[dieNumber] = this.costCalculator[dieNumber].calculate(this.linearX.get(dieNumber), this.linearY.get(dieNumber));
        this.legalCost[dieNumber] = this.costCalculator[dieNumber].calculate(this.legalX.get(dieNumber), this.legalY.get(dieNumber));
    	
    	if(this.isTimingDriven()){
    		this.calculateTimingCost(dieNumber);
    	}

    	this.stopTimer(T_UPDATE_CIRCUIT,dieNumber);
    }

    @Override
    protected void addStatTitles(List<String> titles) {
        titles.add("it");
        titles.add("anchor");
        titles.add("anneal Q");
        
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
    }

    @Override
    protected StringBuilder printStatistics(int iteration, double time, int dieNumber) {
    	List<String> stats = new ArrayList<>();

    	stats.add(Integer.toString(iteration));
    	stats.add(String.format("%.2f", this.anchorWeight[dieNumber]));
    	stats.add(String.format("%.5g", this.legalizer[dieNumber].getSettingValue("anneal_quality")));

        //Wirelength cost
        stats.add(String.format("%.0f", this.linearCost[dieNumber]));
        stats.add(String.format("%.0f", this.legalCost[dieNumber]));

        //Timing cost
        if(this.isTimingDriven()){
        	stats.add(String.format("%.4g", this.timingCost[dieNumber]));
        }

        stats.add(this.latestCost[dieNumber] == this.minCost[dieNumber] ? "yes" : "");
        stats.add(String.format("%.0f", time*Math.pow(10, 3)));
        stats.add(String.format("%d", this.criticalConnections[dieNumber].size()));

    	return this.printStats(stats.toArray(new String[0]));
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
    public void printLegalizationRuntime(int dieNumber){
    	this.legalizer[dieNumber].printLegalizationRuntime();
    }
}
