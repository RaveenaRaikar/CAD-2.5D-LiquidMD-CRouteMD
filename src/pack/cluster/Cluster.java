package pack.cluster;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pack.architecture.Architecture;
import pack.main.Simulation;
import pack.netlist.Netlist;
import pack.partition.Partition;
import pack.util.Output;
//import pack.util.Output;
import pack.util.Util;

public class Cluster {
	private Netlist root;
	private Netlist Orig;
	private Partition partition;
	private Architecture architecture;
	private Simulation simulation;
	private ArrayList<String> SLLPartedges;
	private String vpr_folder;

	private List<LogicBlock> logicBlocks;
	private List<Netlist> leafNodes;
	private int partnum;
	private HashMap<String,Integer> SLLnetChecker;

	public Cluster(Netlist netlist, Architecture architecture, Partition partition, Simulation simulation){
		this.root = netlist;
		this.partition = partition;
		this.architecture = architecture;
		this.simulation = simulation;
		this.vpr_folder = simulation.getStringValue("vpr_folder");
	}
	public Cluster(Netlist Orig, Netlist netlist, Architecture architecture, Partition partition, Simulation simulation, int partnum, ArrayList<String> SLLPartedges,HashMap<String,Integer> SLLNetlistChecker){
		this.Orig = Orig;
		this.root = netlist;
		this.partition = partition;
		this.architecture = architecture;
		this.simulation = simulation;
		this.partnum = partnum;
		this.SLLPartedges = SLLPartedges;
		this.vpr_folder = simulation.getStringValue("vpr_folder");
		this.SLLnetChecker = SLLNetlistChecker;
		
	}
	public Cluster(Netlist Orig, Netlist netlist, Architecture architecture, Simulation simulation, int partnum, ArrayList<String> SLLPartedges){
		this.Orig = Orig;
		this.root = netlist;
		this.architecture = architecture;
		this.simulation = simulation;
		this.partnum = partnum;
		this.SLLPartedges = SLLPartedges;
		this.vpr_folder = simulation.getStringValue("vpr_folder");
		
	}
	public void packing(){
		this.logicBlocks = new ArrayList<>();
		this.leafNodes = new ArrayList<>();
		TPack tpack = new TPack(this.Orig, this.root, this.partition, this.architecture, this.simulation, this.partnum, this.SLLPartedges);
		tpack.seedBasedPacking();
		this.logicBlocks.addAll(tpack.getLogicBlocks());
		this.leafNodes.addAll(this.root.get_leaf_nodes());
		
	}
	public void packingVpr(){
		this.logicBlocks = new ArrayList<>();
		this.leafNodes = new ArrayList<>();
		TPack tpack = new TPack(this.Orig, this.root, this.architecture, this.simulation, this.partnum, this.SLLPartedges);
		tpack.seedBasedPackingVPR();
		this.logicBlocks.addAll(tpack.getLogicBlocks());
		this.leafNodes.addAll(this.root.get_leaf_nodes());
		
	}
	public void writeNetlistFile(){
		NetFileWriter writer = new NetFileWriter(this.logicBlocks, this.root, this.Orig);
		String result_folder = this.simulation.getStringValue("result_folder");

		writer.netlistInputs();
		writer.netlistOutputs();
		
		writer.makeNetFile(result_folder,this.partnum);

		writer.printHeaderToNetFile(result_folder);
		writer.printLogicBlocksToNetFile();
		writer.finishNetFile();
		
	}
	
	public void writeHierarchyFile(){
		try {
			this.tryWriteHierarchyFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void tryWriteHierarchyFile() throws IOException{
		FileWriter w = new FileWriter(this.simulation.getStringValue("result_folder") + root.get_blif() +"_"+ this.partnum + ".multipart.hierarchy");
		BufferedWriter writer = new BufferedWriter(w);

		int logicBlockCounter = 0;
		for(LogicBlock lb:this.logicBlocks){
			if(lb.isFloating()){
				logicBlockCounter++;
			}
		}
		writer.write("Leaf Node: floating blocks (" + logicBlockCounter + " lb) " + this.randomColor() + "\n");
		for(LogicBlock lb:this.logicBlocks){
			if(lb.isFloating()){
				writer.write("\t" + lb.getInfo() + "\n");
			}
		}
		for(Netlist leafNode:this.leafNodes){
			writer.write("Leaf Node: " + leafNode.getHierarchyIdentifier() + " (" + leafNode.getLogicBlocks().size() + " lb) " + this.randomColor() + "\n");
			for(LogicBlock lb:leafNode.getLogicBlocks()){
				writer.write("\t" + lb.getInfo() + "\n");
			}
		}

		writer.close();
	}
	private String randomColor(){
		return "[Color: (" + Util.str((int)(Math.random()*255)) + "," + Util.str((int)(Math.random()*255)) + "," + Util.str((int)(Math.random()*255)) + ")]";
	}
 	public void deleteExistingFiles(){
		//Delete all existing files of this netlist in the vpr files folder
		File folder = new File(this.vpr_folder + "vpr/files/");
		String Simulation_id = String.valueOf(this.simulation.getSimulationID());
		File[] listOfFiles = folder.listFiles();
		for(int i = 0; i < listOfFiles.length; i++){
			File file = listOfFiles[i];
			if(file.isFile()){
				if(file.getName().contains(this.root.get_blif()) && file.getName().contains(Simulation_id)){
					file.delete();
				}
			}
		}
		folder = new File(System.getProperty("user.dir"));
		listOfFiles = folder.listFiles();
		for(int i = 0; i < listOfFiles.length; i++){
			File file = listOfFiles[i];
			if(file.isFile()){
				if(file.getName().contains(this.root.get_blif()) && file.getName().contains(Simulation_id) && (file.getName().endsWith(".net"))){
					file.delete();
				}
			}
		}
		
 	}
}
