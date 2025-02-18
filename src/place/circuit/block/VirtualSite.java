package place.circuit.block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.exceptions.FullSiteException;
import place.circuit.exceptions.InvalidBlockException;
//The Virtual site is used to place the dummy Anchor blocks.
public class VirtualSite extends AbstractSite  {
	private GlobalBlock dummyBlock;

	public VirtualSite(int die, int column, int row, BlockType blockType) {
		super(die, column, row, blockType);
	}
    public GlobalBlock getDummyBlock() {
        return this.dummyBlock;
    }
    @Override
    public GlobalBlock getRandomBlock(Random random) {
        return this.dummyBlock;
    }
    
    @Override
    void addBlock(GlobalBlock block) throws FullSiteException {
    	if(this.isFull()) { //The site has a dummy block placed, hence normal block can be placed.
        	System.out.print("\n Dummy is full");
        	if(block.getCategory() == BlockCategory.SLLDUMMY) {
        		throw new FullSiteException();
        	}
        }
        	this.dummyBlock = block;
        
    }
    public boolean isFull() {
    	return this.dummyBlock != null;
    }
    public void clear() {
        this.dummyBlock = null;
    }
    
    @Override
    public void removeBlock(GlobalBlock block) throws InvalidBlockException {
        if(block != this.dummyBlock) {
            throw new InvalidBlockException();
        }
        this.dummyBlock = null;
    }
    
    @Override
    public Collection<GlobalBlock> getBlocks() {
        if(this.dummyBlock == null) {
            return new ArrayList<GlobalBlock>();
        } else {
            return Arrays.asList(this.dummyBlock);
        }
    }
}
