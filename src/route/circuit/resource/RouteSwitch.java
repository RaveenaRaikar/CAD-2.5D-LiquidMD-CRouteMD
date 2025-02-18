package route.circuit.resource;

public class RouteSwitch {
	public final int index;
	//public final boolean buffered;
	
	public final float r;
	public final float cin;
	public final float cout;
	public final float tdel;
	
	public final float mux_trans_size;
	public final float buf_size;
	public final String name;
	public RouteSwitch(String line) {
		while(line.contains("  ")) line = line.replace("  ", " ");
		
		String[] words = line.split(";");
		
		this.index = Integer.parseInt(words[0]);
		this.name = words[1];
		this.r = Float.parseFloat(words[6]);
		this.cin = Float.parseFloat(words[3]);
		this.cout = Float.parseFloat(words[5]);
		
		this.tdel = Float.parseFloat(words[7]);
		this.mux_trans_size = Float.parseFloat(words[9]);
		this.buf_size = Float.parseFloat(words[8]);
	}
	public RouteSwitch(int Id, String name, float res, float capIn, float capOut, float tdel, float trans_size, float buf_size) {
		this.index = Id;
		this.name = name;
		this.r = res;
		this.cin = capIn;
		this.cout = capOut;
		this.tdel =tdel;
		this.mux_trans_size = trans_size;
		this.buf_size = buf_size;
	}
	

	
	@Override
	public String toString() {
		String result = "";
				
		result += "switch " + this.index + ":" + "\n";
		result += "               r: " + this.r + "\n";
		result += "             cin: " + this.cin + "\n";
		result += "            cout: " + this.cout + "\n";
		result += "            tdel: " + this.tdel + "\n";
		result += "  mux_trans_size: " + this.mux_trans_size + "\n";
		result += "        buf_size: " + this.buf_size + "\n";
		
		return result;
	}
}