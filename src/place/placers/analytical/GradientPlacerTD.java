package place.placers.analytical;

import place.circuit.Circuit;
import place.circuit.architecture.BlockType;
import place.circuit.block.SLLNetBlocks;
import place.circuit.timing.TimingGraph;
import place.circuit.timing.TimingGraphSLL;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.visual.PlacementVisualizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class GradientPlacerTD extends GradientPlacer {

    private static final String
        O_CRITICALITY_EXPONENT = "criticality exponent",
        O_CRITICALITY_THRESHOLD = "criticality threshold",
        O_MAX_PER_CRIT_EDGE = "max per crit edge",
        O_TRADE_OFF = "trade off";

    public static void initOptions(Options options) {
        GradientPlacer.initOptions(options);

        options.add(
                O_CRITICALITY_EXPONENT,
                "criticality exponent of connections",
                new Double(3));

        options.add(
                O_CRITICALITY_THRESHOLD,
                "minimal criticality for adding TD constraints",
                new Double(0.6));

        options.add(
                O_MAX_PER_CRIT_EDGE,
                "the maximum number of critical edges compared to the total number of edges",
                new Double(3));

        options.add(
                O_TRADE_OFF,
                "0 = purely wirelength driven, higher = more timing driven",
                new Double(30));
    }


    private static String
        T_UPDATE_CRIT_CON = "update critical connections";

    private double[] criticalityExponent, criticalityThreshold, maxPerCritEdge;
    private TimingGraph[] timingGraph;

    private CriticalityCalculator[] criticalityCalculator;

    public GradientPlacerTD(Circuit[] circuit, Options options, Random random, Logger logger, 
			PlacementVisualizer[] visualizer, int Totaldies, int SLLrows,TimingGraphSLL timingGraphSys, HashMap<String, SLLNetBlocks> netToBlockSLL) {
        super(circuit, options, random, logger, visualizer,Totaldies,SLLrows, timingGraphSys, netToBlockSLL);

        //System.out.print("\nThe criticailutys is " + options.getDouble(O_CRITICALITY_EXPONENT));
        this.criticalityExponent = new double[this.TotalDies];
        		
        		
        
        this.criticalityThreshold = new double[this.TotalDies];
        this.maxPerCritEdge = new double[this.TotalDies];
        this.timingGraph = new TimingGraph[this.TotalDies];
        this.criticalityCalculator = new CriticalityCalculator[this.TotalDies];

        this.tradeOff = options.getDouble(O_TRADE_OFF);
        int dieCount = 0;
        //System.out.print("\nThis is executed+\n");
        while(dieCount < this.TotalDies) {
        	this.criticalityExponent[dieCount] = options.getDouble(O_CRITICALITY_EXPONENT);
        	this.criticalityThreshold[dieCount] = options.getDouble(O_CRITICALITY_THRESHOLD);
            this.maxPerCritEdge[dieCount] = options.getDouble(O_MAX_PER_CRIT_EDGE);

            
        	dieCount++;
        }
        
    }


    @Override
    public void initializeData() {
        super.initializeData();
        
        int dieCount = 0;
        
        
        while(dieCount < this.TotalDies) {
            this.timingGraph[dieCount] = this.circuitDie[dieCount].getTimingGraph();
            this.timingGraph[dieCount].setCriticalityExponent(this.criticalityExponent[dieCount]);
            this.timingGraph[dieCount].calculateCriticalities(true);

            this.criticalityCalculator[dieCount] = new CriticalityCalculator(
                    this.circuitDie[dieCount],
                    this.netBlocks.get(dieCount),
                    this.timingNets[dieCount]);
            dieCount++;
        }

    }

    @Override
    protected boolean isTimingDriven() {
        return true;
    }

    @Override
    protected void initializeIteration(int iteration, int dieNumber) {

    	this.startTimer(T_UPDATE_CRIT_CON, dieNumber);
    	this.updateCriticalConnections(dieNumber);
    	this.stopTimer(T_UPDATE_CRIT_CON, dieNumber);

        if(iteration > 0) {
            this.anchorWeight[dieNumber] = Math.pow((double)iteration / (this.numIterations - 1.0), this.anchorWeightExponent) * this.anchorWeightStop;
            this.learningRate[dieNumber] *= this.learningRateMultiplier[dieNumber];
            this.legalizer[dieNumber].multiplySettings();
            this.effortLevel[dieNumber] = Math.max(this.effortLevelStop, (int)Math.round(this.effortLevel[dieNumber]*0.5));
            //System.out.print("\nFor die " + dieNumber + " the effort level is " + this.effortLevel[dieNumber] +"\n");
        }
    }

    private void updateCriticalConnections(int dieNumber) {
    	//System.out.print("\nThe die Number is " + dieNumber);
    	for(TimingNet net : this.timingNets[dieNumber]) {
    		for(TimingNetBlock sink : net.sinks) {
    			sink.updateCriticality();
    		}
    	}

    	List<Double> criticalities = new ArrayList<>();
    	for(TimingNet net : this.timingNets[dieNumber]) {
    		NetBlock source = net.source;
    		for(TimingNetBlock sink : net.sinks) {
    			if(sink.criticality > this.criticalityThreshold[dieNumber]) {
    				if(source.blockIndex != sink.blockIndex) {
    					criticalities.add(sink.criticality);
    				}
    			}
    		}
    	}
    	double minimumCriticality = this.criticalityThreshold[dieNumber];
    	int maxNumCritConn = (int) Math.round(this.numRealConn[dieNumber] * this.maxPerCritEdge[dieNumber] / 100);
    	if(criticalities.size() > maxNumCritConn){
    		Collections.sort(criticalities);
    		minimumCriticality = criticalities.get(criticalities.size() - 1 - maxNumCritConn);
    	}

    	this.criticalConnections.get(dieNumber).clear();
    	for(TimingNet net : this.timingNets[dieNumber]) {
    		NetBlock source = net.source;
    		for(TimingNetBlock sink : net.sinks) {
    			if(sink.criticality > minimumCriticality) {
    				if(source.blockIndex != sink.blockIndex) {
    					CritConn c = new CritConn(source.blockIndex, sink.blockIndex, source.offset, sink.offset, this.tradeOff * sink.criticality);
    					this.criticalConnections.get(dieNumber).add(c);
    				}
    			}
    		}
    	}

    	this.legalizer[dieNumber].updateCriticalConnections(this.criticalConnections.get(dieNumber));
    }

    @Override
    protected void processNets(boolean[] processNets, int dieNumber) {
        // Process all nets wirelength driven
    	//System.out.print("\nIn TD placer");
        super.processNets(processNets, dieNumber);

        // Process the most critical source-sink connections
        for(CritConn critConn:this.criticalConnections.get(dieNumber)) {
        	//System.out.print("\nThe die Number is " + dieNumber);
        	this.solver[dieNumber].processConnection(critConn.sourceIndex, critConn.sinkIndex, critConn.sinkOffset - critConn.sourceOffset, critConn.weight, true);
        }
    }

    @Override
    protected void calculateTimingCost(int dieNumber) {
        this.timingCost[dieNumber] = this.criticalityCalculator[dieNumber].calculate(this.legalX.get(dieNumber), this.legalY.get(dieNumber));
    }

    @Override
    public String getName() {
        return "Timing driven gradient placer";
    }


	@Override
	protected void fixSLLblocks(BlockType category, int SLLrows) {
		// TODO Auto-generated method stub
		
	}
}
