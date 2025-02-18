package route.circuit.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import route.circuit.block.AbstractBlock;
import route.circuit.pin.AbstractPin;
import route.circuit.pin.GlobalPin;

public class SllNetData {
	private String netname;
	private AbstractBlock sourceBlock;
	private AbstractBlock sinkBlock;
	private AbstractPin sourceLeafSLL;
	private GlobalPin sourceGlobalSLL;
	private List<AbstractPin> sinkSLL;
	
	private int x_coord, y_coord;
	private int sourceDie;
	private List<Map<AbstractBlock, AbstractPin>> sinkBlocks;
	SllNetData(String netname){
		this.netname = netname;
		this.sinkBlocks = new ArrayList<>();
	}
	public AbstractPin getSLLsourceLeafPin() {
		return this.sourceLeafSLL;
	}
	public AbstractBlock getSLLsourceBlock() {
		return this.sourceBlock;
	}
	public GlobalPin getSLLsourceGlobalPin() {
		return this.sourceGlobalSLL;
	}
	
    public void addSinkBlock(AbstractBlock sinkBlock, AbstractPin inputPin) {
        Map<AbstractBlock, AbstractPin> sinkInfo = new HashMap<>();
        sinkInfo.put(sinkBlock, inputPin);
        this.sinkBlocks.add(sinkInfo);
    }

    public void setSLLXYfromPlace(int x, int y) {
    	this.x_coord = x;
    	this.y_coord = y;
    }
    
    public int[] getSLLXY() {
    	int[] coordinates = {this.x_coord, this.y_coord};
    	return coordinates;
    }
    
    public List<Map<AbstractBlock, AbstractPin>> getSinkBlocks() {
        return this.sinkBlocks;
    }
    
    void setSLLsourceBlock(AbstractBlock sourceBlock) {
    	this.sourceBlock = sourceBlock;
    }
    
    void setSLLsourceLeafPin(AbstractPin sourcePin) {
    	this.sourceLeafSLL = sourcePin;
    }
    
    void setSLLsourceGlobalPin(GlobalPin sourcePin) {
    	this.sourceGlobalSLL = sourcePin;
    	this.sourceDie = sourcePin.getOwner().getDieNumber();
    }
    
    public int getSourceDie() {
    	return this.sourceDie;
    }
	void addSLLsink(AbstractPin sourceSLL) {
		this.sinkSLL.add(sourceSLL);
	}
}
