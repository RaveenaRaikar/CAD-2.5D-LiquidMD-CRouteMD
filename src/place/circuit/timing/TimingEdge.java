package place.circuit.timing;


import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.DelayTables;

public class TimingEdge {
	private double fixedDelay, wireDelay;
	private double criticality;
	
    private final DelayTables delayTables;
	private final TimingNode source, sink;

	private double stagedWireDelay;
	private boolean isSLLedge = false;
	//private boolean 

    TimingEdge(double fixedDelay, TimingNode source, TimingNode sink, DelayTables delayTables){
        this.fixedDelay = fixedDelay;
        
        this.source = source;
        this.sink = sink;
        
        this.delayTables = delayTables;
    }
    TimingEdge(double fixedDelay, TimingNode source, TimingNode sink, DelayTables delayTables, boolean isSLL){
        this.fixedDelay = fixedDelay;
        
        this.source = source;
        this.sink = sink;
        
        this.delayTables = delayTables;
        this.isSLLedge = isSLL;
    }
    
    public double getFixedDelay(){
        return this.fixedDelay;
    }
    void setFixedDelay(double fixedDelay){
        this.fixedDelay = fixedDelay;
    }

    public boolean isSLLedge() {
    	if (this.isSLLedge) return true;
    	else return false;
    }
    public double calculateWireDelay(){
        int deltaX = Math.abs(this.source.getGlobalBlock().getColumn() - this.sink.getGlobalBlock().getColumn());
        int deltaY = Math.abs(this.source.getGlobalBlock().getRow() - this.sink.getGlobalBlock().getRow());
 
        
        //CHANGE
        BlockCategory fromCategory = this.source.getGlobalBlock().getCategory();
        BlockCategory toCategory = this.sink.getGlobalBlock().getCategory();
        return this.delayTables.getDelay(fromCategory, toCategory, deltaX, deltaY);
    }
    

    public void setWireDelay(double wireDelay){
        this.wireDelay = wireDelay;
    }
    public double getWireDelay(){
    	return this.wireDelay;
    }


    public double getTotalDelay(){
        return this.fixedDelay + this.wireDelay;
    }

    public double getCost() {

        return this.criticality * this.wireDelay;
    }
    public double getActualCost() {

        return this.criticality * this.wireDelay;
    }
    public double getSLLCost() {

        return this.fixedDelay;
    }

    void setCriticality(double criticality){
        this.criticality = criticality;
    }
    public double getCriticality(){
        return this.criticality;
    }

    public TimingNode getSource(){
    	return this.source;
    }
    public TimingNode getSink(){
    	return this.sink;
    }


    /*************************************************
     * Functions that facilitate simulated annealing *
     *************************************************/
    void setStagedWireDelay(double stagedWireDelay){
    	this.stagedWireDelay = stagedWireDelay;
    }
    void resetStagedDelay(){
    	this.stagedWireDelay = 0.0;
    }
    
    void pushThrough(){
    	this.wireDelay = this.stagedWireDelay;
    }
    
    double getDeltaCost(){
    	return this.criticality * (this.stagedWireDelay - this.wireDelay);
    }


    @Override
    public String toString() {
        return String.format("%e+%e", this.fixedDelay, this.wireDelay);
    }
}
