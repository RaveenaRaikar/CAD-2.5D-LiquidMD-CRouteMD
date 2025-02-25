package place.placers.simulatedannealing;

import place.circuit.Circuit;
import place.circuit.timing.TimingGraph;
import place.circuit.timing.TimingGraphSLL;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.visual.PlacementVisualizer;

import java.util.List;
import java.util.Random;


//TODO : Raveena
// All functions are executed with dieNumber = 0 for simplicity. 
public class SimulatedAnnealingPlacerTD extends SimulatedAnnealingPlacer {

    private static final String
        O_TRADE_OFF = "trade off",
        O_CRITICALITY_EXPONENT_START = "criticality exponent start",
        O_CRITICALITY_EXPONENT_END = "criticality exponent end",
        O_INNER_LOOP_RECALCULATES = "inner loop recalculates";

    public static void initOptions(Options options) {
        SimulatedAnnealingPlacer.initOptions(options);

        options.add(
                O_TRADE_OFF,
                "trade off between wirelength and timing cost optimization: 0 is pure WLD, 1 is pure TD",
                new Double(0.5));

        options.add(
                O_CRITICALITY_EXPONENT_START,
                "exponent to calculate criticality of connections at start of anneal",
                new Double(1));

        options.add(
                O_CRITICALITY_EXPONENT_END,
                "exponent to calculate criticality of connections at end of anneal",
                new Double(8));

        options.add(
                O_INNER_LOOP_RECALCULATES,
                "number of times the criticalities should be recalculated in the inner loop",
                new Integer(0));
    }


    private static String
        T_UPDATE_CRITICALITIES = "update criticalities",
        T_CALCULATE_COST = "calculate global cost";


    private EfficientBoundingBoxNetCC calculator;
    private final TimingGraph timingGraph;
    private final double criticalityExponentStart, criticalityExponentEnd;
    private double cachedBBCost, cachedTDCost, previousBBCost, previousTDCost;

    private final double tradeOffFactor;
    private final int iterationsBeforeRecalculate;

    public SimulatedAnnealingPlacerTD(Circuit[] circuitDie, Options options, Random random, Logger logger, PlacementVisualizer[] visualizer, int totalDies, int SLLrows) {
        super(circuitDie, options, random, logger, visualizer, totalDies, SLLrows);

        this.calculator = new EfficientBoundingBoxNetCC(circuitDie[0]);
        this.timingGraph = circuitDie[0].getTimingGraph();


        this.tradeOffFactor = this.options.getDouble(O_TRADE_OFF);
        int numRecalculates = this.options.getInteger(O_INNER_LOOP_RECALCULATES);
        if(numRecalculates == 0) {
            this.iterationsBeforeRecalculate = this.movesPerTemperature[0] + 1;
        } else {
            this.iterationsBeforeRecalculate = this.movesPerTemperature[0] / numRecalculates;
        }

        this.criticalityExponentStart = this.options.getDouble(O_CRITICALITY_EXPONENT_START);
        this.criticalityExponentEnd = this.options.getDouble(O_CRITICALITY_EXPONENT_END);
    }

    @Override
    public String getName() {
        return "TD simulated annealing placer";
    }


    @Override
    protected void initializePlace() {
        this.calculator.recalculateFromScratch();
    }

    @Override
    protected void initializeSwapIteration() {



        double criticalityExponent;
        if(this.greedy[0]) {
            criticalityExponent = this.criticalityExponentEnd;

        } else {
            criticalityExponent = this.criticalityExponentStart +
                    (1 - (this.rlim[0] - 1) / (this.initialRlim - 1))
                    * (this.criticalityExponentEnd - this.criticalityExponentStart);
        }

        this.timingGraph.setCriticalityExponent(criticalityExponent);
        this.timingGraph.calculateCriticalities(true);



        this.updatePreviousCosts();
    }

    private void updatePreviousCosts() {
        this.getCost();

        this.previousBBCost = this.cachedBBCost;
        this.previousTDCost = this.cachedTDCost;
    }


    @Override
    protected void addStatisticsTitlesSA(List<String> titles) {
        titles.add("BB cost");
        titles.add("timing cost");
        titles.add("max delay");
    }

    @Override
    protected void addStats(List<String> stats) {
        this.getCost();
        stats.add(String.format("%.5g", this.cachedBBCost));
        stats.add(String.format("%.4g", this.cachedTDCost));
        stats.add(String.format("%.5g", this.timingGraph.getMaxDelay()));
    }


    @Override
    protected double getCost() {
        if(this.circuitChanged) {


            this.circuitChanged = false;
            this.cachedBBCost = this.calculator.calculateTotalCost();
            this.cachedTDCost = this.timingGraph.calculateTotalCost();


        }

        return this.balancedCost(this.cachedBBCost, this.cachedTDCost);
    }

    @Override
    protected double getDeltaCost(Swap swap) {
        double deltaBBCost = this.calculator.calculateDeltaCost(swap);
        double deltaTDCost = this.timingGraph.calculateDeltaCost(swap);

        return this.balancedCost(deltaBBCost, deltaTDCost);
    }

    private double balancedCost(double BBCost, double TDCost) {
        return
                this.tradeOffFactor         * TDCost / this.previousTDCost
                + (1 - this.tradeOffFactor) * BBCost / this.previousBBCost;
    }


    @Override
    protected void pushThrough(int iteration) {
        this.calculator.pushThrough();
        this.timingGraph.pushThrough();

        if(iteration % this.iterationsBeforeRecalculate == 0 && iteration > 0) {
            this.timingGraph.calculateCriticalities(false);
            this.updatePreviousCosts();
        }
    }

    @Override
    protected void revert(int iteration) {
        this.calculator.revert();
        this.timingGraph.revert();

        if(iteration % this.iterationsBeforeRecalculate == 0 && iteration > 0) {
            this.timingGraph.calculateCriticalities(false);
            this.updatePreviousCosts();
        }
    }
}
