package route.circuit.resource;

public class IndexedData {
	private final int index;
	private final float base_cost;
	
	public final int length;
	public final float inv_length;
	
	public final float t_linear;
	public final float t_quadratic;
	public final float c_load;
	
	public final int orthoCostIndex;
	private IndexedData ortho_data;

	public IndexedData(int index, float baseCost, int orthoCost, float invLength, float t_linear, float t_quadratic, float c_load) {
		this.index = index;
		this.base_cost = baseCost;
		this.orthoCostIndex = orthoCost;
		this.inv_length = invLength;
		this.length = (int)Math.round(1.0 / this.inv_length);
		this.t_linear = t_linear;
		this.t_quadratic = t_quadratic;
		this.c_load = c_load;
		this.ortho_data = null;
	}
	
	public void setOrthoData(IndexedData orthoData) {
		this.ortho_data = orthoData;
	}
	public IndexedData getOrthoData() {
		return this.ortho_data;
	}
	
	public float getBaseCost() {
		return this.base_cost;
	}
	
	
	@Override
	public String toString() {
		String result = "";
				
		result += "indexed data " + this.index + ":" + "\n";
		result += "    base_cost: " + this.base_cost + "\n";
		result += "       length: " + this.length + "\n";
		result += "   inv_length: " + this.inv_length + "\n";
		result += "     t_linear: " + this.t_linear + "\n";
		result += "  t_quadratic: " + this.t_quadratic + "\n";
		result += "       c_load: " + this.c_load + "\n";
		result += "  ortho index: " + this.orthoCostIndex + "\n";
		
		return result;
	}
}
