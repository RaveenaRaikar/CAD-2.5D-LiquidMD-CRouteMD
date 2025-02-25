package pack.netlist;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pack.architecture.Architecture;
import pack.cluster.LogicBlock;
import pack.main.Simulation;
import pack.netlist.Model;
import pack.partition.HardBlockGroup;
import pack.partition.Part;
import pack.partition.Partition;
import pack.util.ErrorLog;
import pack.util.Info;
import pack.util.Output;
import pack.util.Timing;
import pack.util.Util;
import pack.netlist.DieLevelNetlist;

public class Netlist{
	private HashMap<Integer,B> blocks;
	private HashMap<Integer,N> nets;
	private HashMap<Integer,T> terminals;
	private ArrayList<String> clocks;
	private ArrayList<String> OrigInputs;
	public ArrayList<String> partInputs = new ArrayList<String>();
	private ArrayList<String> OrigOutputs;
	public ArrayList<String> partOutputs = new ArrayList<String>();
	private HashMap<String,Model> models;
	private int level;
	private int number;
	
	private String hierarchyIdentifier;

	private Netlist parent;
	private ArrayList<Netlist> children;

	private ArrayList<LogicBlock> logicBlocks;

	private Data data;

	//SAVE INFORMATION AFTER CLEAN UP
	private boolean cleaned = false;
	private Map<String,Integer> blockCount = null;
	private int terminalCount = 0;

	private Simulation simulation;
	private int partnum = 0;
	private boolean OrigFile = false;
	private boolean diePart = false;

	public Netlist(Simulation simulation){
		this.OrigFile = true;
		this.diePart = true;
		this.simulation = simulation;

		this.data = new Data();
		
		this.set_blif(simulation.getStringValue("circuit"));

		this.blocks = new HashMap<Integer,B>();
		this.nets = new HashMap<Integer,N>();
		this.terminals = new HashMap<Integer,T>();
		this.OrigInputs = new ArrayList<String>();
		this.OrigOutputs = new ArrayList<String>();

		
		this.models = new HashMap<String,Model>();
		this.blockCount = new HashMap<String,Integer>();
		
		this.read_blif();

		post_blif_netlist_processing();
		Output.newLine();
		
		this.parent = null;
		this.children = new ArrayList<Netlist>();
		
		this.level = 0;
		this.number = 0;
		
		//Updating the DSP and RAM numbers for UB
		Output.println(this.toInfoString());
		Output.print(this.toTypesString());
		Output.newLine();
		
		Output.println("\tClocks:");
		for(String clock:this.clocks){
			int c = 0;
			for(B b:this.get_blocks()){
				if(b.has_clock()){
					if(b.get_clock().equals(clock)){
						c += 1;
					}
				}
			}
			Output.println("\t\t" + clock + ": " + c + " blocks");
		}
		Output.newLine();
		
		int i = 0, o = 0;
		for(T t:this.terminals.values()){
			if(t.is_input_type()){
				i += 1;
			}
			if(t.is_output_type()){
				o += 1;
			}
		}
		if(i+o != this.terminals.size()){
			ErrorLog.print("The sum of inputs and ouputs is not equal to the total number of terminals \n\tIn and Out = " + (i+o) + "\n\tTerminals = " + this.terminals.size());
		}
		Output.println("\tInputs: " + i);
		Output.println("\tOutputs: " + o);
		Output.newLine();

		this.hierarchyIdentifier = "";

		this.trim();
	}
	public Netlist(Simulation simulation, int partnum){
		this.simulation = simulation;
		this.partnum = partnum;
		this.diePart = false;
		this.data = new Data();
		this.set_blif(simulation.getStringValue("circuit"));

		this.blocks = new HashMap<Integer,B>();
		this.nets = new HashMap<Integer,N>();
		this.terminals = new HashMap<Integer,T>();

		this.models = new HashMap<String,Model>();
		this.blockCount = new HashMap<String,Integer>();
		
		this.read_blif();

		post_blif_netlist_processing();
		Output.newLine();
		
		this.parent = null;
		this.children = new ArrayList<Netlist>();
		
		this.level = 0;
		this.number = 0;
		
		Output.println(this.toInfoString());
		Output.print(this.toTypesString());
		Output.newLine();
		
		Output.println("\tClocks:");
		for(String clock:this.clocks){
			int c = 0;
			for(B b:this.get_blocks()){
				if(b.has_clock()){
					if(b.get_clock().equals(clock)){
						c += 1;
					}
				}
			}
			Output.println("\t\t" + clock + ": " + c + " blocks");
		}
		Output.newLine();
		
		int i = 0, o = 0;
		for(T t:this.terminals.values()){
			if(t.is_input_type()){
				i += 1;
			}
			if(t.is_output_type()){
				o += 1;
			}
		}
		if(i+o != this.terminals.size()){
			ErrorLog.print("The sum of inputs and ouputs is not equal to the total number of terminals \n\tIn and Out = " + (i+o) + "\n\tTerminals = " + this.terminals.size());
		}
		Output.println("\tInputs: " + i);
		Output.println("\tOutputs: " + o);
		Output.newLine();

		this.hierarchyIdentifier = "";

		this.trim();
	}
	//// BLIF READER ////
	public void read_blif(){
		Timing blifTiming = new Timing();
		blifTiming.start();
		Output.print("Read " + this.get_blif());
		String file;
		
		//Reading the original netlist
		if(this.diePart == true)
		{
		Output.println("\nThe original blif is executed");
		file = this.simulation.getStringValue("result_folder") + this.simulation.getStringValue("circuit") + ".blif";
		}
		else
		{
		Output.println("\n\tThe partitioned blif is executed");
		file = this.simulation.getStringValue("result_folder") + this.simulation.getStringValue("circuit") + "_" +  this.simulation.getIntValue("simulation_ID") +"_die_" + this.partnum + ".blif";
		}
		
		
		//Read file
		Timing readFile = new Timing();
		readFile.start();
		String [] lines = read_blif_file(file);
		readFile.stop();
		
		ArrayList<String> inputs = get_input_terminals(lines);
		ArrayList<String> outputs = get_output_terminals(lines);
//		
		if(this.OrigFile) {
			this.OrigInputs = inputs;
			this.OrigOutputs = outputs;
		}else {
			this.partInputs = inputs;
			this.partOutputs = outputs;
		}
		
		
		HashSet<String> latchInputs = this.get_latch_inputs(lines);
		HashSet<String> latchOutputs = this.get_latch_outputs(lines);

		//
		
		HashMap<String,String> bufferedNets = this.get_buffered_nets(lines, inputs, outputs, latchInputs, latchOutputs);
		int removedLutBuffers = this.remove_buffers(lines, bufferedNets);
		
		HashMap<String,N> netMap = new HashMap<String,N>();
		
		String truthTable = "";
		B previous = null;

		this.assign_models(lines);
		this.assign_clocks(lines);
		
		//Read the file
		int blockNumber = 0;
		int netNumber = 0;
		int terminalNumber = 0;
		String netName;
		String[] words;
		boolean first_input = true;
		boolean first_output = true;
		for(int i=0; i<lines.length; i++) {
			String line = lines[i];
			if(line.length()>0 && line.charAt(line.length()-1)=='\\'){
					Output.println(line);
			}
			//REMOVED LUT BUFFERS
			if(line.contains(".block_removed")){
				this.assign_truth_table(previous, truthTable);
				previous = null;
				truthTable = "";
			//MODEL
			}else if(line.contains(".model")){
				this.assign_truth_table(previous, truthTable);
				previous = null;
				truthTable = "";
			//NAMES
			}else if(line.contains(".names")) {
				this.assign_truth_table(previous, truthTable);
				previous = null;
				truthTable = "";
				if(!this.has_model(".names")){
					
					Model model = new Model(".names", this.simulation.getStringValue("result_folder") + this.simulation.getStringValue("architecture"));
					model.add_input_port("in");
					model.add_input_port("in");
					model.add_input_port("in");
					model.add_input_port("in");
					model.add_input_port("in");
					model.add_input_port("in");
					model.add_output_port("out");
					this.add_model(model);
				}
				
				Model model = this.get_model(".names");
				model.increment_occurences();
				//Blocknumber and net number is only incremented from .names onwards
				words = line.split(" ");
				B b = new B(words[words.length-1], blockNumber++, model.get_name(), null, null);
				this.add_block(b);
				
				//INPUTS
				for(int j = 1; j<words.length-1; j++) {
					netName = words[j];
					if(valid_net(netName)){
						N n = netMap.get(netName);
						if(n == null){
							n = new N(netName, netNumber++);
							this.add_net(n);
							netMap.put(netName, n);
						}
						//TO RANGE THE LUT INPUTS FROM 0 TO 5
						P p = new P(b, n, "in[" + (j-1) + "]", false, true, 1, null, null, false, false);
						b.add_input_pin("in", p);
						n.add_sink(p);
					}
				}
				//OUTPUTS
				netName = words[words.length-1];
				if(valid_net(netName)){
					N n = netMap.get(netName);
					if(n == null){
						n = new N(netName, netNumber++);
						this.add_net(n);
						netMap.put(netName, n);
					}
					P p = new P(b, n, "out", true, false, 0, null, null, false, false);
					b.add_output_pin("out", p);
					n.set_source(p);
				}
				previous = b;

			//SUBCKT
			}else if (line.contains(".subckt")) {
				this.assign_truth_table(previous, truthTable);
				previous = null;
				truthTable = "";
				words = line.split(" ");
				
				//Model
				Model model = this.get_model(words[1]);
				model.increment_occurences();

				
				//Name
				String name = null;
				for(int j=2; j<words.length;j++){
					String[] gate = words[j].split("=");
					if(gate.length != 2){
						ErrorLog.print("A gate should contain two elements, this gate contains only " + gate.length + " => " + words[j]);
					}
					String port = gate[0];
					netName = gate[1];

					if(port.endsWith("]")){
						port = port.substring(0, port.lastIndexOf("["));
					}
					if(model.is_output(port)){
						//The ports in the blif model must be defined in the same manner as the ports in the ".model" section
						if(!model.get_first_output_port().equals(port)){
							ErrorLog.print("The first output port of the blif primitive " + port + " is not the first output port of the model " + model.get_first_output_port());
						}
						if(name == null){
							name = netName; //Breaks after finding the first output port
							break;
						}else{
							ErrorLog.print("This situation should never occur, name is net of first output");
						}
					}
				}
				if(name == null){
					Output.println("The line is " + line);
					ErrorLog.print("No name found");
				}
				
				//Clock
				String clock = null;
				for(int j=2; j<words.length;j++){
					String[] gate = words[j].split("=");
					if(gate.length != 2){
						ErrorLog.print("A gate should contain two elements, this gate contains only " + gate.length + " => " + words[j]);
					}
					String port = gate[0];
					netName = gate[1];
					if(port.endsWith("]")){
						port = port.substring(0, port.lastIndexOf("["));
					}
					if(model.is_input(port)){
						if(is_clock(port)){
							clock = netName;
							break;
						}
					}
				}
				
				//Block
				//First output port, increment block number, pass the model type
				B b = new B(name, blockNumber++, model.get_name(), clock, null);
				this.add_block(b);
				
				if(name.contains("subckt51360~eccstatus")){
					System.out.println("Block with name " + name);

				}
				
				//Inputs and outputs
				for(int j=2; j<words.length;j++){
					String[] gate = words[j].split("=");
					if(gate.length != 2){
						ErrorLog.print("A gate should contain two elements, this gate contains only " + gate.length + " => " + words[j]);
					}
					String port = gate[0];
					netName = gate[1];

					if(port.endsWith("]")){
						port = port.substring(0, port.lastIndexOf("["));
					}

					if(valid_net(netName)){
						//INPUTS
						if(model.is_input(port)){
							N n = netMap.get(netName);
							if(n == null){
								//All the inputs will be considered as nets and the net number will be incremented
								n = new N(netName, netNumber++);
								this.add_net(n);
								netMap.put(netName, n);
							}
							//Block name is defined with respect to the output hence source to the block is outputs and sink to the block is inputs
							P p = new P(b, n, gate[0], false, true, 1, null, null, false, b.is_sequential());
							n.add_sink(p);
							b.add_input_pin(port, p);	
						
						//OUTPUTS
						}else if(model.is_output(port)){
							N n = netMap.get(netName);
							if(n == null){
								n = new N(netName, netNumber++);
								this.add_net(n);
								netMap.put(netName, n);
							}
							P p = new P(b, n, gate[0], true, false, 0, null, null, b.is_sequential(), false);
							
							n.set_source(p);
							b.add_output_pin(port, p);
						}else{
							ErrorLog.print("Unrecognized port: " + port);
						}
					}
				}
			//LATCH
			}else if (line.contains(".latch")) {
				this.assign_truth_table(previous, truthTable);
				previous = null;
				truthTable = "";
				//First occurence of latch model
				if(!this.has_model(".latch")){
					
					Model model = new Model(".latch", this.simulation.getStringValue("result_folder") + this.simulation.getStringValue("architecture"));
					model.add_input_port("D");
					model.add_input_port("clk");
					model.add_output_port("Q");
					this.add_model(model);
					
				}
				
				Model model = this.get_model(".latch");
				model.increment_occurences();
				
				words = line.split(" ");
				
				//Latches are defined by their output connections. The block name is set to be the output.
				B b = new B(words[2], blockNumber++, model.get_name(), words[4], null);
				this.add_block(b);
				
				//INPUT
				netName = words[1];
				if(valid_net(netName)){

					N n = netMap.get(netName);
					if(n == null){
						n = new N(netName, netNumber++);
						this.add_net(n);
						netMap.put(netName, n);
					}
					P p = new P(b, n, "D", false, true, 1, null, null, false, true);
					b.add_input_pin("D", p);
					n.add_sink(p);
				}
				//CLK
				String clock = words[4];
				if(valid_net(clock)){

					N n = netMap.get(clock);
					if(n == null){
						n = new N(clock, netNumber++);
						this.add_net(n);
						netMap.put(clock, n);
					}
					P p = new P(b, n, "clk", false, true, 1, null, null, false, true);
					b.add_input_pin("clk", p);
					n.add_sink(p);
				}
				//OUTPUT
				netName = words[2];
				if(valid_net(netName)){

					N n = netMap.get(netName);
					if(n == null){
						n = new N(netName, netNumber++);
						this.add_net(n);
						netMap.put(netName, n);
					}
					P p = new P(b, n, "Q", true, false, 0, null, null, true, false);
					b.add_output_pin("Q", p);
					n.set_source(p);
				}
			//INPUTS
			}else if (line.contains(".inputs") && first_input) {
				this.assign_truth_table(previous, truthTable);
				previous = null;
				truthTable = "";
				
				first_input = false;
				words = line.split(" ");
				for(int j = 1; j<words.length; j++){
					netName = words[j];
					if(valid_net(netName)){
						//Make pin
						T t = new T(netName,terminalNumber++,"INPUT");
						this.add_terminal(t);
						//Make net

						N n = netMap.get(netName);
						if(n == null){
							n = new N(netName, netNumber++);
							this.add_net(n);
							netMap.put(netName, n);
						}

						P p = new P(t, n, "inpad", true, false, 0, null, null, true, false);
						n.add_terminal_pin(p);
						t.set_pin(p);
					}
				}
			//OUTPUTS
			}else if (line.contains(".outputs") && first_output) {
				this.assign_truth_table(previous, truthTable);
				previous = null;
				truthTable = "";
				
				first_output = false;
				words = line.split(" ");
				for (int j = 1; j<words.length; j++){
					netName = words[j];
					if(valid_net(netName)){
						//Make pin
						T t = new T(netName, terminalNumber++, "OUTPUT");
						this.add_terminal(t);
						//Make net
						if(bufferedNets.containsKey(netName)){
							netName = bufferedNets.get(netName);
						}

						N n = netMap.get(netName);
						if(n == null){
							n = new N(netName, netNumber++);
							this.add_net(n);
							netMap.put(netName, n);
						}

						P p = new P(t, n, "outpad", false, true, 1, null, null, false, true);
						n.add_terminal_pin(p);
						t.set_pin(p);
					}
				}
			}else{
				if(line.length() > 0){
					while(line.charAt(0) == ' '){
						line = line.substring(1);
					}
					if(line.length()>0 && (line.charAt(0)=='0'||line.charAt(0)=='1'||line.charAt(0)=='-')){
						truthTable = truthTable + line +"\n";
					}else{
						if(line.contains(".end"))
						{
							//To remove the final model dependency
							this.assign_truth_table(previous, truthTable);
							truthTable = truthTable +"\n";

							
						}
					}
				}
			}
		}
				
		blifTiming.stop();
		Output.println(" | Read file took " + readFile.toString() + " | Total time " + blifTiming.toString());
		Output.println("\t" + removedLutBuffers + " buffered luts are removed");
	}
 	private static String[] read_blif_file(String file){
 		ArrayList<String> temp = new ArrayList<String>();
 		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line = br.readLine();
			StringBuilder sb = new StringBuilder();
			boolean endOfLine = false;
			while(line != null){
				if(line.length() > 0){
					//Comments in the blif file are #
					if(!(line.charAt(0) == '#')){
						// Double \ to escape
						if(line.charAt(line.length()-1)=='\\'){
							line = line.substring(0,line.length()-1);
						}else{
							endOfLine = true;
						}
						sb.append(line);
					}
				}else if(sb.toString().length() > 0){
					//Bug fix for pll to make sure that all subcircuits are separated even when \ is added without information on next line
					endOfLine = true;
				}
				if(endOfLine){
					line = sb.toString();
					while(line.contains("  ")){
						line = line.replace("  ", " ");
					}
					temp.add(line);
					sb = new StringBuilder();
					endOfLine = false;
				}
				line = br.readLine();
			}
			br.close();
		} catch (IOException e) {
			System.err.println("Problem in reading the BLIFfile: " + e.getMessage());
			e.printStackTrace();
		}
 		String[] lines = new String[temp.size()];
		return temp.toArray(lines);
	}
	private ArrayList<String> get_input_terminals(String[] lines){
		ArrayList<String> inputs = new ArrayList<String>();
		for(int i=0; i<lines.length; i++){
			if(lines[i].contains(".inputs")){
				String[] words = lines[i].split(" ");
				for(int j = 1; j<words.length; j++){
					inputs.add(words[j]);
				}
				i = lines.length;
			}
		}
		return inputs;
	}
	private ArrayList<String> get_output_terminals(String[] lines){
		ArrayList<String> outputs = new ArrayList<String>();
		for(int i=0; i<lines.length; i++){
			if(lines[i].contains(".outputs")){
				String[] words = lines[i].split(" ");
				for(int j = 1; j<words.length; j++){
					outputs.add(words[j]);
				}
				i = lines.length;
			}
		}
		return outputs;
	}
	private HashSet<String> get_latch_inputs(String[] lines){
		HashSet<String> latch_inputs = new HashSet<String>();
		for(int i=0; i<lines.length-1;i++){
			String[] words = lines[i].split(" ");
			if(words[0].equals(".latch")){
				latch_inputs.add(words[1]);
			}
		}
		
		return latch_inputs;
	}
	private HashSet<String> get_latch_outputs(String[] lines){
		HashSet<String> latch_outputs = new HashSet<String>();
		for(int i=0; i<lines.length-1;i++){
			String[] words = lines[i].split(" ");
			if(words[0].equals(".latch")){
				latch_outputs.add(words[2]);
			}
		}
		
		return latch_outputs;
	}
	private int remove_buffers(String[] lines, HashMap<String,String> bufferedNets){
		boolean changed = false;
		int removedBuffers = 0;
		for (int i=0; i<lines.length-1;i++){
			if(lines[i].contains(".inputs")){
				//Skip the inputs line
			}else if(lines[i].contains(".outputs")){
				//Skip the outputs line
			}else if(lines[i+1].equals("1 1")){
				String[] words = lines[i].split(" ");
				if(bufferedNets.containsKey(words[2])){
					//Skip this block, buffer should not be added
					lines[i] = ".block_removed";
					lines[i+1] = ".block_removed";
					removedBuffers += 1;
				}
			}else{
				changed = false;
				String[] words = lines[i].split(" ");
				for(int w=0; w<words.length; w++){
					if(words[w].contains("=")){
						String name = words[w].split("=")[1];
						if(bufferedNets.containsKey(name)){
							words[w] = words[w].split("=")[0] + "=" + bufferedNets.get(name);
							changed = true;
						}
					}else{
						if(bufferedNets.containsKey(words[w])){
							words[w] = bufferedNets.get(words[w]);
							changed = true;
						}
					}
				}
				if(changed){
					lines[i] = new String();
					for(int w=0; w<words.length; w++){
						if(w<words.length-1){
							lines[i] += words[w] + " ";
						}else{
							lines[i] += words[w];
						}
					}
				}
			}
		}
		return removedBuffers;
	}
	private void assign_models(String[] lines){
		//TODO Mixed width ram is currently not supported
		ArrayList<String> mixedWidthRamBlockTypes = new ArrayList<String>();

		boolean topModel_passed = false;
		for(int i=0; i<lines.length;i++) {
			String line = lines[i];
			if(line.contains(".model")) {
				String[] words = line.split(" ");
				if(!topModel_passed){
					topModel_passed = true;
				}else{
					String modelName = words[1];
					if(mixedWidthRamBlockTypes.contains(modelName)){
						ErrorLog.print("This design contains mxied width ram blocks which is not supported yet");
					}
					
					Model model = new Model(modelName, this.simulation.getStringValue("result_folder") + this.simulation.getStringValue("architecture"));
					this.add_model(model);
					line = lines[++i];
					while(!line.contains(".end")){
						//Add all the input ports and pins to each of the model
						if(line.contains(".inputs")){
							String[] ports = line.split(" ");
							for(int k=1;k<ports.length;k++){
								String port = ports[k];
								if(port.contains("[") && port.contains("]")){
									port = port.substring(0,port.indexOf("["));
								}
								model.add_input_port(port);
							}
						}else if(line.contains(".outputs")){
							String[] ports = line.split(" ");
							for(int k=1;k<ports.length;k++){
								String port = ports[k];
								if(port.endsWith("]")){
									port = port.substring(0, port.lastIndexOf("["));
								}
								model.add_output_port(port);
							}
						}else{
							//To add the black box line
							model.add_to_internals(line);
						}
						line = lines[++i];
					}
				}
			}
		}
	}
	// Buffered nets contain the same data at the input and output. TODO: Check if it is valid for invertor gate
	
	private HashMap<String,String> get_buffered_nets(String[]lines, ArrayList<String> inputs, ArrayList<String> outputs, HashSet<String> latch_inputs, HashSet<String> latch_outputs){
		HashMap<String,String> bufferedNets = new HashMap<String,String>();
		boolean removeBufferedNets = false;
		if(removeBufferedNets){
			for(int i=0; i<lines.length-1; i++) {
				if(lines[i+1].equals("1 1")){
					String[] words = lines[i].split(" ");
					if(!latch_inputs.contains(words[2]) && !outputs.contains(words[2])){
						Output.println("Net " + words[2] + " is not buffered because the net is not connected with a latch or outputpin!");
					}else{
						bufferedNets.put(words[2], words[1]);
					}
				}
			}
		}else{
			Output.println("Buffered nets are not removed!");
		}
		return bufferedNets;
	}
	public void assign_clocks(String[] lines){
		//Read the file
		this.clocks = new ArrayList<String>();
		for(int i=0; i<lines.length; i++){
			String line = lines[i];
			if(line.contains(".subckt")) {
				String[] words = line.split(" ");
				Model model = this.get_model(words[1]);
				for(int j=2; j<words.length;j++){
					String[] gate = words[j].split("=");
					if(gate.length < 2){
						Output.newLine();
						Output.println("\t-3\t" + lines[i-3]);
						Output.println("\t-2\t" + lines[i-2]);
						Output.println("\t-1\t" + lines[i-1]);
						Output.println("\t0\t" + lines[i]);
						Output.println("\t+1\t" + lines[i+1]);
						Output.println("\t+2\t" + lines[i+2]);
						Output.println("\t+3\t" + lines[i+3]);
						ErrorLog.print("The length of gate " + words[j] + " is equal to " + gate.length);
					}
					String port = gate[0];
					String netName = gate[1];
					if(port.endsWith("]")){
						port = port.substring(0, port.lastIndexOf("["));
					}
					if(model.is_input(port)){
						if(is_clock(port)){
							if(valid_net(netName)){
								if(!this.clocks.contains(netName)){
									this.clocks.add(netName);
								}
							}
						}
					}
				}
			//LATCH
			}else if(line.contains(".latch")){
				String[] words = line.split(" ");
				if(!this.clocks.contains(words[4])){
					this.clocks.add(words[4]);
				}
			}
		}
	}
	private void assign_truth_table(B previous, String truthTable){
		if(previous != null 
				&& truthTable.length() > 0 
				&& truthTable.charAt(0) != '\n'){
			previous.set_truth_table(truthTable);
		}
	}
	private boolean is_clock(String port){
		if(port.equals("clk")){
			return true;
		}else if(port.equals("clk0")){
			return true;
		}else if(port.equals("inclk")){
			return true;
		}else{
			return false;
		}
	}
	private boolean valid_net(String netname){
		if(netname.equals("unconn")){
			return false;
		}else{
			return true;
		}
	}
	private void add_net(N n){
		if(this.nets.containsKey(n.get_number())){
			Output.println("The netlist already contains a net with number " + n.get_number() + " and name " + n.toString());
		}
		this.nets.put(n.get_number(), n);
	}
	private void remove_net(N n){
		if(this.nets.remove(n.get_number())==null){
			Output.println("Net " + n.toString() + " not removed correctly");
		}
	}
	private boolean has_net(N n){
		return this.nets.containsKey(n.get_number());
	}
	private void add_block(B b){
		//Checking if the block number is already added in the hashmap
		if(this.blocks.containsKey(b.get_number())){
			Output.println("The netlist already contains a block with number " + b.get_number() + " and name " + b.toString());
		}
		this.blocks.put(b.get_number(), b);
		String blockType = b.get_type();
		if(!this.blockCount.containsKey(blockType)){
			this.blockCount.put(blockType,0);
		}
		//Add each occurence of block type to the hashmap
		this.blockCount.put(blockType, this.blockCount.get(blockType)+1);
	}
	private void remove_block(B b){
		if(this.blocks.remove(b.get_number())==null){
			ErrorLog.print("Block " + b.toString() + " not removed correctly");
		}
		String blockType = b.get_type();
		if(this.blockCount.get(blockType)>0){	
			this.blockCount.put(blockType, this.blockCount.get(blockType)-1);
		}else{
			ErrorLog.print("There are no blocks of this type left in the netlist");
		}
	}
	private void add_terminal(T t){
		if(this.terminals.containsKey(t.get_number())){
			Output.println("The netlist already contains a terminal with number " + t.get_number() + " and name " + t.toString());
		}
		this.terminals.put(t.get_number(), t);
	}
	private void remove_terminal(T t){
		if(this.terminals.remove(t.get_number())==null){
			Output.println("Terminal " + t.toString() + " not removed correctly");
		}
	}
	
	//// POST BLIF READER NETLIST PROCESSING ////
	private void post_blif_netlist_processing(){
		boolean change = true;
		ArrayList<B> removedBlocks = new ArrayList<B>();
		ArrayList<N> removedNets = new ArrayList<N>();
		Output.println("\tRemove blocks and nets ");
		while(change){
			change = false;
			ArrayList<B> localRemovedBlocks = this.remove_blocks();
			if(!localRemovedBlocks.isEmpty()){
				change = true;
				removedBlocks.addAll(localRemovedBlocks);
			}
			ArrayList<N> localRemovedNets = this.remove_nets();
			if(!localRemovedNets.isEmpty()){
				change = true;
				removedNets.addAll(localRemovedNets);
			}
		}
		Output.println("\t\t" + removedNets.size() + " nets with only one node removed from netlist");
		Output.println("\t\t" + removedBlocks.size() + " unconnected blocks removed from netlist");
		for(B b:removedBlocks){
			Output.println("\t\t\t" + b.toString());
		}
	}
	//Blocks
	private ArrayList<B> remove_blocks(){
		ArrayList<B> removedBlocks = new ArrayList<B>();
		for(B b:this.get_blocks()){
			if(!b.has_pins()){
				removedBlocks.add(b);
			}
		}
		for(B b:removedBlocks){
			this.remove_block_from_netlist(b);
		}
		return removedBlocks;
	}
	private void remove_block_from_netlist(B b){
		this.remove_block(b);
		this.get_model(b.get_type()).decrement_occurences();
		if(!b.get_nets().isEmpty()){
			ErrorLog.print("B is removed from the netlist because it does not have nets attached, this situation is unexpected!");
		}
	}
	
	//Nets
	private ArrayList<N> remove_nets(){
		ArrayList<N> removedNets = new ArrayList<N>();
		for(N n:this.get_nets()){
			if(!n.valid()){
				removedNets.add(n);
			}
		}
		for(N n:removedNets){
			this.remove_net_from_netlist(n);
		}
		return removedNets;
	}
	private void remove_net_from_netlist(N n){
		this.remove_net(n);
		if(n.has_terminals()){
			for(P terminalPin:n.get_terminal_pins()){
				if(terminalPin.has_terminal()){
					this.remove_terminal(terminalPin.get_terminal());
				}else{
					Output.println("This pin should have a terminal");
				}
			}
		}
		if(n.has_source()){
			P sourcePin = n.get_source_pin();
			if(sourcePin.has_block()){
				sourcePin.get_block().remove_output_pin(sourcePin);
			}
		}
		for(P sinkPin:n.get_sink_pins()){
			if(sinkPin.has_block()){
				sinkPin.get_block().remove_input_pin(sinkPin);
			}
		}
	}

	//// SDC WRITER ////
	public void writeSDC(String folder, int num, Partition partition, int simulationID){
		try {
			//Find the clocks in the netlist
			HashSet<String> localClocks = new HashSet<String>();
			for(B b:this.get_blocks()){
				if(b.is_sequential()){
					localClocks.add(b.get_clock());
				}
			}
			for(String clock:localClocks){
				if(!this.clocks.contains(clock)){
					ErrorLog.print("Unknown clock found in netlist => " + clock);
				}
			}
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(folder + this.get_blif() + "_" + simulationID + "_" + num + ".sdc"));

			bw.write("#############################" + "\n");
			bw.write("### Clocks in the netlist ###" + "\n");
			bw.write("#############################" + "\n");
			for(String clock:localClocks){
				String oldClock = clock;
				clock = clock.replace("\\", "\\\\");
				clock = clock.replace("|", "\\|");
				clock = clock.replace("[", "\\[");
				clock = clock.replace("]", "\\]");
				if(!clock.equals(oldClock)){
					clock = "{" + clock + "}";
				}
				bw.write("create_clock" + " " + "-period" + " " + "1.0" + " " + clock + "\n");
			}
			bw.newLine();
			
			bw.write("#############################" + "\n");
			bw.write("##### Virtual IO clock ######" + "\n");
			bw.write("#############################" + "\n");
			bw.write("create_clock" + " " + "-period" + " " + "1.0" + " " + "-name" + " " + "virtual_io_clock" + "\n");
			bw.write("set_input_delay" + " " + "-clock" + " " + "virtual_io_clock" + " " + "-max" + " " + "0.0" + " " + "[get_ports{*}]" + "\n");		
			bw.write("set_output_delay" + " " + "-clock" + " " + "virtual_io_clock" + " " + "-max" + " " + "0.0" + " " + "[get_ports{*}]" + "\n");
			bw.newLine();
			
			HashMap<String,HashMap<String,Integer>> remainingOutputTime = partition.getCutEdges().getRemainingOutputTime();
			HashMap<String,HashMap<String,Integer>> remainingInputTime = partition.getCutEdges().getRemainingInputTime();
			HashSet<String> sdcLines = new HashSet<String>();
			for(B block:this.get_blocks()){
				for(P outputPin:block.get_output_pins()){
					String outputNet = outputPin.get_net().get_name();
					if(remainingOutputTime.containsKey(outputNet)){
						if(!remainingOutputTime.get(outputNet).containsKey(outputPin.get_id())){
							Output.println("Outputpin " + outputPin.get_id() + " not found");
							for(String pin:remainingOutputTime.get(outputNet).keySet()){
								Output.println("\tPin: " + pin);
							}
							ErrorLog.print("Outputpin " + outputPin.get_id() + " not found");
						}
						Integer delay = remainingOutputTime.get(outputNet).get(outputPin.get_id());
						//If an outputnet is in fact a buffered net, then the name of the net is not equal to the name of the actual net,
						//but equal to the name of the output net of the buffer.
						if(outputPin.get_net().has_terminals()){
							for(P p:outputPin.get_net().get_terminal_pins()){
								T t = p.get_terminal();
								if(t.is_output_type()){
									if(!t.get_name().equals(outputNet)){
										outputNet = t.get_name();
									}
								}
							}
						}
						
						outputNet = outputNet.replace("\\", "\\\\");
						outputNet = outputNet.replace("|", "\\|");
						outputNet = outputNet.replace("[", "\\[");
						outputNet = outputNet.replace("]", "\\]");
						
						String line = "set_output_delay" + " " + "-clock" + " " + "dummy_clock" + " " + "-max" + " " + (delay.doubleValue()/1000) + " " + "[get_ports{" + outputNet + "}]" + "\n";
						sdcLines.add(line);
					}
				}
				for(P inputPin:block.get_input_pins()){
					String inputNet = inputPin.get_net().get_name();
					if(remainingInputTime.containsKey(inputNet)){
						if(remainingInputTime.get(inputNet).containsKey(inputPin.get_id())){
							Integer delay = remainingInputTime.get(inputNet).get(inputPin.get_id());
							
							inputNet = inputNet.replace("\\", "\\\\");
							inputNet = inputNet.replace("|", "\\|");
							inputNet = inputNet.replace("[", "\\[");
							inputNet = inputNet.replace("]", "\\]");
							
							String line = "set_input_delay" + " " + "-clock" + " " + "dummy_clock" + " " + "-max" + " " + (delay.doubleValue()/1000) + " " + "[get_ports{" + inputNet + "}]" + "\n";
							sdcLines.add(line);
						}
					}
				}
			}
			if(!sdcLines.isEmpty()){
				bw.write("#############################" + "\n");
				bw.write("######## I/O Delays #########" + "\n");
				bw.write("#############################" + "\n");
				bw.write("create_clock -period 1.0 -name dummy_clock" + "\n");
				for(String line:sdcLines){
					bw.write(line);
				}
				bw.newLine();
			}

			if(localClocks.size() > 1){
				bw.write("#############################" + "\n");
				bw.write("####### Clock groups ########" + "\n");
				bw.write("#############################" + "\n");
				String s = new String();
				s += "set_clock_groups";
				s += " ";
				s += "-exclusive";
				for(String clock:localClocks){
					clock = clock.replace("\\", "\\\\");
					clock = clock.replace("|", "\\|");
					clock = clock.replace("[", "\\[");
					clock = clock.replace("]", "\\]");
					s += " ";
					s += "-group";
					s += "{";
					s += clock;
					s += "}";
				}
				s += "\n";
				bw.write(s);
			}
			bw.flush();
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	
	///DUMMY blif writer
	public void writeBlif_dummy(String folder, int num, int simulationID,boolean dielevel){
		
		Blif blif = new Blif(folder, this.get_blif(), num, simulationID);	

		//Inputs
 		for(N inputNet:this.get_input_nets()){
 			
 			boolean hasInputTerminal = false;
			for(P terminalPin:inputNet.get_terminal_pins()){
				T t = terminalPin.get_terminal();
				if(t.is_input_type()){
					blif.add_input(t.get_name());
					hasInputTerminal = true;
				}
			}
			if(!hasInputTerminal){
			boolean hasCutTerminal = false;
				for(P terminalPin:inputNet.get_terminal_pins()){
					T t = terminalPin.get_terminal();
					
					if(t.is_cut_type()){
						blif.add_input(t.get_name());
						hasCutTerminal = true;
					}
				}
				if(!hasCutTerminal){
					ErrorLog.print("An input net should have an input or cut terminal");
				}
			}
 		}
 		
 		//Outputs
 		for(N outputNet:this.get_output_nets()){
 			boolean outputAdded = false;
			for(P terminalPin:outputNet.get_terminal_pins()){
				T t = terminalPin.get_terminal();
				if(t.is_output_type()){
					blif.add_output(t.get_name());
					if(!outputNet.get_name().equals(t.get_name())){
						blif.add_buffer(outputNet.get_name(), t.get_name());
					}
					outputAdded = true;
				}
			}
			if(!outputAdded){
				for(P terminalPin:outputNet.get_terminal_pins()){
					T t = terminalPin.get_terminal();
					if(t.is_cut_type()){
						boolean outputRequired = true;
						for(B b:outputNet.get_sink_blocks()){
							if(b.get_type().contains("port_ram")){
								outputRequired = false;
							}
						}
						if(!outputRequired){
								outputRequired = true;
						}
						if(outputRequired){
							if(!this.clocks.contains(t.get_name())){
								blif.add_output(t.get_name());
								if(!outputNet.get_name().equals(t.get_name())){
									blif.add_buffer(outputNet.get_name(), t.get_name());
								}
								outputAdded = true;
							}else{
								if(outputNet.fanout() == 0){
									Output.println("\t\t\tThe cut clock net has no sinks in the circuit, output pin is added to blif file");
									blif.add_output(t.get_name());
									if(!outputNet.get_name().equals(t.get_name())){
										blif.add_buffer(outputNet.get_name(), t.get_name());
									}
								}
								outputAdded = true;
							}
						}else{
							outputAdded = true; //In fact it is not added, because it is not required
						}
					}
				}
	 		}
			if(!outputAdded){
				ErrorLog.print("This output net " + outputNet + " should have a terminal");
			}
 		}
 		
 		//All blif primitives
		for(B b:this.get_ordered_block_array()){
			b.to_blif_string(blif, this.get_model(b.get_type()));
			}
		
		//Net for unconnected input pins
		blif.add_gate(".names unconn\n0");
		
		blif.write(dielevel);
	}

	//// BLIF WRITER ////
	public void writeBlif(String folder, int num, Partition partition, int simulationID, boolean dielevel){
		
		Blif blif = new Blif(folder, this.get_blif(), num, simulationID);	

		//Cut nets
		HashSet<String> cutNets = partition.getCutEdges().getCutNetNames();

		//Inputs
 		for(N inputNet:this.get_input_nets()){
 			boolean hasInputTerminal = false;
			for(P terminalPin:inputNet.get_terminal_pins()){
				T t = terminalPin.get_terminal();
				if(t.is_input_type()){
					blif.add_input(t.get_name());
					hasInputTerminal = true;
				}
			}
			if(!hasInputTerminal){
			boolean hasCutTerminal = false;
				for(P terminalPin:inputNet.get_terminal_pins()){
					T t = terminalPin.get_terminal();
					
					if(t.is_cut_type()){
						blif.add_input(t.get_name());
						hasCutTerminal = true;
					}
				}
				if(!hasCutTerminal){
					ErrorLog.print("An input net should have an input or cut terminal");
				}
			}
 		}
 		
 		//Outputs
 		for(N outputNet:this.get_output_nets()){
 			boolean outputAdded = false;
			for(P terminalPin:outputNet.get_terminal_pins()){
				T t = terminalPin.get_terminal();
				if(t.is_output_type()){
					blif.add_output(t.get_name());
					if(!outputNet.get_name().equals(t.get_name())){
						blif.add_buffer(outputNet.get_name(), t.get_name());
					}
					outputAdded = true;
				}
			}
			if(!outputAdded){
				for(P terminalPin:outputNet.get_terminal_pins()){
					T t = terminalPin.get_terminal();
					if(t.is_cut_type()){
						boolean outputRequired = true;
						for(B b:outputNet.get_sink_blocks()){
							if(b.get_type().contains("port_ram")){
								outputRequired = false;
							}
						}
						if(!outputRequired){
							if(cutNets.contains(outputNet.get_name())){
								outputRequired = true;
							}
						}
						if(outputRequired){
							if(!this.clocks.contains(t.get_name())){
								blif.add_output(t.get_name());
								if(!outputNet.get_name().equals(t.get_name())){
									blif.add_buffer(outputNet.get_name(), t.get_name());
								}
								outputAdded = true;
							}else{
								if(outputNet.fanout() == 0){
									Output.println("\t\t\tThe cut clock net has no sinks in the circuit, output pin is added to blif file");
									blif.add_output(t.get_name());
									if(!outputNet.get_name().equals(t.get_name())){
										blif.add_buffer(outputNet.get_name(), t.get_name());
									}
								}
								outputAdded = true;
							}
						}else{
							outputAdded = true; //In fact it is not added, because it is not required
						}
					}
				}
	 		}
			if(!outputAdded){
				ErrorLog.print("This output net " + outputNet + " should have a terminal");
			}
 		}
 		
 		//All blif primitives
		for(B b:this.get_ordered_block_array()){
			b.to_blif_string(blif, this.get_model(b.get_type()));
			}
		
		//Net for unconnected input pins
		blif.add_gate(".names unconn\n0");
		
		blif.write(dielevel);
	}
	public static void write_blif(String folder, int num, Set<B> floatingBlocks, String blifName, HashMap<String,Model> models, int simulationID, boolean dielevel){
		Blif blif = new Blif(folder, blifName, num, simulationID);	
		
		//Inputs
		for(B floatingBlock:floatingBlocks){
			if(floatingBlock.has_inputs()){
				ErrorLog.print("Floating block has inputs!");
			}
		}
		
 		//Outputs
		for(B floatingBlock:floatingBlocks){
			for(N outputNet:floatingBlock.get_output_nets()){
				for(P terminalPin:outputNet.get_terminal_pins()){
					T t = terminalPin.get_terminal();
					if(t.is_output_type()){
						blif.add_output(t.get_name());
					}else{
						ErrorLog.print("Output net of floating block has an input terminal");
					}
					if(!outputNet.get_name().equals(t.get_name())){
						blif.add_buffer(outputNet.get_name(), t.get_name());
					}
				}
	 		}
		}	
		
		//All floating blif primitives
		for(B floatingBlock:floatingBlocks){
			floatingBlock.to_blif_string(blif, models.get(floatingBlock.get_type()));
		}		
		blif.write(dielevel);
	}
	public HashSet<N> get_input_nets(){
		HashSet<N> inputNets = new HashSet<N>();
		for(T t:this.get_terminals()){
			if(t.is_input_type()){
				inputNets.add(t.get_pin().get_net());
			}
		}
		for(B b:this.get_blocks()){
			for(N n:b.get_input_nets()){
				if(!n.has_source() && n.has_terminals()){
					inputNets.add(n);
				}
			}
		}
		return inputNets;
	}
	public ArrayList<String> get_Orig_input_nets(){
		return this.OrigInputs;
	}
	
	public ArrayList<String> get_Orig_output_nets(){
		return this.OrigOutputs;
	}
	public HashSet<N> get_output_nets(){
		HashSet<N> outputNets = new HashSet<N>();
		for(T t:this.get_terminals()){
			if(t.is_output_type()){
				outputNets.add(t.get_pin().get_net());
			}
		}
		for(B b:this.get_blocks()){
			for(N n:b.get_output_nets()){
				if(n.has_terminals()){
					outputNets.add(n);
				}
			}
		}
		return outputNets;
	}
	
	//// TESTERS ////
	public void netlist_checker(){
		int maxFanout = 10000;
		this.test_fanout(maxFanout);
		this.test_pins();
		this.test_nets();
		this.test_blocks();
	}
	//Fanout
	private String test_fanout(int maxFanout){
		String s = "";
		for(B b:this.get_blocks()){
			int fanout = 0;
			for(N n:b.get_output_nets()){
				fanout += n.fanout();
			}
			if(fanout > maxFanout){
				s += "Block: " + b + "=>";
				for(N out:b.get_output_nets()){
					s += " Net " + out + ": "+ out.fanout() + "|";
				}
				s += "\n";
			}
		}
		return s;
	}

	//Pin
	public void test_pins(){
		this.test_each_pin_has_a_net();
	}
	private void test_each_pin_has_a_net(){
		for(T p:this.get_terminals()){
			if(!p.has_pin()){
				ErrorLog.print("Pin " + p.toString() + " has no pin");
			}
		}
	}

	//Net
	public void test_nets(){
		this.test_each_net_has_two_nodes();
	}
	private void test_each_net_has_two_nodes(){
		for(N n:this.get_nets()){
			n.has_two_nodes();
		}
	}

	//Block
	public void test_blocks(){
		this.test_each_block_has_nets();
	}
	private void test_each_block_has_nets(){
		for(B b:this.get_blocks()){
			if(!b.has_pins()){
				ErrorLog.print("Block " + b.toString() + " has no nets");
			}
		}
	}
	
	//MODELS
	private void add_model(Model model){
		if(!this.has_model(model.get_name())){
			this.models.put(model.get_name(), model);
		}else{
			ErrorLog.print("Model " + model.get_name() + " already added");
		}
	}
	public Model get_model(String name){
		if(this.has_model(name)){
			return this.models.get(name);
		}else{
			ErrorLog.print("Model \"" + name + "\" not available");
			return null;
		}
	}
	private boolean has_model(String name){
		if(this.has_models()){
			return this.models.containsKey(name);
		}else{
			ErrorLog.print("This netlist has no models");
			return false;
		}
	}
	private boolean has_models(){
		return (this.models != null);
	}
	public HashMap<String,Model> get_models(){
		return this.models;
	}

	//// COMMON DATA ////
	private Data get_data(){
		return this.data;
	}
	private void set_blif(String blif){
		this.get_data().set_blif(blif);
	}
	public String get_blif(){
		return this.get_data().get_blif();
	}
	
	public HashSet<B> get_floating_blocks(){
		return this.get_data().get_floating_blocks();
	}
	public boolean has_floating_blocks(){
		if(this.get_data().get_floating_blocks() == null){
			return false;
		}else if(this.get_data().get_floating_blocks().size() > 0){
			return true;
		}else{
			ErrorLog.print("Unexpected situation in the has_floating_blocks function");
			return false;
		}
	}
	//// NETLIST COPY ////
	public Netlist(Part part, Netlist parent){
		this.simulation = parent.simulation;
		
		boolean timingInfo = true;
		Timing timer = new Timing();
		if(timingInfo)timer.start();
		
		this.clocks = new ArrayList<String>(parent.get_clocks());
		this.data = parent.get_data();
		this.models = new HashMap<String,Model>();
		
		this.blockCount = new HashMap<String,Integer>();

		HashMap<String,P> pinTable = new HashMap<String,P>();
		
		//NETS IN NEW NETLIST
		HashSet<N> netsInNewNetlist = this.findNetsInNewNetlist(part);
		HashSet<T> terminalsInNewNetlist = this.findTerminalsInNewNetlist(netsInNewNetlist);

		int maxTerminalNumber = 0;
		for(T t:terminalsInNewNetlist){
			if(t.get_number()>maxTerminalNumber){
				maxTerminalNumber = t.get_number();
			}
		}
		maxTerminalNumber += 1;

		Boolean[] availableTerminalNumbers = new Boolean[maxTerminalNumber];
		for(int i=0; i<maxTerminalNumber; i++){
			availableTerminalNumbers[i] = true;
		}
		for(T t:terminalsInNewNetlist){
			availableTerminalNumbers[t.get_number()] = false;
		}

		this.blocks = new HashMap<Integer,B>();
		this.terminals = new HashMap<Integer,T>();
		this.nets = new HashMap<Integer,N>();
		
		this.initializeAllNetlistElements(part, terminalsInNewNetlist, netsInNewNetlist, parent);
		
		//CONNECT EVERYTHING
		this.connectTerminals(terminalsInNewNetlist);
		this.connectNets(part, pinTable, netsInNewNetlist, maxTerminalNumber, availableTerminalNumbers);
		this.connectBlocks(part, pinTable);
		
		//GENERATE RAM MOLECULES
		this.generateRamMolecules(part);
		
		this.trim();
		
		if(timingInfo)timer.stop();
		if(timingInfo)Info.add("netgen", "atoms" + "\t" + this.atom_count() + "\t" + "required_time" + "\t" + Util.str(timer.time()).replace(".", ","));
	}
	public void updateFamily(Netlist parent){//Parent and children
		this.set_parent(parent);
		parent.add_child(this);
		
		this.children = new ArrayList<Netlist>();
		
		this.level = parent.get_level()+1;
		this.number = parent.get_children().size();
	}
	private HashSet<N> findNetsInNewNetlist(Part part){
		HashSet<N> netsInNewNetlist = new HashSet<N>();
		for(B b:part.getBlocks()){
			for(String inputPort:b.get_input_ports()){
				for(P inputPin:b.get_input_pins(inputPort)){
					netsInNewNetlist.add(inputPin.get_net());
				}
			}
			for(String outputPort:b.get_output_ports()){
				for(P outputPin:b.get_output_pins(outputPort)){
					netsInNewNetlist.add(outputPin.get_net());
				}
			}
		}
		return netsInNewNetlist;
	}
	private HashSet<T> findTerminalsInNewNetlist(HashSet<N> netsInNewNetlist){
		HashSet<T> terminalsInNewNetlist = new HashSet<T>();
		for(N n:netsInNewNetlist){
			if(n.has_terminals()){
				for(P terminalPin:n.get_terminal_pins()){
					terminalsInNewNetlist.add(terminalPin.get_terminal());
				}
			}
		}
		return terminalsInNewNetlist;
	}
	private void initializeAllNetlistElements(Part part, HashSet<T> terminalsInNewNetlist, HashSet<N> netsInNewNetlist, Netlist parent){
		for(B b:part.getBlocks()){
			HardBlockGroup rg = null;
			if(b.hasHardBlockGroup()){
				rg = b.getHardBlockGroup();
			}
			B newB = new B(b.get_name(), b.get_number(), b.get_type(), b.get_clock(), rg);
			if(b.has_atoms()){
				for(B atom:b.get_atoms()){
					newB.add_atom(atom);
				}
			}
			newB.set_truth_table(b.get_truth_table());
			this.add_block(newB);
			
			if(!this.has_model(newB.get_type())){
				this.add_model(new Model(parent.get_model(newB.get_type())));
			}
			this.get_model(newB.get_type()).increment_occurences();
		}
		
		for(T t:terminalsInNewNetlist){
			T newT = new T(t.get_name(),t.get_number(), t.get_type());
			this.add_terminal(newT);
		}
		
		for(N n:netsInNewNetlist){
			N newN = new N(n.get_name(),n.get_number());
			this.add_net(newN);
		}
	}
	private void connectTerminals(HashSet<T> terminalsInNewNetlist){
		for(T t:terminalsInNewNetlist){
			T newT = this.get_terminal(t.get_number());
			N newN = this.get_net(t.get_pin().get_net().get_number());
			if(newT.is_cut_type()){
				P p = new P(newT, newN, "ter", false, false, 1, null, null, false, false);
				newT.set_pin(p);
			}else{
				P p = new P(newT, newN, t.get_pin());
				newT.set_pin(p);
			}
		}
	}
	private void connectNets(Part part, HashMap<String,P> pinTable, HashSet<N> netsInNewNetlist, int maxTerminalNumber, Boolean[] availableTerminalNumbers){
		int pinNumber = 0;
		for(N n:netsInNewNetlist){
			N newN = this.get_net(n.get_number());
			if(n.has_terminals()){
				for(P terminalPin:n.get_terminal_pins()){
					newN.add_terminal_pin(this.get_terminal(terminalPin.get_terminal().get_number()).get_pin());
				}
			}
			if(!n.is_cut()){
				//Add pin if net is cut
				if(n.has_source()){
					B b = n.get_source_pin().get_block();
					if(!part.hasBlock(b)){
						if(pinNumber < maxTerminalNumber){
							while(!availableTerminalNumbers[pinNumber]){
								pinNumber += 1;
								if(pinNumber == maxTerminalNumber){
									break;
								}
							}
							if(pinNumber < maxTerminalNumber){
								availableTerminalNumbers[pinNumber] = false;
							}
						}else{
							pinNumber += 1;
						}
						T newT = new T(n.get_name(), pinNumber, "CUT");
						this.add_terminal(newT);
						
						P pin = new P(newT, newN, "ter", false, false, 1, null, null, false, false);
						newT.set_pin(pin);
						newN.add_terminal_pin(pin);
					}
				}
			}
			if(!n.is_cut()){
				for(P p:n.get_sink_pins()){
					B b = p.get_block();
					if(!part.hasBlock(b)){
						if(pinNumber < maxTerminalNumber){
							while(!availableTerminalNumbers[pinNumber]){
								pinNumber += 1;
								if(pinNumber == maxTerminalNumber){
									break;
								}
							}
							if(pinNumber < maxTerminalNumber){
								availableTerminalNumbers[pinNumber] = false;
							}
						}else{
							pinNumber += 1;
						}
						T newT = new T(n.get_name(), pinNumber, "CUT");
						this.add_terminal(newT);
						
						P pin = new P(newT, newN, "ter", false, false, 1, null, null, false, false);
						newT.set_pin(pin);
						newN.add_terminal_pin(pin);
						
						break;
					}
				}
			}
			//Add source
			if(n.has_source()){
				P sourcePin = n.get_source_pin();
				B sourceBlock = sourcePin.get_block();
				if(part.hasBlock(sourceBlock)){
					B newB = this.get_block(sourceBlock.get_number());
					P p = new P(newB, newN, sourcePin);
					pinTable.put(p.get_id(), p);
					newN.set_source(p);
				}
			}
			//Add all sinks
			for(P sinkPin:n.get_sink_pins()){
				B sinkBlock = sinkPin.get_block();
				if(part.hasBlock(sinkBlock)){
					B newB = this.get_block(sinkBlock.get_number());
					P p = new P(newB, newN, sinkPin);
					pinTable.put(p.get_id(), p);
					newN.add_sink(p);
				}
			}
		}
	}
	private void connectBlocks(Part part, HashMap<String,P> pinTable){
		for(B b:part.getBlocks()){
			B newB = this.get_block(b.get_number());
			for(String inputPort:b.get_input_ports()){
				for(P inputPin:b.get_input_pins(inputPort)){
					N inputNet = inputPin.get_net();
					String hash = "b" + newB.get_number() + "_" + "n" + inputNet.get_number() + "_" + inputPin.get_port();
					P p = pinTable.get(hash);
					newB.add_input_pin(inputPort, p);
				}
			}
			for(String outputPort:b.get_output_ports()){
				for(P outputPin:b.get_output_pins(outputPort)){
					N outputNet = outputPin.get_net();
					String hash = "b" + newB.get_number() + "_" + "n" + outputNet.get_number() + "_" + outputPin.get_port();
					P p = pinTable.get(hash);
					newB.add_output_pin(outputPort, p);
				}
			}
		}
	}
	private void generateRamMolecules(Part part){
		while(part.hasRamMolecule()){
			ArrayList<B> atoms = part.getRamMolecule();
			ArrayList<B> molecule = new ArrayList<B>();
			for(B atom:atoms){
				if(!this.blocks.containsKey(atom.get_number())){
					ErrorLog.print("This netlist does not contain " + atom.toString());
				}
				molecule.add(this.get_block(atom.get_number()));
			}
			this.pack_to_molecule(molecule, "RAM");
		}
	}
	
	public void pre_pack_lut_ff(){
		Timing t = new Timing();
		t.start();

		Output.println("LUT-FF pre partition:");
		
		ArrayList<ArrayList<B>> molecules = new ArrayList<ArrayList<B>>();
		for(N n:this.get_nets()){
			if(n.has_source()){
				if(n.has_sinks()){
					if(n.get_sink_blocks().size() == 1){

						B source = n.get_source_block();
						B sink = n.get_sink_blocks().get(0);
						
						if(source.get_type().equals("stratixiv_lcell_comb")){
							if(sink.get_type().equals("dffeas")){
								if(source.get_output_pins().size() == 1){
									if(n.fanout() == 1){
										if(n.get_sink_pins().iterator().next().get_port().equals("d")){
											ArrayList<B> molecule = new ArrayList<B>();
											molecule.add(source);
											molecule.add(sink);
											
											molecules.add(molecule);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		
		for(ArrayList<B> molecule:molecules){
			this.pack_to_molecule(molecule, "LUT_FF");
		}
		
		Output.println("\tThe design has " + molecules.size() + " LUT-FF pairs out of a total of " + this.get_nets().size() + " nets which is " + Util.round((double)molecules.size() /  this.get_nets().size() * 100, 2) + "%");
		Output.newLine();

		t.stop();
		Output.println("\tTook " + t.toString());
		Output.newLine();
	}
	public void pre_pack_carry(){
		Timing t = new Timing();
		t.start();
		
		Output.println("Carry pre partition:");
		
		ArrayList<ArrayList<B>> carryChains = new ArrayList<ArrayList<B>>();
		int chainStart = 0;
		int chainMiddle = 0;
		int chainEnd = 0;
		
		//TESTER 1
		for(B b:this.get_blocks()){
			if(!b.has_input_port("cin") && b.has_output_port("cout")){
				chainStart++;
			}
			if(b.has_input_port("cin") && b.has_output_port("cout")){
				chainMiddle++;
			}
			if(b.has_input_port("cin") && !b.has_output_port("cout")){
				chainEnd++;
			}
		}
		Output.println("\tchain start:\t" + chainStart);
		Output.println("\tchain middle:\t" + chainMiddle);
		Output.println("\tchain end:\t" + chainEnd);
		Output.newLine();
		
		for(B b:this.get_blocks()){
			if(!b.has_input_port("cin") && b.has_output_port("cout")){
				ArrayList<B> carryChain = new ArrayList<B>();
				carryChain.add(b);
				
				B carryBlock = b;
				while(carryBlock.has_output_port("cout")){
					carryBlock = carryBlock.get_next_block_in_chain("cout");
					if(carryBlock == null) {
						break;
					}
					carryChain.add(carryBlock);
				}
				carryChains.add(carryChain);
			}
		}
		Output.println("\tCarry chains:\t" + carryChains.size());
		Output.newLine();
		
		for(ArrayList<B> carryChain:carryChains){
			this.pack_to_molecule(carryChain, "CARRY_CHAIN");
		}
		
		t.stop();
		Output.println("\tTook " + t.toString());
		
		Output.newLine();
	}
	public void pre_pack_share(){
		Timing t = new Timing();
		t.start();
		
		Output.println("Share pre partition:");
		
		ArrayList<ArrayList<B>> shareChains = new ArrayList<ArrayList<B>>();
		int shareStart = 0;
		int shareMiddle = 0;
		int shareEnd = 0;
		
		//TESTER 1
		for(B b:this.get_blocks()){
			if(!b.has_input_port("sharein") && b.has_output_port("shareout")){
				shareStart++;
			}
			if(b.has_input_port("sharein") && b.has_output_port("shareout")){
				shareMiddle++;
			}
			if(b.has_input_port("sharein") && !b.has_output_port("shareout")){
				shareEnd++;
			}
		}
		Output.println("\tchain start:\t" + shareStart);
		Output.println("\tchain middle:\t" + shareMiddle);
		Output.println("\tchain end:\t" + shareEnd);
		Output.newLine();
		
		for(B b:this.get_blocks()){
			if(!b.has_input_port("sharein") && b.has_output_port("shareout")){
				ArrayList<B> shareChain = new ArrayList<B>();
				shareChain.add(b);
				
				B shareBlock = b;
				while(shareBlock.has_output_port("shareout")){
					shareBlock = shareBlock.get_next_block_in_chain("shareout");
					shareChain.add(shareBlock);
				}
				shareChains.add(shareChain);
			}
		}
		Output.println("\tShare chains:\t" + shareChains.size());
		Output.newLine();
		
		for(ArrayList<B> shareChain:shareChains){
			this.pack_to_molecule(shareChain, "SHARE_CHAIN");
		}
		
		t.stop();
		Output.println("\tTook " + t.toString());
		
		Output.newLine();
	}
	public void pre_pack_dsp(){
		Timing t = new Timing();
		t.start();
		
		Output.println("DSP pre partition:");
		HashSet<ArrayList<B>> dspMolecules = new HashSet<ArrayList<B>>();
		
		
		HashSet<B> multipliers = new HashSet<B>();
		HashSet<B> usedMultipliers = new HashSet<B>();
		ArrayList<B> dspMolecule = new ArrayList<B>();
		for(B mult:this.get_blocks()){
			if(mult.get_type().contains("multiply")){
				multipliers.add(mult);
			}
		}
		for(B acc:this.get_blocks()){
			if(acc.get_type().contains("mac_int")){
				
				dspMolecule.add(acc);
				for(String inputPort:acc.get_input_ports()){
					if(inputPort.equals("a") || inputPort.equals("b")){
						B mult = null;
						for(P inputPin:acc.get_input_pins(inputPort)){
							if(inputPin.get_net().get_source_pin().has_block()){
								if(inputPin.get_net().get_source_pin().get_block().get_type().contains("multiply")){
									if(mult == null){
										mult = inputPin.get_net().get_source_pin().get_block();
									}else if(inputPin.get_net().get_source_pin().get_block().get_number() != mult.get_number()){
										ErrorLog.print("Accumulator " + acc.toString() + " is connected to " + mult.toString() + " and " +  inputPin.get_net().get_source_pin().get_block().toString() + " on input port " + inputPort);
									}
								}else{
									ErrorLog.print("Accumulator " + acc.toString() + " is not connected with multiplier block");
								}
							}else{
								ErrorLog.print("Accumulator " + acc.toString() + " is connected with terminal pin");
							}
						}
						if(usedMultipliers.contains(mult)){
							ErrorLog.print("This multiplier is already connected with an accumulator");
						}else{
							usedMultipliers.add(mult);
						}
						dspMolecule.add(mult);
					}
				}
				if(!dspMolecule.isEmpty()){
					dspMolecules.add(dspMolecule);
				}
			}
		}
		if(usedMultipliers.size() != multipliers.size()){
			Output.println(multipliers.size() - usedMultipliers.size() + " multipliers are not connected to an accumulator");
			//IF ONLY MULTIPLIERS EXIST IN THE NETLIST THEN ADD THEM TO THE DSP MOLECULE.
			for(B mult:multipliers){
				dspMolecule.add(mult);
				if(!dspMolecule.isEmpty()){
					dspMolecules.add(dspMolecule);
				
				}
			}		
	
		}
		Output.println("\t DSP: " + dspMolecules.size());
		Output.newLine();
		
		if((multipliers.size() - usedMultipliers.size()) == 0) {
			for(ArrayList<B> atoms:dspMolecules){
				this.pack_to_molecule(atoms, "dsp");
			}
		}
		t.stop();
		Output.println("\tTook " + t.toString());
		
		Output.newLine();
	}
	// PRE PACK RAM
	public HashSet<B> get_ram_blocks(){
		HashSet<B> ramBlocks = new HashSet<B>();
		for(B b:this.get_blocks()){
			if(b.get_type().contains("port_ram")){
				ramBlocks.add(b);
			}
		}
		return ramBlocks;
	}
	// PRE PACK DSP FOR KOIOS BENCHMARKS
	public HashSet<B> get_dsp_blocks(){
		HashSet<B> dspBlocks = new HashSet<B>();
		for(B b:this.get_blocks()){
			if(b.get_type().contains("multiply")){
				dspBlocks.add(b);
			}
		}
		return dspBlocks;
	}
	private void print_ram_blocks(HashSet<B> ramBlocks){
		Output.println("\tRAM Slices:");
		HashMap<String,Integer> ramTypes = new HashMap<String,Integer>();
		for(B ram:ramBlocks){
			ramTypes.put(ram.get_type(), 0);
		}
		for(B ram:ramBlocks){
			ramTypes.put(ram.get_type(), ramTypes.get(ram.get_type()) + 1);
		}
		for(String type:ramTypes.keySet()){
			Output.println("\t\t" + Util.fill(ramTypes.get(type), 6) + " " + type);
		}
		Output.newLine();
	}
	private void print_dsp_blocks(HashSet<B> ramBlocks){
		Output.println("\tDSP Slices:");
		HashMap<String,Integer> ramTypes = new HashMap<String,Integer>();
		for(B ram:ramBlocks){
			ramTypes.put(ram.get_type(), 0);
		}
		for(B ram:ramBlocks){
			ramTypes.put(ram.get_type(), ramTypes.get(ram.get_type()) + 1);
		}
		for(String type:ramTypes.keySet()){
			Output.println("\t\t" + Util.fill(ramTypes.get(type), 6) + " " + type);
		}
		Output.newLine();
	}
	
	private HashMap<String,ArrayList<B>> get_ram_groups(HashSet<B> ramBlocks){
		HashMap<String,ArrayList<B>> hashedRam = new HashMap<String,ArrayList<B>>();
		for(B ram:ramBlocks){
			String hash = ram.get_hash();
			if(!hashedRam.containsKey(hash)){
				hashedRam.put(hash, new ArrayList<B>());
			}
			hashedRam.get(hash).add(ram);
		}
		return hashedRam;
	}
	private void print_hashed_ram(HashMap<String,ArrayList<B>> hashedRam){
		boolean printHashedRam = false;
		if(printHashedRam){
			Output.println("\tTOTAL NUMBER OF HASHES: " + hashedRam.size());
			for(String hash:hashedRam.keySet()){

				Model model = this.get_model(hashedRam.get(hash).get(0).get_type());

				Output.println("\t\t" + Util.fill(hashedRam.get(hash).size(), 5) + "ram blocks | " + Util.fill(model.get_ram_slices(), 2) + " M20K | " + " | " + hashedRam.get(hash).get(0).get_type() + " | " + hash);	
			}
			Output.newLine();
		}
	}

	public ArrayList<B> getBlocksOfType(String type){
		ArrayList<B> blocks = new ArrayList<B>();
		for(B b:this.get_blocks()){
			if(b.get_type().equals(type)){
				blocks.add(b);
			}
		}
		return blocks;
	}
	public void pre_pack_dsp_ram(Architecture architecture) {
		Timing t = new Timing();
		t.start();
		Output.println("DSP & RAM pre partition:");
		Output.newLine();
		
		HashSet<B> ramBlocks = this.get_ram_blocks();
		this.print_ram_blocks(ramBlocks);
		

		int halfDSP = 0;
		for(B b:this.get_blocks()){

			if(b.get_type().equals("multiply")){
				halfDSP += 1;
			}
		}
		int reqDSP = (int)Math.ceil(halfDSP);
		int req20K = 0;

		for(B ram:this.get_blocks()) {
			if(ram.get_type().contains("port_ram")){
				req20K+=1;
			}
		}

		
		t.stop();
		Output.println("\tTook " + t.toString());
		Output.newLine();
	}
	public void pre_pack_ram(Architecture architecture){ 
		Timing t = new Timing();
		t.start();
		Output.println("RAM pre partition:");
		Output.newLine();
		
		HashSet<B> ramBlocks = this.get_ram_blocks();
		this.print_ram_blocks(ramBlocks);
		
		HashMap<String, ArrayList<B>> hashedRam = this.get_ram_groups(ramBlocks);
		this.print_hashed_ram(hashedRam);
		
		
		///////////////////////////////////////////////////
		//////////// CALCULATE REQUIRED BLOCKS ////////////
		int halfDSP = 0;
		for(B b:this.get_blocks()){
			if(b.get_type().equals("multiply")){
				halfDSP += 1;
			}
		}
		int reqDSP = (int)Math.ceil(halfDSP);

		
		int req20K = 0;

		for(String hash:hashedRam.keySet()){
			Model model = this.get_model(hashedRam.get(hash).get(0).get_type());
			if(model.get_ram_slices() != 0) {
				req20K += Math.ceil((1.0*hashedRam.get(hash).size())/model.get_ram_slices());
			}
		}
		
		//MAKE FPGA
		FPGA fpga = new FPGA();
		fpga.set_hardbock_size(architecture.getFPGAdimensionsMap());
		fpga.set_size(architecture.getSizeX(), architecture.getSizeY());
		Output.println("\tFIXED FPGA SIZE: " + fpga.sizeX() + " x " + fpga.sizeY());
		Output.println("\t\tLAB | AV: " + Util.fill(fpga.LAB(), 3));
		Output.println("\t\tDSP | AV: " + Util.fill(fpga.DSP(), 3) + " | REQ: " + reqDSP);
		Output.println("\t\tM20K | AV: " + Util.fill(fpga.M20K(), 3) + " | REQ: " + req20K);
		Output.newLine();

		
		if(req20K > fpga.M20K() || reqDSP > fpga.DSP()){
			ErrorLog.print("Larger FPGA size required");
		}

		
		this.print_hashed_ram(hashedRam);
		
		//PACK TO RAM MOLECULE IF ALL SLICES FIT INTO A SINGLE RAM OR IF A RAM CONTAINS ONLY ONE SLICE
		HashSet<ArrayList<B>> ramMolecules = new HashSet<ArrayList<B>>();
		HashSet<String> removedHashes = new HashSet<String>();
		for(String hash:hashedRam.keySet()){
			ArrayList<B> slices = hashedRam.get(hash);
			Model model = this.models.get(slices.get(0).get_type());
			int M20K = model.get_ram_slices();

			if(M20K > 0){
				if(slices.size() <= M20K){
					ramMolecules.add(hashedRam.get(hash));
					removedHashes.add(hash);
				}else if(M20K == 1){
					for(B slice:hashedRam.get(hash)){
						ArrayList<B> temp = new ArrayList<B>();
						temp.add(slice);
						ramMolecules.add(temp);
					}
					removedHashes.add(hash);
				}
			}

			else{
				ErrorLog.print(model.get_name());
			}
		}
		for(String hash:removedHashes){
			hashedRam.remove(hash);
		}
		int[] M20K = new int[2];
		int[] M9K = new int[2];
		int[] M144K= new int[2];
		
		for(ArrayList<B> ramAtoms:ramMolecules){
			String type = ramAtoms.get(0).get_type();
			if(type.contains("port_ram")) {
				M20K[0] +=1;
			}
			this.pack_to_molecule(ramAtoms, "RAM");
		}
	
		this.print_hashed_ram(hashedRam);
		
		//ADD AN RGROUP FOR THE REMAINING RAM BLOCKS
		for(String hash:hashedRam.keySet()){
			Model model = this.models.get(hashedRam.get(hash).get(0).get_type());
			HardBlockGroup rg = new HardBlockGroup(hashedRam.get(hash), model);
			for(B b:hashedRam.get(hash)){
				b.setHardBlockGroup(rg);
			}
			
			String type = hashedRam.get(hash).get(0).get_type();
			if(type.contains("port_ram")) {
				M20K[1] += Math.ceil((1.0*hashedRam.get(hash).size())/model.get_ram_slices());
			}else if(type.contains("M9K") && type.contains("M144K")){
				ErrorLog.print("Invalid type: " + type);
			}else if(type.contains("M9K")){
				//M9K[1] += Math.ceil((1.0*hashedRam.get(hash).size())/model.get_stratixiv_ram_slices_9());
			}else if(type.contains("M144K")){
				//M144K[1] += Math.ceil((1.0*hashedRam.get(hash).size())/model.get_stratixiv_ram_slices_144());
			}else{
				ErrorLog.print("Unrecognized type: " + type);
			}
		}
		Output.println("\tRAM statistics:");
		Output.println("\t\tPacked to single molecule  | " + "M20K:   " + Util.fill(M20K[0], 4));
		Output.println("\t\tHierarchical RAM packing   | " + "M20K:   " + Util.fill(M20K[1], 4));
		Output.println("\t\tTotal amount of RAM blocks | " + "M20K:   " + Util.fill((M20K[0]+M20K[1]), 4));
		Output.newLine();
		
		t.stop();
		Output.println("\tTook " + t.toString());
		Output.newLine();
	}
	public double unpack_all_molecules(){
		Timing t = new Timing();
		t.start();
		ArrayList<B> molecules = new ArrayList<B>();
		
		for(B b:this.get_blocks()){
			if(Util.isMoleculeType(b.get_type())){
				molecules.add(b);
			}
		}
		for(B molecule:molecules){
			unpack_to_atoms(molecule);
		}
		t.stop();
		return t.time();
	}
	public void pack_to_molecule(ArrayList<B> atoms, String moleculeType){
		String clock = null;
		for(B atom:atoms){
			
			if(atom.has_clock()){
				if(clock == null){
					clock = atom.get_clock();
				}else if(!clock.equals(atom.get_clock())){
					ErrorLog.print("Molecule has multiple clocks:\n\t" + clock + "\n\t" + atom.get_clock());
				}
			}
		}
		
		//Generate molecule block
		B molecule = new B(atoms.get(0).get_name(), atoms.get(0).get_number(), moleculeType, clock, null/*A MOLECULE CAN NOT BE A PART OF A RGROUP*/);
		for(B atom:atoms){
			molecule.add_atom(atom.clean_copy());
		}
		
		//Connect everything
		HashSet<N> removedNets = new HashSet<N>();
		
		//INPUT PINS
		for(B atom:atoms){
			for(String inputPort:atom.get_input_ports()){
				for(P inputPin:atom.get_input_pins(inputPort)){
					N inputNet = inputPin.get_net();
					inputNet.remove_sink(inputPin);
					
					boolean localGeneratedNet = false;
					if(inputNet.has_source()){
						B sourceBlock = inputNet.get_source_pin().get_block();
						if(atoms.contains(sourceBlock)){
							localGeneratedNet = true;
						}
					}
					if(!localGeneratedNet){
						P p = new P(molecule, inputNet, atom.get_name() + "." + inputPin.get_port(), inputPin.is_source_pin(), inputPin.is_sink_pin(), inputPin.get_net_weight(), inputPin.get_arrival_time(), inputPin.get_required_time(), inputPin.is_start_pin(), inputPin.is_end_pin());
						inputNet.add_sink(p);
						if(!inputPin.get_port_name().equals(inputPort)){
							ErrorLog.print("inputPin.get_port_name(): " + inputPin.get_port_name() + " | inputPort:" + inputPort);
						}
						molecule.add_input_pin(atom.get_name() + "." + inputPin.get_port_name(), p);
					}
				}
			}
		}
		//OUTPUT PINS
		for(B atom:atoms){
			for(String outputPort:atom.get_output_ports()){
				for(P outputPin:atom.get_output_pins(outputPort)){
					N outputNet = outputPin.get_net();
					boolean netHasOutsideConnections = false;
					if(outputNet.has_terminals()){
						netHasOutsideConnections = true;
					}else if(outputNet.has_sinks()){//The inputpins of all atom blocks are removed, so if a net still has a sink, then this is a block outside this molecule
						netHasOutsideConnections = true;
					}
					if(netHasOutsideConnections){
						outputNet.remove_source(outputPin);
						
						P p = new P(molecule, outputNet, atom.get_name() + "." + outputPin.get_port(), outputPin.is_source_pin(), outputPin.is_sink_pin(), outputPin.get_net_weight(), outputPin.get_arrival_time(), outputPin.get_required_time(), outputPin.is_start_pin(), outputPin.is_end_pin());
						outputNet.set_source(p);
						if(!outputPin.get_port_name().equals(outputPort)){
							ErrorLog.print("outputPin.get_port_name(): " + outputPin.get_port_name() + " | outputPort:" + outputPort);
						}
						molecule.add_output_pin(atom.get_name() + "." + outputPin.get_port_name(), p);
					}else{
						removedNets.add(outputNet);
					}
				}
			}
		}
		for(N n:removedNets){
			this.remove_net(n);
		}

		//Remove and add netlist elements
		for(B atom:atoms){
			this.remove_block(atom);
			this.get_models().get(atom.get_type()).decrement_occurences();
		}
		this.add_block(molecule);
		if(!this.has_model(moleculeType)){
			Model model = new Model(moleculeType, this.simulation.getStringValue("result_folder") + this.simulation.getStringValue("architecture"));
			this.add_model(model);
		}
		this.get_model(moleculeType).increment_occurences();
	}
	public void unpack_to_atoms(B molecule){	
		//Remove molecule
		for(String inputPort:molecule.get_input_ports()){
			for(P inputPin:molecule.get_input_pins(inputPort)){
				N inputNet = inputPin.get_net();
				inputNet.remove_sink(inputPin);
			}
		}
		for(String outputPort:molecule.get_output_ports()){
			for(P outputPin:molecule.get_output_pins(outputPort)){
				N outputNet = outputPin.get_net();
				outputNet.remove_source(outputPin);
			}
		}
		this.remove_block(molecule);
		this.get_model(molecule.get_type()).decrement_occurences();
		
		//Add atoms
		for(B cleanAtom:molecule.get_atoms()){
			HardBlockGroup rg = null;
			if(cleanAtom.hasHardBlockGroup()){
				rg = cleanAtom.getHardBlockGroup();
			}
			this.add_block(new B(cleanAtom.get_name(), cleanAtom.get_number(), cleanAtom.get_type(), cleanAtom.get_clock(), rg));
			if(!this.has_model(cleanAtom.get_type())){
				//Search for the the model in the tree structure, if an atom in this unpacked block contains a model, then one of the parents should have this model
				Model atomModel = null;
				Netlist parent = this.get_parent();
				while(atomModel == null){
					if(parent.has_models()){
						if(parent.has_model(cleanAtom.get_type())){
							atomModel = parent.get_model(cleanAtom.get_type());
						}else if(parent.has_parent()){
							parent = parent.get_parent();
						}else{
							ErrorLog.print("Model \"" + cleanAtom.get_type() + "\" not found");
						}
					}else if(parent.has_parent()){
						parent = parent.get_parent();
					}else{
						ErrorLog.print("Model \"" + cleanAtom.get_type() + "\" not found");
					}
				}
				Model model = new Model(atomModel);
				this.add_model(model);
			}
			this.get_model(cleanAtom.get_type()).increment_occurences();
		}
		
		//Add nets
		for(B cleanAtom:molecule.get_atoms()){
			for(P inputPin:cleanAtom.get_input_pins()){
				N inputNet = inputPin.get_net();
				if(!this.has_net(inputNet)){
					this.add_net(new N(inputNet.get_name(), inputNet.get_number()));
				}
			}
			for(P outputPin:cleanAtom.get_output_pins()){
				N outputNet = outputPin.get_net();
				if(!this.has_net(outputNet)){
					this.add_net(new N(outputNet.get_name(), outputNet.get_number()));
				}
			}
		}
		
		//Connect everything
		for(B cleanAtom:molecule.get_atoms()){
			int atomNumber = cleanAtom.get_number();
			B atom = this.get_block(atomNumber);
			for(String inputPort:cleanAtom.get_input_ports()){	
				for(P inputPin:cleanAtom.get_input_pins(inputPort)){
					int inputNetNumber = inputPin.get_net().get_number();
					N inputNet = this.get_net(inputNetNumber);
					P p = new P(atom, inputNet, inputPin);
					inputNet.add_sink(p);
					atom.add_input_pin(inputPort, p);
				}
			}
			for(String outputPort:cleanAtom.get_output_ports()){
				for(P outputPin:cleanAtom.get_output_pins(outputPort)){
					int outputNetNumber = outputPin.get_net().get_number();
					N outputNet = this.get_net(outputNetNumber);
					P p = new P(atom, outputNet, outputPin);
					outputNet.set_source(p);
					atom.add_output_pin(outputPort, p);
				}
			}
		}
	}

	//// GETTERS ////
	public ArrayList<String> get_clocks(){
		return this.clocks;
	}
	
	//BLOCKS
	public Collection<B> get_blocks(){
		return this.blocks.values();
	}
	public ArrayList<B> get_ordered_block_array(){
		ArrayList<B> res = new ArrayList<B>();
		int i = 0;
		while(res.size() < this.blocks.size()){
			if(this.blocks.containsKey(i)){
				res.add(this.blocks.get(i));
			}
			i += 1;
		}
		return res;
	}
	public ArrayList<B> get_block_array(){
		return new ArrayList<B>(this.get_blocks());
	}
	public B get_block(){
		if(this.block_count()==1){
			return this.get_block_array().get(0);
		}else{
			ErrorLog.print("The netlist " + this.toString() + " does not have exactly one block" + " => " + this.block_count());
			return null;
		}
	}
	public B get_block(int blockNumber){
		return this.blocks.get(blockNumber);
	}
	//PINS
	public Map<Integer,T> get_pin_map(){
		return this.terminals;	
	}
	public Collection<T> get_terminals(){
		return this.terminals.values();	
	}
	public T get_terminal(int pinNumber){
		return this.terminals.get(pinNumber);
	}
	//NETS
	public Collection<N> get_nets(){
		return this.nets.values();	
	}
	public ArrayList<N> get_net_array(){
		return new ArrayList<N>(this.get_nets());
	}
	public N get_net(int netNumber){
		return this.nets.get(netNumber);
	}
	
	//AMOUNT
	public int atom_count(){
		int atomCount = 0;
		for(B b:this.get_blocks()){
			if(b.has_atoms()){
				atomCount += b.get_atoms().size();
			}else{
				atomCount += 1;
			}
		}
		return atomCount;
	}
	public int block_count(){
		if(this.blocks == null){
			int sum = 0;
			for(String blockType:this.get_models().keySet()){
				sum += this.block_count(blockType);
			}
			return sum;
		}else{
			return this.blocks.size();
		}
	}
	public int block_count(String blockType){
		if(this.blockCount == null){
			ErrorLog.print("Problem with blockCount");
			return 0;
		}else if(!this.blockCount.containsKey(blockType)){
			//ErrorLog.print("BlockType not found in blockCount");
			return 0;
		}else{
			return this.blockCount.get(blockType);
		}
	}
	public int net_count(){
		return this.nets.size();
	}
	public int pin_count(){
		return this.terminals.size();
	}

	//TERMINALS
	public int get_terminal_count(){
		if(this.cleaned){
			return this.terminalCount;
		}else{
			this.calculate_terminal_count();
			return this.terminalCount;
		}
	}
	public void calculate_terminal_count(){
		this.terminalCount = 0;
		for(N n:this.get_nets()){
			if(n.has_terminals()){
				this.terminalCount += 1;
			}
		}
	}
	//LEVEL
	public int get_level(){
		if(this.level == -1){
			Output.println("The level of the netlist is equal to -1!");
		}
		return this.level;
	}
	public int get_number(){
		if(this.number == -1){
			Output.println("The number of the netlist is equal to -1!");
		}
		return this.number;
	}	
	//// CHILDREN ////
	public void add_child(Netlist child){
		this.children.add(child);
	}
	public ArrayList<Netlist> get_children(){
		return this.children;
	}
	public boolean has_children(){
		if(this.get_children().isEmpty()){
			return false;
		}else{
			return true;
		}
	}
	public void remove_children(){
		this.children = new ArrayList<Netlist>();
	}
	public void set_parent(Netlist parent){
		if(this.parent == null){
			this.parent = parent;
		}else{
			ErrorLog.print("This netlist " + this.toString() + " already has a parent");
		}
	}
	public Netlist get_parent(){
		return this.parent;
	}
	public boolean has_parent(){
		if(this.parent!=null){
			return true;
		}else{
			return false;
		}
	}
	public ArrayList<B> get_unconnected_blocks(){
		ArrayList<B> unconnectedBlocks = new ArrayList<B>();
		for(B b:this.get_blocks()){
			if(!b.is_connected_with_other_block()){
				unconnectedBlocks.add(b);
			}
		}
		return unconnectedBlocks;
	}
	
	//// TRIM AND CLEAN UP////
	public void trim(){
		for(B b:this.get_blocks()){
			b.trim();
		}
	}
	public void clean_up(){
		if(!this.cleaned){
			this.calculate_terminal_count();
				
			for(B b:this.get_blocks()){
				b.clean_up();
			}
			for(T p:this.get_terminals()){
				p.clean_up();	
			}
			for(N n:this.get_nets()){
				n.clean_up();
			}
			this.terminals = null;
			this.blocks = null;
			this.nets = null;
			this.clocks = null;
			this.models = null;
			
			this.cleaned = true;
		}
	}
	
	//// TOSTRING ////
	public String toString(){
		return this.get_blif() + "_" + toNumberString();

	}
	public String toNumberString(){
		String s = new String();
		
		int level_length = 2;
		int number_length = 3;
		
		String str_level = Util.str(this.get_level());
		if(str_level.length()>level_length)ErrorLog.print("Problem in netlist_number: the level length is to small");
		String str_number = Util.str(this.get_number());
		if(str_number.length()>number_length)ErrorLog.print("Problem in netlist_number: the number length is to small");
		for(int i = str_level.length(); i<level_length; i++){
			s += "0";
		}
		s += str_level;
		
		for(int i = str_number.length(); i<number_length; i++){
			s += "0";
		}
		s += str_number;
		
		return s;
	}
	public String toTypesString(){
		String s = new String();
		int maxLength1 = 0;
		int maxLength2 = 0;
		for(String blockType:this.get_models().keySet()){
			if(blockType.length() > maxLength1){
				maxLength1 = blockType.length();
			}
			if(Util.str(this.block_count(blockType)).length() > maxLength2){
				maxLength2 = Util.str(this.block_count(blockType)).length();
			}
			
		}
		for(String blockType:this.get_models().keySet()){
			if(this.block_count(blockType) != 0){
				s += "\t" + blockType;
				int l1 = blockType.length();
				while(l1<maxLength1){
					s += " ";
					l1 += 1;
				}
				s += "   " + this.block_count(blockType);
				int l2 = Util.str(this.block_count(blockType)).length();
				while(l2<maxLength2){
					s += " ";
					l2 += 1;
				}
				s += "   " + Util.round((100.0*this.block_count(blockType))/this.block_count(), 2) + "%\n";
			}
		}
		return s;
	}
	public String toInfoString(){
		String s = new String();
		s += this.get_blif();
		s += " -- ";
		s += this.block_count() + " blocks";
		s += " -- ";
		s += this.net_count() + " nets";
		
		int inputPins = 0;
		int outputPins = 0;
		for(B b:this.get_blocks()){
			inputPins += b.get_input_pins().size();
			outputPins += b.get_output_pins().size();
		}
		s += " -- ";
		s += "input pins: " + inputPins;
		s += " -- ";
		s += "output pins: " + outputPins;
		
		return s;
	}

	//// PACKING ////
	public List<Netlist> get_leaf_nodes(){
		List<Netlist> result = new ArrayList<Netlist>();
		this.getLeafNodes(result);
		return result;
	}
	private void getLeafNodes(List<Netlist> result){
		if(this.has_children()){
			for(Netlist child:this.children){
				child.getLeafNodes(result);
			}
		}else{
			result.add(this);
		}
	}

	//FLOATING BLOCKS
	public void floating_blocks(){
		ArrayList<B> floatingBlocks = this.get_unconnected_blocks();
		if(!floatingBlocks.isEmpty()){
			Output.println("Floating blocks:");
			for(B b:floatingBlocks){
				if(b.is_connected_with_other_block()){
					Output.println("Block " + b.toString() + " is not a floating block");
				}
				this.get_data().add_floating_block(b);
				this.remove_block(b);
				for(N n:b.get_nets()){
					if(n.get_blocks().size() != 1){
						Output.println("Net " + n.get_name() + " has " + n.get_blocks().size() + " blocks");
					}
					if(!n.has_terminals()){
						Output.println("Net " + n.get_name() + " has no terminal!");
					}
					for(P terminalPin:n.get_terminal_pins()){
						this.remove_terminal(terminalPin.get_terminal());
					}
					this.remove_net(n);
				}
			}
			Output.println("\t" + floatingBlocks.size() + " floating blocks removed");
			for(B floatingBlock:floatingBlocks){
				Output.println("\t\t" + floatingBlock.get_name());
			}
			Output.newLine();
		}
	}
	public void test_dsp_distribution(){
		ArrayList<String> errorLines = new ArrayList<String>();
		ArrayList<Netlist> work = new ArrayList<Netlist>();
		work.add(this);
		while(!work.isEmpty()){
			Netlist netlist = work.remove(0);
			if(netlist.has_children()){
				work.addAll(netlist.get_children());
			}else if(netlist.block_count("HALF_DSP")%2 == 1){
				errorLines.add("Netlist " + netlist.toNumberString() + " has " + netlist.block_count("HALF_DSP") + " HALF DSP Molecules");
			}
		}
		if((this.block_count("HALF_DSP")%2 == 1) && (errorLines.size() > 1)){
			Output.println("Problem in hierarchical DSP partitioning:");
			for(String error:errorLines){
				Output.println("\t" + error);
			}
			Output.newLine();
		}else if((this.block_count("HALF_DSP")%2 == 0) && (errorLines.size() > 0)){
			Output.println("Problem in hierarchical DSP partitioning:");
			for(String error:errorLines){
				Output.println("\t" + error);
			}
			Output.newLine();
		}
	}

	
	//Hierarchy
	public void setRecursiveHierarchyIdentifier(String val){
		//Test functionality

		if(this.children.size() > 2){
			ErrorLog.print("Each netlist should have a maximum of 2 children");
		}
		if(this.children.size() == 1){
			ErrorLog.print("Each netlist should have 0 or 2 children");
		}

		this.hierarchyIdentifier = val;
		
		int index = 0;
		for(Netlist child:this.children){
			child.setRecursiveHierarchyIdentifier(this.hierarchyIdentifier + Util.str(index));
			index++;
		}
	}
	//For die level. Without the child check
	public void setDieRecursiveHierarchyIdentifier(String val){
		//Test functionality
		
		this.hierarchyIdentifier = val;
		
		int index = 0;
		for(Netlist child:this.children){
			child.setRecursiveHierarchyIdentifier(this.hierarchyIdentifier + Util.str(index));
			index++;
		}
	}
	public String getHierarchyIdentifier(){
		return this.hierarchyIdentifier;
	}
	public void addLogicBlock(LogicBlock logicBlock){
		if(this.children.size() != 0){
			ErrorLog.print("Only leaf nodes can have logic blocks");
		}
		if(this.logicBlocks == null){
			this.logicBlocks = new ArrayList<LogicBlock>();
		}
		this.logicBlocks.add(logicBlock);
	}
	public void updateEnabledLogicBlocks(){
		List<LogicBlock> temp = this.logicBlocks;
		this.logicBlocks = new ArrayList<LogicBlock>();
		for(LogicBlock block:temp){
			if(block.enabled()){
				this.logicBlocks.add(block);
			}
		}
	}
	public List<LogicBlock> getLogicBlocks(){
		return this.logicBlocks;
	}
}
