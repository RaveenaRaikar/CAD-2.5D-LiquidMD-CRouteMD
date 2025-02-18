package place.placers.simulatedannealing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import place.circuit.Circuit;
import place.circuit.block.GlobalBlock;
import place.circuit.block.Site;
import place.circuit.pin.AbstractPin;
import place.circuit.pin.GlobalPin;



public class EfficientBoundingBoxNetCC {

    private Map<GlobalBlock, List<EfficientBoundingBoxData>> bbDataMap;
    private ArrayList<EfficientBoundingBoxData> bbDataArray;
    private Map<GlobalBlock, List<EfficientBoundingBoxData>> bbDataMapSLL;
    private ArrayList<EfficientBoundingBoxData> bbDataArraySLL;
    private int numPins;
    private int numBlockspair;


    // Contains the blocks for which the associated boundingBox's might need to be reverted
    private List<GlobalBlock> toRevert = new ArrayList<>();

    public EfficientBoundingBoxNetCC(Circuit circuit) {

        this.bbDataArray = new ArrayList<EfficientBoundingBoxData>();
        this.bbDataMap = new HashMap<GlobalBlock, List<EfficientBoundingBoxData>>();

        // Process all nets by iterating over all net source pins
        this.numPins = 0;
        for(GlobalBlock block : circuit.getGlobalBlocks()) {

            for(AbstractPin pin : block.getOutputPins()) {
            	
                this.processPin((GlobalPin) pin);
            }
        }

        this.bbDataArray.trimToSize();
    }   
    

    public EfficientBoundingBoxNetCC(Circuit[] circuit, int dieCount, int SLLrows ) {

        this.bbDataArraySLL = new ArrayList<EfficientBoundingBoxData>();

        Map<String,ArrayList<GlobalBlock>> dummyBlocks = new HashMap<String, ArrayList<GlobalBlock>>();
        String blockname;
        for(int dieCounter = 0; dieCounter < dieCount ;dieCounter++) {
        	for(GlobalBlock block : circuit[dieCounter].getGlobalBlocks()) {
        		if(block.isSLLDummy()) {
        			blockname = block.getName();
        			if(!dummyBlocks.containsKey(blockname)) {
        				dummyBlocks.put(blockname, new ArrayList<GlobalBlock>());
        				dummyBlocks.get(blockname).add(block);
        			}else {
        				dummyBlocks.get(blockname).add(block);
        			}

        		}
        	}
        }
        
        for(String sllname : dummyBlocks.keySet()) {
        	this.processBlocks(dummyBlocks.get(sllname), dieCount, SLLrows);
        }
        

        this.bbDataArraySLL.trimToSize();
    } 

    private void processBlocks(ArrayList<GlobalBlock> SLLblocks, int dieCount ,int SLLrows) {
    	this.numBlockspair++;
    	GlobalBlock[] blockArray = new GlobalBlock[dieCount];
    	blockArray = SLLblocks.toArray(blockArray);
    	
    	EfficientBoundingBoxData SLLBBData = new EfficientBoundingBoxData(blockArray, SLLrows); 
    	this.bbDataArraySLL.add(SLLBBData);
    }
    private void processPin(GlobalPin pin) {

        int numSinks = pin.getNumSinks();
        if(numSinks == 0 || pin.getSink(0).getPortType().isClock()) {
            return;
        }
        this.numPins++;

        EfficientBoundingBoxData bbData = new EfficientBoundingBoxData(pin);
        this.bbDataArray.add(bbData);


        if(this.bbDataMap.get(pin.getOwner()) == null) {
            this.bbDataMap.put(pin.getOwner(), new ArrayList<EfficientBoundingBoxData>());
        }
        this.bbDataMap.get(pin.getOwner()).add(bbData);

        for(int i = 0; i < numSinks; i++) {
            GlobalPin sink = pin.getSink(i);
            if(this.bbDataMap.get(sink.getOwner()) == null) {
                this.bbDataMap.put(sink.getOwner(), new ArrayList<EfficientBoundingBoxData>());
            }
            List<EfficientBoundingBoxData> sinkBlockList = this.bbDataMap.get(sink.getOwner());
            boolean isAlreadyIn = false;
            for(EfficientBoundingBoxData data: sinkBlockList) {
                if(data == bbData) {
                    isAlreadyIn = true;
                    break;
                }
            }

            if(!isAlreadyIn) {
                sinkBlockList.add(bbData);
            }
        }
    }


    public double calculateAverageNetCost() {
        return calculateTotalCost() / this.numPins;
    }


    public double calculateTotalCost() {
        double totalCost = 0.0;
        System.out.print("\nThe size of bbData array is " + this.bbDataArray.size()+"\n");
        for(int i = 0; i < this.numPins; i++) {
            totalCost += this.bbDataArray.get(i).getNetCost();
        }
        return totalCost;
    }
    public double calculateTotalSLLCost() {
        double totalCost = 0.0;
        for(int i = 0; i < this.numBlockspair; i++) {
            totalCost += this.bbDataArraySLL.get(i).getNetCost();
        }
        return totalCost;
    }
    public double calculateBlockCost(GlobalBlock block){
    	double cost = 0.0;
    	if(this.bbDataMap.containsKey(block)){
        	for(EfficientBoundingBoxData data:this.bbDataMap.get(block)){
        		cost += data.getFanoutWeightedNetCost();
        	}
    	}
    	return cost;
    }

    public double calculateDeltaCost(Swap swap) {
        this.toRevert.clear();

        double deltaCost = 0;

        int numBlocks = swap.getNumBlocks();
        for(int i = 0; i < numBlocks; i++) {
            Site site1 = swap.getSite1(i);
            GlobalBlock block1 = site1.getBlock();

            Site site2 = swap.getSite2(i);
            GlobalBlock block2 = site2.getBlock();

            deltaCost += this.addToRevert(block1, site2);
            if(block2 != null) {
                deltaCost += this.addToRevert(block2, site1);
            }
        }

        return deltaCost;
    }

    private double addToRevert(GlobalBlock block, Site site) {
        this.toRevert.add(block);

        double deltaCost = 0;

        List<EfficientBoundingBoxData> bbDataList = this.bbDataMap.get(block);
        if(bbDataList != null) {
            for(EfficientBoundingBoxData bbData: bbDataList) {
                bbData.saveState();
                deltaCost += bbData.calculateDeltaCost(block, site);
            }
        }

        return deltaCost;
    }


    public void recalculateFromScratch() {
        for(int i = 0; i < this.numPins; i++) {
            this.bbDataArray.get(i).calculateBoundingBoxFromScratch(false);
        }
    }


    public void revert() {
        for(GlobalBlock block : this.toRevert) {
            List<EfficientBoundingBoxData> bbDataList = this.bbDataMap.get(block);
            if(bbDataList != null) {
                for(EfficientBoundingBoxData bbData: bbDataList) {
                    bbData.revert();
                }
            }
        }
    }


    public void pushThrough() {
        for(GlobalBlock block : this.toRevert) {
            List<EfficientBoundingBoxData> bbDataList = this.bbDataMap.get(block);
            if(bbDataList != null) {
                for(EfficientBoundingBoxData bbData: bbDataList) {
                    bbData.pushThrough();
                }
            }
        }
    }
}
