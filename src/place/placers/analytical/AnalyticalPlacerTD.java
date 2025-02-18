package place.placers.analytical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import place.circuit.Circuit;
import place.circuit.timing.TimingGraph;
import place.circuit.timing.TimingGraphSLL;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.visual.PlacementVisualizer;

public abstract class AnalyticalPlacerTD extends AnalyticalPlacer {

    private static final String
        O_CRITICALITY_EXPONENT = "criticality exponent",
        O_CRITICALITY_THRESHOLD = "criticality threshold",
        O_MAX_PER_CRIT_EDGE = "max per crit edge",
        O_TRADE_OFF = "trade off";

    public static void initOptions(Options options) {
        AnalyticalPlacer.initOptions(options);

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
                "trade_off between timing driven and wirelength driven (0 = not timing driven)",
                new Double(20));
    }

    private static String 
    	T_UPDATE_CRIT_CON = "update critical connections";

    private double[] criticalityExponent, criticalityThreshold, maxPerCritEdge;
    private TimingGraph[] timingGraph;
    private double maxSystemDelay = 0;
    private CriticalityCalculator[] criticalityCalculator;

    public AnalyticalPlacerTD(Circuit[] circuitDie, Options options, Random random, Logger logger, 
    		PlacementVisualizer[] visualizer, int TotalDies, int SLLrows, TimingGraphSLL timingGraphSys) {
        super(circuitDie, options, random, logger, visualizer, TotalDies, SLLrows, timingGraphSys);

        this.criticalityExponent[TotalDies] = options.getDouble(O_CRITICALITY_EXPONENT);
        this.criticalityThreshold[TotalDies] = options.getDouble(O_CRITICALITY_THRESHOLD);
        this.maxPerCritEdge[TotalDies] = options.getDouble(O_MAX_PER_CRIT_EDGE);

        this.tradeOff = options.getDouble(O_TRADE_OFF);

        
    }

    @Override
    public void initializeData() {
        super.initializeData();
        
        int dieCounter = 0;
        this.maxSystemDelay = this.timingGraphSLL.getTotalMaxDelay();
        while(dieCounter < this.TotalDies) {
        	this.timingGraph[dieCounter] = this.circuitDie[dieCounter].getTimingGraph();
            this.timingGraph[dieCounter].setCriticalityExponent(this.criticalityExponent[dieCounter]);
            this.timingGraph[dieCounter].calculateCriticalities(true);

            this.criticalityCalculator[dieCounter] = new CriticalityCalculator(
                    this.circuitDie[dieCounter],
                    this.netBlocks.get(dieCounter),
                    this.timingNets[dieCounter]);
            dieCounter++;
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
            this.anchorWeight[dieNumber] *= this.anchorWeightMultiplier;
            this.legalizer[dieNumber].multiplySettings();
        }
    }

    private void updateCriticalConnections(int dieNumber) {

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

        this.criticalConnections[dieNumber].clear();
        for(TimingNet net : this.timingNets[dieNumber]) {
            NetBlock source = net.source;
            for(TimingNetBlock sink : net.sinks) {
            	if(sink.criticality > minimumCriticality) {
            		if(source.blockIndex != sink.blockIndex) {
            			CritConn c = new CritConn(source.blockIndex, sink.blockIndex, source.offset, sink.offset, (float)(this.tradeOff * sink.criticality));
            			this.criticalConnections[dieNumber].add(c);
            		}
            	}
        	}
        }

        this.legalizer[dieNumber].updateCriticalConnections(this.criticalConnections[dieNumber]);
    }

    @Override
    protected void calculateTimingCost(int dieNumber) {
        this.timingCost[dieNumber] = this.criticalityCalculator[dieNumber].calculate(this.legalX.get(dieNumber), this.legalY.get(dieNumber));
    }

    @Override
    public String getName() {
        return "Timing driven analytical placer";
    }
}
