package place.circuit.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import place.circuit.Circuit;
import place.circuit.block.AbstractSite;
import place.circuit.block.GlobalBlock;
import place.circuit.pin.AbstractPin;
import place.circuit.pin.GlobalPin;


public class PlaceDumper {

    private Circuit[] circuit;
    private File[] netFile, placeFile;
    private File architectureFileVPR;
    private String netPath, architecturePath;
    private int totalDies;

    public PlaceDumper(Circuit[] circuit, File[] netFile, File[] placeFile, File architectureFileVPR, int totaldies) {
        this.circuit = circuit;
        this.netFile = netFile;
        this.placeFile = placeFile;
        
        this.architectureFileVPR = architectureFileVPR;

        this.architecturePath = this.architectureFileVPR.getAbsolutePath();
        this.totalDies = totaldies;

    }

    
    public void dump(Circuit[] circuit) throws IOException {
        
        int dieCounter = 0;
        
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<?>> futures = new ArrayList<>();

        for (int dieIndex = 0; dieIndex < this.totalDies; dieIndex++) {
            final int index = dieIndex;

            futures.add(executor.submit(() -> {
                try {
                    // Create directories
                    this.placeFile[index].getAbsoluteFile().getParentFile().mkdirs();

                    // Local length computation
                    int length = 0;
                    for (GlobalBlock block : circuit[index].getGlobalBlocks()) {
                        length = Math.max(length, block.getName().length());
                    }
                    length += 26;

                    // Dump header
                    int width = circuit[index].getWidth(), height = circuit[index].getHeight();
                    int archRows = circuit[index].getArchitecture().archRows, archCols = circuit[index].getArchitecture().archCols;
                    try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(this.placeFile[index])))) {
                        this.dumpHeader(writer, width, height, length);

                        // Build index-sorted block array
                        int maxIndex = 0;
                        for (GlobalBlock block : circuit[index].getGlobalBlocks()) {
                            maxIndex = Math.max(maxIndex, block.getIndex());
                        }

                        GlobalBlock[] blocks = new GlobalBlock[maxIndex + 1];
                        for (GlobalBlock block : circuit[index].getGlobalBlocks()) {
                            blocks[block.getIndex()] = block;
                        }

                        // Build subblk and write block positions
                        Map<AbstractSite, Integer> siteOccupations = new HashMap<>();
                        Map<String, Integer> subblk = new HashMap<>();

                        for (GlobalBlock block : blocks) {
                            AbstractSite site = block.getSite();
                            int x = site.getColumn();
                            int y = site.getRow();
                            if(archCols == 1) {
                                if (index >= 1) {
                                    y += circuit[index].getHeight() * index;
                                }
                            }else if(archCols == 2) {
                            	if(index == 2 || index == 3) {
                            		y += circuit[index].getHeight();
                            	}
                            	if(index == 1 || index == 3) {
                            		x += circuit[index].getWidth();
                            	}
                            	
                            	
                            }else {
                            	System.err.print("\nConfiguration not supported");
                            }
                            
                            


                            int z = siteOccupations.getOrDefault(site, 0);
                            siteOccupations.put(site, z + 1);

                            writer.printf("%-" + length + "s\t%d\t%d\t%d\t#%d\t%s\n", block.getName(), x, y, z, block.getIndex(), block.wireType);
                            subblk.put(block.getName(), z);
                        }

                        // Post-place block info
                        try (PrintWriter blockWriter = new PrintWriter(new BufferedWriter(new FileWriter(this.placeFile[index].toString().replace(".place", ".post_place.blocks"))))) {
                            for (GlobalBlock block : circuit[index].getGlobalBlocks()) {
                                String name = block.getName();
                                String type = block.getType().toString().split("<")[0];
                                AbstractSite site = block.getSite();
                                int x = site.getColumn();
                                int y = site.getRow();
                                int blockIndex = block.getIndex();
                                int z = subblk.getOrDefault(name, 0); // Avoid null pointer
                                blockWriter.println(name + ";" + type + ";" + x + ";" + y + ";" + z + ";" + blockIndex);
                            }
                        }

                        // Post-place net info
                        this.checkNetNames(index);
                        try (PrintWriter netWriter = new PrintWriter(new BufferedWriter(new FileWriter(this.placeFile[index].toString().replace(".place", ".post_place.nets"))))) {
                            for (GlobalBlock sourceBlock : circuit[index].getGlobalBlocks()) {
                                for (AbstractPin abstractSourcePin : sourceBlock.getOutputPins()) {
                                    GlobalPin sourcePin = (GlobalPin) abstractSourcePin;
                                    if (!sourcePin.getSinks().isEmpty()) {
                                        netWriter.print("Net_" + sourcePin.getNetName());
                                        netWriter.print(";" + sourceBlock.getName() + "." + sourcePin.getPortType() + "[" + sourcePin.getIndex() + "]");
                                        for (AbstractPin sinkPin : sourcePin.getSinks()) {
                                            GlobalBlock sink = (GlobalBlock) sinkPin.getOwner();
                                            netWriter.print(";" + sink.getName() + "." + sinkPin.getPortType() + "[" + sinkPin.getIndex() + "]");
                                        }
                                        netWriter.println();
                                    }
                                }
                            }
                        }

                        // Bounding box (BB_info) output
                        this.checkNetNames(index);
                        try (PrintWriter bbWriter = new PrintWriter(new BufferedWriter(new FileWriter(this.placeFile[index].toString().replace(".place", ".BB_info"))))) {
                            for (GlobalBlock sourceBlock : circuit[index].getGlobalBlocks()) {
                                for (AbstractPin abstractSourcePin : sourceBlock.getOutputPins()) {
                                    GlobalPin sourcePin = (GlobalPin) abstractSourcePin;
                                    if (!sourcePin.getSinks().isEmpty()) {
                                        double maxX = sourceBlock.getColumn();
                                        double minX = maxX;
                                        double maxY = sourceBlock.getRow();
                                        double minY = maxY;

                                        for (AbstractPin sinkPin : sourcePin.getSinks()) {
                                            GlobalBlock sink = (GlobalBlock) sinkPin.getOwner();
                                            double x = sink.getColumn();
                                            double y = sink.getRow();
                                            maxX = Math.max(maxX, x);
                                            minX = Math.min(minX, x);
                                            maxY = Math.max(maxY, y);
                                            minY = Math.min(minY, y);
                                        }

                                        bbWriter.println("Net_" + sourcePin.getNetName() + ";" + maxX + ";" + minX + ";" + maxY + ";" + minY + ";" + sourcePin.getSinks().size());
                                    }
                                }
                            }
                        }

                    } // End writer try-with-resources

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));
        }

        // Wait for all threads
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        executor.shutdown();

        
    }
    
    
    private void checkNetNames(int dieCount){
    	for(GlobalBlock sourceBlock:this.circuit[dieCount].getGlobalBlocks()){
    		
    		for(AbstractPin abstractSourcePin:sourceBlock.getOutputPins()){
    			GlobalPin sourcePin = (GlobalPin) abstractSourcePin;
    			
    			if(sourcePin.getSinks().size() > 0){
    				String netName = sourcePin.getNetName();
    				
    				boolean print = false;
    				for(AbstractPin abstractSinkPin:sourcePin.getSinks()){
    					GlobalPin sinkPin = (GlobalPin) abstractSinkPin;
    					if(!sinkPin.getNetName().equals(netName)) print = true;
    				}
    				if(print){
 					    					
        				System.err.println("Net_" + sourcePin.getNetName());
        				System.err.println(sourcePin.getNetName());
        				for(AbstractPin abstractSinkPin:sourcePin.getSinks()){
        					GlobalPin sinkPin = (GlobalPin) abstractSinkPin;
        					System.err.println(sinkPin.getNetName());
        				}
        				System.err.println();
    				}
    			}
    		}
    	}
    }

    private void dumpHeader(PrintWriter writer, int width, int height, int length) {
        // Print out the header
        writer.printf("Netlist file: %s   Architecture file: %s\n", this.netPath, this.architecturePath);
        writer.printf("Array size: %d x %d logic blocks\n\n", width, height);

        writer.printf("%-"+length+"s\tx\ty\tsubblk\tblock number\tWireType\n", "#block name");
        writer.printf("%-"+length+"s\t--\t--\t------\t------------\t------------\n", "#----------");
    }
    
//    class parallelplaceDump implements Runnable{
//    	int dieCounter;
//    	public parallelplaceDump(int dieCounter) {
//    		this.dieCounter = dieCounter;
//    	}
//    	public void run() {
//    		try {
//				parallelplaceDump(this.dieCounter);
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//    		}
//    }
}
