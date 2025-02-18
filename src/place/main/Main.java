package place.main;

import org.jfree.ui.RefineryUtilities;
import org.xml.sax.SAXException;

import place.circuit.Circuit;
import place.circuit.architecture.Architecture;
import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.architecture.ParseException;
import place.circuit.block.GlobalBlock;
import place.circuit.exceptions.InvalidFileFormatException;
import place.circuit.exceptions.PlacementException;
import place.circuit.io.*;
import place.hierarchy.LeafNode;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.interfaces.Options.Required;
import place.interfaces.OptionsManager;
import place.placers.Placer;

import place.placers.simulatedannealing.EfficientBoundingBoxNetCC;
import place.util.Timer;
import place.visual.LineChart;
import place.visual.PlacementVisualizer;
import route.circuit.pin.AbstractPin;
import place.circuit.timing.TimingGraphSLL;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private long randomSeed;

    private String circuitName;
    private File blifFile, inputPlaceFile, netFile, inputHierarchyFile, partialPlaceFile, outputPlaceFile;
    private File [] netFileDie;
    private File [] outputPlaceFileDie;
    private ArrayList<File> netFiles;
    private ArrayList<File> inputHierarchyFiles;

    private Integer TotDie;
    private Integer SLLrows;
    private float SLLDelay;

    private Architecture architecture; 
    private File architectureFile;  

    private boolean useVprTiming;
    private String vprCommand;
    private File lookupDumpFile;

    private File inputFolder;

    private boolean visual;
    private TimingGraphSLL timingGraphSLL;

    private Logger logger;
    private OptionsManager options;
    private PlacementVisualizer[] visualizer;


    private Map<String, Timer> timers = new HashMap<String, Timer>();
    private String mostRecentTimerName;
    private Circuit circuit;
    private Circuit[] circuitDie;


    private static final String
        O_ARCHITECTURE = "architecture file",
        O_BLIF_FILE = "blif file",
        O_NET_FILE = "net file",
        O_INPUT_PLACE_FILE = "input place file",
        O_INPUT_HIERARCHY_FILE = "input hierarchy file",
        O_PARTIAL_PLACE_FILE = "partial place file",
        O_OUTPUT_PLACE_FILE = "output place file",
        O_VPR_TIMING = "vpr timing",
        O_VPR_COMMAND = "vpr command",
        O_LOOKUP_DUMP_FILE = "lookup dump file",
        O_VISUAL = "visual",
        O_RANDOM_SEED = "random seed",
    	O_NUM_DIE = "Number of dies",
    	O_NUM_SLL_ROWS = "Number of SLL rows",
    	O_SLL_DELAY = "SLL delay";
    	

    //@SuppressWarnings("deprecation")
	public static void initOptionList(Options options) {
        options.add(O_ARCHITECTURE, "", File.class);
        options.add(O_BLIF_FILE, "", File.class);

        options.add(O_NET_FILE, "(default: based on the blif file)", ArrayList.class, Required.FALSE);
        options.add(O_INPUT_PLACE_FILE, "if omitted the initial placement is random", File.class, Required.FALSE);
        options.add(O_INPUT_HIERARCHY_FILE, "if omitted no hierarchy information is used", ArrayList.class, Required.FALSE);
        options.add(O_NUM_DIE, "Number of dies chosen as 2", new Integer(2));
        options.add(O_NUM_SLL_ROWS, "Number of SLL rows default set to 5", new Integer(36));
        options.add(O_SLL_DELAY, "Delay through SLL connection", new Float(1000e-12));
        options.add(O_PARTIAL_PLACE_FILE, "placement of a part of the blocks", File.class, Required.FALSE);      
        options.add(O_OUTPUT_PLACE_FILE, "(default: based on the blif file)", File.class, Required.FALSE);

        options.add(O_VPR_TIMING, "Use vpr timing information", Boolean.TRUE);
        options.add(O_VPR_COMMAND, "Path to vpr executable", "./vpr");
        options.add(O_LOOKUP_DUMP_FILE, "Path to a vpr lookup_dump.echo file", File.class, Required.FALSE);

        options.add(O_VISUAL, "show the placed circuit in a GUI", Boolean.FALSE);
        options.add(O_RANDOM_SEED, "seed for randomization", new Long(1));
    }


    public Main(OptionsManager options) {
        this.options = options;
        this.logger = options.getLogger();

        this.parseOptions(options.getMainOptions());
        
    }

    private void parseOptions(Options options) {

        this.randomSeed = options.getLong(O_RANDOM_SEED);
        this.TotDie = options.getInteger(O_NUM_DIE);
        this.SLLrows = options.getInteger(O_NUM_SLL_ROWS);
        this.SLLDelay = options.getFloat(O_SLL_DELAY);
        this.inputPlaceFile = options.getFile(O_INPUT_PLACE_FILE);

        this.inputHierarchyFiles = options.getFiles(O_INPUT_HIERARCHY_FILE);
        this.partialPlaceFile = options.getFile(O_PARTIAL_PLACE_FILE);

        this.blifFile = options.getFile(O_BLIF_FILE);
        this.netFiles = options.getFiles(O_NET_FILE);

        this.outputPlaceFile = options.getFile(O_OUTPUT_PLACE_FILE);
        this.outputPlaceFileDie = new File[this.TotDie];

        this.inputFolder = this.blifFile.getParentFile();
        this.circuitName = this.blifFile.getName().replaceFirst("(.+)\\.blif", "$1");

        if(this.netFiles == null) {
            File netFile = new File(this.inputFolder, this.circuitName + ".net");
            this.netFiles = new ArrayList<File>();
            this.netFiles.add(netFile);
        }
        if(this.inputHierarchyFiles == null) {
            File inputHierarchyFile = new File(this.inputFolder, this.circuitName + ".multipart.hierarchy");
            this.inputHierarchyFiles = new ArrayList<File>();
            this.inputHierarchyFiles.add(inputHierarchyFile);
        }
        if(this.outputPlaceFile == null) {
        	for (int i = 0; i < this.netFiles.size(); i++) {
        		this.outputPlaceFileDie[i] = new File(this.inputFolder, this.circuitName + "_" + i + ".place");
        	}
        }

        this.architectureFile = options.getFile(O_ARCHITECTURE);

        this.useVprTiming = options.getBoolean(O_VPR_TIMING);
        this.vprCommand = options.getString(O_VPR_COMMAND);
        this.lookupDumpFile = options.getFile(O_LOOKUP_DUMP_FILE);

        this.visual = options.getBoolean(O_VISUAL);


        this.checkFileExistence("Blif file", this.blifFile);
        for (int i = 0; i < this.netFiles.size(); i++) {
            File netFile = this.netFiles.get(i);
            this.checkFileExistence("Net file", netFile);
        }
        this.checkFileExistence("Input place file", this.inputPlaceFile);
        this.checkFileExistence("Partial place file", this.partialPlaceFile);

        this.checkFileExistence("Architecture file", this.architectureFile);
        
        System.out.println("Architecture File: " + this.architectureFile.getName());
        System.out.println("Number of SLL rows: " + this.SLLrows);
        System.out.println("SLL delay: "  + this.SLLDelay);


    }


    protected void checkFileExistence(String prefix, File file) {
        if(file == null) {
            return;
        }

        if(!file.exists()) {
            this.logger.raise(new FileNotFoundException(prefix + " " + file));

        } else if(file.isDirectory()) {
            this.logger.raise(new FileNotFoundException(prefix + " " + file + " is a director"));
        }
    }

    public void runPlacements(){
    	this.netFileDie = new File[this.TotDie];
    	this.circuitDie = new Circuit[this.TotDie];
        String totalString = "Total flow took";
        String ArchParsing = "\nArchitecture parsing";
        this.startTimer(totalString);
        this.startTimer(ArchParsing);
        this.loadArchitecture();
        this.stopAndPrintTimer(ArchParsing);
        
        String loadCircuit = "Loading the Netlist for both dies took";
        this.startTimer(loadCircuit);

	      int dieCount = this.TotDie;
        StringBuilder localOutput = new StringBuilder();
	      ExecutorService executor = Executors.newFixedThreadPool(2);
	      for (int i = 0; i < dieCount; i++) {  
	          Runnable worker = new parallelReadNet(i, localOutput);  
	          executor.execute(worker);
	        }  
	      executor.shutdown();  
	      while (!executor.isTerminated()) {   } 
        System.out.print(localOutput);
        this.stopAndPrintTimer(loadCircuit);
        String timingGraph = "Building the system level timing graph took";
        this.startTimer(timingGraph);

        this.TimingGraphSystem(this.circuitDie);
        this.stopAndPrintTimer(timingGraph);
        
        String completePlace = "Overall placement took";
        this.startTimer(completePlace);
        this.runPlacement(this.circuitDie);
        this.stopAndPrintTimer(completePlace);
        this.stopAndPrintTimer(totalString);
        this.printGCStats();
        int dieCounter = 0;
        

        while(dieCounter < this.TotDie) {
        	this.visualizer[dieCounter].createAndDrawGUI();
        	dieCounter++;
        }
        

     
    }

    public void NetlistParsing (int dieCounter, StringBuilder localOutput) {

        this.netFile = this.netFiles.get(dieCounter);
        this.netFileDie[dieCounter] = this.netFiles.get(dieCounter);
        this.circuitDie[dieCounter] = this.loadNetCircuit(this.netFileDie[dieCounter], dieCounter, localOutput);   //this.loadNetCircuit();
    }
    public void TimingGraphSystem(Circuit[] circuitdie) {
    	System.out.print("\nBuilding the system level graph\n ");
    	this.timingGraphSLL = new TimingGraphSLL(circuitdie, this.TotDie);
    	this.timingGraphSLL.build();
    }
    

    public void runPlacement(Circuit[] circuit) {

    	this.visualizer = new PlacementVisualizer[this.TotDie];

    	int dieCounter = 0;

    	while(dieCounter < this.TotDie) {

    		// Enable the visualizer
    		this.visualizer[dieCounter] = new PlacementVisualizer(this.logger);
            if(this.visual) {
                this.visualizer[dieCounter].setCircuit(this.circuitDie[dieCounter]);

           }
             
            
         // Read the place file
            if(this.partialPlaceFile != null) {
                PlaceParser placeParser = new PlaceParser(this.circuitDie[dieCounter], this.partialPlaceFile);
                try {
                    placeParser.iohbParse();
                } catch(IOException | BlockNotFoundException | PlacementException | IllegalSizeException error) {
                    this.logger.raise("Something went wrong while parsing the partial place file", error);
                }
                this.options.insertRandomPlacer();
            }else if(this.inputPlaceFile != null){
                this.startTimer("Placement parser");
                PlaceParser placeParser = new PlaceParser(this.circuitDie[dieCounter], this.inputPlaceFile);
                try {
                    placeParser.parse();
                } catch(IOException | BlockNotFoundException | PlacementException | IllegalSizeException error) {
                    this.logger.raise("Something went wrong while parsing the place file", error);
                }
                this.stopTimer();
                this.printStatistics("Placement parser", false);
            }

            if(this.inputHierarchyFile != null){
            	HierarchyParser hierarchyParser = new HierarchyParser(this.circuitDie[dieCounter], this.inputHierarchyFile);
            	try {
            		hierarchyParser.parse();
            	} catch(IOException error) {
                     this.logger.raise("Something went wrong while parsing the hierarchy file", error);
    			}
            }
            
            dieCounter++;
            //Garbage collection
            System.gc();
            
           
    	}
    	this.options.insertRandomPlacer();

        int numPlacers = this.options.getNumPlacers();
        this.logger.println("The num placer is " + numPlacers);

        String timeplace = "Time placement took";
        this.startTimer(timeplace);
        for(int placerIndex = 0; placerIndex < numPlacers; placerIndex++) {

            this.timePlacement(placerIndex);
        }
        this.stopAndPrintTimer(timeplace);
        String placerdump = "Placer dump took";
        this.startTimer(placerdump);
        if(numPlacers > 0) {
        	PlaceDumper placeDumper = new PlaceDumper(
                    this.circuitDie,
                    this.netFileDie,
                    this.outputPlaceFileDie,
                    this.architectureFile,
                    this.TotDie);

            try {
                placeDumper.dump(this.circuitDie);
            } catch(IOException error) {
                this.logger.raise("Failed to write to place file: " + this.outputPlaceFile, error);
            }
        }
        
        boolean printBlockDistance = false;
        if(printBlockDistance){
        	this.printBlockDistance();
        }
        this.stopAndPrintTimer(placerdump);
    }

    private Architecture loadArchitecture() {
        // Parse the architecture file
        

        this.architecture = new Architecture(
                this.circuitName,
                this.architectureFile,
                this.blifFile,
                this.netFile,
                this.TotDie,
                this.SLLrows,
                this.SLLDelay);//this.netFile);

        try {
            architecture.parse();
        } catch (IOException | InvalidFileFormatException | InterruptedException | ParseException | ParserConfigurationException | SAXException error) {
            this.logger.raise("Failed to parse architecture file or delay tables", error);
        }

        if (this.useVprTiming) {
            try {
                if (this.lookupDumpFile == null) {
                    architecture.getVprTiming(this.vprCommand);
                } else {
                    architecture.getVprTiming(this.lookupDumpFile);
                }

            } catch (IOException | InterruptedException | InvalidFileFormatException error) {
                this.logger.raise("Failed to get vpr delays", error);
            }
        }


        return this.architecture;
    }
    private Circuit loadNetCircuit(File NetFile, int dieCounter, StringBuilder localOutput){
        this.circuitName = NetFile.getName().replaceFirst("(.+)\\.net", "$1");
        this.outputPlaceFile = new File(this.inputFolder, this.circuitName + ".place");
        this.logger.print("The circuit name is " + this.circuitName);

        try {

            NetParser netParser = new NetParser(this.architecture, this.circuitName, NetFile, this.TotDie, dieCounter , this.SLLrows);
            this.circuit = netParser.parse(dieCounter);

            localOutput.append(this.circuit.stats());

        } catch(IOException error) {
            this.logger.raise("Failed to read net file", error);
        }

        localOutput.append("\n");

        this.printNumBlocks(localOutput);
        return this.circuit;
    }


    private void printNumBlocks(StringBuilder localOutput) {
        int numLut = 0,
            numFf = 0,
            numClb = 0,
            numHardBlock = 0,
            numIo = 0,
            numSLL =0;
        
        int numPLL = 0,
        	numM9K = 0, 
        	numM144K = 0, 
        	numDSP = 0,
        	numMem = 0;

        int numPins = 0;
        for(GlobalBlock block:this.circuit.getGlobalBlocks()){
        	numPins += block.numClockPins();
        	numPins += block.numInputPins();
        	numPins += block.numOutputPins();
        }
        for(BlockType blockType : BlockType.getBlockTypes()) {

            String name = blockType.getName();
            BlockCategory category = blockType.getCategory();
            int numBlocks = this.circuit.getBlocks(blockType).size();
            
            if(name.equals("lut")) {
                numLut += numBlocks;

            } else if(name.equals("ff") || name.equals("dff")) {
                numFf += numBlocks;

            } else if(category == BlockCategory.CLB) {
                numClb += numBlocks;

            } else if (category == BlockCategory.SLLDUMMY) {
            	numSLL += numBlocks;
            	
            } else if(category == BlockCategory.HARDBLOCK) {
                numHardBlock += numBlocks;

                if(blockType.equals(BlockType.getBlockTypes(BlockCategory.HARDBLOCK).get(0))){
                	if(name.contains("dsp"))
                	{
                		numDSP += numBlocks;
                	}else if (name.contains("memory"))
                	{
                		numMem += numBlocks;
                	}else
                	{
                		numPLL += numBlocks;
                	}
                }else if(blockType.equals(BlockType.getBlockTypes(BlockCategory.HARDBLOCK).get(1))){
                	if(name.contains("dsp"))
                	{
                		numDSP += numBlocks;
                	}else if (name.contains("memory"))
                	{
                		numMem += numBlocks;
                	}
                }else if(blockType.equals(BlockType.getBlockTypes(BlockCategory.HARDBLOCK).get(2))){
                	numM9K += numBlocks;
                }else if(blockType.equals(BlockType.getBlockTypes(BlockCategory.HARDBLOCK).get(3))){
                	numM144K += numBlocks;
                }

            } else if(category == BlockCategory.IO) {
                numIo += numBlocks;
            }
        }
        localOutput.append("Circuit statistics:");
        localOutput.append(String.format("   clb: %d\n      lut: %d\n      ff: %d\n   SLL: %d\n   hardblock: %d\n      PLL: %d\n      DSP: %d\n      Memory: %d\n      M9K: %d\n      M144K: %d\n   io: %d\n\n",
                numClb, numLut, numFf, numSLL, numHardBlock, numPLL, numDSP, numMem, numM9K, numM144K, numIo));
        localOutput.append(String.format("   CLB usage ratio: " + String.format("%.3f",this.circuit.ratioUsedCLB())  + "\n"));
        localOutput.append(String.format("   Num pins: " + numPins + "\n\n"));

    }


    private void timePlacement(int placerIndex) {
        long seed = this.randomSeed;
        Random random = new Random(seed);
        Placer placer = this.options.getPlacer(placerIndex, this.circuitDie, random, this.visualizer, this.TotDie, this.SLLrows, this.timingGraphSLL);
        String placerName = placer.getName();

        this.startTimer(placerName);

        placer.initializeData();
        try {
        	placer.place();
        } catch(PlacementException error) {
            this.logger.raise(error);
        }
        this.stopTimer();

        placer.printRuntimeBreakdown();
        String placerStats = "Printing statistics";
        this.startTimer(placerStats);
        if(!(placerIndex == 0))
        {
        	this.printStatistics(placerName, true);
        }
        this.stopAndPrintTimer(placerStats);
        
    }


    private void startTimer(String name) {
        this.mostRecentTimerName = name;

        Timer timer = new Timer();
        this.timers.put(name, timer);
        timer.start();
    }

    private void stopTimer() {
        this.stopTimer(this.mostRecentTimerName);
    }
    private void stopTimer(String name) {
        Timer timer = this.timers.get(name);

        if(timer == null) {
            this.logger.raise("Timer hasn't been initialized: " + name);
        } else {
            try {
                timer.stop();
            } catch(IllegalStateException error) {
                this.logger.raise("There was a problem with timer \"" + name + "\":", error);
            }
        }
    }

    private double getTime(String name) {
        Timer timer = this.timers.get(name);

        if(timer == null) {
            this.logger.raise("Timer hasn't been initialized: " + name);
            return -1;

        } else {
            double time = 0;
            try {
                time = timer.getTime();

            } catch(IllegalStateException error) {
                this.logger.raise("There was a problem with timer \"" + name + "\":", error);
            }

            return time;
        }
    }


    private void printStatistics(String prefix, boolean printTime) {
    	EfficientBoundingBoxNetCC effcc[] = new EfficientBoundingBoxNetCC[this.TotDie];
    	double totalWLCostDie = 0;
    	this.logger.println(prefix + " results:");
    	String format = "%-11s | %g%s\n";
        if(printTime) {
            double placeTime = this.getTime(prefix);
            this.logger.printf(format, "runtime", placeTime, " s");
        }
        
        int dieCounter = 0;
        
        while(dieCounter < this.TotDie) {

        	System.out.print("\nCalculating for die " + dieCounter+"\n");
        	effcc[dieCounter] = new EfficientBoundingBoxNetCC(this.circuitDie[dieCounter]);
        	double totalWLCost= effcc[dieCounter].calculateTotalCost();

            this.logger.printf(format, "BB cost", totalWLCost, "");
            this.logger.print("\n========= Starting timing cost calculation ============");
            this.circuitDie[dieCounter].recalculateTimingGraph();
            
            double totalTimingCost = this.circuitDie[dieCounter].getTotalTimingCost();
            this.logger.println();
            double maxDelay = this.circuitDie[dieCounter].getMaxDelay();
            this.logger.printf(format, "timing cost", totalTimingCost, "");
            this.logger.printf(format, "max delay", maxDelay, " ns");

            this.logger.println();
            this.logger.print("\n========= Ending timing cost calculation ============");
            boolean printCostOfEachBlockToFile = false;
            if(printCostOfEachBlockToFile){
            	Map<BlockType, Double> costPerBlockType = new HashMap<>();
    	        for(GlobalBlock block:this.circuitDie[dieCounter].getGlobalBlocks()){
    	        	double cost = effcc[dieCounter].calculateBlockCost(block);
    	        	if(!costPerBlockType.containsKey(block.getType())){
    	        		costPerBlockType.put(block.getType(), 0.0);
    	        	}
    	        	costPerBlockType.put(block.getType(), costPerBlockType.get(block.getType()) + cost);
    	        }
                this.logger.println("----\t-------");
                this.logger.println("Type\tBB Cost");
                this.logger.println("----\t-------");
                for(BlockType blockType:costPerBlockType.keySet()){
                	this.logger.printf("%s\t%.0f\n", blockType.toString().split("<")[0], costPerBlockType.get(blockType));
                }
                this.logger.println("----\t-------");
                	
            }
            this.logger.println();
            totalWLCostDie += totalWLCost;
            dieCounter++;
        }
        this.logger.print("\n========= Starting System level BB cost calculation ============");
        this.logger.println();
        EfficientBoundingBoxNetCC SLLCC = new EfficientBoundingBoxNetCC(this.circuitDie, this.TotDie , this.SLLrows);
        double SLLWLcost = SLLCC.calculateTotalSLLCost();
        this.logger.printf(format, "SLL BB cost", SLLWLcost, "");
        totalWLCostDie += SLLWLcost;
        this.logger.printf(format, "System level BB cost", totalWLCostDie, "");
        this.logger.print("\n========= Starting System level timing cost calculation ============");
        this.timingGraphSLL.recalculateTimingGraph();
        double totalMaxDelay = this.timingGraphSLL.getTotalMaxDelay();
        double SLLTimingCost = this.timingGraphSLL.calculateSLLtimingCost();
        this.logger.println();
        this.logger.printf(format, "SLL timing cost", SLLTimingCost,"");
        double totalSystemTimingCost = this.timingGraphSLL.calculateSystemTotalCost();
        totalSystemTimingCost += SLLTimingCost;
        this.logger.println();
        this.logger.printf(format, "System level timing cost", totalSystemTimingCost,"");
        this.logger.println();
        
        this.logger.printf(format, "System level max delay", totalMaxDelay, " ns");
        this.logger.println();
        this.logger.print("\n========= Ending System level timing cost calculation ============");
        this.logger.println();
    }
    private void printBlockDistance(){
    	this.printDistance("Cut Level");
    	this.printDistance("Total Distance");
    	this.printDistance("Test");
    }
    private void printDistance(String type){
    	int maxFPGADistance = this.circuit.getWidth() + this.circuit.getHeight();
    	Map<Integer, int[]> distanceCharts = new HashMap<Integer, int[]>();
    	for(GlobalBlock sourceBlock:this.circuit.getGlobalBlocks()){
    		if(!sourceBlock.getLeafNode().isFloating()){
    			for(GlobalBlock sinkBlock:this.circuit.getGlobalBlocks()){
    				if(!sinkBlock.getLeafNode().isFloating()){
    					if(sourceBlock.getIndex() != sinkBlock.getIndex()){
            				int fpgaDistance = this.fpgaDistance(sourceBlock, sinkBlock);
            				int hierarchyDistance = this.hierarchyDistance(type, sourceBlock, sinkBlock);

            				if(!distanceCharts.containsKey(hierarchyDistance)){
            					distanceCharts.put(hierarchyDistance, new int[maxFPGADistance + 1]);
            				}
            				distanceCharts.get(hierarchyDistance)[fpgaDistance]++;
            			}
    				}
        		}
    		}
    	}
    	this.makeGraph(type, distanceCharts);
    }
    
    //Distance
    private int fpgaDistance(GlobalBlock b1, GlobalBlock b2){
		int horizontalDistance = Math.abs(b1.getSite().getColumn() - b2.getSite().getColumn());
		int verticalDistance = Math.abs(b1.getSite().getRow() - b2.getSite().getRow());
		int fpgaDistance = horizontalDistance + verticalDistance;
		return fpgaDistance;
    }
    private int hierarchyDistance(String type, GlobalBlock b1, GlobalBlock b2){
    	LeafNode ln1 = b1.getLeafNode();
    	LeafNode ln2 = b2.getLeafNode();
    	
    	if(type.equals("Cut Level")){
    		return ln1.cutLevel(ln2);
    	}else if(type.equals("Total Distance")){
    		return ln1.totalDistance(ln2);
    	}else if(type.equals("Test")){
    		return ln1.cutSeparation(ln2) - ln1.cutLevel(ln2);
    	}else{
    		System.out.println("Unknown type hierarchy distance type: " + type);
    		return 0;
    	}
    }
    
    private boolean hasValues(int[] array){
    	int l = array.length;
    	for(int i = 0; i < l; i++){
    		if(array[i] > 0){
    			return true;
    		}
    	}
    	return false;
    }
    private void makeGraph(String name, Map<Integer, int[]> distanceCharts){
        LineChart chart = new LineChart(name, "Distance", "Number of connections");
        for(int hierarchyDistance = 0; hierarchyDistance < 1000 ; hierarchyDistance++){
        	if(distanceCharts.containsKey(hierarchyDistance)){
            	int[] temp = distanceCharts.get(hierarchyDistance);
            	if(this.hasValues(temp)){
                	int maxValue = 0;
                	for(int value:temp){
                		if(value > maxValue){
                			maxValue = value;
                		}
                	}
                	
                	maxValue = 1;
                	
                	for(int fpgaDistance = 0; fpgaDistance <= temp.length * 2 / 3; fpgaDistance++){
                		double value = temp[fpgaDistance] * (1.0 / maxValue);
        	        	chart.addData("" + hierarchyDistance, fpgaDistance, value);
        	        }
            	}
        	}
        }
        chart.pack( );
        RefineryUtilities.centerFrameOnScreen(chart);
        chart.setVisible(true);
    }

    
    

    private void stopAndPrintTimer() {
        this.stopAndPrintTimer(this.mostRecentTimerName);
    }
    private void stopAndPrintTimer(String timerName) {
        this.stopTimer(timerName);
        this.printTimer(timerName);
    }

    private void printTimer(String timerName) {
        double placeTime = this.getTime(timerName);
        this.logger.printf("%s: %f s\n", timerName, placeTime);
    }
    


    private void printGCStats() {
        long totalGarbageCollections = 0;
        long garbageCollectionTime = 0;

        for(GarbageCollectorMXBean gc :
                ManagementFactory.getGarbageCollectorMXBeans()) {

            long count = gc.getCollectionCount();

            if(count >= 0) {
                totalGarbageCollections += count;
            }

            long time = gc.getCollectionTime();

            if(time >= 0) {
                garbageCollectionTime += time;
            }
        }

        this.logger.printf("Total garbage collections: %d\n", totalGarbageCollections);
        this.logger.printf("Total garbage collection time: %f s\n", garbageCollectionTime / 1000.0);
    }
    class parallelReadNet implements Runnable{
    	int dieCounter;
    	StringBuilder localOutput;
    	public parallelReadNet(int dieCounter,  StringBuilder localOutput) {
    		this.dieCounter = dieCounter;
    		this.localOutput = localOutput;
    	}
    	public void run() {
    		NetlistParsing(this.dieCounter, this.localOutput);
    		}
    }
    

}
