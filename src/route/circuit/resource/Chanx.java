package route.circuit.resource;

public class Chanx extends RouteNode {
	
	public Chanx(int index, int xlow, int xhigh, int ylow, int yhigh, int n, float r, float c, IndexedData indexedData,String direction, int numChildren) {
		super(index, xlow, xhigh, ylow, yhigh, n, 1, RouteNodeType.CHANX, r, c, indexedData, direction, numChildren);
	}
}


//Direction of the wire represents a unidirection wire headed in the increasing direction (relative to coordinate system.)
//INC_DIR : driven from the lower end of the coordinates
//DEC_DIR : driven from the upper end of the coordinates
