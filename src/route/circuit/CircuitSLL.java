package route.circuit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import route.circuit.block.AbstractBlock;
import route.circuit.block.GlobalBlock;
import route.circuit.io.SllNetData;
import route.circuit.pin.AbstractPin;
import route.circuit.pin.GlobalPin;
import route.circuit.resource.ResourceGraph;
import route.circuit.resource.RouteNode;
import route.route.Connection;
import route.route.Net;

public class CircuitSLL {
	private HashMap<String, SllNetData> sllNetInfo;
	private List<Connection> sllConnections;
	private List<Net> sllNets;
	private List<String> globalNets;
	private int totDie;
	
	private List<AbstractPin> allLeafPins = new ArrayList<>();
	private ResourceGraph rrg;
	public List<Connection> tempList = new ArrayList<>();
	public CircuitSLL(HashMap<String, SllNetData> sllInfo, List<String> globalNetList, int TotDie) {
		this.sllNetInfo = sllInfo;
		this.globalNets = globalNetList;
	}
	

	public void createSLLPinConnections() {
    	for(String sllNet : this.sllNetInfo.keySet()){
    		SllNetData sllNetConn = this.sllNetInfo.get(sllNet);
    		GlobalPin sourcePin = sllNetConn.getSLLsourceGlobalPin();
    		GlobalBlock sourceBlock = (GlobalBlock) sllNetConn.getSLLsourceBlock();
    		List<Map<AbstractBlock, AbstractPin>> sinkPinsInfo = sllNetConn.getSinkBlocks();
        	for(int i = 0; i< sinkPinsInfo.size(); i++) {
				for (Map.Entry<AbstractBlock, AbstractPin> sinkMap : sinkPinsInfo.get(i).entrySet()) {
					GlobalPin sinkPin = (GlobalPin) sinkMap.getValue();
					GlobalBlock sinkBlock = (GlobalBlock) sinkMap.getKey();
					sourcePin.addSink(sinkPin);
					sinkPin.setSource(sourcePin);
					sourcePin.setSLLGlobalPin();
					this.allLeafPins.add(sinkPin);
				}
        	}

    	}
    }
	
	
	public void updateSLLCoordinates(String netName, int[] coordinates, int dieID) {
		
		SllNetData sllNetInfo = this.sllNetInfo.get(netName);
		if(sllNetInfo.getSourceDie() == dieID) {
			sllNetInfo.setSLLXYfromPlace(coordinates[0], coordinates[1]);
		}
		
	}
	public  List<AbstractPin> getleafPins() {
		return this.allLeafPins;
		
	}
	public void loadSllNetsAndConnections(ResourceGraph rrg, int counter) {
    	short boundingBoxRange = 3; 
    	
    	this.sllConnections = new ArrayList<>();
    	this.sllNets = new ArrayList<>();
        
        int id = counter;
        
        for(String sllNet: this.sllNetInfo.keySet()) {
        	if(!this.globalNets.contains(sllNet)) {
            	SllNetData netInfo = this.sllNetInfo.get(sllNet);
            	GlobalPin sourcePin = netInfo.getSLLsourceGlobalPin();
            	GlobalBlock sourceBlock = (GlobalBlock) netInfo.getSLLsourceBlock(); 
            	int sllFanout = 0;
            	if(sourcePin.getNumSinks() > 0) {
            		String netName = sourcePin.getNetName();
            		List<Connection> net = new ArrayList<>();
            		
            		for(AbstractPin abstractSinkPin : sourcePin.getSinks()) {
            			GlobalPin sinkPin = (GlobalPin) abstractSinkPin;
            			GlobalBlock sinkBlock = sinkPin.getOwner();
            			Connection c;
            			
    					if(sourceBlock.getDieNumber() != sinkBlock.getDieNumber()) {
    						c = new Connection(id, sourcePin, sinkPin, netInfo.getSLLXY(), rrg, 2);
    						sllFanout++;
    						tempList.add(c);
    					}else {
    						c = new Connection(id, sourcePin, sinkPin, 2);
    					}
    					
    					this.sllConnections.add(c);

            			net.add(c);
            			id++;
            			
            		}
            		this.sllNets.add(new Net(netName, net, boundingBoxRange, sllFanout));
            	}
        	}
        }
	}
	
	public HashMap<String, SllNetData> getSLLBlocks(){
		return this.sllNetInfo;
	}
	
    
    public void printNetDelays() {
    	for(Net net : this.sllNets) {
    		float delay = 0;
    		
    		List<Connection> connsOfNets = net.getConnections();
    		for(Connection eachConnection : connsOfNets) {
    			for(RouteNode allNodes : eachConnection.routeNodes) {
    				delay += allNodes.getDelay();
    			}
    			
    		}
    		System.out.print("\nThe net name is " + net.getName() + " and the delay is " + delay);
    	}
    }
    
    public List<Connection> getSLLConnections(){
    	return this.sllConnections;
    }
    public List<Net> getSLLNets(){
    	return this.sllNets;
    }
}
