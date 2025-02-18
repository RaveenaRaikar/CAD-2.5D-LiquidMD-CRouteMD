package place.circuit.block;

import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.architecture.PortType;
import place.circuit.pin.AbstractPin;
import place.hierarchy.LeafNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;



public abstract class AbstractBlock implements Comparable<AbstractBlock> {

    private String name;
    protected BlockType blockType;
    private BlockCategory category;
    private int index;
    private static int SLLIndex = 0;
    private boolean clocked;

    //TO MARK THE BLOCK AS SLL BLOCK 
    private List<LocalBlock> children;
    private List<AbstractPin> pins;
    private boolean SLLblock;
    private boolean isSLLSource = false;
    private boolean isSLLSink = false;

    public AbstractBlock(String name, BlockType blockType, int index) {

    	
        this.name = new String(name);
        this.blockType = blockType;
        this.category = blockType.getCategory();
        this.index = index;

        this.clocked = blockType.isClocked();

        int numChildren = blockType.getNumChildren();
        this.children = new ArrayList<LocalBlock>(Collections.nCopies(numChildren, (LocalBlock) null));

        int numPins = blockType.getNumPins();
        this.pins = new ArrayList<AbstractPin>(numPins);

        for(PortType portType : blockType.getPortTypes()) {

            int[] portRange = portType.getRange();
            int portStart = portRange[0];
            int portEnd = portRange[1];

            for(int totalIndex = portStart; totalIndex < portEnd; totalIndex++) {
                int pinIndex = totalIndex - portStart;
                AbstractPin newPin = this.createPin(portType, pinIndex);
                this.pins.add(newPin);
            }
        }
    }

   public AbstractBlock(String name, BlockType blockType, int index, Boolean SLLblock) {

    	
        this.name = new String(name);
        this.blockType = blockType;

        this.category = blockType.getCategory();
        this.index = index;
        this.clocked = blockType.isClocked();
        this.SLLblock = SLLblock;
        if(!SLLblock) {
            int numChildren = blockType.getNumChildren();
            this.children = new ArrayList<LocalBlock>(Collections.nCopies(numChildren, (LocalBlock) null));
        }

        

        int numPins = blockType.getNumPins();
        this.pins = new ArrayList<AbstractPin>(numPins);

        for(PortType portType : blockType.getPortTypes()) {

            int[] portRange = portType.getRange();
            int portStart = portRange[0];
            int portEnd = portRange[1];

            for(int totalIndex = portStart; totalIndex < portEnd; totalIndex++) {
                int pinIndex = totalIndex - portStart;
                AbstractPin newPin = this.createPin(portType, pinIndex);
                this.pins.add(newPin);
            }
        }
    }

    public abstract AbstractBlock getParent();
    public abstract AbstractPin createPin(PortType portType, int index);

    public void compact() {
        for(AbstractPin pin : this.pins) {
            pin.compact();
        }
    }


    public String getName() {
        return this.name;
    }
    public BlockType getType() {
        return this.blockType;
    }

    public BlockCategory getCategory() {
        return this.category;
    }

    public void setCategory(Integer SllBlockNum, Boolean isSLL) {
        if(isSLL) {
        	this.category = this.blockType.setCategory(SllBlockNum);
        }
    }
    public int getIndex() {
        return this.index;
    }

    public boolean isGlobal() {
        return this.getType().isGlobal();
    }
    public boolean isLeaf() {
        return this.getType().isLeaf();
    }
    public boolean isSLLDummy() {
        return this.getType().isSLLDummy();
    }
    public void setSLLStatus(Boolean isSLL) {
    	this.SLLblock = isSLL;
    }
    
    public boolean getSLLStatus(){
    	return this.SLLblock;
    }


    public List<LocalBlock> getChildren() {
        return this.children;
    }
    public List<LocalBlock> getChildren(BlockType blockType) {
        int[] childRange = this.blockType.getChildRange(blockType);
        return this.children.subList(childRange[0], childRange[1]);
    }
    public LocalBlock getChild(BlockType blockType, int childIndex) {
        return this.getChildren(blockType).get(childIndex);
    }
    public void setChild(LocalBlock block, int childIndex) {

        int childStart = this.blockType.getChildRange(block.getType())[0];

        this.children.set(childStart + childIndex, block);
    }

    public void setSLLsource(Boolean isSLLsource) {
    	this.isSLLSource = isSLLsource;
    }
    
    public void setSLLsink(Boolean isSLLsink) {
    	this.isSLLSink = isSLLsink;
    }
    
    public boolean isSLLSource() {
    	return this.isSLLSource;
    }
    
    public boolean isSLLSink() {
    	return this.isSLLSink;
    }

    public boolean isClocked() {
        return this.clocked;
    }


    public int numInputPins() {
        int[] pinRange = this.blockType.getInputPortRange();
        return pinRange[1] - pinRange[0];
    }
    public int numOutputPins() {
        int[] pinRange = this.blockType.getOutputPortRange();
        return pinRange[1] - pinRange[0];
    }
    public int numClockPins() {
        int[] pinRange = this.blockType.getClockPortRange();
        return pinRange[1] - pinRange[0];
    }

    public List<AbstractPin> getInputPins() {
        int[] pinRange = this.blockType.getInputPortRange();
        return this.getPins(pinRange);
    }
    public List<AbstractPin> getOutputPins() {
        int[] pinRange = this.blockType.getOutputPortRange();
        return this.getPins(pinRange);
    }
    public List<AbstractPin> getClockPins() {
        int[] pinRange = this.blockType.getClockPortRange();
        return this.getPins(pinRange);
    }

    public List<AbstractPin> getPins(PortType portType) {
        int[] pinRange = portType.getRange();
        return this.getPins(pinRange);
    }

    public AbstractPin getPin(PortType portType, int pinIndex) {
        return this.getPins(portType).get(pinIndex);
    }

    private List<AbstractPin> getPins(int[] range) {
        return this.pins.subList(range[0], range[1]);
    }

    // These methods should only be used for serialization and deserialization!
    public void setPins(List<AbstractPin> pins) {
        this.pins = pins;
    }
    public List<AbstractPin> getPins() {
        return this.pins;
    }

    
    @Override
    public int hashCode() {
        return this.name.hashCode() + this.blockType.hashCode();
    }

    @Override
    public String toString() {
        return this.blockType.toString() + ":" + this.getName();
    }

    @Override
    public int compareTo(AbstractBlock otherBlock) {
        return this.index - otherBlock.index;
    }
}
