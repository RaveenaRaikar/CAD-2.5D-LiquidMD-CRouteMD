package place.placers.analytical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import place.circuit.block.GlobalBlock;
import place.circuit.block.SLLNetBlocks;
import place.placers.analytical.AnalyticalAndGradientPlacer.BlockInfo;




public class SllLegalizer {
	protected List<double[]> linearX, linearY;
    protected List<double[]> legalX, legalY;
    protected Map<String,Integer> tempLegalX, tempLegalY;
    protected Map<String, Integer> sLLNodeList;
    protected List<List<List<SLLBlock>>> blockMatrix;
    private List<SLLBlock> allSLLBlocks;
    private Map<String, SLLNetBlocks> netToBlockSLL = new HashMap<>();
    protected Map<String, List<BlockInfo>> SLLcounter;
    protected Map<String,List<Integer>> sllYlocation;
    protected Boolean[] fullSitesTempX;
    protected Integer width;
    protected Integer height;
    protected Integer SLLrows;
    protected Map<Integer,List<String>> fullSitesX; //Initial check only for the X location, if there is overfill then check Y
    protected Map<Integer,List<String>> fullSitesY;
	SllLegalizer(
			List<double[]> linearX,
			List<double[]> linearY,
			List<double[]> legalX,
			List<double[]> legalY,
			Map<String, List<BlockInfo>> SLLcounter,
			Map<String,Integer> sllNodelist,
			Integer width,
			Integer height,
			Integer sllRows,
			Map<String, SLLNetBlocks> netToBlockSLL) {
		this.linearX = linearX;
		this.linearY = linearY;
		this.legalX = legalX;
		this.legalY = legalY;
		this.SLLcounter = SLLcounter;
		this.width = width;
		this.height = height;
		this.SLLrows = sllRows;
		this.sLLNodeList = sllNodelist;
		this.netToBlockSLL = netToBlockSLL;
		//This array contains the SLL blocks which act as the sink on the Die (Datain timing node)
		
		this.tempLegalX = new HashMap<String,Integer>();
		this.tempLegalY = new HashMap<String,Integer>();
		
        this.blockMatrix = new ArrayList<List<List<SLLBlock>>>(this.width+2);
       // this.finalBlockMatrix = new ArrayList<List<List<SLLBlock>>>(this.width+2);
        for(int column = 0; column < this.width + 2; column++) {
            List<List<SLLBlock>> blockColumn = new ArrayList<>(this.SLLrows+2);
            for(int row = 0; row < this.SLLrows + 1; row++) {
                blockColumn.add(new ArrayList<SLLBlock>());
            }
            this.blockMatrix.add(blockColumn);
         //   this.finalBlockMatrix.add(blockColumn);
        }
    }
	
	protected StringBuilder legaliseSLLblock(StringBuilder localOutput) {
//		localOutput.append("\nThe Synchronisation is ongoing");
//		getBestPositions();
		calculateBBoxofNet();
		initializeSLLregion();
		checkForCongestion();
		updateLegalPosition();
		return localOutput;
	}
	
	protected void calculateBBoxofNet() {
		//Get the SLLNetBlock;
		//Get the sourceBlock, and sinkBlocks;
		//Get the corresponding netBlocks, and the Legal positions
		//Get minX,maxX and so on.

		for(String netName : this.netToBlockSLL.keySet()) {
			int dieCount = 0;
			List<Integer> blockIndexList = new ArrayList<>();
			double minX, maxX, minY, maxY = 0;
			double die0_Y = 0, die1_Y = 0;
			double currentX,currentY = 0;
			int finalX,finalY = 0;
			SLLNetBlocks sllBlock = this.netToBlockSLL.get(netName);
			GlobalBlock sourceBlock = sllBlock.sourceBlock;
			GlobalBlock dummyBlock = sllBlock.dummySource;
			dieCount = sourceBlock.getDie();
			minX = this.legalX.get(dieCount)[sllBlock.getNetBlockIndex(sourceBlock)];
			maxX = this.legalX.get(dieCount)[sllBlock.getNetBlockIndex(sourceBlock)];
			minY = this.legalY.get(dieCount)[sllBlock.getNetBlockIndex(sourceBlock)];
			maxY = this.legalY.get(dieCount)[sllBlock.getNetBlockIndex(sourceBlock)];
			
			//if die0 then maxY ; if die 1 then minY
			
			if(dieCount == 0) {
				dieCount = 1;
				die0_Y= maxY;
			}else {
				dieCount = 0;
				die1_Y = minY;
			}
			blockIndexList = sllBlock.getBlockIndexList();
			for(int blockID: blockIndexList) {
				currentX = this.legalX.get(dieCount)[blockID];
				currentY = this.legalY.get(dieCount)[blockID];
				if(currentX < minX) {
					minX = currentX;
				}
				if(currentX >= maxX) {
					maxX = currentX;
				}
				if(currentY < minY) {
					minY = currentY;
				}
				if(currentY >= maxY) {
					maxY = currentY;
				}
				if(dieCount == 1) {
					die1_Y = minY;
				}else {
					die0_Y= maxY;
				}
			}
			
			//Ideal coordinates:
			finalX = this.getBestXPosition(minX, maxX);
			finalY = (int) this.getYCloseToSLLRows(die0_Y, die1_Y);
//			System.out.print("\nThe block added is " + dummyBlock.getName());
//			System.out.print("min Y : " + minY + " max Y:" + maxY);
			this.tempLegalX.put(dummyBlock.getName(), finalX);
			this.tempLegalY.put(dummyBlock.getName(), finalY);
		}
		
		
	}
	protected void initializeSLLregion(){
        // Clear the block matrix
        for(int column = 0; column < this.width - 1; column++) {
            for(int row = 0; row < this.SLLrows + 1; row++) { 

                this.blockMatrix.get(column).get(row).clear();

            }
        }
        // Loop through all the blocks of the correct block type and add them to their closest position
		for(String block: this.SLLcounter.keySet())
    	{

			Map<Integer, Integer> blockcounter = new HashMap<>(); 

			int column = this.tempLegalX.get(block);
			int row = this.tempLegalY.get(block);
			
			SLLBlock newBlock = new SLLBlock(block, blockcounter.get(0), blockcounter.get(1));

			this.blockMatrix.get(column).get(row).add(newBlock);

			
    	}
	}
	protected void getBestPositions() {
		//Clear the SLL blockMatrix
		//Get the linear positions of the dummy blocks
		
		for(String block: this.SLLcounter.keySet())
    	{

			int die0_X, die1_X = 0;
			int die0_Y, die1_Y = 0;
			int avgX, avgY = 0;
			Map<Integer, Integer> blockcounter = new HashMap<>();
			
			die0_X = (int) this.linearX.get(0)[blockcounter.get(0)];
			die0_Y = (int) this.linearY.get(0)[blockcounter.get(0)];
			die1_X = (int) this.linearX.get(1)[blockcounter.get(1)];
			die1_Y = (int) this.linearY.get(1)[blockcounter.get(1)];

			boolean die1ID = false;

			avgX = this.getBestXPosition(die0_X, die1_X);
			avgY = this.getBestYPosition(die0_Y, die1_Y);

			this.tempLegalX.put(block, avgX);
			this.tempLegalY.put(block, avgY);

    	}
	}
	protected void updateLegalPosition(){

		int height = this.height;
		int SLLcounter = 0;
        for(int column = 1; column < this.width - 1; column++) {

        	for(int row = 0; row < this.SLLrows - 1; row++) { 
            	List<SLLBlock> tempList = this.blockMatrix.get(column).get(row);
            	//
            	if(tempList.size() > 1) {

            		System.out.print("\nThere is an error\n");
            		System.out.print("\n The column is " + column + " and the row is " + row);
            		System.out.print("\nThe size of the matrix is " + this.blockMatrix.get(column).get(row).size());
            	}else if(tempList.size() == 1) {

                	SLLBlock newBlock = tempList.get(0);


                	this.legalX.get(0)[newBlock.blockIndexDie0] = column;
        			this.legalX.get(1)[newBlock.blockIndexDie1] = column;
        			this.legalY.get(0)[newBlock.blockIndexDie0] = height - this.SLLrows + 1 + row;
        			this.legalY.get(1)[newBlock.blockIndexDie1] = row;
        			
                	this.linearX.get(0)[newBlock.blockIndexDie0] = column;
        			this.linearX.get(1)[newBlock.blockIndexDie1] = column;
        			this.linearY.get(0)[newBlock.blockIndexDie0] = height - this.SLLrows + 1 + row;
        			this.linearY.get(1)[newBlock.blockIndexDie1] = row;
        			SLLcounter++;
            	}

            }
        }
	}
	protected int getBestXPosition(double die0X, double die1X) {
		int bestX = 0;
		
		bestX = (int) Math.round((die0X + die1X)/2);
		
		return bestX;
		
	}
	
	
	protected int getBestYPositionNew(double minY) {
		if(minY < this.SLLrows) {
			return (int) minY;
		}else {
			return this.SLLrows - 2;
		}
	}
	protected int getBestX(double die0X, double die1X) {
		int bestX = 0;
		
		bestX = (int) Math.round((die0X + die1X)/2);
		
		return bestX;
		
	}
	protected int getBestYPosition(double die0Y, double die1Y) {
		int bestY = 0;
		double minCost = Double.MAX_VALUE;
		int height = this.height;
		double currentCost = 0;
		for(int colCounter = 0; colCounter < this.SLLrows - 1; colCounter++) {
			currentCost = ((height - this.SLLrows + 1 + colCounter) - die0Y) * ((height - this.SLLrows + 1 + colCounter) - die0Y) + (colCounter - die1Y) *(colCounter - die1Y);
			if(currentCost<=minCost) {
				minCost = currentCost;
				bestY = colCounter;
			}
		}
		return bestY;
	}
	
	protected int getYCloseToSLLRows(double die0Y, double die1Y) {
        // Define the ranges
        int y1LowerBound = this.height - this.SLLrows;
        int y1UpperBound = this.height;
        int y2LowerBound = 0;
        int y2UpperBound = this.SLLrows;

        // Check if Y1 is within its range
        if (die0Y > y1LowerBound && die0Y <= y1UpperBound) {
            return (int) die0Y - y1LowerBound;
        }

        // Check if Y2 is within its range
        if (die1Y >= y2LowerBound && die1Y < y2UpperBound) {
            return (int) die1Y;
        }

        // Neither Y1 nor Y2 is in the range, determine which is closer
        int distanceToY1Range = (int) Math.min(Math.abs(die0Y - y1LowerBound), Math.abs(die0Y - y1UpperBound));
        int distanceToY2Range = (int) Math.min(Math.abs(die1Y - y2LowerBound), Math.abs(die1Y - y2UpperBound));

        // Return 0 if Y1 is closer to its range, or sllrows if Y2 is closer
        return (distanceToY1Range <= distanceToY2Range) ? 0 : this.SLLrows - 1;
	}
	protected double getBestYPositionNew(boolean die1ID, double die0Y, double die1Y) {
		int height = this.height;
		double bestY = 0;
		if(die1ID) {
			bestY = die1Y;
		}else {
			bestY = height - die0Y;
			if(bestY > this.SLLrows) {
				bestY = height - this.SLLrows;
			}
		}
		
		return bestY;
	}
	protected void checkForCongestion(){
        for(int column = 1; column < this.width - 1; column++) {
            for(int row = 0; row < this.SLLrows ; row++) { //Change to accept SLL variable
            	this.allSLLBlocks = this.blockMatrix.get(column).get(row);
            	//numSLLblocks = this.blockMatrix.get(column).get(row);
            	int initialBlocks = this.blockMatrix.get(column).get(row).size();
            	
            	if(initialBlocks>1) {
            		while(initialBlocks > 1) {
            			
                		SLLBlock dummyBlock = this.allSLLBlocks.get(0);
                		assignNewBlockPositions(column, row, dummyBlock);
                		this.allSLLBlocks.remove(dummyBlock);
                		initialBlocks--;
                	}
            		
            		SLLBlock FinalBlock = this.allSLLBlocks.get(0);
            		this.blockMatrix.get(column).get(row).remove(0);
            		this.blockMatrix.get(column).get(row).add(FinalBlock);
            	}

            }
        }
	}
	
	protected void assignNewBlockPositions(int column, int row, SLLBlock dummyBlock) {
		int origColumn = column;
		int origRow = row;
		boolean done = false;
		int counter = 1;

		int NewCol, NewRow = 0;
		
		while(counter < this.width - 1) {
			NewCol = origColumn;
			NewRow = origRow - counter;
			if(isValid(NewCol,NewRow) && isfree(NewCol, NewRow)) {

				this.blockMatrix.get(NewCol).get(NewRow).add(dummyBlock);
        		done = true;
        		break;
			}
			//SLLBlock dummyBlock = numSLLblocks.get(blockCounter);
			//TOP-LEFT
	 	    while(NewRow<origRow && !done) {
	 	    	NewCol = NewCol - 1;
	 	    	NewRow = NewRow + 1;
	 	    	
	 	    	if(isValid(NewCol,NewRow) && isfree(NewCol, NewRow)) {

	 	    		this.blockMatrix.get(NewCol).get(NewRow).add(dummyBlock);
	        		done = true;
	        		break;
	        	}
	 	    	
	 	    }
	 	    //BOTTOM-LEFT
	 	   while(NewCol<origColumn && !done) {
	 		  NewCol = NewCol + 1;
	 		  NewRow = NewRow + 1;
	 		  if(isValid(NewCol,NewRow) && isfree(NewCol, NewRow)) {
	 			  this.blockMatrix.get(NewCol).get(NewRow).add(dummyBlock);

	 			  done = true;
	 			  break;

	        	}
	 	    } 
	 	    while(NewRow>origRow && !done) {
	 	    	NewCol = NewCol + 1;
	 	    	NewRow = NewRow - 1;

		 	    if(isValid(NewCol,NewRow) && isfree(NewCol, NewRow)) {

		 	    	this.blockMatrix.get(NewCol).get(NewRow).add(dummyBlock);
	        		done = true;
	        		break;
	        	}
	 	    }
	 	    while(NewCol>origColumn && !done) {
	 	    	NewCol = NewCol - 1;
	 	    	NewRow = NewRow - 1;

		 	    if(isValid(NewCol,NewRow) && isfree(NewCol, NewRow)) {
		 	    	this.blockMatrix.get(NewCol).get(NewRow).add(dummyBlock);
	        		done = true;
	        		break;

	        	}
	 	    }
			if(done) {
				break;
			}
			counter++;
		}
	}
	

	
	protected void assignBlockNewPosition(int column, int row, SLLBlock dummyBlock) {

		int counter = 1;

		while(counter < (this.width - 1)) {
			if(getNeighbourPosition(column, row, counter, dummyBlock)) {

				break;
			}else if(getDiagonalPosition(column, row, counter, dummyBlock)){

				break;
			} else {
				System.out.print("\nIs it true");
			}
			counter++;
			
		}

	}
	
	protected boolean isValid(int column, int row) {
		boolean valid = false;
		if(row >=0 && (row < (this.SLLrows - 1)) && column >=1 && (column < this.width - 1)) {
			valid = true;
		}
		return valid;
	}
	
	protected boolean isfree(int column, int row){
		boolean isfree = false;
		if(this.blockMatrix.get(column).get(row).size() == 0) {
			isfree = true;
		}
		return isfree;
	}
	
	protected boolean getFreeNeighbour(int column, int row, SLLBlock dummyBlock) {
		boolean done = false;
        int dirCol[] = { 0, -1, 1, 0 };
        int dirRow[] = { -1, 0, 0, 1 };
        
        for (int k = 0; k < 4; k++)
        {
    		int newCol = column + dirCol[k];
    		int newRow = row + dirRow[k];

        	if(isValid(newCol,newRow) && isfree(newCol, newRow)) {
        		this.blockMatrix.get(newCol).get(newRow).add(dummyBlock);
        		done = true;
        		break;
        	}

        }

        while(true) {
            if(!done) {
            	for (int k = 0; k < 4; k++)
            	{
            		int newCol = column + dirCol[k];
                	int newRow = row + dirRow[k];

                    if (isValid(newCol, newRow)) {
                        done = getFreeNeighbour(newCol, newRow, dummyBlock);
                        break;
                      }
            	}
            }else {
            	break;
            }
        }

        return done;
        
	}
	
	protected boolean getNeighbourPosition( int column, int row, int counter, SLLBlock dummyBlock) {
		boolean success = false;
		if((row !=0) && ((row - counter) >= 0) && (this.blockMatrix.get(column).get(row - counter).size() == 0)){
			this.blockMatrix.get(column).get(row - counter).add(dummyBlock);
			success = true;
		}else if((column !=1) && ((column - counter) > 1) && (this.blockMatrix.get(column - counter).get(row).size() == 0)){
			this.blockMatrix.get(column - counter).get(row).add(dummyBlock);
			success = true;
		}else if((row != this.SLLrows - 1) && ((row+counter) < this.SLLrows) && this.blockMatrix.get(column).get(row + counter).size() == 0) {
			this.blockMatrix.get(column).get(row + counter).add(dummyBlock);
			success = true;
		}else if((column != this.width) && ((column + counter) < (this.width+1)) && (this.blockMatrix.get(column + counter).get(row).size() == 0)){
			this.blockMatrix.get(column + counter).get(row).add(dummyBlock);
			success = true;
		}
		return success;
	}
	
	protected boolean getDiagonalPosition( int column, int row, int counter, SLLBlock dummyBlock) {

		int tempCount = counter*4;
		boolean success = false;
		int back = 1 - counter;
		int front = counter -1;
		//(-1,-1)
		
		while(tempCount>=0) {
			tempCount--;
		}
		if(((row + back) >= 0) && ((row + back) < this.SLLrows) && ((column + back)>=1)  && ((column + back) < (this.width - 1))){
			if(this.blockMatrix.get(column + back).get(row + back).size() ==0) {
				this.blockMatrix.get(column + back).get(row + back).add(dummyBlock);
				success = true;
			}
			//(-1,+1)
		}else if(((row + back) >= 0) && ((row + back) < this.SLLrows) && ((column + front)>=1)  && ((column + front) < (this.width - 1))){
			if(this.blockMatrix.get(column + front).get(row + back).size() ==0) {
				this.blockMatrix.get(column + front).get(row + back).add(dummyBlock);
				success = true;
			}
			//(-1,+1)
		}else if(((row + front) >= 0) && ((row + front) < this.SLLrows) && ((column + back)>=1)  && ((column + back) < (this.width - 1))){
			if(this.blockMatrix.get(column + back).get(row + front).size() ==0) {
				this.blockMatrix.get(column + back).get(row + front).add(dummyBlock);
				success = true;
			}
			//(+1,+1)
		}else if(((row + front) >= 0) && ((row + front) < this.SLLrows) && ((column + front)>=1)  && ((column + front) < (this.width - 1))){
			if(this.blockMatrix.get(column + front).get(row + front).size() ==0) {
				this.blockMatrix.get(column + front).get(row + front).add(dummyBlock);
				success = true;
			}
		}

		return success;
	}

		
    class SLLBlock {
    	String blockName;
    	int blockIndexDie0;
        int blockIndexDie1;

        SLLBlock(String blockName, int blockIndexDie0, int blockIndexDie1) {
        	this.blockName = blockName;
            this.blockIndexDie0 = blockIndexDie0;
            this.blockIndexDie1 = blockIndexDie1;
        }
    }
}
