package pack.cluster;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.text.html.HTMLDocument.Iterator;

import pack.netlist.B;
import pack.netlist.N;
import pack.netlist.Netlist;
import pack.netlist.P;
import pack.util.Output;
import pack.util.Timing;
import pack.util.Util;

public class NetFileWriter {
	private Netlist root;
	private Netlist Orig;
	private List<LogicBlock> logicBlocks;
	
	private List<N> netlistInputs;
	private List<N> netlistOutputs;
	private List<String> netlistSLLs;

	private BufferedWriter writer;
	
	private int tabs;
	private ArrayList<String> names;
	
	private Timing t;

	private int blockC;
	
	public NetFileWriter(List<LogicBlock> logicBlocks, Netlist root, Netlist Orig){
		this.root = root;
		this.Orig = Orig;
		this.logicBlocks = logicBlocks;
		
		this.t = new Timing();
		this.t.start();
		
		this.blockC = 0;
		
		this.tabs = 0;
		this.names = new ArrayList<>();
	}
	
	public void netlistInputs(){
		this.netlistInputs = new ArrayList<>();
		this.netlistSLLs  = new ArrayList<>();
		
		for(String netname: this.root.partInputs) {
			if(!this.Orig.get_Orig_input_nets().contains(netname)) {
				this.netlistSLLs.add(netname);
			}
		}
		for(B b:this.root.get_blocks()){
			for(N n:b.get_input_nets()){
				if(!n.has_source() && n.has_terminals()){
					if(!this.netlistInputs.contains(n) ){
						if(this.Orig.get_Orig_input_nets().contains(n.get_name()))
						{
							this.netlistInputs.add(n);
						}
					}
				}
			}
		}
		Collections.sort(this.netlistInputs, N.NetFanoutComparator);
	}
	
	public void netlistSLLs(List<String> SLLConns){
		this.netlistSLLs = new ArrayList<String>();
		
		for(String element:SLLConns){
			this.netlistSLLs.add(element);
		}
	}
	
	public void netlistOutputs(){
		this.netlistOutputs = new ArrayList<>();
		ArrayList<String> tempList = new ArrayList<String>();
		tempList = this.Orig.get_Orig_output_nets();
		
		for(String netname: this.root.partOutputs) {
			if(!this.Orig.get_Orig_input_nets().contains(netname)) {
				if(!this.netlistSLLs.contains(netname)) {
					this.netlistSLLs.add(netname);
				}
				
			}
		}
		for(B b:this.root.get_blocks()){
			for(N n:b.get_output_nets()){
				if(n.has_terminals()){				
					if(!this.netlistOutputs.contains(n)){
						if(tempList.contains(n.get_name()))
						{
							this.netlistOutputs.add(n);
						}
						
					}
				}
			}
		}
		if(this.root.has_floating_blocks()){
			for(B b:this.root.get_floating_blocks()){
				for(N n:b.get_output_nets()){
					if(n.has_terminals()){
						if(!this.netlistOutputs.contains(n)){
							this.netlistOutputs.add(n);
						}
					}
				}
			}
		}
	}
	public List<N> getNetlistOutput() {
		return this.netlistOutputs;
	}
	public void makeNetFile(String resultFolder, int partnum){
		makeNetFile(this.root, resultFolder, partnum);
	}
	public void printHeaderToNetFile(String result_folder){
		this.writeBlockToNetFile(result_folder + this.root.get_blif() + ".net", "FPGA_packed_netlist[0]", null);
		for(N input:this.netlistInputs)add(input);
		this.writeInputsToNetFile();
		//Write SLL connections
		for(String SLL:this.netlistSLLs)add(SLL);
		this.writeSLLToNetFile();
		for(N output:this.netlistOutputs){

			for(P terminalOutputPin:output.get_terminal_pins()){
				
				if(terminalOutputPin.get_terminal().is_output_type()){
					add("out:" + terminalOutputPin.get_terminal().toString());
				}
			}
		}
		writeOutputsToNetFile();
		for(String clock:this.root.get_clocks()) add(clock);
		writeClocksToNetFile();
	}
	public void printLogicBlocksToNetFile(){
		for(LogicBlock lb:this.logicBlocks){
			lb.setInstanceNumber(this.blockC++);
			this.writeToNetFile(lb.toNetString(1));
		}
	}
	public void finishNetFile(){
		this.writeToNetFile("</block>");
		closeNetFile();

		this.t.stop();
		Output.println("\tNetfile writer took " + this.t.toString());
	}
	
	//// WRITERS ////
	private void writeBlockToNetFile(String name, String instance, String mode){
		writeToNetFile(Util.tabs(this.tabs));
		writeToNetFile("<?xml version=\"1.0\"?>");
		writeToNetFile("\n");
		writeToNetFile("<block");
		if(name != null){
			writeToNetFile(" name=\"");
			writeToNetFile(name);
			writeToNetFile("\"");
		}
		if(instance != null){
			writeToNetFile(" instance=\"");
			writeToNetFile(instance);
			writeToNetFile("\"");
		}
		if(mode != null){
			writeToNetFile(" mode=\"");
			writeToNetFile(mode);
			writeToNetFile("\"");
		}
		writeToNetFile(">\n");
		
		this.tabs += 1;
	}
	private void writeInputsToNetFile(){
		writeToNetFile(Util.tabs(this.tabs));
		writeToNetFile("<inputs>");
		writeToNetFile("\n");
		
		writeToNetFile(Util.tabs(this.tabs + 1));
		for(String input:this.names){
			writeToNetFile(input + " ");
		}
		writeToNetFile("\n");
		this.names.clear();
		
		writeToNetFile(Util.tabs(this.tabs));
		writeToNetFile("</inputs>");
		writeToNetFile("\n");
	}
	
	private void writeSLLToNetFile() {
		writeToNetFile(Util.tabs(this.tabs));
		writeToNetFile("<SLLs>");
		writeToNetFile("\n");
		
		writeToNetFile(Util.tabs(this.tabs + 1));
		for(String input:this.names){
			writeToNetFile(input + " ");
		}
		writeToNetFile("\n");
		this.names.clear();
		
		writeToNetFile(Util.tabs(this.tabs));
		writeToNetFile("</SLLs>");
		writeToNetFile("\n");
	}
	
	
	
	private void writeOutputsToNetFile(){
		writeToNetFile(Util.tabs(this.tabs));
		writeToNetFile("<outputs>");
		writeToNetFile("\n");
	
		writeToNetFile(Util.tabs(this.tabs + 1));
		for(String output:this.names){
			writeToNetFile(output + " ");
		}
		writeToNetFile("\n");
		this.names.clear();

		writeToNetFile(Util.tabs(this.tabs));
		writeToNetFile("</outputs>");
		writeToNetFile("\n");
	}
	private void writeClocksToNetFile(){
		writeToNetFile(Util.tabs(this.tabs));
		writeToNetFile("<clocks>");
		writeToNetFile("\n");
		
		writeToNetFile(Util.tabs(this.tabs + 1));
		for(String clock:this.names){
			writeToNetFile(clock + " ");
		}
		writeToNetFile("\n");
		this.names.clear();
		
		writeToNetFile(Util.tabs(this.tabs));
		writeToNetFile("</clocks>");
		writeToNetFile("\n");
	}
	
	//// WRITER ////
	private void makeNetFile(Netlist root, String resultFolder, int partnum){
		try {
			Output.println("The partnum is " + partnum);
			FileWriter w = new FileWriter(resultFolder + root.get_blif() + "_"+ partnum + ".net");
			this.writer = new BufferedWriter(w);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private void writeToNetFile(String line){
		try {
			this.writer.write(line);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private void closeNetFile(){
		try {
			this.writer.flush();
			this.writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//// NAMES ////
	public void add(N n){
		this.names.add(n.toString());
	}
	public void add(String s){
		this.names.add(s);
	}
}
