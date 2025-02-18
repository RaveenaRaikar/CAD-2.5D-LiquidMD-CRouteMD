package place.placers.simulatedannealing;

import place.circuit.Circuit;
import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.block.GlobalBlock;
import place.circuit.block.Site;
import place.circuit.exceptions.PlacementException;
import place.circuit.pin.AbstractPin;
import place.circuit.timing.TimingGraphSLL;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.placers.Placer;
import place.visual.PlacementVisualizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

abstract class SimulatedAnnealingPlacer extends Placer {

    private static final String
        O_GREEDY = "greedy",
        O_DETAILED = "detailed",
        O_EFFORT_LEVEL = "effort level",
        O_EFFORT_EXPONENT = "effort exponent",
        O_TEMPERATURE = "temperature",
        O_STOP_RATIO = "stop ratio",
        O_RLIM = "rlim",
        O_MAX_RLIM = "max rlim",
        O_FIX_IO_PINS = "fix io pins";

    public static void initOptions(Options options) {
        options.add(
                O_GREEDY,
                "place greedy",
                Boolean.FALSE);

        options.add(
                O_DETAILED,
                "place detailed",
                Boolean.FALSE);


        options.add(
                O_EFFORT_LEVEL,
                "multiplier for the number of swap iterations",
                new Double(1));

        options.add(
                O_EFFORT_EXPONENT,
                "exponent to calculater inner num",
                new Double(4.0 / 3.0));

        options.add(
                O_TEMPERATURE,
                "multiplier for the starting temperature",
                new Double(1));

        options.add(
                O_STOP_RATIO,
                "ratio T / cost per net below which to stop",
                new Double(0.005));


        options.add(
                O_RLIM,
                "maximum distance for a swap at start of placement",
                new Integer(-1));

        options.add(
                O_MAX_RLIM,
                "maximum rlim for all iterations",
                new Integer(-1));


        options.add(
                O_FIX_IO_PINS,
                "fix the IO pins",
                Boolean.TRUE);
    }


    protected static String
        T_INITIALIZE_DATA = "initialize data",
        T_CALCULATE_TEMPERATURE = "calculate initial temperature",
        T_DO_SWAPS = "do swaps";


    protected double[] rlim;
    protected int initialRlim, maxRlim;
    private double[] temperature, stopRatio;

    private final double temperatureMultiplier;

    private final boolean[] fixPins = null;
    protected boolean[] greedy, detailed;
    protected final int[] movesPerTemperature = null;

    protected boolean circuitChanged = true;
    private double[][] deltaCosts;
    private int[] numNets;


    protected SimulatedAnnealingPlacer(Circuit[] circuitDie, Options options, Random random, Logger logger, PlacementVisualizer[] visualizer, int totalDies, int SLLrows) {
        super(circuitDie, options, random, logger, visualizer, totalDies, SLLrows);


        this.greedy[totalDies] = this.options.getBoolean(O_GREEDY);
        this.detailed[totalDies] = this.options.getBoolean(O_DETAILED);

        this.fixPins[totalDies] = this.options.getBoolean(O_FIX_IO_PINS);


        


        this.temperatureMultiplier = this.options.getDouble(O_TEMPERATURE);
        this.stopRatio[totalDies] = this.options.getDouble(O_STOP_RATIO);
    }


    protected abstract void addStatisticsTitlesSA(List<String> titles);
    protected abstract void addStats(List<String> statistics);

    protected abstract void initializePlace();
    protected abstract void initializeSwapIteration();
    protected abstract double getCost();
    protected abstract double getDeltaCost(Swap swap);
    protected abstract void pushThrough(int iteration);
    protected abstract void revert(int iteration);


    @Override
    public void initializeData() {

        

        int dieCounter = 0;
        
        while(dieCounter < this.TotalDies) {
        	this.startTimer(T_INITIALIZE_DATA, dieCounter);
            this.logger.printf("Swaps per iteration: %d\n\n", this.movesPerTemperature[dieCounter]);
            double effortLevel = this.options.getDouble(O_EFFORT_LEVEL);
            double effortExponent = this.options.getDouble(O_EFFORT_EXPONENT);
            this.movesPerTemperature[dieCounter] = (int) (effortLevel * Math.pow(this.circuitDie[dieCounter].getNumGlobalBlocks(), effortExponent));
            int size = Math.max(this.circuitDie[dieCounter].getWidth(), this.circuitDie[dieCounter].getHeight());
            
            // Set Rlim options
            

            int RlimOption = this.options.getInteger(O_RLIM);
            if(RlimOption == -1) {
                RlimOption = size - 1;
            }

            int maxRlimOption = this.options.getInteger(O_MAX_RLIM);
            if(maxRlimOption == -1) {
                maxRlimOption = size - 1;
            }

            this.initialRlim = RlimOption;

            this.maxRlim = maxRlimOption;
            this.rlim[dieCounter] = Math.min(RlimOption, this.maxRlim);
            
            
            // Count the number of nets
            this.numNets[dieCounter] = 0;
            for(GlobalBlock block : this.circuitDie[dieCounter].getGlobalBlocks()) {
                for(AbstractPin pin : block.getOutputPins()) {
                    if(pin.getNumSinks() > 0) {
                        this.numNets[dieCounter]++;
                    }
                }
            }

        	
            this.stopTimer(T_INITIALIZE_DATA, dieCounter);
        	dieCounter++;
        }
        

        
    }

    @Override
    protected void addStatTitles(List<String> titles) {
        titles.add("iteration");
        titles.add("temperature");
        titles.add("rlim");
        titles.add("succes rate");
        titles.add("t multiplier");

        this.addStatisticsTitlesSA(titles);
    }

    private void printStatistics(Integer iteration, Double temperature, Double rlim, Double succesRate, Double gamma) {
        List<String> stats = new ArrayList<>();

        stats.add(iteration.toString());
        stats.add(String.format("%.4g", temperature));
        stats.add(String.format("%.3g", rlim));
        stats.add(String.format("%.3f", succesRate));
        stats.add(gamma.toString());

        this.addStats(stats);

        this.printStats(stats.toArray(new String[0]));
    }


    @Override
    protected void doPlacement() throws PlacementException {


        this.initializePlace();


        int iteration = 0;
        int dieCounter = 0;
        
        while(dieCounter < this.TotalDies) {
        	
            if(!this.greedy[dieCounter]) {
                this.calculateInitialTemperature(dieCounter);

                // Do placement
                while(this.temperature[dieCounter] > this.stopRatio[dieCounter] * this.getCost() / this.numNets[dieCounter]) {
                    int numSwaps = this.doSwapIteration(dieCounter);
                    double alpha = ((double) numSwaps) / this.movesPerTemperature[dieCounter];

                    double previousTemperature = this.temperature[dieCounter];
                    double previousRlim = this.rlim[dieCounter];
                    this.updateRlim(alpha,dieCounter);
                    double gamma = this.updateTemperature(alpha, dieCounter);

                    this.printStatistics(iteration, previousTemperature, previousRlim, alpha, gamma);

                    iteration++;
                }

                this.rlim[dieCounter] = 3;
            }

            // Finish with a greedy iteration
            this.greedy[dieCounter] = true;
            int numSwaps = this.doSwapIteration(dieCounter);
            double alpha = ((double) numSwaps) / this.movesPerTemperature[dieCounter];
            this.printStatistics(iteration, this.temperature[dieCounter], this.rlim[dieCounter], alpha, 0.0);


            this.logger.println();
            
            dieCounter++;
        }

    }


    private void calculateInitialTemperature(int dieNumber) throws PlacementException {
        if(this.detailed[dieNumber]) {
            this.temperature[dieNumber] = this.calculateInitialTemperatureDetailed(dieNumber);
        } else {
            this.temperature[dieNumber] = this.calculateInitialTemperatureGlobal(dieNumber);
        }
    }

    private double calculateInitialTemperatureGlobal(int dieNumber) throws PlacementException {
        int numSamples = this.circuitDie[dieNumber].getNumGlobalBlocks();
        double stdDev = this.doSwapIteration(numSamples, false, dieNumber);

        return this.temperatureMultiplier * stdDev;
    }

    private double calculateInitialTemperatureDetailed(int dieNumber) throws PlacementException {
        // Use the method described in "Temperature Measurement and
        // Equilibrium Dynamics of Simulated Annealing Placements"

        int numSamples = Math.max(this.circuitDie[dieNumber].getNumGlobalBlocks() / 5, 500);
        this.doSwapIteration(numSamples, false, dieNumber);

        this.startTimer(T_DO_SWAPS, dieNumber);

        Arrays.sort(this.deltaCosts[dieNumber]);

        int zeroIndex = Arrays.binarySearch(this.deltaCosts[dieNumber], 0);
        if(zeroIndex < 0) {
            zeroIndex = -zeroIndex - 1;
        }

        double Emin = integral(this.deltaCosts[dieNumber], 0, zeroIndex, 0);
        double maxEplus = integral(this.deltaCosts[dieNumber], zeroIndex, numSamples, 0);

        if(maxEplus < Emin) {
            this.logger.raise("SA failed to get a temperature estimate");
        }

        double minT = 0;
        double maxT = Double.MAX_VALUE;

        // very coarse estimate
        double temperature = this.deltaCosts[dieNumber][this.deltaCosts.length - 1] / 1000;

        while(minT == 0 || maxT / minT > 1.1) {
            double Eplus = integral(this.deltaCosts[dieNumber], zeroIndex, numSamples, temperature);

            if(Emin < Eplus) {
                if(temperature < maxT) {
                    maxT = temperature;
                }

                if(minT == 0) {
                    temperature /= 8;
                } else {
                    temperature = (maxT + minT) / 2;
                }

            } else {
                if(temperature > minT) {
                    minT = temperature;
                }

                if(maxT == Double.MAX_VALUE) {
                    temperature *= 8;
                } else {
                    temperature = (maxT + minT) / 2;
                }
            }
        }

        this.stopTimer(T_DO_SWAPS, dieNumber);

        return temperature * this.temperatureMultiplier;
    }

    private double integral(double[] values, int start, int stop, double temperature) {
        double sum = 0;
        for(int i = start; i < stop; i++) {
            if(temperature == 0) {
                sum += values[i];
            } else {
                sum += values[i] * Math.exp(-values[i] / temperature);
            }
        }

        return Math.abs(sum / values.length);
    }



    private int doSwapIteration(int dieNumber) throws PlacementException {
        return (int) this.doSwapIteration(this.movesPerTemperature[dieNumber], true, dieNumber);
    }

    private double doSwapIteration(int moves, boolean pushThrough, int dieNumber) throws PlacementException {

        this.initializeSwapIteration();

        String timer = pushThrough ? T_DO_SWAPS : T_CALCULATE_TEMPERATURE;
        this.startTimer(timer, dieNumber);

        int numSwaps = 0;

        double sumDeltaCost = 0;
        double quadSumDeltaCost = 0;
        if(!pushThrough) {
            this.deltaCosts[dieNumber] = new double[moves];
        }

        int intRlim = (int) Math.round(this.rlim[dieNumber]);

        for (int i = 0; i < moves; i++) {
            Swap swap = this.findSwap(intRlim, dieNumber);
            double deltaCost = this.getDeltaCost(swap);

            if(pushThrough) {
                if(deltaCost <= 0 || (this.greedy[dieNumber] == false && this.random.nextDouble() < Math.exp(-deltaCost / this.temperature[dieNumber]))) {

                    swap.apply();
                    numSwaps++;

                    this.pushThrough(i);
                    this.circuitChanged = true;

                } else {
                    this.revert(i);
                }

            } else {
                this.revert(i);
                this.deltaCosts[dieNumber][i] = deltaCost;
                sumDeltaCost += deltaCost;
                quadSumDeltaCost += deltaCost * deltaCost;
            }
        }

        double result;
        if(pushThrough) {
            result = numSwaps;

        } else {
            double sumQuads = quadSumDeltaCost;
            double quadSum = sumDeltaCost * sumDeltaCost;

            double numBlocks = this.circuitDie[dieNumber].getNumGlobalBlocks();
            double quadNumBlocks = numBlocks * numBlocks;

            result = Math.sqrt(Math.abs(sumQuads / numBlocks - quadSum / quadNumBlocks));
        }

        this.stopTimer(timer, dieNumber);
        return result;
    }



    protected Swap findSwap(int Rlim, int dieNumber) {
        while(true) {
            // Find a suitable from block
            GlobalBlock fromBlock = null;
            do {
                fromBlock = this.circuitDie[dieNumber].getRandomBlock(this.random);
            } while(this.isFixed(fromBlock, dieNumber));

            BlockType blockType = fromBlock.getType();

            int freeAbove = 0;
            if(fromBlock.isInMacro()) {
                fromBlock = fromBlock.getMacro().getBlock(0);
                freeAbove = fromBlock.getMacro().getHeight() - 1;
            }

            int column = fromBlock.getColumn();
            int row = fromBlock.getRow();
            int minRow = Math.max(1, row - Rlim);
            int maxRow = Math.min(this.circuitDie[dieNumber].getHeight() - freeAbove, row + Rlim);

            // Find a suitable site near this block
            int maxTries = Math.min(4 * Rlim * Rlim / fromBlock.getType().getHeight(), 10);
            for(int tries = 0; tries < maxTries; tries++) {
                Site toSite = (Site) this.circuitDie[dieNumber].getRandomSite(blockType, column, Rlim, minRow, maxRow, this.random);

                // If toSite is null, no swap is possible with this fromBlock
                // Go find another fromBlock
                if(toSite == null) {
                    break;

                // Check if toSite contains fromBlock
                } else if(!fromBlock.getSite().equals(toSite)) {

                    // Make sure toSite doesn't contain a block that is in a macro
                    // (This is also not supported in VPR)
                    boolean toBlocksInMacro = false;
                    int toColumn = toSite.getColumn();
                    int toMinRow = toSite.getRow();
                    int toMaxRow = toMinRow + freeAbove;
                    for(int toRow = toMinRow; toRow <= toMaxRow; toRow++) {
                        GlobalBlock toBlock = ((Site) this.circuitDie[dieNumber].getSite(this.circuitDie[dieNumber].getCurrentDie(), toColumn, toRow)).getBlock();
                        if(toBlock != null && toBlock.isInMacro()) {
                            toBlocksInMacro = true;
                            break;
                        }
                    }

                    if(!toBlocksInMacro) {
                        Swap swap = new Swap(this.circuitDie[dieNumber], fromBlock, toSite);
                        return swap;
                    }
                }
            }
        }
    }

    private boolean isFixed(GlobalBlock block, int dieCounter) {
        // Only IO blocks are fixed, if fixPins option is true
        return this.fixPins[dieCounter] && block.getCategory() == BlockCategory.IO;
    }



    protected final double updateTemperature(double alpha, int dieNumber) {
        double gamma;

        if (alpha > 0.96) {
            gamma = 0.5;
        } else if (alpha > 0.8) {
            gamma = 0.9;
        } else if (alpha > 0.15  || this.rlim[dieNumber] > 1) {
            gamma = 0.95;
        } else {
            gamma = 0.8;
        }

        this.temperature[dieNumber] *= gamma;

        return gamma;
    }


    protected final void setMaxRlim(int maxRlim) {
        this.maxRlim = maxRlim;
    }

    protected final void updateRlim(double alpha, int dieNumber) {
        this.rlim[dieNumber] *= (1 - 0.44 + alpha);

        this.rlim[dieNumber] = Math.max(Math.min(this.rlim[dieNumber], this.maxRlim), 1);
    }
}
