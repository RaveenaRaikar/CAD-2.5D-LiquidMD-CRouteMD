package pack.architecture;

import java.util.ArrayList;

import pack.util.ErrorLog;
import pack.util.Output;

public class Port {
	private String portType;
	private String portName;
	private ArrayList<Pin> pins;
	private String portClass;

	private String equivalent;
	public Port(Line line, Block parentBlock){
		this.portType = line.get_type();
		this.portName = line.get_value("name");
		
		this.pins = new ArrayList<Pin>();
		for(int pinNum=0;pinNum<Integer.parseInt(line.get_value("num_pins"));pinNum++){
			Pin p = new Pin(this.portType, this.portName, pinNum, parentBlock);
			this.pins.add(p);
		}

		if(line.has_property("port_class")) this.portClass = line.get_value("port_class");
		if(line.has_property("equivalent")){
			if(line.get_value("equivalent").equalsIgnoreCase("true")){
				this.equivalent = "true";
			}else if(line.get_value("equivalent").equalsIgnoreCase("false")){
				this.equivalent = "false";
			}
			else if(line.get_value("equivalent").equalsIgnoreCase("full")){
				this.equivalent = "full";
			}else if(line.get_value("equivalent").equalsIgnoreCase("none")){
				this.equivalent = "none";
			}else if(line.get_value("equivalent").equalsIgnoreCase("instance")){
				this.equivalent = "instance";
			}
		}
	}
	public String get_type(){
		return this.portType;
	}
	public String get_name(){
		return this.portName;
	}
	public int get_num_pins(){
		return this.pins.size();
	}
	public Pin get_pin(int pinNum){
		Pin p = this.pins.get(pinNum);
		if(p.get_pin_num() != pinNum) ErrorLog.print("p.get_pin_num() = " + p.get_pin_num() + " " + "pinNum = " + pinNum);
		return p;
	}
	public ArrayList<Pin> get_pins(){
		return this.pins;
	}
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("<");
		sb.append(this.portType);

		sb.append(" ");
		sb.append("name=\"");
		sb.append(this.portName);
		sb.append("\"");

		sb.append(" ");
		sb.append("num_pins=\"");
		sb.append(this.pins.size());
		sb.append("\"");

		if(this.equivalent != null){
			sb.append(" ");
			sb.append("equivalent=\"");
			if(this.equivalent.contentEquals("true")){
				sb.append("true");
				
			}else if(this.equivalent.contentEquals("false")){
				sb.append("false");
			}else if(this.equivalent.contentEquals("full")){
				sb.append("full");
			}else if(this.equivalent.contentEquals("none")){
				sb.append("none");
			}else if(this.equivalent.contentEquals("instance")){
				sb.append("instance");
			}
			sb.append("\"");
		}
		if(this.portClass != null){
			sb.append(" ");
			sb.append("port_class=\"");
			sb.append(this.portClass);
			sb.append("\"");
		}
		sb.append("/>");
		return sb.toString();
	}
}
