package place.circuit.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        
        while(dieCounter < this.totalDies) {
        	 int length = 0;
            this.placeFile[dieCounter].getAbsoluteFile().getParentFile().mkdirs();

            PrintWriter writer = null;
            writer = new PrintWriter(new BufferedWriter(new FileWriter(this.placeFile[dieCounter])));
            this.netPath = this.netFile[dieCounter].getAbsolutePath();
        	System.out.print("\nThe die number is " + dieCounter + "\n");
        	
        	for(GlobalBlock block : circuit[dieCounter].getGlobalBlocks()) {
                if(block.getName().length() > length) {
                    length = block.getName().length();
                }
            }

            length = length + 26;


            int width = circuit[dieCounter].getWidth(), height = circuit[dieCounter].getHeight();

            this.dumpHeader(writer, width, height, length);

            int maxIndex = 0;
            for(GlobalBlock block : circuit[dieCounter].getGlobalBlocks()){
            	if(block.getIndex() > maxIndex){
            		maxIndex = block.getIndex();
            		
            	}
            }

            GlobalBlock[] blocks = new GlobalBlock[maxIndex+1];
            for(GlobalBlock block : circuit[dieCounter].getGlobalBlocks()){
            	if(blocks[block.getIndex()] != null){

            	}
            	blocks[block.getIndex()] = block;
            	
            }
            for(int index=0;index<blocks.length;index++){
            	if(blocks[index] == null){
            		System.out.println("Unused index: " + index);
            	}
            }

            Map<AbstractSite, Integer> siteOccupations = new HashMap<AbstractSite, Integer>();
            Map<String, Integer> subblk = new HashMap<>();
            for(GlobalBlock block : blocks) {
                AbstractSite site = block.getSite();
                int x = site.getColumn();
                int y = site.getRow();
                if(dieCounter == 1) {

                	y = y + this.circuit[dieCounter].getHeight();
                }
                int index = block.getIndex();

                int z;
                if(siteOccupations.containsKey(site)) {
                    z = siteOccupations.get(site);
                } else {
                    z = 0;
                }
                siteOccupations.put(site, z + 1);
                writer.printf("%-"+length+"s\t%d\t%d\t%d\t#%d\n",block.getName(), x, y, z, index);
                
                subblk.put(block.getName(), z);
            }
            writer.close();
            

            
            boolean writePostPlaceAdditionalInformation = true;
            if(writePostPlaceAdditionalInformation) {
                //Information of the blocks
                writer = new PrintWriter(new BufferedWriter(new FileWriter(this.placeFile[dieCounter].toString().replace(".place", ".post_place.blocks"))));
                for(GlobalBlock block:circuit[dieCounter].getGlobalBlocks()){
                    String name = block.getName();
                    String type = block.getType().toString().split("<")[0];
                	AbstractSite site = block.getSite();
                    int x = site.getColumn();
                    int y = site.getRow();
                    int index = block.getIndex();
                    writer.println(name + ";" + type + ";" + x + ";" + y + ";" + subblk.get(block.getName()) + ";" + index);
                }
                writer.close();
                
                //Information of the nets
                this.checkNetNames(dieCounter);
                writer = new PrintWriter(new BufferedWriter(new FileWriter(this.placeFile[dieCounter].toString().replace(".place", ".post_place.nets"))));
            	for(GlobalBlock sourceBlock:circuit[dieCounter].getGlobalBlocks()){
            		for(AbstractPin abstractSourcePin:sourceBlock.getOutputPins()){
            			GlobalPin sourcePin = (GlobalPin) abstractSourcePin;
            			if(sourcePin.getSinks().size() > 0){
            				writer.print("Net_" + sourcePin.getNetName());
            				writer.print(";" + sourceBlock.getName() + "." + sourcePin.getPortType() + "[" + sourcePin.getIndex() + "]");
            				for(AbstractPin sinkPin:sourcePin.getSinks()){
            					GlobalBlock sink = (GlobalBlock) sinkPin.getOwner();
            					writer.print(";" + sink.getName() + "." + sinkPin.getPortType() + "[" + sinkPin.getIndex() + "]");
            				}
            				writer.println();
            			}
            		}
            	}
                writer.close();
            }
            boolean writeNetInformation = true;
            if(writeNetInformation) {
            	double minX,minY = -1;
            	double maxX, maxY = Integer.MAX_VALUE;
                //Information of the nets
                this.checkNetNames(dieCounter);
                writer = new PrintWriter(new BufferedWriter(new FileWriter(this.placeFile[dieCounter].toString().replace(".place", ".BB_info"))));
            	for(GlobalBlock sourceBlock:circuit[dieCounter].getGlobalBlocks()){
            		for(AbstractPin abstractSourcePin:sourceBlock.getOutputPins()){
            			GlobalPin sourcePin = (GlobalPin) abstractSourcePin;
            			if(sourcePin.getSinks().size() > 0){
            				maxX = sourceBlock.getColumn();
            				minX = sourceBlock.getColumn();
            				maxY = sourceBlock.getRow();
            				minY = sourceBlock.getRow();
            				
            				for(AbstractPin sinkPin:sourcePin.getSinks()){
            					GlobalBlock sink = (GlobalBlock) sinkPin.getOwner();
            					double x = sink.getColumn();
            					double y = sink.getRow();
            					if(x > maxX) {
            						maxX = x;
            					}else if(x < minX) {
            						minX = x;
            					}else if(y > maxY) {
            						maxY = y;
            					}else if(y < minY) {
            						minY = y;
            					}
            				}
            				writer.print("Net_" + sourcePin.getNetName() + ";" + maxX + ";" + minX + ";" + maxY + ";" + minY +";" +sourcePin.getSinks().size() );
            				writer.println();
            			}
            		}
            	}
                writer.close();
            }
            
            dieCounter++;
        }
        
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

        writer.printf("%-"+length+"s\tx\ty\tsubblk\tblock number\n", "#block name");
        writer.printf("%-"+length+"s\t--\t--\t------\t------------\n", "#----------");
    }
    

}
