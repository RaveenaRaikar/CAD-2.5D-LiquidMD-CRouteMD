package place.circuit.block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.exceptions.FullSiteException;
import place.circuit.exceptions.InvalidBlockException;

public class Site extends AbstractSite {

    private GlobalBlock block;
    public Site(int die, int x, int y, BlockType blockType) {
        super(die, x, y, blockType);
    }

    public GlobalBlock getBlock() {
        return this.block;
    }


    @Override
    public GlobalBlock getRandomBlock(Random random) {
        return this.block;
    }

    @Override
    void addBlock(GlobalBlock block) throws FullSiteException {
        if(this.isFull()) {
        		throw new FullSiteException();  
        	}

        	this.block = block;
        
    }

    @Override
    public void removeBlock(GlobalBlock block) throws InvalidBlockException {
        if(block != this.block) {
            throw new InvalidBlockException();
        }
        this.block = null;
    }

    @Override
    public void clear() {
        this.block = null;
    }

    @Override
    public boolean isFull() {
        return this.block != null;
    }

    @Override
    public Collection<GlobalBlock> getBlocks() {
        if(this.block == null) {
            return new ArrayList<GlobalBlock>();
        } else {
            return Arrays.asList(this.block);
        }
    }
}
