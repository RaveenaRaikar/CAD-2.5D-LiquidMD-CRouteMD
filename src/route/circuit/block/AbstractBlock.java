package route.circuit.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import route.circuit.architecture.BlockCategory;
import route.circuit.architecture.BlockType;
import route.circuit.architecture.PortType;
import route.circuit.pin.AbstractPin;



public abstract class AbstractBlock implements Comparable<AbstractBlock> {

    private String name;
    protected BlockType blockType;
    private BlockCategory category;
    private int index;
    private boolean clocked;
    private boolean isSLLSource = false;
    private boolean isSLLSink = false;
    private List<LocalBlock> children;
    private List<AbstractPin> pins;

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



    public abstract AbstractBlock getParent();
    protected abstract AbstractPin createPin(PortType portType, int index);

    public void compact() {
        for(AbstractPin pin : this.pins) {
            pin.compact();
        }
    }

    public void setSLLSourceBlock() {
    	this.isSLLSource = true;
    }

    public void setSLLSinkBlock() {
    	this.isSLLSink = true;
    }
    
    
    public Boolean getSLLSourceBlock() {
    	return this.isSLLSource;
    }
    
    public Boolean getSLLSinkBlock() {
    	return this.isSLLSink;
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
    public int getIndex() {
        return this.index;
    }

    public boolean isGlobal() {
        return this.getType().isGlobal();
    }
    public boolean isLeaf() {
        return this.getType().isLeaf();
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
