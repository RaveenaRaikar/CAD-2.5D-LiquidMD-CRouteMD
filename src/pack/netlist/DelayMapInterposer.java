package pack.netlist;

import java.util.HashMap;
import java.util.Map;

public class DelayMapInterposer {
	private Map<String,Integer> interposerDelay;
	
	public DelayMapInterposer(){
		this.interposerDelay = new HashMap<String,Integer>();
	}
	public void addDelay(P sourcePin, P sinkPin, int delay){
		this.interposerDelay.put(sourcePin.get_id() + sinkPin.get_id(), delay);
	}
	public int getDelay(P sourcePin, P sinkPin){
		return this.interposerDelay.get(sourcePin.get_id() + sinkPin.get_id());
	}
}