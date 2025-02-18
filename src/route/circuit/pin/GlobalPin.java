package route.circuit.pin;

import route.circuit.architecture.PortType;
import route.circuit.block.GlobalBlock;
import route.util.PinCounter;

public class GlobalPin extends AbstractPin {
	private int id;
	private String netName;

	private Boolean globalNetPin = false;
	private Boolean sllGlobalPin = false;
    public GlobalPin(GlobalBlock owner, PortType portType, int index) {
        super(owner, portType, index);
        
        this.id = PinCounter.getInstance().addPin();
    }
    
    public void setNetName(String netName){
    	this.netName = netName;
    }
    public String getNetName(){
    	return this.netName;
    }

    @Override
    public GlobalBlock getOwner() {
        return (GlobalBlock) this.owner;
    }
    
    @Override
    public int hashCode() {
    	return this.id;
    }
    
    public void setSLLGlobalPin() {
    	this.sllGlobalPin = true;
    }
    
    public boolean getSLLGlobalPin() {
    	return this.sllGlobalPin;
    }
    
    public void setGlobalNetPin() {
    	this.globalNetPin = true;
    }
    
    public boolean getGlobalNetPin() {
    	return this.globalNetPin;
    }
}
