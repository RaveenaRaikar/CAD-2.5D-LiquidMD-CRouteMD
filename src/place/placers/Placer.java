package place.placers;

import place.circuit.Circuit;
import place.circuit.exceptions.PlacementException;
import place.circuit.timing.TimingGraphSLL;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.util.Timer;
import place.visual.PlacementVisualizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;



public abstract class Placer {

    protected Logger logger;
    protected PlacementVisualizer[] visualizer;
    protected Circuit[] circuitDie;
    protected Options options;
    protected Random random;
    protected int TotalDies;
    protected int SLLrows;
    protected TimingGraphSLL timingGraphSLL;
    //protected double maxSystemDelay;
    private Map<Integer,Map<String, Timer>> timers = new HashMap<>();
    private Map<String, Timer> timerSystem = new LinkedHashMap<>();
    //= new LinkedHashMap<>();
    private int maxTimerNameLength = 0;

    protected List<String> statTitles;
    private List<Integer> statLengths;
    private int numStats;
    private static int statSpaces = 3;


    protected Placer(Circuit[] circuitDie, Options options, Random random, Logger logger, 
    		PlacementVisualizer[] visualizer, int TotalDies, int SLLrows) {
        this.circuitDie = circuitDie;
        this.options = options;
        this.random = random;
        this.logger = logger;
        this.visualizer = visualizer;
        this.TotalDies = TotalDies;
        this.SLLrows = SLLrows;
        
        for(int i = 0; i < this.TotalDies; i++) {
        	this.timers.put(i, new LinkedHashMap<>());
        }

    }
    protected Placer(Circuit[] circuitDie, Options options, Random random, Logger logger, 
    		PlacementVisualizer[] visualizer, int TotalDies, int SLLrows, TimingGraphSLL timingGraphSLL) {
        this.circuitDie = circuitDie;
        this.options = options;
        this.random = random;
        this.logger = logger;
        this.visualizer = visualizer;
        this.TotalDies = TotalDies;
        this.SLLrows = SLLrows;
        this.timingGraphSLL = timingGraphSLL;
        for(int i = 0; i < this.TotalDies; i++) {
        	this.timers.put(i, new LinkedHashMap<>());
        }

    }


    public abstract String getName();
    public abstract void initializeData();
    protected abstract void doPlacement() throws PlacementException;

    protected abstract void addStatTitles(List<String> titles);


    public void place() throws PlacementException {
        this.statTitles = new ArrayList<>();
        this.statLengths = new ArrayList<>();
        this.addStatTitles(this.statTitles);
        this.numStats = this.statTitles.size();
        
        String printOptions = "Print options";
        this.startSystemTimer(printOptions);
        this.printOptions();

        if(this.numStats > 0) {
            this.printStatsHeader();
        }
        this.stopandPrintSystemTimer(printOptions);
        this.doPlacement();
    }

    private final void printOptions() {
        int maxLength = this.options.getMaxNameLength();

        this.logger.printf("%s options:\n", this.getName());

        String format = String.format("%%-%ds| %%s\n", maxLength + 1);
        for(Map.Entry<String, Object> optionEntry : this.options.entrySet()) {
            String optionName = optionEntry.getKey();
            Object optionValue = optionEntry.getValue();

            this.logger.printf(format, optionName, optionValue);
        }

        this.logger.println();
    }
    protected void startSystemTimer(String name) {
        if(!this.timerSystem.containsKey(name)) {
            this.timerSystem.put(name, new Timer());

            if(name.length() > this.maxTimerNameLength) {
                this.maxTimerNameLength = name.length();
            }
        }

        try {
            this.timerSystem.get(name).start();
        } catch(IllegalStateException error) {
            this.logger.raise("There was a problem with timer \"" + name + "\":", error);
        }
    }
    protected void stopandPrintSystemTimer(String name) {
        if(this.timerSystem.containsKey(name)) {

            try {
                this.timerSystem.get(name).clearStop();
                this.logger.printf("%s: %f s\n", name, this.timerSystem.get(name).getTime());
            } catch(IllegalStateException error) {
                this.logger.raise("There was a problem with timer \"" + name + "\":", error);
            }

        } else {
            this.logger.raise("Timer hasn't been initialized: " + name);
        }
    }
    
    protected StringBuilder stopandPrintSystemTimerNew(String name) {
    	StringBuilder line = new StringBuilder();
        if(this.timerSystem.containsKey(name)) {

            try {
                this.timerSystem.get(name).clearStop();
                line.append(String.format("%s: %f s\n", name, this.timerSystem.get(name).getTime()));
            } catch(IllegalStateException error) {
                this.logger.raise("There was a problem with timer \"" + name + "\":", error);
            }

        } else {
            this.logger.raise("Timer hasn't been initialized: " + name);
        }
        return line;
    }
    protected void startTimer(String name, Integer dieNumber) {
        if(!this.timers.get(dieNumber).containsKey(name)) {
            this.timers.get(dieNumber).put(name, new Timer());

            if(name.length() > this.maxTimerNameLength) {
                this.maxTimerNameLength = name.length();
            }
        }

        try {
            this.timers.get(dieNumber).get(name).start();
        } catch(IllegalStateException error) {
            this.logger.raise("There was a problem with timer \"" + name + "\":", error);
        }
    }
    protected void stopTimer(String name, Integer dieNumber) {
        if(this.timers.get(dieNumber).containsKey(name)) {

            try {
                this.timers.get(dieNumber).get(name).stop();

            } catch(IllegalStateException error) {
                this.logger.raise("There was a problem with timer \"" + name + "\":", error);
            }

        } else {
            this.logger.raise("Timer hasn't been initialized: " + name);
        }
    }
    protected void stopandPrintTimer(String name, Integer dieNumber) {
        if(this.timers.get(dieNumber).containsKey(name)) {

            try {
                this.timers.get(dieNumber).get(name).stop();
                this.logger.printf("%s: %f s\n", name, this.timers.get(dieNumber).get(name).getTime());
            } catch(IllegalStateException error) {
                this.logger.raise("There was a problem with timer \"" + name + "\":", error);
            }

        } else {
            this.logger.raise("Timer hasn't been initialized: " + name);
        }
    }
    private void printStatsHeader() {
        StringBuilder header = new StringBuilder();
        StringBuilder underlines = new StringBuilder();

        for(String title : this.statTitles) {
            int length = title.length();
            this.statLengths.add(length);

            String format = "%-" + (length + Placer.statSpaces) + "s";

            char[] underline = new char[length];
            Arrays.fill(underline, '-');

            header.append(String.format(format, title));
            underlines.append(String.format(format, new String(underline)));
        }


        this.logger.println(header.toString());
        this.logger.println(underlines.toString());
    }

    protected StringBuilder printStats(String... stats) {
        StringBuilder line = new StringBuilder();
        for(int i = 0; i < this.numStats; i++) {
            int length = this.statLengths.get(i);
            String format = "%-" + (length + Placer.statSpaces) + "s";
            String stat = String.format(format, stats[i]);
            
            line.append(String.format("%-"+length+"s", stat));
        }
        return line;
    }

    public void printRuntimeBreakdown() {
    	for(int dieCounter = 0 ; dieCounter < this.TotalDies; dieCounter++) {
            if(this.timers.get(dieCounter).size() > 0) {
                this.logger.printf("%s runtime breakdown:\n", this.getName());

                String totalName = "total";
                int maxLength = Math.max(this.maxTimerNameLength, totalName.length());
                String format = String.format("%%-%ds| %%f\n", maxLength + 1);

                double totalTime = 0;
                for(Map.Entry<String, Timer> timerEntry : this.timers.get(dieCounter).entrySet()) {
                    String name = timerEntry.getKey();

                    double time = 0;
                    try {
                        time = timerEntry.getValue().getTime();
                    } catch(IllegalStateException error) {
                        this.logger.raise("There was a problem with timer \"" + name + "\":", error);
                    }
                    totalTime += time;

                    this.logger.printf(format, name, time);
                }

                this.logger.printf(format, totalName, totalTime);
                this.logger.println();
            }
    	}

    }
}
