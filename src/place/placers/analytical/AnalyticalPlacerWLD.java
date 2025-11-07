package place.placers.analytical;

import place.circuit.Circuit;
import place.circuit.block.SLLNetBlocks;
import place.circuit.timing.TimingGraphSLL;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.visual.PlacementVisualizer;

import java.util.HashMap;
import java.util.Random;

public abstract class AnalyticalPlacerWLD extends AnalyticalPlacer {

    public AnalyticalPlacerWLD(Circuit[] circuitDie, Options options, Random random, Logger logger, 
    		PlacementVisualizer[] visualizer, int TotalDies,int SLLrows,TimingGraphSLL timingGraphSys,HashMap<String, SLLNetBlocks>  netToBlockSLL) {
        super(circuitDie, options, random, logger, visualizer, TotalDies, SLLrows, timingGraphSys, netToBlockSLL);
    }

    @Override
    protected boolean isTimingDriven() {
        return false;
    }
    
    @Override
    protected void initializeIteration(int iteration, int dieCounter){
    	this.logger.println("\nInitialise iteration in analytical placer WLD");
    	if(iteration > 0) {
    		this.anchorWeight[dieCounter] *= this.anchorWeightMultiplier;
    		this.legalizer[dieCounter].multiplySettings();
    	}
    }

    @Override
    public String getName() {
        return "Wirelength driven analytical placer";
    }

	@Override
	protected void calculateTimingCost(int dieCounter) {
		this.timingCost[dieCounter] = 0;
	}
}
