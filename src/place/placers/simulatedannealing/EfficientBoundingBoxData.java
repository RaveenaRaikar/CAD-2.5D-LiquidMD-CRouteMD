package place.placers.simulatedannealing;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import place.circuit.block.AbstractSite;
import place.circuit.block.GlobalBlock;
import place.circuit.pin.GlobalPin;

class EfficientBoundingBoxData {

    private double weight;
    private GlobalBlock[] blocks;
    private boolean alreadySaved;
    private int SLLrowCount;
    
    private int archRows;
    private int archCols;
    private int width;
    private int height;

    private int min_x;
    private int nb_min_x;
    private int max_x;
    private int nb_max_x;
    private int min_y;
    private int nb_min_y;
    private int max_y;
    private int nb_max_y;
    private int boundingBox;

    private int min_x_old;
    private int nb_min_x_old;
    private int max_x_old;
    private int nb_max_x_old;
    private int min_y_old;
    private int nb_min_y_old;
    private int max_y_old;
    private int nb_max_y_old;
    private int boundingBox_old;


    public EfficientBoundingBoxData(GlobalPin pin, int archCols, int archrows) {
        Set<GlobalBlock> blockSet = new HashSet<GlobalBlock>();
    	this.archRows = archrows;
    	this.archCols = archCols;
        blockSet.add(pin.getOwner());

        int numSinks = pin.getNumSinks();
        for(int i = 0; i < numSinks; i++) {
            blockSet.add(pin.getSink(i).getOwner());
        }

        this.blocks = new GlobalBlock[blockSet.size()];
        blockSet.toArray(this.blocks);
        this.setWeightandSize();

        this.boundingBox = -1;
        this.min_x = Integer.MAX_VALUE;
        this.min_y = -1;
        this.max_x = Integer.MAX_VALUE;
        this.max_y = -1;

        this.calculateBoundingBoxFromScratch(false);
        this.alreadySaved = false;
    }
    public EfficientBoundingBoxData(GlobalBlock [] SLLDummypair, int SLLrowCount, int archrows, int archCols, int width, int height) {
    	
    	this.blocks = SLLDummypair;
    	this.SLLrowCount = SLLrowCount;
    	this.archRows = archrows;
    	this.archCols = archCols;
    	
    	this.width = width;
    	this.height = height;
	this.setWeightandSize();

        this.boundingBox = -1;
        this.min_x = Integer.MAX_VALUE;
        this.min_y = -1;
        this.max_x = Integer.MAX_VALUE;
        this.max_y = -1;

        this.calculateBoundingBoxFromScratch(true);
        this.alreadySaved = false;
    }



    public double calculateDeltaCost(GlobalBlock block, AbstractSite newSite) {
        double originalBB = this.boundingBox;

        if((block.getColumn() == this.min_x && this.nb_min_x == 1 && newSite.getColumn() > this.min_x)
                || (block.getColumn() == this.max_x && this.nb_max_x == 1 && newSite.getColumn() < this.max_x)
                || (block.getRow() == this.min_y && this.nb_min_y == 1 && newSite.getRow() > this.min_y)
                || (block.getRow() == this.max_y && this.nb_max_y == 1 && newSite.getRow() < this.max_y)) {

            calculateBoundingBoxFromScratch(block, newSite, false);

        } else {
            if(newSite.getColumn() < this.min_x) {
                this.min_x = newSite.getColumn();
                this.nb_min_x = 1;
            } else if(newSite.getColumn() == this.min_x && block.getColumn() != this.min_x) {
                this.nb_min_x++;
            } else if(newSite.getColumn() > this.min_x && block.getColumn() == this.min_x) {
                this.nb_min_x--;
            }

            if(newSite.getColumn() > this.max_x) {
                this.max_x = newSite.getColumn();
                this.nb_max_x = 1;
            } else if(newSite.getColumn() == this.max_x && block.getColumn() != this.max_x) {
                this.nb_max_x++;
            } else if(newSite.getColumn() < this.max_x && block.getColumn() == this.max_x) {
                this.nb_max_x--;
            }

            if(newSite.getRow() < this.min_y) {
                this.min_y = newSite.getRow();
                this.nb_min_y = 1;
            } else if(newSite.getRow() == this.min_y && block.getRow() != this.min_y) {
                this.nb_min_y++;
            } else if(newSite.getRow() > this.min_y && block.getRow() == this.min_y) {
                this.nb_min_y--;
            }

            if(newSite.getRow() > this.max_y) {
                this.max_y = newSite.getRow();
                this.nb_max_y = 1;
            } else if(newSite.getRow() == this.max_y && block.getRow() != this.max_y) {
                this.nb_max_y++;
            } else if(newSite.getRow() < this.max_y && block.getRow() == this.max_y) {
                this.nb_max_y--;
            }
        }

        this.boundingBox = (this.max_x - this.min_x + 1) + (this.max_y - this.min_y + 1);

        return this.weight * (this.boundingBox - originalBB);
    }


    public void pushThrough() {
        this.alreadySaved = false;
    }


    public void revert() {
        this.boundingBox = this.boundingBox_old;

        this.min_x = this.min_x_old;
        this.nb_min_x = this.nb_min_x_old;

        this.max_x = this.max_x_old;
        this.nb_max_x = this.nb_max_x_old;

        this.min_y = this.min_y_old;
        this.nb_min_y = this.nb_min_y_old;

        this.max_y = this.max_y_old;
        this.nb_max_y = this.nb_max_y_old;

        this.alreadySaved = false;
    }


    public void saveState() {
        if(!this.alreadySaved) {
            this.min_x_old = this.min_x;
            this.nb_min_x_old = this.nb_min_x;

            this.max_x_old = this.max_x;
            this.nb_max_x_old = this.nb_max_x;

            this.min_y_old = this.min_y;
            this.nb_min_y_old = this.nb_min_y;

            this.max_y_old = this.max_y;
            this.nb_max_y_old = this.nb_max_y;

            this.boundingBox_old = this.boundingBox;
            this.alreadySaved = true;
        }
    }


    public double getNetCost() {
        return this.boundingBox * this.weight;
    }
    
    public double getFanoutWeightedNetCost() {
        return this.boundingBox * this.weight / this.blocks.length;
    }


    public void calculateBoundingBoxFromScratch(boolean forSLL)  {
        this.calculateBoundingBoxFromScratch(null, null, forSLL);
    }

    private int computeDieSpan(Set<Integer> dies, int archRows, int archCols) {
        List<Integer> dieList = new ArrayList<>(dies);
        int minRow = Integer.MAX_VALUE, maxRow = Integer.MIN_VALUE;
        int minCol = Integer.MAX_VALUE, maxCol = Integer.MIN_VALUE;
        
        for (int dieId : dieList) {
            int row = dieId / archCols;
            int col = dieId % archCols;
            minRow = Math.min(minRow, row);
            maxRow = Math.max(maxRow, row);
            minCol = Math.min(minCol, col);
            maxCol = Math.max(maxCol, col);
        }

        int rowSpan = maxRow - minRow + 1;
        int colSpan = maxCol - minCol + 1;
        
        // You could weigh these differently if vertical/diagonal hops are more costly
        return Math.max(rowSpan, colSpan);  // or rowSpan + colSpan
    }

    
    public void calculateBoundingBoxFromScratch(GlobalBlock block, AbstractSite alternativeSite, boolean forSLL)  {
        this.min_x = Integer.MAX_VALUE;
        this.max_x = -1;
        this.min_y = Integer.MAX_VALUE;
        this.max_y = -1;

        AbstractSite site;

        
        //switch between 3 cases:
        //case 1: 2x1
        //case 2: 4x1
        //case 3: 2x2
              
        switch(this.archCols) {
        case 1:
        	if(this.archRows == 2) {
                for(int i = 0; i < this.blocks.length; i++) {
                    if(this.blocks[i] == block) {
                        site = alternativeSite;
                    } else {
                        site = this.blocks[i].getSite();
                    }

                    if(site.getColumn() < this.min_x) {
                        this.min_x = site.getColumn();
                        this.nb_min_x = 1;
                    } else if(site.getColumn() == this.min_x){
                        this.nb_min_x++;
                    }

                    if(site.getColumn() > this.max_x) {
                        this.max_x = site.getColumn();
                        this.nb_max_x = 1;
                    } else if(site.getColumn() == this.max_x) {
                        this.nb_max_x++;
                    }

                    if(site.getRow() < this.min_y) {
                        this.min_y = site.getRow();
                        this.nb_min_y = 1;
                    } else if(site.getRow() == this.min_y) {
                        this.nb_min_y++;
                    }

                    if(site.getRow() > this.max_y) {
                        this.max_y = site.getRow();
                        this.nb_max_y = 1;
                    } else if(site.getRow() == this.max_y) {
                        this.nb_max_y++;
                    }
                }

                if(forSLL) {
                	this.boundingBox = (this.max_x - this.min_x + 1) + this.SLLrowCount ;
                	
                }else {
                	this.boundingBox = (this.max_x - this.min_x + 1) + (this.max_y - this.min_y + 1);
                }
        		
        	}else if(this.archRows == 4) {
                int minDie = Integer.MAX_VALUE;
                int maxDie = -1;
        		for(int i = 0; i < this.blocks.length; i++) {
                    if(this.blocks[i] == block) {
                        site = alternativeSite;
                    } else {
                        site = this.blocks[i].getSite();
                    }
                    
                    int dieID = site.getdie(); // Assuming your site knows which die it belongs to
//                    System.out.print("\nBlock name " + this.blocks[i].getName() + " on die " + dieID + " at site " + site);
                    if(minDie > dieID) {
                    	minDie = dieID;
                    }
                    
                    if(maxDie < dieID) {
                    	maxDie = dieID;
                    }

                    if(site.getColumn() < this.min_x) {
                        this.min_x = site.getColumn();
                        this.nb_min_x = 1;
                    } else if(site.getColumn() == this.min_x){
                        this.nb_min_x++;
                    }

                    if(site.getColumn() > this.max_x) {
                        this.max_x = site.getColumn();
                        this.nb_max_x = 1;
                    } else if(site.getColumn() == this.max_x) {
                        this.nb_max_x++;
                    }
                    
                    if(forSLL) {
                        if(dieID == minDie) {
                        	this.min_y = site.getRow();
                            this.nb_min_y = 1;
                        	if(site.getRow() == this.min_y) {
                                this.nb_min_y++;
                            }
                        	
                        }

                        if(dieID == maxDie) {
                            this.max_y = site.getRow();
                            this.nb_max_y = 1;
                        	if(site.getRow() == this.max_y) {
                                this.nb_max_y++;
                            }

                        }
                    }else {
                    	if(site.getRow() < this.min_y) {
                            this.min_y = site.getRow();
                            this.nb_min_y = 1;
                        } else if(site.getRow() == this.min_y) {
                            this.nb_min_y++;
                        }
                    	if(site.getRow() > this.max_y) {
                            this.max_y = site.getRow();
                            this.nb_max_y = 1;
                        } else if(site.getRow() == this.max_y) {
                            this.nb_max_y++;
                        }
                    	                   
                    }
        		}
                if (forSLL) {
                	int spannedDies = maxDie - minDie;
                	
//                	System.out.print("\n This.maxx " + this.max_x + " spannedDies " + maxDie + " min x " + this.min_x + " min y" + this.min_y + " max y " + this.max_y);
                	if(spannedDies == 1) {
                    	this.boundingBox = this.SLLrowCount + (this.max_x - this.min_x + 1);
                    }else {
//                    	System.out.print("\n The first part is " + (((maxDie) * this.height) + this.max_y) + " second part is " + (((minDie) * this.height) + this.min_y));
                    	this.boundingBox = (this.max_x - this.min_x + 1) +((((maxDie) * this.height) + this.max_y)  - (((minDie) * this.height) + this.min_y) + 1) ;
                    }
                	
//                	System.out.print("\nThe BB is " + this.boundingBox);
                    
                } else {
                    this.boundingBox = (this.max_x - this.min_x + 1) + (this.max_y - this.min_y + 1);
                }
        	}else {
        		System.out.print("\nThis configuration is not supported yet");
        	}
        	break;
        case 2:
        	if(this.archRows == 2) {

    			Set<Integer> dieIDs = new HashSet<>();

    			List<GlobalBlock> preferredMinXBlocks = new ArrayList<>();
    			List<GlobalBlock> preferredMaxXBlocks = new ArrayList<>();
    			List<GlobalBlock> preferredMinYBlocks = new ArrayList<>();
    			List<GlobalBlock> preferredMaxYBlocks = new ArrayList<>();

        			// First pass: collect blocks per preferred dies
    			for (int i = 0; i < this.blocks.length; i++) {
    				

				    if (this.blocks[i] == block) {
				        site = alternativeSite;
				    } else {
				        site = this.blocks[i].getSite();
				    }

    			    int dieID = site.getdie();
    			    dieIDs.add(dieID);
//    			    System.out.print("\nBlock name " + this.blocks[i].getName() + " on die " + dieID + " at site " + site);
    			    // For min_x (prefer die 0 or 2)
    			    if (dieID == 0 || dieID == 2) {
    			        preferredMinXBlocks.add(this.blocks[i]);
    			    }

    			    // For max_x (prefer die 1 or 3)
    			    if (dieID == 1 || dieID == 3) {
    			        preferredMaxXBlocks.add(this.blocks[i]);
    			    }

    			    // For min_y (prefer die 0 or 1)
    			    if (dieID == 0 || dieID == 1) {
    			        preferredMinYBlocks.add(this.blocks[i]);
    			    }

    			    // For max_y (prefer die 2 or 3)
    			    if (dieID == 2 || dieID == 3) {
    			        preferredMaxYBlocks.add(this.blocks[i]);
    			    }
    			}

    			// If preferred dies are empty, fall back to all blocks
    			if (preferredMinXBlocks.isEmpty()) preferredMinXBlocks = Arrays.asList(this.blocks);
    			if (preferredMaxXBlocks.isEmpty()) preferredMaxXBlocks = Arrays.asList(this.blocks);
    			if (preferredMinYBlocks.isEmpty()) preferredMinYBlocks = Arrays.asList(this.blocks);
    			if (preferredMaxYBlocks.isEmpty()) preferredMaxYBlocks = Arrays.asList(this.blocks);

    			// Now compute min_x
    			this.min_x = Integer.MAX_VALUE;
    			this.nb_min_x = 0;
    			for (GlobalBlock b : preferredMinXBlocks) {
    				AbstractSite s = (b == block) ? alternativeSite : b.getSite();
    			    int x = s.getColumn();
    			    if (x < this.min_x) {
    			        this.min_x = x;
    			        this.nb_min_x = 1;
    			    } else if (x == this.min_x) {
    			        this.nb_min_x++;
    			    }
				}

    			// Similarly compute max_x
    			this.max_x = Integer.MIN_VALUE;
    			this.nb_max_x = 0;
    			for (GlobalBlock b : preferredMaxXBlocks) {
    				AbstractSite s = (b == block) ? alternativeSite : b.getSite();
    			    int x = s.getColumn();
    			    if (x > this.max_x) {
    			        this.max_x = x;
    			        this.nb_max_x = 1;
    			    } else if (x == this.max_x) {
    			        this.nb_max_x++;
    			    }
    			}

    			// Compute min_y
    			this.min_y = Integer.MAX_VALUE;
    			this.nb_min_y = 0;
    			for (GlobalBlock b : preferredMinYBlocks) {
    				AbstractSite s = (b == block) ? alternativeSite : b.getSite();
    			    int y = s.getRow();
    			    if (y < this.min_y) {
    			        this.min_y = y;
    			        this.nb_min_y = 1;
    			    } else if (y == this.min_y) {
    			        this.nb_min_y++;
    			    }
    			}

    			// Compute max_y
    			this.max_y = Integer.MIN_VALUE;
    			this.nb_max_y = 0;
    			for (GlobalBlock b : preferredMaxYBlocks) {
    				AbstractSite s = (b == block) ? alternativeSite : b.getSite();
    			    int y = s.getRow();
    			    if (y > this.max_y) {
    			        this.max_y = y;
    			        this.nb_max_y = 1;
    			    } else if (y == this.max_y) {
    			        this.nb_max_y++;
    			    }
    			}

                
                if (forSLL) {
                	boolean row0 = dieIDs.contains(0) || dieIDs.contains(1);
                	boolean row1 = dieIDs.contains(2) || dieIDs.contains(3);
                	boolean bothRowsActive = row0 && row1;
                	int dieYOffset = 0;
                	
                	if(bothRowsActive) {
                		dieYOffset = this.height;
                	}

                	boolean col0 = dieIDs.contains(0) || dieIDs.contains(2);
                	boolean col1 = dieIDs.contains(1) || dieIDs.contains(3);
                	boolean bothColsActive = col0 && col1;
                	int dieXOffset = 0;
                	
                	if(bothColsActive) {
                		dieXOffset = this.width;
                	}
//                	System.out.print("\n This.maxx " + this.max_x + " diexoffset " + dieXOffset + " min x " + this.min_x + " min y" + this.min_y + " max y " + this.max_y + " dieyoffset " + dieYOffset);
//                	this.boundingBox = this.max_x + this.min_x + 1 + this.max_y + this.min_y + 1;
                	this.boundingBox = (dieXOffset + this.max_x) - this.min_x + 1 + (dieYOffset + this.max_y) - this.min_y + 1;
//                	this.boundingBox = (this.max_x - (dieXOffset - this.min_x) + 1) + (this.max_y - (dieYOffset - this.min_y) + 1) ;
                	
                    
                } else {
                    this.boundingBox = (this.max_x - this.min_x + 1) + (this.max_y - this.min_y + 1);
                }
                
//                System.out.print("\nThe BB is " + this.boundingBox);
        	}else {
        		System.out.print("\nThis configuration is not supported yet now");
        	}
        	break;
        default:
        	System.out.print("\nThis configuration is not supported yet here");
        }
        
    }


    private void setWeightandSize() {
        int size = this.blocks.length;
        switch (size)  {
            case 1:  this.weight = 1; break;
            case 2:  this.weight = 1; break;
            case 3:  this.weight = 1; break;
            case 4:  this.weight = 1.0828; break;
            case 5:  this.weight = 1.1536; break;
            case 6:  this.weight = 1.2206; break;
            case 7:  this.weight = 1.2823; break;
            case 8:  this.weight = 1.3385; break;
            case 9:  this.weight = 1.3991; break;
            case 10: this.weight = 1.4493; break;
            case 11:
            case 12:
            case 13:
            case 14:
            case 15: this.weight = (size-10) * (1.6899-1.4493) / 5 + 1.4493; break;
            case 16:
            case 17:
            case 18:
            case 19:
            case 20: this.weight = (size-15) * (1.8924-1.6899) / 5 + 1.6899; break;
            case 21:
            case 22:
            case 23:
            case 24:
            case 25: this.weight = (size-20) * (2.0743-1.8924) / 5 + 1.8924; break;
            case 26:
            case 27:
            case 28:
            case 29:
            case 30: this.weight = (size-25) * (2.2334-2.0743) / 5 + 2.0743; break;
            case 31:
            case 32:
            case 33:
            case 34:
            case 35: this.weight = (size-30) * (2.3895-2.2334) / 5 + 2.2334; break;
            case 36:
            case 37:
            case 38:
            case 39:
            case 40: this.weight = (size-35) * (2.5356-2.3895) / 5 + 2.3895; break;
            case 41:
            case 42:
            case 43:
            case 44:
            case 45: this.weight = (size-40) * (2.6625-2.5356) / 5 + 2.5356; break;
            case 46:
            case 47:
            case 48:
            case 49:
            case 50: this.weight = (size-45) * (2.7933-2.6625) / 5 + 2.6625; break;
            default: this.weight = (size-50) * 0.02616 + 2.7933; break;
        }

        this.weight *= 0.01;
    }

}
