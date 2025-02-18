package place.placers.analytical;

import place.circuit.Circuit;
import place.circuit.timing.TimingGraphSLL;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.visual.PlacementVisualizer;

import java.util.Random;

public class GradientPlacerWLD extends GradientPlacer {


	public GradientPlacerWLD(Circuit[] circuit, Options options, Random random, Logger logger, 
			PlacementVisualizer[] visualizer, int Totaldies, int SLLrows,TimingGraphSLL timingGraphSys) {
        super(circuit, options, random, logger, visualizer,Totaldies,SLLrows, timingGraphSys);
    }

    @Override
    protected boolean isTimingDriven() {
        return false;
    }

    @Override
    protected void initializeIteration(int iteration, int dieCounter) {
        if(iteration > 0) {
            this.anchorWeight[dieCounter] = Math.pow((double)iteration / (this.numIterations - 1.0), this.anchorWeightExponent) * this.anchorWeightStop;
            this.learningRate[dieCounter] *= this.learningRateMultiplier[dieCounter];
            this.legalizer[dieCounter].multiplySettings();
            this.effortLevel[dieCounter] = Math.max(this.effortLevelStop, (int)Math.round(this.effortLevel[dieCounter]*0.5));
        }
    }

    @Override
    public String getName() {
        return "Wirelength driven gradient descent placer";
    }

	@Override
	protected void calculateTimingCost(int dieCounter) {
		this.timingCost[dieCounter] = 0;
	}

}
