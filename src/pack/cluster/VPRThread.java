package pack.cluster;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import pack.main.Simulation;
import pack.netlist.Netlist;
import pack.util.Info;
import pack.util.Output;
import pack.util.Util;

public class VPRThread {
	private int thread;
	private Process proc;
	private Simulation simulation;
	private String run;
	private int size;
	private Netlist netlist;
	private int PartNum;

	public VPRThread(int thread, Simulation simulation, Netlist netlist, int DieNum){
		this.thread = thread;
		this.simulation = simulation;
		this.run = new String();
		this.netlist = netlist;
		this.PartNum = DieNum;
	}
	public VPRThread(Simulation simulation, Netlist netlist, int DieNum){
		this.simulation = simulation;
		this.run = new String();
		this.netlist = netlist;
		this.PartNum = DieNum;
	}
	public void run(int size){
		String circuit = this.simulation.getStringValue("circuit");
		String vpr_folder = this.simulation.getStringValue("vpr_folder");
		String result_folder = this.simulation.getStringValue("result_folder");
		String ArchName = "arch.light_" + this.PartNum + ".xml";
		
		
		if(vpr_folder.lastIndexOf("/") != vpr_folder.length() - 1) vpr_folder += "/";
		if(result_folder.lastIndexOf("/") != result_folder.length() - 1) result_folder += "/";
		
		this.run = new String();
		this.run += vpr_folder + "vpr/vpr" + " ";
    	this.run += result_folder + ArchName+ " ";
    	
    	this.run += vpr_folder + "vpr/files/" + circuit + "_" + this.simulation.getSimulationID() + "_" + this.thread + ".blif" + " ";
    	this.run += "--pack" + " " + "--absorb_buffer_luts" + " " + "on";
   
    	ProcessBuilder pb = new ProcessBuilder(this.run.split(" "));
    	try {
    		this.proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(this.proc.getInputStream()));
                 String line;
                 while ((line = reader.readLine()) != null) {
           }
	
        }catch (IOException e) {
			Output.println("Problems with vpr process");
			e.printStackTrace();
		}
    	
		Output.println("\t\t" + circuit + " | " + "Thread: " + Util.fill(this.thread,2) + " | Blocks: " + Util.fill(size,5));
		
		this.size = size;
	}
	
	public void runaapack(int size){
		String circuit = simulation.getStringValue("circuit") + "_" + this.simulation.getIntValue("simulation_ID") +"_die_" + this.PartNum + ".blif";
		String vpr_folder = simulation.getStringValue("vpr_folder");
		String result_folder = simulation.getStringValue("result_folder");
		String arch_name = simulation.getStringValue("architecture");
		
		
		if(vpr_folder.lastIndexOf("/") != vpr_folder.length() - 1) vpr_folder += "/";
		if(result_folder.lastIndexOf("/") != result_folder.length() - 1) result_folder += "/";
		
		this.run = new String();
		this.run += vpr_folder + "vpr/vpr" + " ";
    	this.run += result_folder + arch_name+ " ";
		
    	this.run += result_folder + "/" + circuit + " ";;
    	this.run += "--pack" + " " + "--absorb_buffer_luts" + " " + "off";
   
    	ProcessBuilder pb = new ProcessBuilder(this.run.split(" "));
    	try {
    		this.proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(this.proc.getInputStream()));
                 String line;
                 while ((line = reader.readLine()) != null) {
           }
	
        }catch (IOException e) {
			Output.println("Problems with vpr process");
			e.printStackTrace();
		}
    	
		Output.println("\t\t" + circuit + " | | Blocks: " + Util.fill(size,5));
		
		this.size = size;
	}

	public boolean isRunning(int i) {
		try {
			this.proc.exitValue();

			BufferedReader reader =  new BufferedReader(new InputStreamReader(this.proc.getInputStream()));

			String line = reader.readLine();
			double runtime = 0.0;
			while(line != null){
				if(line.contains("Packing took")){
					line = line.replace("Packing took ", "");
					line = line.replace(" seconds", "");
					runtime = Double.parseDouble(line);
				}
				line = reader.readLine();
			}
			String output = "size" + "\t" + this.size + "\t" + "total_runtime" + "\t" + Util.round(runtime, 4) + "\t" + "runtime_per_block" + "\t" + Util.round(runtime/this.size*1000.0, 4);
			output = output.replace(".", ",");
			Info.add("rpb", output);
			return false;
		} catch (Exception e) {
			return true;
		}
	}

	public String getCommand(){
		return this.run;
	}
	public int getThread(){
		return this.thread;
	}
	public Netlist getNetlist(){
		return this.netlist;
	}
}
