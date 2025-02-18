package route.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import route.circuit.Circuit;
import route.circuit.CircuitSLL;
import route.circuit.architecture.Architecture;
import route.circuit.architecture.BlockCategory;
import route.circuit.architecture.BlockType;
import route.circuit.exceptions.InvalidFileFormatException;
import route.circuit.io.NetParser;
import route.interfaces.Logger;
import route.interfaces.Options;
import route.interfaces.Options.Required;
import route.interfaces.OptionsManager;
import route.circuit.block.AbstractBlock;
import route.circuit.block.GlobalBlock;
import route.circuit.architecture.ParseException;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;


import route.circuit.io.SllNetData;
import route.circuit.exceptions.PlacementException;
import route.circuit.io.BlockNotFoundException;
import route.circuit.io.IllegalSizeException;
import route.circuit.io.PlaceParser;
import route.circuit.pin.AbstractPin;
import route.circuit.pin.GlobalPin;
import route.circuit.resource.ResourceGraph;
import route.circuit.timing.TimingGraphSLL;
import route.route.ConnectionRouter;

public class Main {
	
	private Logger logger;
	
	private String circuitName;
	private File architectureFile, blifFile, netFile, placeFile, lookupDumpFile, sdcFile, rrgFile;
	private File [] netFileDie, placeFileDie;
	private ArrayList<File> netFiles;
	private ArrayList<File> placeFiles;
	private File inputFolder;
	
	private TimingGraphSLL timingGraphSystem;
	private ResourceGraph resourceGraph;
	private Architecture architecture;
	private Integer CurrentDie;
	private Circuit circuit;
	private Circuit[] circuitDie;
    private Integer TotDie;
    private Integer SLLrows;
    private Float sllDelay;
    private HashMap<String, SllNetData> sllNetInfo;
    private List<String> globalNetList;
    private CircuitSLL circuitSLL;
	private OptionsManager options;
	
    private static final String
	    O_ARCHITECTURE = "architecture_file",
	    O_BLIF_FILE = "blif_file",
	    O_SDC_FILE = "sdc_file",
	    O_NET_FILE = "net_file",
	    O_INPUT_PLACE_FILE = "place_file",
	    O_LOOKUP_DUMP_FILE = "lookup_dump_file",
	    O_RRG_FILE = "rr_graph_file",
		O_NUM_DIE = "number_of_dies",
		O_NUM_SLL_ROWS = "number_of_SLL_rows",
    	O_SLL_DELAY = "delay_of_SLL_wire";


	public static void initOptionList(Options options) {
	    options.add(O_ARCHITECTURE, "", File.class);
	    options.add(O_BLIF_FILE, "", File.class);
	    options.add(O_SDC_FILE, "", File.class);
	    options.add(O_NET_FILE, "(default: based on the blif file)", ArrayList.class, Required.FALSE);
	    options.add(O_INPUT_PLACE_FILE, " placement results", ArrayList.class, Required.FALSE);
	    options.add(O_LOOKUP_DUMP_FILE, "", File.class);
	    options.add(O_RRG_FILE, "", File.class, Required.FALSE);
	    options.add(O_NUM_DIE, "Number of dies chosen as 2", new Integer(2));
	    options.add(O_NUM_SLL_ROWS, "Number of SLL rows default set to 5", new Integer(36));
	    options.add(O_SLL_DELAY, "Delay of SLL wire default set to 360ps", new Float(360));
	}

	
    public Main(OptionsManager options) {
        this.options = options;
        this.logger = options.getLogger();

        this.parseOptions(options.getMainOptions());
    }
    
    private void parseOptions(Options options) {

        this.TotDie = options.getInteger(O_NUM_DIE);
        this.SLLrows = options.getInteger(O_NUM_SLL_ROWS);
        this.sllDelay = options.getFloat(O_SLL_DELAY);

        this.blifFile = options.getFile(O_BLIF_FILE);
        this.netFiles = options.getFiles(O_NET_FILE);
        this.placeFiles = options.getFiles(O_INPUT_PLACE_FILE);


        this.inputFolder = this.blifFile.getParentFile();
        this.circuitName = this.blifFile.getName().replaceFirst("(.+)\\.blif", "$1");

        if(this.netFiles == null) {
            File netFile = new File(this.inputFolder, this.circuitName + ".net");
            this.netFiles = new ArrayList<File>();
            this.netFiles.add(netFile);
        }
        
        if(this.placeFiles == null) {
            File placeFile = new File(this.inputFolder, this.circuitName + ".place");
            this.placeFiles = new ArrayList<File>();
            this.placeFiles.add(placeFile);
        }

        this.architectureFile = options.getFile(O_ARCHITECTURE);

        this.lookupDumpFile = options.getFile(O_LOOKUP_DUMP_FILE);
        
        this.sdcFile = options.getFile(O_SDC_FILE);
        
        this.rrgFile = options.getFile(O_RRG_FILE);

        this.checkFileExistence("architecture_file", this.architectureFile);
        this.checkFileExistence("blif_file", this.blifFile);
        for (int i = 0; i < this.netFiles.size(); i++) {
            File netFile = this.netFiles.get(i);
            this.checkFileExistence("net_file", netFile);
        }
        for (int i = 0; i < this.placeFiles.size(); i++) {
            File placeFile = this.placeFiles.get(i);
            this.checkFileExistence("place_file", placeFile);
        }
		this.checkFileExistence("lookup_dump_file", this.lookupDumpFile);
		this.checkFileExistence("sdc_file", this.sdcFile);

		this.printOptions();
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
	public void printOptions() {
		System.out.print("\nArchitecture file : " + this.architectureFile);
		System.out.print("\nTotal Dies : " + this.TotDie);
		System.out.print("\nSLL rows : " + this.SLLrows);

		
	}
    
	public void runCrouteMD() {
		
		this.circuitName = this.blifFile.getName().replaceFirst("(.+)\\.blif", "$1");
		this.logger.println("\nCircuit : " + this.circuitName);
		
		this.loadCircuit();
    	this.circuitSLL = new CircuitSLL(this.sllNetInfo, this.globalNetList, this.TotDie);
    	this.circuitSLL.createSLLPinConnections();
		//Build the resource graph now.
		this.buildSystemTimingGraph();
		this.loadRRG();
		this.printGCStats();

		this.loadPlaceFile();

		this.timingGraphSystem.initializeTiming();

		
		this.sanityCheck();
		
		System.gc();
		
		ConnectionRouter connectionRouter = new ConnectionRouter(this.resourceGraph, this.circuitDie, this.circuitSLL, this.TotDie, this.timingGraphSystem);
		int timeMilliseconds = connectionRouter.route();
		
		System.out.printf("Routing took %.2fs\n", (timeMilliseconds * Math.pow(10, -3)));
		System.out.println();
		
		this.timingGraphSystem.calculateActualWireDelay();
		this.timingGraphSystem.calculateArrivalRequiredAndCriticality(1, 1);

		System.out.println("-------------------------------------------------------------------------------");
		System.out.println("|               Timing information (based on actual wire delay)               |");
		System.out.println("-------------------------------------------------------------------------------");
		this.timingGraphSystem.printDelays();
		System.out.println();
		System.out.println();
		
		this.resourceGraph.printChannelUsage();
		this.resourceGraph.printWireUsage();
		System.out.print(this.timingGraphSystem.criticalPathToString());

	}
	
	
	private void loadRRG() {
		this.resourceGraph = new ResourceGraph(this.circuitDie);
		this.resourceGraph.build();
	}
	
	private void buildSystemTimingGraph() {
		System.out.print("\nBuilding the System level graph\n");
		
		this.timingGraphSystem = new TimingGraphSLL(this.circuitDie, this.sllNetInfo, this.TotDie);
		this.timingGraphSystem.build();
		
	}
    private void loadCircuit() {
    	//Process the architecture file
    	this.architecture = new Architecture(
    			this.circuitName,
    			this.architectureFile,
    			this.blifFile,
    			this.sdcFile,
    			this.rrgFile,
    			this.SLLrows,
    			this.sllDelay,
    			this.TotDie);
    	try {
    		architecture.parse();
    		architecture.getVprTiming(this.lookupDumpFile);
    	} catch(IOException | InvalidFileFormatException | InterruptedException | ParseException | ParserConfigurationException | SAXException error) {
    		this.logger.raise("Failed to parse architecture file or delay tables", error);
    	}
    	
    	// Parse net file
  
		this.netFileDie = new File[this.TotDie];
		this.circuitDie = new Circuit[this.TotDie];
		this.sllNetInfo = new HashMap<String, SllNetData>();
		this.globalNetList = new ArrayList<String>();
		
		for(int i = 0; i < this.TotDie; i++) {
			this.CurrentDie = i;
			this.netFile = this.netFiles.get(i);
			this.netFileDie[i] = this.netFiles.get(i);
			this.circuitDie[i] = this.loadNetCircuit(this.netFileDie[i], this.CurrentDie);
			this.globalNetList.addAll(this.circuitDie[i].getGlobalNetNames());
			this.logger.println(this.circuit.stats());
		}
		
    }
    
    private void loadSLLNets(int currentID) {

    	this.circuitSLL.loadSllNetsAndConnections(this.resourceGraph, currentID);
    	this.timingGraphSystem.addSLLConnections(this.circuitSLL.getSLLConnections());
    	this.timingGraphSystem.addAbstractPins(this.circuitSLL.getleafPins());
    }
    
    private void loadPlaceFile() {
		this.placeFileDie = new File[this.TotDie];
		int counter = 0;
		for(int i = 0; i < this.TotDie; i++) {
			this.CurrentDie = i;
			this.placeFile = this.placeFiles.get(i);
			this.placeFileDie[i] = this.placeFiles.get(i);
			counter = this.placeFileParse(this.CurrentDie, this.placeFileDie[i], counter);

		}
		this.loadSLLNets(counter);
		//Need to add a connection between source SLL block and sink SLL block.
    }
    private int placeFileParse(int currentDie, File placeFileDie, int counter) {
    	if(placeFileDie != null) {
    		PlaceParser placeParser = new PlaceParser(this.circuitDie[currentDie], this.circuitSLL, placeFileDie, this.resourceGraph,currentDie);
    		try {
                placeParser.parse();
            } catch(IOException | BlockNotFoundException | PlacementException | IllegalSizeException error) {
                this.logger.raise("Something went wrong while parsing the place file", error);
            }
        }else{
        	this.logger.raise("No valid place file");
        }
        return this.circuitDie[currentDie].loadNetsAndConnections(counter);

    }
    private Circuit loadNetCircuit(File NetFile, int dieCounter) {
    	 this.circuitName = this.netFile.getName().replaceFirst("(.+)\\.net", "$1");
    	 this.logger.print("\nThe circuit name is " + this.circuitName);
     		try {
    			NetParser netParser = new NetParser(this.architecture, this.circuitName, NetFile, this.resourceGraph);
    			this.circuit = netParser.parse(dieCounter, this.sllNetInfo);
    			this.logger.println(this.circuit.stats());
    		
    	} catch(IOException error) {
    		this.logger.raise("Failed to read net file", error);
    	}
     	this.logger.println();
     	this.detaildGlobalBlockInformation();
     	this.printNumBlocks();
     	return this.circuit;
    }

	
    private void checkFileExistence(String prefix, File file) {
        if(file == null) {
        	this.logger.raise(new FileNotFoundException(prefix + " not given as an argument"));
        }

        if(!file.exists()) {
        	this.logger.raise(new FileNotFoundException(prefix + " " + file));

        } else if(file.isDirectory()) {
        	this.logger.raise(new FileNotFoundException(prefix + " " + file + " is a directory"));
        }
    }
    
    private void printNumBlocks() {
        int numLut = 0,
            numFf = 0,
            numClb = 0,
            numHardBlock = 0,
            numIo = 0;
        
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

        this.logger.println("Circuit statistics:");
        this.logger.printf("   clb: %d\n      lut: %d\n      ff: %d\n   hardblock: %d\n      PLL: %d\n      DSP: %d\n      Memory: %d\n      M9K: %d\n      M144K: %d\n   io: %d\n\n",
                numClb, numLut, numFf, numHardBlock, numPLL, numDSP, numMem, numM9K, numM144K, numIo);
        this.logger.print("   Num pins: " + numPins + "\n\n");
    }
    
    private void sanityCheck() {
    	this.resourceGraph.sanityCheck();
    }
    private void detaildGlobalBlockInformation() {
		for(GlobalBlock block : this.circuit.getGlobalBlocks()) {
			for(AbstractPin abstractPin : block.getOutputPins()) {
				GlobalPin pin = (GlobalPin) abstractPin;
				
				if(pin.getNetName() == null && pin.getNumSinks() > 0) {
					System.err.println("\t" + "Block: " + block.getName() + " " + "Net: " + pin.getNetName() + " " + pin.getNumSinks() + " " + pin.getPortName() + "[" + pin.getIndex() + "] " + pin.getPortType().isEquivalent());
				}
			}
		}
    }
}
