package place.circuit.block;

import java.util.ArrayList;
import java.util.List;

import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.architecture.PortType;
import place.circuit.exceptions.FullSiteException;
import place.circuit.exceptions.InvalidBlockException;
import place.circuit.exceptions.PlacedBlockException;
import place.circuit.exceptions.UnplacedBlockException;
import place.circuit.pin.GlobalPin;
import place.circuit.timing.TimingNode;
import place.hierarchy.LeafNode;
import place.placers.analytical.AnalyticalAndGradientPlacer.NetBlock;

public class GlobalBlock extends AbstractBlock {

    private AbstractSite site;
    private ArrayList<TimingNode> timingNodes = new ArrayList<TimingNode>();

    private Macro macro;
    private int macroOffsetY = 0;
    
    //TO MARK AS SLL BLOCK
    private boolean SLLblock;
    private LeafNode leafNode;   
    
    public boolean isSLLSource = false;
    public boolean isSLLSink = false;
    
    public NetBlock netBlock;
    //Source -- source --SLL -- sink --Sink

    public GlobalBlock(String name, BlockType type, int index , Boolean SLLblock) {
        super(name, type, index, SLLblock);

        this.leafNode = null;
    }

    //Leaf node
    public void setLeafNode(LeafNode hierarchyNode){
    	this.leafNode = hierarchyNode;
    }
    public LeafNode getLeafNode(){
    	return this.leafNode;
    }
    public boolean hasLeafNode(){
    	return !(this.leafNode == null);
    }

    
    public void setSLLStatus(Boolean isSLL) {
    	this.SLLblock = isSLL;
    }
    
    public boolean getSLLStatus() {
    	return this.SLLblock;
    }
    @Override
    public void compact() {
        super.compact();
        this.timingNodes.trimToSize();
    }

    public AbstractSite getSite() {
        return this.site;
    }

    public int getColumn() {
        return this.site.getColumn();
    }
    public int getRow() {
        return this.site.getRow();
    }

    public int getDie() {
    	return this.site.getdie();
    }
    public boolean hasCarry() {
        return this.blockType.getCarryFromPort() != null;
    }
    public GlobalPin getCarryIn() {
        return (GlobalPin) this.getPin(this.blockType.getCarryToPort(), 0);
    }
    public GlobalPin getCarryOut() {
        return (GlobalPin) this.getPin(this.blockType.getCarryFromPort(), 0);
    }

    public void setMacro(Macro macro, int offsetY) {
        this.macro = macro;
        this.macroOffsetY = offsetY;
    }
    public Macro getMacro() {
        return this.macro;
    }
    public int getMacroOffsetY() {
        return this.macroOffsetY;
    }
    public boolean isInMacro() {
        return this.macro != null;
    }

    public void removeSite() throws UnplacedBlockException, InvalidBlockException {
        if(this.site == null) {
            throw new UnplacedBlockException();
        }

        this.site.removeBlock(this);
        this.site = null;
    }
    public void setSite(AbstractSite site) throws PlacedBlockException, FullSiteException {
        if(this.site != null) {
        	//Get the type of block at this place
        	if(!this.getBlockStatus(site)) {
        		throw new PlacedBlockException();
        	}
        }
        this.site = site;
        this.site.addBlock(this);
    }


    private boolean getBlockStatus(AbstractSite site) {
    	boolean blockstatus = false;
		Site Absite = (Site) site;
		GlobalBlock block = Absite.getBlock();
		if(block.getCategory() == BlockCategory.SLLDUMMY) {
			blockstatus = true;
		}
    	return blockstatus;
    }
    public void addTimingNode(TimingNode node) {
        this.timingNodes.add(node);
    }
    public List<TimingNode> getTimingNodes() {
        return this.timingNodes;
    }

    @Override
    public AbstractBlock getParent() {
        return null;
    }

    @Override
	public GlobalPin createPin(PortType portType, int index) {
        return new GlobalPin(this, portType, index);
    }
}
