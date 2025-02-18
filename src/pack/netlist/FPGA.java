package pack.netlist;

import pack.util.ErrorLog;
import pack.util.Output;

import java.util.ArrayList;
import java.util.Map;

import pack.architecture.Architecture;
import pack.main.Simulation;
import pack.partition.Partition;



public class FPGA {
//	private Architecture architecture;
	private int[] arch;
	private int maxSize;
	
	private int sizeX;
	private int sizeY;
	private ArrayList<String> lines;
	private int availableLAB;
	private int availableDSP;
	private int availablePLL;
	private int availableM20K;
	//DSP, RAM block size
	private int DSPht;
	private int RAMht;
	private int DSPwt;
	private int RAMwt;
	//DSP, RAM start and repeat locations
	private int DSPx;
	private int RAMx;
	private int DSPstartx;
	private int RAMstartx;
	private int RAMpriority;
	private int DSPpriority;
	
	public FPGA(){
		this.maxSize = 500;
		this.arch = new int[this.maxSize];
	
		
		this.sizeX = 0;
		this.sizeY = 0;
		
		this.update_available_blocks();
	}
	public void set_size(int sizeX,int sizeY){
		this.sizeX = sizeX;
		this.sizeY = sizeY;

		if(this.sizeX > this.maxSize){
			ErrorLog.print("Maximum sizeX of " + this.maxSize + " exceeded => " + this.sizeX);
		}
		this.update_available_blocks();
	}
	public void set_hardbock_size(Map<String, Integer> alldimensions){
		this.DSPx = alldimensions.get("DSPx");
		this.DSPht = alldimensions.get("DSPht");
		this.DSPwt = alldimensions.get("DSPwt");
		this.DSPstartx = alldimensions.get("DSPstartx");
		this.DSPpriority = alldimensions.get("DSPpriority");

		this.RAMht = alldimensions.get("RAMht");
		this.RAMwt = alldimensions.get("RAMwt");
		this.RAMx = alldimensions.get("RAMx");
		this.RAMstartx = alldimensions.get("RAMstartx");
		this.RAMpriority = alldimensions.get("RAMpriority");
		
		if(this.DSPpriority >= this.RAMpriority)
		{
			Output.println("DSP is on higher priorty");
			this.DSPpriority = 2;
			this.RAMpriority = 3;
		}else {
			Output.println("RAM is on higher priorty");
			this.DSPpriority = 3;
			this.RAMpriority = 2;
		}
		for(int i=0;i<this.maxSize;i++){
			this.arch[i] = 1;//LAB
		}
		for(int i=this.RAMstartx;i<this.maxSize;i+=this.RAMx){
			this.arch[i] = this.RAMpriority;//"M20K"
		}
		for(int i=this.DSPstartx;i<this.maxSize;i+=this.DSPx){
			this.arch[i] = this.DSPpriority;//"DSP"
		}


	}
	public void increase_size(){
		this.sizeY += 1;
		this.sizeX = (int)Math.round(sizeY * 1.5);
		if(this.sizeX > this.maxSize){
			ErrorLog.print("Maximum sizeX of " + this.maxSize + " exceeded => " + this.sizeX);
		}
		this.update_available_blocks();
	}
	private void update_available_blocks(){
		this.availableLAB = 0;
		this.availableM20K = 0;
		this.availableDSP = 0;
		this.availablePLL = 0;
		
		for(int a=1;a<=this.sizeX;a++){
			switch (this.arch[a]) {
				case 1: this.availableLAB += this.sizeY; break;
				case 2: this.availableM20K += Math.floor(this.sizeY*(1/(double)this.RAMht)); break;
				case 3: this.availableDSP += Math.floor(this.sizeY*(1/(double)this.DSPht)); break;
				case 4: this.availablePLL += this.sizeY; break;
				default: ErrorLog.print("Unknown block type => " + this.arch[a]);
			}
		}
	}
	public int sizeX(){
		return this.sizeX;
	}
	public int sizeY(){
		return this.sizeY;
	}
	public int LAB(){
		return this.availableLAB;
	}
	public int M20K(){
		return this.availableM20K;
	}

	public int DSP(){
		return this.availableDSP;
	}
	public int PLL(){
		return this.availablePLL;
	}
}
