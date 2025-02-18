package place.placers.random;

import place.circuit.Circuit;
import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.block.AbstractBlock;
import place.circuit.block.AbstractSite;
import place.circuit.block.GlobalBlock;
import place.circuit.block.Macro;
import place.circuit.exceptions.FullSiteException;
import place.circuit.exceptions.PlacedBlockException;
import place.circuit.exceptions.PlacementException;
import place.circuit.timing.TimingGraphSLL;
import place.interfaces.Logger;
import place.interfaces.Options;
import place.placers.Placer;
import place.visual.PlacementVisualizer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class RandomPlacer extends Placer {

    @SuppressWarnings("unused")
    public static void initOptions(Options options) {
    }

    public RandomPlacer(Circuit[] circuitDie, Options options, Random random, Logger logger, 
    		PlacementVisualizer[] visualizer, int TotalDies, int SLLrows, TimingGraphSLL timingGraphSLL) {
        super(circuitDie, options, random ,logger, visualizer, TotalDies, SLLrows, timingGraphSLL);
        
    }

    @Override
    public String getName() {
        return "Random placer";
    }

    @Override
    public void initializeData() {
        // Do nothing
    }

    @Override
    protected void addStatTitles(List<String> titles) {
        // Do nothing
    }

    @Override
    protected void doPlacement() throws PlacementException {
    	
    	
    	int dieCounter = 0;
    	String randomPlace = "Random Placement";
    	this.startSystemTimer(randomPlace);

    	while(dieCounter < this.TotalDies) {
            Map<BlockType, List<AbstractSite>> sites = new HashMap<>();
            Map<BlockType, List<AbstractSite>> virtualSites = new HashMap<>();
            Map<BlockType, Integer> nextSiteIndexes = new HashMap<>();
            Map<BlockType, Integer> nextVirtualSiteIndexes = new HashMap<>();
            BlockType SllType = BlockType.getBlockTypes(BlockCategory.SLLDUMMY).get(0);
            BlockType emptyType = BlockType.getBlockTypes(BlockCategory.EMPTY).get(0);

            for(BlockType blockType : this.circuitDie[dieCounter].getGlobalBlockTypes()) {
            	if(blockType.equals(SllType)) {
            		List<AbstractSite> typeVirtualSites = this.circuitDie[dieCounter].getVirtualSites(blockType, dieCounter);
            		Collections.shuffle(typeVirtualSites, this.random);
            		virtualSites.put(blockType, typeVirtualSites);
            		nextVirtualSiteIndexes.put(blockType, 0);
            	}else if(!blockType.equals(emptyType)){
                    List<AbstractSite> typeSites = this.circuitDie[dieCounter].getSites(blockType, dieCounter);
                    typeSites.removeIf(Objects::isNull);
                    Collections.shuffle(typeSites, this.random);
                    sites.put(blockType, typeSites);
                    nextSiteIndexes.put(blockType, 0);
            	}

            }
            for(Macro macro : this.circuitDie[dieCounter].getMacros()) {
            	if(macro.getBlock(0).getSite() == null){
                    BlockType blockType = macro.getBlock(0).getType();
                    List<AbstractSite> typeSites = sites.get(blockType);
                    int nextSiteIndex = nextSiteIndexes.get(blockType);

                    nextSiteIndex += this.placeMacro(macro, blockType, typeSites, nextSiteIndex,dieCounter);
                    nextSiteIndexes.put(blockType, nextSiteIndex);
            	}
            }
            
            for(BlockType blockType : this.circuitDie[dieCounter].getGlobalBlockTypes()) {
            	if(blockType.equals(SllType)) {
            		List<AbstractBlock> typeDummyBlocks = this.circuitDie[dieCounter].getBlocks(blockType);
                    List<AbstractSite> typeVirtualSites = virtualSites.get(blockType);
                    int nextSiteIndex = nextVirtualSiteIndexes.get(blockType);
                    nextSiteIndex += this.placeBlocks(typeDummyBlocks, typeVirtualSites, nextSiteIndex);
                    nextVirtualSiteIndexes.put(blockType, nextSiteIndex);
            	}else if(!blockType.equals(emptyType)){
                    List<AbstractBlock> typeBlocks = this.circuitDie[dieCounter].getBlocks(blockType);
                    List<AbstractSite> typeSites = sites.get(blockType);
                    int nextSiteIndex = nextSiteIndexes.get(blockType);
                    nextSiteIndex += this.placeBlocks(typeBlocks, typeSites, nextSiteIndex);
                    nextSiteIndexes.put(blockType, nextSiteIndex);
            	}

            }

            this.visualizer[dieCounter].addPlacement("Random placement die " + dieCounter);
                    
            sites.clear();
            virtualSites.clear();
            nextSiteIndexes.clear();
            nextVirtualSiteIndexes.clear();
            dieCounter++;
    	}
    	this.stopandPrintSystemTimer(randomPlace);
    }

    private int placeMacro(Macro macro, BlockType blockType, List<AbstractSite> sites, int siteIndex, int dieNumber) throws PlacedBlockException, FullSiteException {

        int blockSpace = macro.getBlockSpace();
        int numBlocks = macro.getNumBlocks();
        int numSites = sites.size();
        
        int nextSiteIndex = 1;
        while(true) {
            AbstractSite firstSite = sites.get(siteIndex);
            int column = firstSite.getColumn();
            int firstRow = firstSite.getRow();
            int lastRow = firstRow + numBlocks * blockSpace;
            if(lastRow >= this.circuitDie[dieNumber].getHeight() - 2) {
                firstSite = sites.get(siteIndex + 1);
                column  = firstSite.getColumn();
                firstRow = firstSite.getRow();
                lastRow = firstRow + numBlocks * blockSpace;
                nextSiteIndex = nextSiteIndex + 1;
            }

            boolean free = true;
            for(int row = firstRow; row <= lastRow; row += blockSpace) {
                if(illegalSite(blockType, column, row, dieNumber)) {
                    free = false;
                    break;
                }
            }

            if(free) {
                for(int index = 0; index < numBlocks; index++) {
                    int row = firstRow + blockSpace * index;

                    AbstractSite site = this.circuitDie[dieNumber].getSite(dieNumber, column, row);
                    macro.getBlock(index).setSite(site);
                }

                return nextSiteIndex;
            } else {
                int randomIndex = this.random.nextInt(numSites - siteIndex - 1) + siteIndex + 1;
                AbstractSite tmp = sites.get(siteIndex);
                sites.set(siteIndex, sites.get(randomIndex));
                sites.set(randomIndex, tmp);
            }
        }
    }

    private boolean illegalSite(BlockType blockType, int column, int row, int dieNumber) {
        if(row >= this.circuitDie[dieNumber].getHeight() + 2) {
            return true;
        }
        if(column >= this.circuitDie[dieNumber].getWidth() + 2) {
            return true;
        }

        AbstractSite site = this.circuitDie[dieNumber].getSite(dieNumber, column, row);
        if(site == null || !site.getType().equals(blockType) || site.isFull()) {
            return true;
        }

        return false;
    }

    private int placeBlocks(List<AbstractBlock> blocks, List<AbstractSite> sites, int nextSiteIndex) throws PlacedBlockException, FullSiteException {

        int placedBlocks = 0;
        int siteIndex = nextSiteIndex;
        BlockType SllType = BlockType.getBlockTypes(BlockCategory.SLLDUMMY).get(0);

        for(AbstractBlock abstractBlock : blocks) {
            GlobalBlock block = (GlobalBlock) abstractBlock;

            if(!block.isInMacro()) {
            	{
                	if(block.getSite() == null){

                        AbstractSite site;
                        
                        do {
                            site = sites.get(siteIndex);
                            siteIndex++;
                        } while(site.isFull());

                        placedBlocks += 1;
                        block.setSite(site);
                	}
            	}

            }
        }

        return placedBlocks;
    }
}
