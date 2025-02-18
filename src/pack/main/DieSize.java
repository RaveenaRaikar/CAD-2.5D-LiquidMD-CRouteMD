package pack.main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import pack.util.Output;
import pack.util.Util;

public class DieSize {
	private Simulation simulation;
	private int DieX;
	private int DieY;
	private double DieDensity;

	private int DieNum;

	private String HierarchyFile;
	private String CircuitName;
	private String ResultFolder;
	
	public DieSize(Simulation simulation){

		this.DieNum = simulation.getIntValue("Number_of_die");
		this.ResultFolder = simulation.getStringValue("result_folder");
		this.CircuitName = simulation.getStringValue("circuit");
		this.DieDensity = simulation.getDoubleValue("Die_Density");   

	}
	
	public void UpdateDieSize(){

		int [] DSPCount = new int[this.DieNum];
		int [] ALBCount = new int[this.DieNum];
		int [] IOCount = new int[this.DieNum];
		int [] MemoryCount = new int[this.DieNum]; 
		this.HierarchyFile = "";
	    for(int dienum = 0; dienum < this.DieNum; dienum++) {
	    	Output.println("\tBlock statistics of Die " + dienum);
	    	this.HierarchyFile = this.ResultFolder + "/" + this.CircuitName + "_" + dienum + ".multipart.hierarchy";
	    	DSPCount[dienum] = ReadHierarchyFile("instance=\"dsp_top[", HierarchyFile);
	    	ALBCount[dienum] = ReadHierarchyFile(" instance=\"alb[",HierarchyFile);
	 		IOCount[dienum] = ReadHierarchyFile("instance=\"io[", HierarchyFile);
	 		MemoryCount[dienum] = ReadHierarchyFile("instance=\"memory[", HierarchyFile);
			Output.println("\t\tThe number of DSP on die " + dienum + " is " + DSPCount[dienum]);
			Output.println("\t\tThe number of ALB on die " + dienum + " is " +ALBCount[dienum]);
			Output.println("\t\tThe number of IO on die " + dienum + " is " + IOCount[dienum]);
			Output.println("\t\tThe number of Memory on die " + dienum + " is " + MemoryCount[dienum]);
	    }
	    
	   
	    Output.println("\nDie dimensions with Max value");
	    //Get max value
	    int DSPnum = maxvalue(DSPCount);
	    int ALBnum = maxvalue(ALBCount);
	    int IOnum = maxvalue(IOCount);
	    int Memnum = maxvalue(MemoryCount);

	    Output.println("\tThe max count of DSP is " + DSPnum);
	    Output.println("\tThe max count of ALB is " + ALBnum);
	    Output.println("\tThe max count of IO is " + IOnum);
	    Output.println("\tThe max count of Mem is " + Memnum);
		//Calculate the Y axis
	    //Height of DSP and RAM is 4
		double FPGAY = Math.sqrt((ALBnum + (DSPnum*4) + (Memnum*4))/(this.DieDensity*1.5));
		double FPGAX = 1.5*FPGAY;
		
		
		this.DieY = (int)Math.round(FPGAY);
		this.DieX = (int)Math.round(FPGAX);
		
		Output.println("The value of X and Y is " + this.DieX + " " + this.DieY);
		//To calculate the actual numbers
		
		//Period and start location
		int cDSP = ((this.DieX - 6 -2)/16);
	//	int cALB = 
		int cMem = ((this.DieX - 2 -2)/16);
		
		int AvailDSP = cDSP *(this.DieY/4);
		int AvailMem = cMem *(this.DieY/4);
		int AvailALB = (this.DieX * this.DieY) - AvailDSP - AvailMem -(2*(this.DieX+this.DieY));
		int AvailIO = (16*(this.DieX + this.DieY));
		Output.println("\tThe number of available DSPs " + AvailDSP);
		Output.println("\tThe number of available Mem " + AvailMem);
		Output.println("\tThe number of available ALB " + AvailALB);
		Output.println("\tThe number of available IO " + AvailIO);
		
		if(DSPnum>AvailDSP)
		{
			Output.println("The DSP count is above limit");
		}else if(Memnum>AvailMem)
		{
			Output.println("The Memory count is above limit");
		}else if(ALBnum>AvailALB)
		{
			Output.println("The ALB count is above limit");
		}else if(IOnum>(AvailIO*8)) {
			Output.println("The IO count is above limit");
		}
	    for(int dienum = 0; dienum < this.DieNum; dienum++) {
	    	String ArchName = "arch.light_" + dienum+ ".xml";
	    			//Read architecture file
			read_file_update_size(this.ResultFolder + ArchName, dienum);
	    }
	}
	
	private void read_file_update_size(String fileName,int dienum){
		if(!Util.fileExists(fileName)){
			Output.println("Architecture file " + fileName + " does not exist");
		}
		//Output.println("\tFilename: " + fileName);
		ArrayList<String> lines = new ArrayList<String>();
		String NewLayout = "<fixed_layout name=\"H_arch_" + dienum +"\" width=\""+ this.DieX + "\" height=\"" + this.DieY + "\">";
		Output.println(NewLayout);
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileName));
		    String line = br.readLine();
		    while (line != null) {
		    	if(line.contains("<!--") && !line.contains("-->")){
		    		StringBuilder sb = new StringBuilder();
		    		while(!line.contains("-->")){
		    			sb.append(line);
		    			line = br.readLine();
		    		}
		    		sb.append(line);
		    		lines.add(sb.toString());
		    	}else if(line.contains("fixed_layout") && !line.contains("</")){
		    		line = line.replace(line, NewLayout);
		    		lines.add(line);
		    	}else {
		    		lines.add(line);
		    	}
		        line = br.readLine();
		    }
		    br.close();
		}catch (IOException e) {
			e.printStackTrace();
		}
		//write to the file.
			try{
				BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
				for(String line:lines){
					bw.write(line);
					bw.newLine();
				}
				bw.close();
			}catch(IOException ex){
				Output.println (ex.toString());
			}
		
		
	}
	public int ReadHierarchyFile(String stringToLookFor, String fileName){
		  int count = 0;
		  try{
		    FileInputStream fstream = new FileInputStream(fileName);
		    DataInputStream in = new DataInputStream(fstream);
		    BufferedReader br = new BufferedReader(new InputStreamReader(in));
		    String strLine;
		    while ((strLine = br.readLine()) != null)   {
			      int startIndex = strLine.indexOf(stringToLookFor);
			      while (startIndex != -1) {
			        count++;
			        startIndex = strLine.indexOf(stringToLookFor, 
			                                  startIndex +stringToLookFor.length());
			      }
			    }
		    in.close();
		  }catch (Exception e){//Catch exception if any
		    System.err.println("Error: " + e.getMessage());
		  }
		  return count;
		}
	
	public int maxvalue(int[] arr) {
		Arrays.sort(arr); 
        return arr[arr.length - 1]; 
		}
}