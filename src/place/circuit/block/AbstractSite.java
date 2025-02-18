package place.circuit.block;

import java.util.Collection;
import java.util.Random;

import place.circuit.architecture.BlockType;
import place.circuit.exceptions.FullSiteException;
import place.circuit.exceptions.InvalidBlockException;



public abstract class AbstractSite {

    private int column, row, die;
    private BlockType blockType;
    private boolean isVirtual = false;

    public AbstractSite(int die, int column, int row, BlockType blockType) {
        this.column = column;
        this.row = row;
        this.die = die;
        this.blockType = blockType;
    }
   

    public int getColumn() {
        return this.column;
    }
    public int getRow() {
        return this.row;
    }
    public int getdie() {
        return this.die;
    }
    public BlockType getType() {
        return this.blockType;
    }

    public void siteIsVirtual() {
    	this.isVirtual = true;
    }
    public boolean isVirtual() {
    	return this.isVirtual;
    }
    public abstract GlobalBlock getRandomBlock(Random random);
    abstract void addBlock(GlobalBlock block) throws FullSiteException;
    public abstract void removeBlock(GlobalBlock block) throws InvalidBlockException;
    public abstract void clear();
    public abstract boolean isFull();

    public abstract Collection<GlobalBlock> getBlocks();


    @Override
    public int hashCode() {
        // 8191 is a prime number that is larger than any row index can possibly be,
        // plus it's a Mersenne prime which allows for bitwise optimization
        return 8191 * this.column + this.row;
    }

    @Override
    public String toString() {
        return "[" + this.die + ", " + this.column + ", " + this.row + "]";
    }
}
