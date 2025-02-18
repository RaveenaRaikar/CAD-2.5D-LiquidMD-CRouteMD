package route.circuit.resource;
import java.util.ArrayList;
import java.util.List;

import route.circuit.architecture.BlockType;

public class BlockTypeRRG implements Comparable<BlockTypeRRG> {
	protected final int index;
	protected final int height;
	protected String blocktype;
	private List<PinClass> pinClasses;
	
	public BlockTypeRRG(int index, int height,int width, String blockType) {
		this.index = index;
		this.height = height;
		this.blocktype = blocktype;
		this.pinClasses = new ArrayList<PinClass>();
	}
	
    public int getBlockID() {
        return index;
    }

    public String getBlockType() {
        return blocktype;
    }
    
    public List<PinClass> getPinClasses() {
        return pinClasses;
    }

    public void addPinClass(String pinName, int pinId, String pintype) {
        PinClass pinClass = new PinClass(pinName, pinId, pintype);
        pinClasses.add(pinClass);
    }
    
    public String getPinClassNameByPinId(int targetPinId) {
        for (PinClass pinClass : pinClasses) {
            if (pinClass.getPinId() == targetPinId) {
                return pinClass.getPinName();
            }
        }
        return null; 
    }

	class PinClass {
		private String pinName;
		private int pinPtc;
		private String pintype;
		
		public PinClass(String pinName, int pinId, String pintype) {
	        this.pinName = pinName;
	        this.pinPtc = pinId;
	        this.pintype = pintype;
	    }
		
	    public String getPinName() {
	        return pinName;
	    }

	    public int getPinId() {
	        return pinPtc;
	    }
	    public String getPintype() {
	        return pintype;
	    }
	}
	@Override
	public int compareTo(BlockTypeRRG otherBlockType) {
		return Integer.compare(this.index, otherBlockType.index);
	}
}
