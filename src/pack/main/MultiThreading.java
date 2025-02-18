package pack.main;

import java.util.ArrayList;
import java.util.HashSet;

import pack.architecture.Architecture;
import pack.cluster.Cluster;
import pack.netlist.N;
import pack.netlist.Netlist;
import pack.netlist.PathWeight;
import pack.partition.Partition;
import pack.util.Info;
import pack.util.Output;

public class MultiThreading implements Runnable{

	private int PartNum;
	private Simulation simulation;
	private Netlist netlist;
	private Architecture [] PartArch; 
	private Netlist [] PartNet; 
	private Cluster [] Pack; 
	private ArrayList<String> SLLPartedges = new ArrayList<String>();

	public MultiThreading(int partnum, Simulation simulation, Netlist netlist, ArrayList<String> SLLPartedges) {
		this.PartNum = partnum;
		this.simulation = simulation;
		this.netlist = netlist;
		this.SLLPartedges = SLLPartedges;
		
		this.PartArch = new Architecture[simulation.getIntValue("Number_of_die")];
		this.PartNet = new Netlist [simulation.getIntValue("Number_of_die")];
		this.Pack = new Cluster [simulation.getIntValue("Number_of_die")];
	}
	@Override
	public void run() {
		//Initialisation sequence
		this.PartArch[this.PartNum] = new Architecture(this.simulation);
    	Output.println("\nPHASE 2: AApack PACKING");
    	this.PartArch[this.PartNum] = new Architecture(this.simulation);
    	this.PartArch[this.PartNum].PartInitialize(this.PartNum);
    	this.PartNet[this.PartNum] = new Netlist(simulation,this.PartNum);
	    this.Pack[this.PartNum] = new Cluster(netlist,PartNet[this.PartNum], PartArch[this.PartNum], this.simulation, this.PartNum, this.SLLPartedges);
	    
	    this.Pack[this.PartNum].packingVpr();

	    this.Pack[this.PartNum].writeNetlistFile(); 

		
	}

}