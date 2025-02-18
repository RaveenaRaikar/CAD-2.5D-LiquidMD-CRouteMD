package route.circuit.block;

import route.circuit.architecture.BlockType;
import route.circuit.architecture.PortType;
import route.circuit.pin.LeafPin;

public class LeafBlock extends LocalBlock {

    private GlobalBlock globalParent;
    private boolean isWire = false;


    public LeafBlock(String name, BlockType type, int index, AbstractBlock parent, GlobalBlock globalParent) {
        super(name, type, index, parent);

        this.globalParent = globalParent;
    }
    
    public LeafBlock(String name, BlockType type, int index, AbstractBlock parent, GlobalBlock globalParent, Boolean isWire) {
        super(name, type, index, parent);

        this.globalParent = globalParent;
        this.isWire = true;
    }

    public boolean isWire() {
    	return this.isWire;
    }
    @Override
    protected LeafPin createPin(PortType portType, int index) {
        return new LeafPin(this, portType, index);
    }

    public GlobalBlock getGlobalParent() {
        return this.globalParent;
    }
}
