package placers.analyticalplacer;

import java.util.ArrayList;
import java.util.List;

import placers.analyticalplacer.TwoDimLinkedList.Axis;

import circuit.Circuit;
import circuit.architecture.BlockType;
import circuit.architecture.BlockCategory;
import circuit.block.AbstractSite;

/**
 * This is approximately the legalizer as proposed in
 * Heterogeneous Analytical Placement (HeAP).
 *
 */
class HeapLegalizer extends Legalizer {

    // Contain the properties of the blockType that is currently being legalized
    private BlockType blockType;
    private BlockCategory blockCategory;
    private int blockStart, blockRepeat, blockHeight;

    // These are temporary data structures
    private Area[][] areaPointers;
    private List<List<List<LegalizerBlock>>> blockMatrix;


    HeapLegalizer(
            Circuit circuit,
            List<BlockType> blockTypes,
            List<Integer> blockTypeIndexStarts,
            double[] linearX,
            double[] linearY,
            int[] heights) throws IllegalArgumentException {

        super(circuit, blockTypes, blockTypeIndexStarts, linearX, linearY, heights);


        // Initialize the matrix to contain a linked list at each coordinate
        this.blockMatrix = new ArrayList<List<List<LegalizerBlock>>>(this.width);
        for(int column = 0; column < this.width; column++) {
            List<List<LegalizerBlock>> blockColumn = new ArrayList<>(this.height);
            for(int row = 0; row < this.height; row++) {
                blockColumn.add(new ArrayList<LegalizerBlock>());
            }
            this.blockMatrix.add(blockColumn);
        }
    }


    @Override
    protected void legalizeBlockType(double tileCapacity, BlockType blockType, int blocksStart, int blocksEnd) {
        this.blockType = blockType;
        this.blockCategory = this.blockType.getCategory();

        this.blockStart = this.blockType.getStart();
        this.blockHeight = this.blockType.getHeight();
        this.blockRepeat = this.blockType.getRepeat();

        // Make a matrix that contains the blocks that are closest to each position
        initializeBlockMatrix(blocksStart, blocksEnd);

        // Build a set of disjunct areas that are not over-utilized
        this.areaPointers = new Area[this.width][this.height];
        List<Area> areas = this.growAreas();

        // Legalize all unabsorbed areas
        for(Area area : areas) {
            if(!area.isAbsorbed()) {
                //System.out.printf("(%d, %d, %d, %d), %b\n", area.left, area.top, area.right, area.bottom, area.isAbsorbed());
                this.legalizeArea(area);
            }
        }
    }


    private void initializeBlockMatrix(int blocksStart, int blocksEnd) {

        // Clear the block matrix
        for(int column = 0; column < this.width; column++) {
            for(int row = 0; row < this.height; row++) {
                this.blockMatrix.get(column).get(row).clear();
            }
        }

        // Loop through all the blocks of the correct block type and add them to their closest position
        for(int index = blocksStart; index < blocksEnd; index++) {
            double x = this.linearX[index],
                   y = this.linearY[index];
            int height = this.heights[index];

            for(int offset = 0; offset < height; offset++) {
                AbstractSite site = this.getClosestSite(x, y + offset);
                int column = site.getColumn();
                int row = site.getRow();

                LegalizerBlock newBlock = new LegalizerBlock(index, offset, height);
                this.blockMatrix.get(column).get(row).add(newBlock);
            }
        }
    }


    private AbstractSite getClosestSite(double x, double y) {

        switch(this.blockType.getCategory()) {
            case IO:
                int siteX, siteY;
                if(x > y) {
                    if(x > this.height - y - 1) {
                        // Right quadrant
                        siteX = this.width - 1;
                        siteY = (int) Math.max(Math.min(Math.round(y), this.height - 2), 1);

                    } else {
                        // Top quadrant
                        siteX = (int)  Math.max(Math.min(Math.round(x), this.height - 2), 1);
                        siteY = 0;
                    }

                } else {
                    if(x > this.height - y - 1) {
                        //Bottom quadrant
                        siteX = (int)  Math.max(Math.min(Math.round(x), this.height - 2), 1);
                        siteY = this.height - 1;

                    } else {
                        // Left quadrant
                        siteX = 0;
                        siteY = (int) Math.max(Math.min(Math.round(y), this.height - 2), 1);
                    }
                }

                return this.circuit.getSite(siteX, siteY);

            case CLB:
                int row = (int) Math.round(Math.max(Math.min(y, this.height - 2), 1));

                // Get closest column
                // Not easy to do this with calculations if there are multiple hardblock types
                // So just trial and error
                int column = (int) Math.round(x);
                int step = 1;
                int direction = (x > column) ? 1 : -1;

                while(true) {
                    if(column > 0 && column < this.width-1 && this.circuit.getColumnType(column).equals(this.blockType)) {
                        break;
                    }

                    column += direction * step;
                    step++;
                    direction *= -1;
                }

                return this.circuit.getSite(column, row);


            // Hardblocks
            default:
                int start = this.blockType.getStart();
                int repeat = this.blockType.getRepeat();
                int blockHeight = this.blockType.getHeight();

                int numRows = (int) Math.floor((this.height - 2) / blockHeight);
                int numColumns = (int) Math.floor((this.width - start - 2) / repeat + 1);

                int columnIndex = (int) Math.round(Math.max(Math.min((x - start) / repeat, numColumns - 1), 0));
                int rowIndex = (int) Math.round(Math.max(Math.min((y - 1) / blockHeight, numRows - 1), 0));

                return this.circuit.getSite(columnIndex * repeat + start, rowIndex * blockHeight + 1);
        }
    }


    private List<Area> growAreas() {
        List<Integer> columns = new ArrayList<Integer>();

        // This dummy element is added to simplify the test inside the while loop
        columns.add(Integer.MIN_VALUE);
        for(int column = this.blockStart; column < this.width - 1; column += this.blockRepeat) {
            if(this.circuit.getColumnType(column).equals(this.blockType)) {
                columns.add(column);
            }
        }
        int columnStartIndex = columns.size() / 2;
        int columnEndIndex = (columns.size() + 1) / 2;
        double centerX = (columns.get(columnStartIndex) + columns.get(columnEndIndex)) / 2.0;


        List<Integer> rows = new ArrayList<Integer>();
        rows.add(Integer.MIN_VALUE);
        for(int row = 1; row < this.height - this.blockHeight; row += this.blockHeight) {
            rows.add(row);
        }
        int rowStartIndex = rows.size() / 2;
        int rowEndIndex = (rows.size() + 1) / 2;
        double centerY = (rows.get(rowStartIndex) + rows.get(rowEndIndex)) / 2.0;

        List<Area> areas = new ArrayList<Area>();

        // Grow from the center coordinate(s)
        for(int rowIndex = rowStartIndex; rowIndex <= rowEndIndex; rowIndex++) {
            int row = rows.get(rowIndex);

            for(int columnIndex = columnStartIndex; columnIndex <= columnEndIndex; columnIndex++) {
                int column = columns.get(columnIndex);

                this.tryNewArea(areas, column, row);
            }
        }

        while(columnStartIndex > 1 || rowStartIndex > 1) {
            // Run over the two closest columns
            if(centerX - columns.get(columnStartIndex - 1) <= centerY - rows.get(rowStartIndex - 1)) {
                columnStartIndex--;
                columnEndIndex++;

                int column1 = columns.get(columnStartIndex);
                int column2 = columns.get(columnEndIndex);
                for(int i = (rowEndIndex - rowStartIndex) / 2; i >= 0; i--) {
                    int row1 = rows.get(rowStartIndex + i);
                    int row2 = rows.get(rowEndIndex - i);

                    this.tryNewArea(areas, column1, row1);
                    this.tryNewArea(areas, column1, row2);
                    this.tryNewArea(areas, column2, row1);
                    this.tryNewArea(areas, column2, row2);
                }

            // Run over the two closest rows
            } else {
                rowStartIndex--;
                rowEndIndex++;

                int row1 = rows.get(rowStartIndex);
                int row2 = rows.get(rowEndIndex);
                for(int i = (columnEndIndex - columnStartIndex) / 2; i >= 0; i--) {
                    int column1 = columns.get(columnStartIndex + i);
                    int column2 = columns.get(columnEndIndex - i);

                    this.tryNewArea(areas, column1, row1);
                    this.tryNewArea(areas, column1, row2);
                    this.tryNewArea(areas, column2, row1);
                    this.tryNewArea(areas, column2, row2);
                }
            }
        }

        return areas;
    }


    private void tryNewArea(List<Area> areas, int x, int y) {
        if(this.blockMatrix.get(x).get(y).size() >= 1
                && this.areaPointers[x][y] == null) {
            Area newArea = this.newArea(x, y);
            areas.add(newArea);
        }
    }

    private Area newArea(int x, int y) {

        // left, top, right, bottom
        Area area = new Area(
                this.linearX,
                this.linearY,
                x,
                y,
                this.tileCapacity,
                this.blockType);

        do {
            int[] direction = area.nextGrowDirection();
            Area goalArea = new Area(area, direction);

            boolean growthPossible = goalArea.isLegal(this.width, this.height);
            if(growthPossible) {
                this.growArea(area, goalArea);

            } else {
                area.disableDirection();
            }
        } while(area.getOccupation() > area.getCapacity());

        return area;
    }


    private void growArea(Area area, Area goalArea) {

        // While goalArea is not completely covered by area
        while(true) {
            int rowStart, rowEnd, columnStart, columnEnd;

            // Check if growing the area would go out of the bounds of the FPGA
            if(goalArea.right > area.right || goalArea.left < area.left) {
                rowStart = area.bottom;
                rowEnd = area.top;

                if(goalArea.right > area.right) {
                    area.right += this.blockRepeat;
                    columnStart = area.right;

                } else {
                    area.left -= this.blockRepeat;
                    columnStart = area.left;
                }

                columnEnd = columnStart;

            } else if(goalArea.top > area.top || goalArea.bottom < area.bottom) {
                columnStart = area.left;
                columnEnd = area.right;

                if(goalArea.top > area.top) {
                    area.top += this.blockHeight;
                    rowStart = area.top;

                } else {
                    area.bottom -= this.blockHeight;
                    rowStart = area.bottom;
                }

                rowEnd = rowStart;

            } else {
                return;
            }



            for(int row = rowStart; row <= rowEnd; row += this.blockHeight) {
                for(int column = columnStart; column <= columnEnd; column += this.blockRepeat) {
                    this.addTileToArea(area, goalArea, column, row);
                }
            }
        }
    }

    private void addTileToArea(Area area, Area goalArea, int column, int row) {

        // If this tile is occupied by an unabsorbed area
        Area neighbour = this.areaPointers[column][row];
        if(neighbour != null && !neighbour.isAbsorbed()) {
            neighbour.absorb();

            // Update the goal area to contain the absorbed area
            goalArea.left = Math.min(goalArea.left, neighbour.left);
            goalArea.right = Math.max(goalArea.right, neighbour.right);
            goalArea.bottom = Math.min(goalArea.bottom, neighbour.bottom);
            goalArea.top = Math.max(goalArea.top, neighbour.top);
        }

        // Update the area pointer
        this.areaPointers[column][row] = area;

        // Update the capacity and occupancy
        AbstractSite site = this.circuit.getSite(column, row, true);
        if(site != null && site.getType().equals(this.blockType)) {
            area.incrementTiles();

            for(LegalizerBlock block : this.blockMatrix.get(column).get(row)) {
                // Add this block to the area if it is the root of a macro
                if(block.offset == 0) {
                    area.addBlock(block);
                }

                // If this is a macro:
                // Update the goal area to contain the entire macro
                if(block.macroHeight > 1) {
                    goalArea.top = Math.max(goalArea.top, row + block.macroHeight - 1 - block.offset);
                    goalArea.bottom = Math.min(goalArea.bottom, row - block.offset);
                }
            }
        }
    }



    private void legalizeArea(Area area) {
        TwoDimLinkedList blocks = area.getBlockIndexes();
        SplittingArea splittingArea = new SplittingArea(area);

        // Calculate the capacity of the area
        int capacity = 0;
        int columnHeight = (area.top - area.bottom) / this.blockHeight + 1;
        for(int column = area.left; column <= area.right; column += this.blockRepeat) {
            if(this.circuit.getColumnType(column) == this.blockType) {
                capacity += columnHeight;
            }
        }

        this.legalizeArea(splittingArea, capacity, blocks);
    }

    private void legalizeArea(
            SplittingArea area,
            int capacity,
            TwoDimLinkedList blocks) {

        int sizeX = area.right - area.left + 1,
            sizeY = area.top - area.bottom + 1;
        int numRows = (sizeY - 1) / this.blockHeight + 1;
        int numColumns = capacity / numRows;

        if(blocks.size() == 0) {
            return;

        // If the area is only one tile big: place all the blocks on this tile
        } else if(capacity == 1) {

            // Get the block index of the one contained block
            // (This for loop always does exactly one iteration)
            for(LegalizerBlock block : blocks) {
                this.legalY[block.blockIndex] = area.bottom;

                // Find the first column of the correct type
                for(int column = area.left; column <= area.right; column++) {
                    if(this.circuit.getColumnType(column).equals(this.blockType)) {
                        this.legalX[block.blockIndex] = column;
                        break;
                    }
                }
            }

            return;

        // If there is only one block left: find the closest site in the area
        } else if(blocks.numBlocks() == 1) {
            for(LegalizerBlock block : blocks) {
                this.placeBlock(block, area);
            }

            return;

        } else if(numColumns == 1) {
            // Find the first column of the correct type
            for(int column = area.left; column <= area.right; column++) {
                if(this.circuit.getColumnType(column).equals(this.blockType)) {
                    this.placeBlocksInColumn(blocks, column, area.bottom, area.top);

                    break;
                }
            }
        }


        // Choose which axis to split along

        Axis axis;
        if(sizeX / (double) this.blockRepeat >= sizeY / (double) this.blockHeight) {
            axis = Axis.X;
        } else {
            axis = Axis.Y;
        }

        // Split area along axis and store ratio between the two subareas
        // Sort blocks along axis
        SplittingArea area1 = new SplittingArea(area);
        SplittingArea area2;

        int splitPosition = -1, capacity1;

        if(axis == Axis.X) {
            int numColumnsLeft;

            // If the blockType is CLB
            if(this.blockCategory == BlockCategory.CLB) {
                numColumnsLeft = 0;
                for(int column = area.left; column <= area.right; column++) {
                    if(this.circuit.getColumnType(column).equals(this.blockType)) {
                        numColumnsLeft++;
                    }

                    if(numColumnsLeft >= numColumns / 2) {
                        splitPosition = column + 1;
                        break;
                    }
                }

            // Else: it's a hardblock
            } else {
                numColumnsLeft = numColumns / 2;
                splitPosition = area.left + numColumnsLeft * this.blockRepeat;
            }

            capacity1 = numColumnsLeft * numRows;
            area2 = area1.splitHorizontal(splitPosition, this.blockRepeat);


        } else {

            int numRowsBottom = numRows / 2;

            if(blocks.maxHeight() <= numRowsBottom) {
                capacity1 = numRowsBottom * numColumns;
                splitPosition = area.bottom + (numRowsBottom) * this.blockHeight;

                area2 = area1.splitVertical(splitPosition, this.blockHeight);

            // If there is a macro that is higher than half of the
            // current area size
            } else {
                // We are gonna split the area into three parts, along the X axis:
                // - multiple columns to the left of the high block
                // - one column that contains the high block
                // - multiple columns to the right of the high block

                // First split the left part of, and do a recursive call.
                // The high block is then in the first column of the second area.
                // Then split this one column off and do a recursive call.

                axis = Axis.X;
                int maxIndex = blocks.maxIndex();
                int columnIndex = numColumns * maxIndex / blocks.size();

                if(columnIndex > 0) {
                    capacity1 = (columnIndex * numRows * blocks.size()) / capacity;
                } else {
                    capacity1 = (numRows * blocks.size()) / capacity;
                }


                // Find the column that contains the high block
                int columnCounter = 0;
                for(int column = area.left; column <= area.right; column++) {
                    if(this.circuit.getColumnType(column).equals(this.blockType)) {
                        if(columnCounter == columnIndex) {
                            splitPosition = column;
                            break;
                        }
                        columnCounter++;
                    }
                }

                area2 = area1.splitHorizontal(splitPosition, this.blockRepeat);
            }
        }

        int capacity2 = capacity - capacity1;


        int splitIndex = (int) Math.ceil(capacity1 * blocks.size() / (double) capacity);
        TwoDimLinkedList otherBlockIndexes = blocks.split(splitIndex, axis);

        this.legalizeArea(area1, capacity1, blocks);
        this.legalizeArea(area2, capacity2, otherBlockIndexes);
    }

    private void placeBlock(LegalizerBlock block, SplittingArea area) {
        int blockIndex = block.blockIndex;
        double linearX = this.linearX[blockIndex];
        double linearY = this.linearY[blockIndex];

        // Find the closest row
        int macroHeight = block.macroHeight;
        if(macroHeight % 2 == 0) {
            linearY -= 0.5;
        }
        int row = (int) Math.round(linearY);

        // Make sure the row fits in the coordinates
        if(row - (macroHeight - 1) / 2 < area.bottom) {
            row = area.bottom + (macroHeight - 1) / 2;
        } else if(row + macroHeight / 2 > area.top) {
            row = area.top - macroHeight / 2;
        }
        this.legalY[blockIndex] = row;

        // Find the closest column
        int column = (int) Math.round(linearX);

        if(column > area.left && column < area.right) {
            int direction = linearX > column ? 1 : -1;
            while(this.badColumn(column, area)) {
                column += direction;
                direction = -(direction + (int) Math.signum(direction));
            }

        } else {
            int direction = column <= area.left ? 1 : -1;
            while(this.badColumn(column, area)) {
                column += direction;
            }
        }

        this.legalX[blockIndex] = column;
    }

    private void placeBlocksInColumn(TwoDimLinkedList blocks, int column, int rowStart, int rowEnd) {
        double y = rowStart;
        double cellsPerRow = blocks.size() / (double) (rowEnd - rowStart + 1);

        for(LegalizerBlock block : blocks) {
            int blockIndex = block.blockIndex;
            int height = block.macroHeight;

            int row = (int) Math.round(y + (height - 1) / 2);
            this.legalX[blockIndex] = column;
            this.legalY[blockIndex] = row;

            y += cellsPerRow * height;
        }
    }

    private boolean badColumn(int column, SplittingArea area) {
        return
                !this.circuit.getColumnType(column).equals(this.blockType)
                || column < area.left
                || column > area.right;
    }


    class LegalizerBlock {
        int blockIndex;
        int offset;
        int macroHeight;

        LegalizerBlock(int blockIndex, int offset, int macroHeight) {
            this.blockIndex = blockIndex;
            this.offset = offset;
            this.macroHeight = macroHeight;
        }
    }


    private class SplittingArea {

        int left, right, bottom, top;

        SplittingArea(int left, int right, int bottom, int top) {
            this.left = left;
            this.right = right;
            this.bottom = bottom;
            this.top = top;
        }

        SplittingArea(Area area) {
            this(area.left, area.right, area.bottom, area.top);
        }

        SplittingArea(SplittingArea area) {
            this(area.left, area.right, area.bottom, area.top);
        }

        SplittingArea splitHorizontal(int split, int space) {
            SplittingArea newArea = new SplittingArea(split, this.right, this.bottom, this.top);
            this.right = split - space;

            return newArea;
        }

        SplittingArea splitVertical(int split, int space) {
            SplittingArea newArea = new SplittingArea(this.left, this.right, split, this.top);
            this.top = split - space;

            return newArea;
        }

        @Override
        public String toString() {
            return String.format("h: [%d, %d], v: [%d, %d]", this.left, this.right, this.bottom, this.top);
        }
    }

    private class Area {

        int bottom, top, left, right;

        private boolean absorbed = false;

        private double areaTileCapacity;
        private int areaBlockHeight, areaBlockRepeat;

        private int numTiles = 0;
        private TwoDimLinkedList blockIndexes;

        private int[][] growDirections = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        private boolean[] originalDirection = {true, true, true, true};
        private int growDirectionIndex = -1;

        Area(Area a, int[] direction) {
            this.blockIndexes = new TwoDimLinkedList(a.blockIndexes);

            this.areaTileCapacity = a.areaTileCapacity;
            this.areaBlockHeight = a.areaBlockHeight;
            this.areaBlockRepeat = a.areaBlockRepeat;


            this.left = a.left;
            this.right = a.right;

            this.bottom = a.bottom;
            this.top = a.top;

            this.grow(direction);
        }

        Area(double[] linearX, double[] linearY, int column, int row, double tileCapacity, BlockType blockType) {
            // Thanks to this two-dimensionally linked list, we
            // don't have to sort the list of blocks after each
            // area split: the block list is splitted and resorted
            // in linear time.
            this.blockIndexes = new TwoDimLinkedList(linearX, linearY);

            this.areaTileCapacity = tileCapacity;
            this.areaBlockHeight = blockType.getHeight();
            this.areaBlockRepeat = blockType.getRepeat();

            this.left = column;
            this.right = column - this.areaBlockRepeat;
            this.bottom = row;
            this.top = row;


        }


        void absorb() {
            this.absorbed = true;
        }

        boolean isAbsorbed() {
            return this.absorbed;
        }


        void incrementTiles() {
            this.numTiles++;
        }
        double getCapacity() {
            return this.numTiles * this.areaTileCapacity;
        }


        void addBlock(LegalizerBlock block) {
            this.blockIndexes.add(block);
        }
        TwoDimLinkedList getBlockIndexes() {
            return this.blockIndexes;
        }
        int getOccupation() {
            return this.blockIndexes.size();
        }


        boolean isLegal(int width, int height) {
            return
                    this.left >=1
                    && this.right <= width - 2
                    && this.bottom >= 1
                    && this.top + this.areaBlockHeight <= height - 1;
        }



        int[] nextGrowDirection() {
            int[] direction;
            do {
                this.growDirectionIndex = (this.growDirectionIndex + 1) % 4;
                direction = this.growDirections[this.growDirectionIndex];
            } while(direction[0] == 0 && direction[1] == 0);

            return direction;
        }


        void grow(int[] direction) {
            this.grow(direction[0], direction[1]);
        }
        void grow(int horizontal, int vertical) {
            if(horizontal == -1) {
                this.left -= this.areaBlockRepeat;

            } else if(horizontal == 1) {
                this.right += this.areaBlockRepeat;

            } else if(vertical == -1) {
                this.bottom -= this.areaBlockHeight;

            } else if(vertical == 1) {
                this.top += this.areaBlockHeight;
            }
        }


        void disableDirection() {
            int index = this.growDirectionIndex;
            int oppositeIndex = (index + 2) % 4;

            if(this.originalDirection[index]) {
                if(!this.originalDirection[oppositeIndex]) {
                    this.growDirections[oppositeIndex][0] = 0;
                    this.growDirections[oppositeIndex][1] = 0;
                }

                this.originalDirection[index] = false;
                this.growDirections[index][0] = this.growDirections[oppositeIndex][0];
                this.growDirections[index][1] = this.growDirections[oppositeIndex][1];

            } else {
                this.growDirections[index][0] = 0;
                this.growDirections[index][1] = 0;
                this.growDirections[oppositeIndex][0] = 0;
                this.growDirections[oppositeIndex][1] = 0;
            }

            // Make sure the replacement for the current grow direction is chosen next,
            // since growing in the current direction must have failede
            this.growDirectionIndex--;
        }


        @Override
        public String toString() {
            return String.format("[[%d, %d], [%d, %d]", this.left, this.bottom, this.right, this.top);
        }
    }
}