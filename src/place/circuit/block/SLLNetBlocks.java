package place.circuit.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import place.circuit.pin.GlobalPin;
import place.placers.analytical.AnalyticalAndGradientPlacer.NetBlock;

public class SLLNetBlocks {
	public final String netName;
//	public final int dieID;
	//public Map<GlobalBlock, NetBlock> gbTonb;
	public GlobalBlock sourceBlock;
	public List<GlobalBlock> sinkBlocks;
	public GlobalBlock dummySink, dummySource;
	public GlobalPin sourcePin;
	public List<GlobalPin> sinkPins;
	public GlobalPin dummySinkPin, dummySourcePin;
	
	public SLLNetBlocks(String netName) {
		this.netName = netName;
//		this.dieID = dieID;
		this.sinkBlocks = new ArrayList<>();
		this.sinkPins = new ArrayList<>();
//		this.gbTonb = new HashMap<>();
	}
	
//	public void addNetBlock(GlobalBlock gblock, NetBlock nblock) {
//		this.gbTonb.put(gblock, nblock);
//	}
	public int getNetBlockIndex(GlobalBlock global) {
//		NetBlock netBlock = this.gbTonb.get(global);
		return global.netBlock.getBlockIndex();
	}
	public float getBlockOffset(GlobalBlock global) {
//		NetBlock netBlock = this.gbTonb.get(global);
		return global.netBlock.getOffset();
	}
	
	public void setSourcePin(GlobalPin sourcePin) {
		this.sourcePin = sourcePin;
		this.sourceBlock = this.sourcePin.getOwner();
	}
	public void addSinkPins(GlobalPin sinkPin) {
		if(!this.sinkPins.contains(sinkPin)) {
			this.sinkPins.add(sinkPin);
			this.sinkBlocks.add(sinkPin.getOwner());
		}
	}
	
	//gb -- datain
	public void addDummySink(GlobalPin dummySinkPin) {
		this.dummySinkPin = dummySinkPin;
//		System.out.print("\nThe sink pin is " + dummySinkPin);
		this.dummySink = this.dummySinkPin.getOwner();
	}
	
	//dataout -- gb
	public void addDummySource(GlobalPin dummySourcePin) {
		this.dummySourcePin = dummySourcePin;
		this.dummySource = this.dummySourcePin.getOwner();
	}
	public List<Integer> getBlockIndexList(){
		List<Integer> tempList = new ArrayList<>();
		//tempList.add(this.getNetBlockIndex(this.sourceBlock));
		for(GlobalBlock allsinks:this.sinkBlocks) {
//			System.out.print("\nThe block is " + allsinks);
			if(!allsinks.isInMacro()) {
				if(!tempList.contains(this.getNetBlockIndex(allsinks))){
					tempList.add(this.getNetBlockIndex(allsinks));
				}
			}

		}
	return tempList;
	}
	
	
	
	public void merge(SLLNetBlocks other) {
		if(other == null) {
			return;
		}
		if(this.netName.equals(other.netName)) {
			//current obj already has source info
			if(this.dummySourcePin != null) {
//				System.out.print("\nT);
				this.addDummySink(other.dummySinkPin);
				this.setSourcePin(other.sourcePin);
			}else {
				this.addDummySource(other.dummySourcePin);
				for(GlobalPin sinkPin:other.sinkPins) {
					this.addSinkPins(sinkPin);
				}
			}
		}else {
			System.out.print("\nNetnames dont match");
			System.out.print("\nCurrent name : " + this.netName);
			System.out.print("\nThe other name : " + other.netName);
		}
		
	}
	
	//What does this maintain?
	
	//actual source
	//source pin
	//dummy (sink)
	//This pin
	//dummy (source)
	//all sink blocks
	//all sink pins
	// sLL net name
	//block indices
	

}

