package route.circuit.resource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import place.circuit.architecture.ParseException;
import place.circuit.exceptions.InvalidFileFormatException;
import route.circuit.Circuit;
import route.circuit.architecture.Architecture;
import route.circuit.architecture.BlockCategory;
import route.circuit.architecture.BlockType;
import route.circuit.resource.Site;
import route.circuit.resource.RouteNode;

public class ResourceGraph {
	private final Circuit[] circuit;
	private final Architecture architecture;
	
	private final int width, height;
	private File RRGFile;
    private Document xmlDocument;
    private XPath xPath = XPathFactory.newInstance().newXPath();
    
	private final List<Site> sites;
	private final Site[][] siteArray;
	private final InterposerSite[][] sllSiteArray;
	
	private int sllRows;
	private float sllDelay;
	private int totDie;
	private int dieBoundary; 
	
	private int indexStart = 0;
	private final Map<String, BlockTypeRRG> blockTypeNodes;
	private final List<RouteNode> routeNodes;
	private final Map<Integer, RouteNode> routeNodeIndex;
	private Map<String, IndexedData> indexedDataList;
	private List<RouteSwitch> switchTypesList;
	private Map<String , Integer> lengthToSwitchMap;
	private Map<Integer, String> segmentList;
	
	private final Map<RouteNodeType, List<RouteNode>> routeNodeMap;
	
	private static int SOURCE_COST_INDEX = 0;
	private static int SINK_COST_INDEX = 1;
	private static int OPIN_COST_INDEX = 2;
	private static int IPIN_COST_INDEX = 3;
	
    public ResourceGraph(Circuit[] circuit) {
    	this.circuit = circuit;
    	this.architecture = this.circuit[0].getArchitecture();
    	this.RRGFile = this.architecture.getRRGFile();
    	System.out.print("\nthe RRG is " + this.RRGFile);
    	
    	this.width = this.architecture.getWidth();
    	this.height = this.architecture.getHeight();
    	
    	this.sllRows = this.architecture.getSllRows();
    	this.sllDelay = (float) (this.architecture.getSllDelay() * 1e-12);
    	this.totDie = this.architecture.getTotDie();
    	this.dieBoundary = this.height/this.totDie;
		this.sites = new ArrayList<>();
		this.siteArray = new Site[this.width+2][this.height+2];
		this.sllSiteArray = new InterposerSite[this.width+2][this.height+2];
		
		this.blockTypeNodes = new HashMap<>();
		this.routeNodes = new ArrayList<>();
		this.routeNodeIndex = new HashMap<>();
		this.routeNodeMap = new HashMap<>();
		for(RouteNodeType routeNodeType: RouteNodeType.values()){
			List<RouteNode> temp = new ArrayList<>();
			this.routeNodeMap.put(routeNodeType, temp);
		}
    }
    
    public void build(){
        this.createSites();
        
		try {
			this.parseRRG();
		} catch (ParseException | IOException | InvalidFileFormatException | InterruptedException | ParserConfigurationException | SAXException error)  {
			System.err.println("Problem in generating RRG: " + error.getMessage());
			error.printStackTrace();
		}
		
		this.assignNamesToSourceAndSink();
		this.connectSourceAndSinkToSite();
		this.connectSLLWiresToSite();
    }
    
    public IndexedData get_ipin_indexed_data() {
    	return this.indexedDataList.get("IPIN");
    }
    public IndexedData get_opin_indexed_data() {
    	return this.indexedDataList.get("OPIN");
    }
    public IndexedData get_source_indexed_data() {
    	return this.indexedDataList.get("SOURCE");
    }
    public IndexedData get_sink_indexed_data() {
    	return this.indexedDataList.get("SINK");
    }
    public Map<String, IndexedData> getIndexedDataList() {
    	return this.indexedDataList;
    }
    
    private void createSites() {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        int ioCapacity = this.architecture.getIoCapacity();
        int ioHeight = ioType.getHeight();
        
        //IO Sites
        for(int i = 1; i < this.height - 1; i++) {
        	this.addSite(new Site(1, i, ioHeight, ioType, ioCapacity));
            this.addSite(new Site(this.width - 2, i, ioHeight, ioType, ioCapacity));
        }
        for(int i = 1; i < this.width - 1; i++) {
        	this.addSite(new Site(i, 1, ioHeight, ioType, ioCapacity));
            this.addSite(new Site(i, this.height - 2, ioHeight, ioType, ioCapacity));
        }
        
        
        //Sites to hold SLL interposer routeNodes
    	for(int column = 2; column < this.width - 2; column++) {
        	for(int row = (this.dieBoundary - this.sllRows + 1); row < this.dieBoundary + this.sllRows + 1; row++) {
        		this.addInterposerSite(new InterposerSite(column, row));
        	}
    	}
        
        
        for(int column = 2; column < this.width - 2; column++) {
            BlockType blockType = this.circuit[0].getColumnType(column);
            
            int blockHeight = blockType.getHeight();
            for(int row = 2; row < this.height - 1 - blockHeight; row += blockHeight) {
            	this.addSite(new Site(column, row, blockHeight, blockType, 1));
            }
        }
    }
    public void addSite(Site site) {
    	this.siteArray[site.getColumn()][site.getRow()] = site;
    	this.sites.add(site);
    }
    
    public void addInterposerSite(InterposerSite site) {
    	this.sllSiteArray[site.getColumn()][site.getRow()] = site;
    }
    
    
    /**
     * Return the site at coordinate (x, y). If allowNull is false,
     * return the site that overlaps coordinate (x, y) but possibly
     * doesn't start at that position.
     */
    public Site getSite(int column, int row) {
        return this.getSite(column, row, false);
    }
    public Site getSite(int column, int row, boolean allowNull) {

        if(allowNull) {
            return this.siteArray[column][row];
        } else {
            Site site = null;
            int topY = row;
            while(site == null) {
                site = this.siteArray[column][topY];
                topY--;
            }
            
            return site;
        }
    }
    
    public InterposerSite getInterposerSite(int column, int row) {
    	return this.sllSiteArray[column][row];
    }
    
    public String getBlocktypeAtSite(int column, int row) {
    	Site blockSite = this.getSite(column, row);
    	String blockName = blockSite.getInstance(0).getBlockType().getName();
    	return blockName;
    }
    public List<Site> getSites(BlockType blockType) {
        BlockType ioType = BlockType.getBlockTypes(BlockCategory.IO).get(0);
        List<Site> sites;
        
        if(blockType.equals(ioType)) {
            int ioCapacity = this.architecture.getIoCapacity();
            sites = new ArrayList<Site>((this.width + this.height) * 2 * ioCapacity);
            
            for(int n = 0; n < ioCapacity; n++) {
            	for(int i = 1; i < this.height - 1; i++) {
                    sites.add(this.siteArray[1][i]);
                    sites.add(this.siteArray[this.width - 2][i]);
                }
                
            	for(int i = 1; i < this.width - 1; i++) {
                    sites.add(this.siteArray[i][1]);
                    sites.add(this.siteArray[i][this.height - 2]);
                }
            }
        } else {
        	
        	//assumption: circuits are identical
            List<Integer> columns = this.circuit[0].getColumnsPerBlockType(blockType);
            int blockHeight = blockType.getHeight();
            sites = new ArrayList<Site>(columns.size() * this.height);
            
            for(Integer column : columns) {
            	for(int row = 2; row < this.height - 2 - blockHeight; row += blockHeight) {
                    sites.add(this.siteArray[column][row]);
                }
            }
        }
    
        return sites;
    }
    public List<Site> getSites(){
    	return this.sites;
    }
    
    /******************************
     * GENERATE THE RRG READ FROM * 
     * RRG FILE DUMPED BY VPR     *
     ******************************/
    //Changing the RRG format to now read as an XML file instead of VPR7 format.
    
    private void parseRRG() throws ParseException, IOException, InvalidFileFormatException, InterruptedException, ParserConfigurationException, SAXException{
		System.out.println("---------------");
		System.out.println("| Process RRG |");
		System.out.println("---------------");
        
        //Process index list
        this.processIndexList();
        //process channels
        
        //process switches
        this.processSwitchList();
        
        //process segments
        this.processSegmentList();
        //process blocktypes
        this.processBlockTypes();
        //process RRG nodes
        this.processRRGNodes();
        this.processRRGEdges();
        this.postProcess();

    }
    
    private void postProcess() {
    	System.out.print("\nThe postProcess is on going");
		for(RouteNode node : this.routeNodes) {

			for(int i = 0; i < node.getNumChildren(); i++) {
				RouteNode child = node.children[i];
				if(child != null) {
					RouteSwitch routeSwitch = node.switches[i];
					child.setDelay(routeSwitch);
				}
				
			}
		}
		for(RouteNode node : this.routeNodeMap.get(RouteNodeType.SOURCE)) {
			Source source = (Source) node;
			source.setDelay(null);
		}
		
		for(RouteNode node: this.routeNodes) {
			if(node.getDelay() < 0) {
				node.setDelay();
			}
		}
		
		System.out.println();
    }
 
    
    private void processSegmentList() throws IOException {
    	System.out.print("\n Processing the segmentList");
		this.segmentList = new HashMap<>();
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		System.out.print("RR file path is " + rrgIndexFileName);
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + "segment_info" + this.sllRows +"L.echo";
        }
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(newFileName));
		System.out.println("\n   Read " + newFileName);
        String line;
        int id = 0;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.length() > 0) {
				while(line.contains("  ")) line = line.replace("  ", " ");
				
				String[] words = line.split(";");
		        id = Integer.parseInt(words[0]);
		        String name = words[1];
		        this.segmentList.put(id, name);
			}
		}
        reader.close();
    }

	private void processSwitchList() throws IOException{
		System.out.print("\n Processing the switchList");
		this.switchTypesList = new ArrayList<>();
		this.lengthToSwitchMap = new HashMap<String, Integer>();
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		System.out.print("RR file path is " + rrgIndexFileName);
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + "switch_info" + this.sllRows + "L.echo";
        }
            //return newFileName;
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(newFileName));
		System.out.println("\n   Read " + newFileName);
        String line;

        int indexCounter = 0;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.length() > 0) {
				
				this.switchTypesList.add(new RouteSwitch(line));
			}
			indexCounter++;
		}
		
        reader.close();
        
		for(RouteSwitch switchInfor : this.switchTypesList) {
			this.lengthToSwitchMap.put( switchInfor.name, switchInfor.index);
		}
	}

	private void processBlockTypes() throws IOException {
		System.out.print("\n Processing the Blocktypes");
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		System.out.print("RR file path is " + rrgIndexFileName);
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + "block_type_info.echo";
        }
            //return newFileName;
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(newFileName));
		System.out.println("\n   Read " + newFileName);
        String line;
        //reader.readLine();
        BlockTypeRRG newBlock = null;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.length() > 0) {
				while(line.contains("  ")) line = line.replace("  ", " ");
//				
				String[] words = line.split(";");

		        int id = Integer.parseInt(words[1]);
		        String name = words[2];
	            int width = Integer.parseInt(words[3]);
	            int height = Integer.parseInt(words[0]);  
	            if(this.blockTypeNodes.get(name)!= null) {
	            	newBlock = this.blockTypeNodes.get(name);
	            }else {
	            	newBlock = new BlockTypeRRG(id, height, width, name);
	            }
	            String pinClassType = words[4];
	            String pinValue	= words[6];
	            int ptc = Integer.parseInt(words[5]);
	            newBlock.addPinClass(pinValue, ptc, pinClassType);
	            this.blockTypeNodes.put(name, newBlock);
			}
		}
		
        reader.close();
        
	}

	private void processRRGNodes() throws IOException {
    	System.out.print("\n Processing the RRGNodes");
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		System.out.print("RR file path is " + rrgIndexFileName);
		String fileName = "rrNode_info_" + this.sllRows + "L.echo";
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + fileName;
        }
            //return newFileName;
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(newFileName));
		System.out.println("\n   Read " + newFileName);
		RouteNode routeNode = null;
		BlockTypeRRG blockNode = null;
		String currentBlockTypeName = null;
		String currentPort = null;
		int portIndex = -1;
		IndexedData data = null;
		String fullName = null;
		String name = null;
		String names[] = null;

		String line;
        String[] words = null;

		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.length() > 0) {
				while(line.contains("  ")) line = line.replace("  ", " ");
        		words = line.split(";");
        		int capacity = Integer.parseInt(words[3]);
        		int index = Integer.parseInt(words[0]);
        		this.indexStart = Math.max(this.indexStart, index);
        		String direction = words[2];
        		String type = words[1];

        		int ptc = Integer.parseInt(words[8]);
        		int xhigh = Integer.parseInt(words[6]);
        		int xlow  = Integer.parseInt(words[4]);
        		int yhigh = Integer.parseInt(words[7]);
        		int ylow  = Integer.parseInt(words[5]);
                
        		int segID = 0;
        		String chanType;
        		
        		float Reg = Float.parseFloat(words[9]);
        		float Cap = Float.parseFloat(words[10]);
        		int numChildren = Integer.parseInt(words[12]);		
        		switch (type) {
    				case "SOURCE":        				
    					assert Reg == 0;
    					assert Cap == 0;
    					data = this.indexedDataList.get(type);
    					routeNode = new Source(index, xlow, xhigh, ylow, yhigh, ptc, capacity, data, numChildren);
    					
    					break;
    				case "SINK":        				
    					assert Reg == 0;
    					assert Cap == 0;
    					data = this.indexedDataList.get(type);
    					routeNode = new Sink(index, xlow, xhigh, ylow, yhigh, ptc, capacity, data, numChildren);
    					
    					break;
    			case "IPIN":
    				//Assertions
    				assert capacity == 1;
    				assert Reg == 0;
    				assert Cap == 0;
    				currentBlockTypeName = this.getBlocktypeAtSite(xlow, ylow);

    				blockNode = this.blockTypeNodes.get(currentBlockTypeName);
  
    				fullName = blockNode.getPinClassNameByPinId(ptc); // currentBlockTypeName);

    				names = fullName.split("\\.");
    				names = names[1].split("\\[");
    				name = names[0];
    				if(currentPort == null){
    					currentPort = name;
    					portIndex = 0;
    				}else if(!currentPort.equals(name)){
    					currentPort = name;
    					portIndex = 0;
    				}
    				data = this.indexedDataList.get(type);
    				routeNode = new Ipin(index, xlow, xhigh, ylow, yhigh, ptc, currentPort, portIndex, data, direction, numChildren);
    				
    				portIndex += 1;
    				
    				break;
    			case "OPIN":        				
    				//Assertions
    				assert capacity == 1;
    				assert Reg == 0;
    				assert Cap == 0;
    				
    				currentBlockTypeName = this.getBlocktypeAtSite(xlow, ylow);
    				blockNode = this.blockTypeNodes.get(currentBlockTypeName);
    				fullName = blockNode.getPinClassNameByPinId(ptc); //, currentBlockTypeName);

    				
    				names = fullName.split("\\.");
    				names = names[1].split("\\[");
    				name = names[0];
    				
    				if(currentPort == null){
    					currentPort = name;
    					portIndex = 0;
    				}else if(!currentPort.equals(name)){
    					currentPort = name;
    					portIndex = 0;
    				}
    				data = this.indexedDataList.get(type);
    				routeNode = new Opin(index, xlow, xhigh, ylow, yhigh, ptc, currentPort, portIndex, data, direction, numChildren);
    				
    				portIndex += 1;
    				
    				break;
    			case "CHANX":        				
    				assert capacity == 1;
    				segID = Integer.parseInt(words[11]);
    				chanType = type + "_" + this.segmentList.get(segID);
    				data = this.indexedDataList.get(chanType);
    				routeNode = new Chanx(index, xlow, xhigh, ylow, yhigh, ptc, Reg, Cap, data, direction, numChildren);
    				if(xhigh - xlow == (this.sllRows - 1)) {
    					routeNode.setAsSLL();
    					}
    				break;
    			case "CHANY":        				

    				assert capacity == 1;
    				segID = Integer.parseInt(words[11]);
    				chanType = type + "_" + this.segmentList.get(segID);
    				data = this.indexedDataList.get(chanType);
    				routeNode = new Chany(index, xlow, xhigh, ylow, yhigh, ptc, Reg, Cap, data, direction, numChildren);
    				if(yhigh - ylow == (this.sllRows - 1)) {
    					routeNode.setAsSLL();
    					}
    				break;
    			default:
    				System.out.println("Unknown type: " + type);
    				break;
    		}
        			
        		this.addRouteNode(routeNode);       						
			}
		}

        reader.close();
        System.out.print("\n RR nodes: " + this.routeNodes.size());
	}
   

    private void processRRGEdges() throws IOException {
    	System.out.print("\n Processing the RRGEdges");
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		String fileName = "rrEdge_info_" + this.sllRows + "L.echo";
		System.out.print("\nRR file path is " + rrgIndexFileName);
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + fileName;
        }
            //return newFileName;
		BufferedReader reader = null;
		reader = new BufferedReader(new FileReader(newFileName));
		System.out.println("\n   Read " + newFileName);
		
		String line;
        String[] words = null;

        int counter = 0;
        while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.length() > 0) {
				while(line.contains("  ")) line = line.replace("  ", " ");
        		words = line.split(";");

        		int sinkNode = Integer.parseInt(words[0]);
        		int sourceNode = Integer.parseInt(words[1]);
        		int switchID = Integer.parseInt(words[2]);
        		
        		RouteNode parent = this.routeNodeIndex.get(sourceNode);
        		RouteNode child = this.routeNodeIndex.get(sinkNode);

        		if (!shouldEliminateConnection(parent, child, this.dieBoundary)) {
                    int index = parent.currentIndex;

                    parent.setChild(index, child);
                    counter++;
                    RouteSwitch routeSwitch = this.switchTypesList.get(switchID);
                    parent.setSwitchType(index, routeSwitch);

        		}


			}
		}
		
        reader.close();
        System.out.print("\n RR Edges: " + counter);
    }
    

    private boolean shouldEliminateConnection(RouteNode parent, RouteNode child, int dieBoundary) {
        if (parent == null || child == null) {
            return true; // Eliminate if either node is null
        }
        
        if (!(parent.isSLL || child.isSLL)) {
            return false; // Do not eliminate if neither node is SLL
        }

        boolean parentIncDir = parent.isSLL && parent.direction.equals("INC_DIR");
        boolean childIncDir = child.isSLL && child.direction.equals("INC_DIR");
        boolean parentDecDir = parent.isSLL && parent.direction.equals("DEC_DIR");
        boolean childDecDir = child.isSLL && child.direction.equals("DEC_DIR");

        if ((parentIncDir && child.ylow < dieBoundary) || 
            (childIncDir && parent.yhigh >= dieBoundary) ||
            (parentDecDir && child.yhigh >= dieBoundary) || 
            (childDecDir && parent.ylow < dieBoundary)) {
            return true; // Eliminate connection
        }

        return false; // Do not eliminate connection
    }

    
    private void processIndexList() throws IOException {
    	BufferedReader reader = null;
    	this.indexedDataList = new HashMap<String, IndexedData>();
		String rrgIndexFileName = this.RRGFile.getAbsolutePath();
		System.out.print("RR file path is " + rrgIndexFileName);
        int lastSlashIndex = rrgIndexFileName.lastIndexOf("/");
        String newFileName = null;
        if (lastSlashIndex >= 0) {
            String directoryPath = rrgIndexFileName.substring(0, lastSlashIndex + 1); // Include the last slash
            newFileName = directoryPath + "rr_indexed_data_" + this.sllRows + "L.echo";
        } 
		reader = new BufferedReader(new FileReader(newFileName));
		System.out.println("\n   Read " + newFileName);
        String line;
        Float baseCostDefault = (float) 0.0;
        line = reader.readLine();
        if(line.contains("Delay normalization factor:")) {

    		String[] header = line.split(":");
    		baseCostDefault = Float.parseFloat(header[1]);

    	}
        // Process each line of the file
        while ((line = reader.readLine()) != null) {
        	
        	while(line.contains("  ")) line = line.replace("  ", " ");
            String[] tokens = line.split("\\s");

            float baseCost, invLength, tLinear,tQuadratic, cLoad = 0;
            int orthoCostIndex, segIndex, index = 0;
            if(!tokens[0].equals("Cost")) {
                if (tokens.length >= 8) {
                    index = Integer.parseInt(tokens[0]);
                    String type = tokens[1];
                    if(type.contains("CHAN")) {
                    	type = tokens[1] +"_"+ tokens[2];

                    	baseCost = Float.valueOf(tokens[3]);
                        orthoCostIndex = Integer.parseInt(tokens[4]);
                        segIndex = Integer.parseInt(tokens[5]);
                        invLength = 0;
                          if(tokens[6].equals("nan")) {
                        	invLength = 0;
                        }else {
                        	invLength = Float.valueOf(tokens[6]);
                        }
                        
                        tLinear = Float.valueOf(tokens[7]);
                        tQuadratic = Float.valueOf(tokens[8]);
                        cLoad = Float.valueOf(tokens[9]);
                    }else {

                        baseCost = Float.valueOf(tokens[2]);
                        orthoCostIndex = Integer.parseInt(tokens[3]);
                        segIndex = Integer.parseInt(tokens[4]);
                        invLength = 0;
                        if(tokens[5].equals("nan")) {
                        	invLength = 0;
                        }else {
                        	invLength = Float.valueOf(tokens[5]);
                        }
                        
                        tLinear = Float.valueOf(tokens[6]);
                        tQuadratic = Float.valueOf(tokens[7]);
                        cLoad = Float.valueOf(tokens[8]);

                    }
                    

                    this.indexedDataList.put(type ,new IndexedData(index, baseCost, orthoCostIndex, invLength, tLinear, tQuadratic, cLoad));

                } else {
                    System.err.println("Invalid line: " + line);
                }
            }


        }
        reader.close();
        for(String dataTypes: this.indexedDataList.keySet()) {

        	IndexedData data = this.indexedDataList.get(dataTypes);
        	if (data.orthoCostIndex != -1) {

            	String[] chann = dataTypes.split("_");
            	String orthdata = null;
            	if(chann[0].equals("CHANX")) {
            		orthdata = "CHANY_" +  chann[1];
            		
            	}else {
            		orthdata = "CHANX_" +  chann[1];
            	}

        		data.setOrthoData(this.indexedDataList.get(orthdata));
        	}
 	
        }
        
        
    }

	private void assignNamesToSourceAndSink() {
		for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SOURCE)){
			Source source = (Source) routeNode;
			source.setName();
		}
		
		for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.IPIN)){
			Ipin ipin = (Ipin) routeNode;
			ipin.setSinkName();
		}
	}
	
	private void connectSLLWiresToSite() {
		for(RouteNode routeNode: this.routeNodeMap.get(RouteNodeType.CHANY)) {
			if(routeNode.isSLL) {
				Chany sllWire = (Chany) routeNode;
				InterposerSite site;

				if(sllWire.direction.equals("INC_DIR")) {
					site = this.getInterposerSite(sllWire.xlow, sllWire.ylow);
				}else {
					site = this.getInterposerSite(sllWire.xhigh, sllWire.yhigh);
				}

				if(site.addInterposerNode(sllWire) == false) {
					System.err.println("\nUnable to add " + routeNode + " as source to " + site);
				}
			}
		}
	}
	
    private void connectSourceAndSinkToSite() {
    	for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SOURCE)){
			Source source = (Source) routeNode;

			Site site = this.getSite(source.xlow, source.ylow);
			

			if(site.addSource((Source)routeNode) == false) {
				System.out.print("\nThe site is " + site.getblockType());
				System.err.println("\nUnable to add " + routeNode + " as source to " + site);
			}
		}
    	for(RouteNode routeNode:this.routeNodeMap.get(RouteNodeType.SINK)){
			Sink sink = (Sink) routeNode;

			Site site = this.getSite(sink.xlow, sink.ylow);

			if(site.addSink((Sink)routeNode) == false) {
				System.err.println("\nUnable to add " + routeNode + " as sink to " + site);
			}
		}
    }
	
    

    
    
	private void addRouteNode(RouteNode routeNode) {
		assert routeNode.index == this.routeNodes.size();
		
		this.routeNodes.add(routeNode);
		this.routeNodeIndex.put(routeNode.index, routeNode);
		this.routeNodeMap.get(routeNode.type).add(routeNode);
	}
	public List<RouteNode> getRouteNodes() {
		return this.routeNodes;
	}
	public int numRouteNodes() {
		return this.routeNodes.size();
	}
	public int numRouteNodes(RouteNodeType type) {
		if(this.routeNodeMap.containsKey(type)) {
			return this.routeNodeMap.get(type).size();
		} else {
			return 0;
		}
	}
	
	@Override
	public String toString() {
		String s = new String();
		
		s+= "The system has " + this.numRouteNodes() + " rr nodes:\n";
		
		for(RouteNodeType type : RouteNodeType.values()) {
			s += "\t" + type + "\t" + this.numRouteNodes(type) + "\n";
		}
		return s;
	}
	
	/********************
	 * Routing statistics
	 ********************/
	public int totalWireLength() {
		int totalWireLength = 0;
		int counter = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					totalWireLength += routeNode.wireLength();
					counter++;
				}
			}
		}
		return totalWireLength;
	}
	public int congestedTotalWireLengt() {
		int totalWireLength = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					totalWireLength += routeNode.wireLength() * routeNode.routeNodeData.occupation;
				}
			}
		}
		return totalWireLength;
	}
	public void nodeInfo() {
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					System.out.print("\n route node occupation is " + routeNode.routeNodeData.occupation);
				}
			}
		}
	}
	public int wireSegmentsUsed() {
		int wireSegmentsUsed = 0;
		for(RouteNode routeNode : this.routeNodes) {
			if(routeNode.isWire) {
				if(routeNode.used()) {
					wireSegmentsUsed++;
				}
			}
		}
		return wireSegmentsUsed;
	}
	public void sanityCheck() {
		for(Site site:this.getSites()) {
			site.sanityCheck();
		}
	}
	public void printRoutingGraph() {
	
		for(RouteNode node : this.getRouteNodes()) {
			if(node.used()) {
				for (RouteNode child : node.children) {
					System.out.println("\t" + child);
				}
				System.out.println();
			}
			

		}
	}
	public void printChannelUsage() {
		System.out.println("-------------------------------------------------------------------------------");
		System.out.println("|                              CHANNEL STATS                                  |");
		System.out.println("-------------------------------------------------------------------------------");
		int[][] usageX = new int[this.width][this.height];
		int[][] usageY = new int[this.width][this.height];
		int[][] availableX = new int[this.width][this.height];
		int[][] availableY = new int[this.width][this.height];
		// Initialize the arrays to zero if needed
		for (int x = 0; x < this.width ; x++) {
		    for (int y = 0; y < this.height; y++) {
		    	usageX[x][y] = 0; // Initialize usage to zero
		    	usageY[x][y] = 0;
		    	availableX[x][y] = 0; // Initialize available to zero
		    	availableY[x][y] = 0;
		    }
		}
		
		for (RouteNode node : this.routeNodes) {
			if((node.type == RouteNodeType.CHANX) || (node.type == RouteNodeType.CHANY)) {
				if(node.type == RouteNodeType.CHANX) {
					int y = node.ylow;
					for(int x = node.xlow; x <= node.xhigh;x++) {
							
						availableX[x][y] += node.capacity;
						if(node.used()) {
							usageX[x][y]++;
						}
					}
				}else {
					int x = node.xlow;
					for(int y = node.ylow; y<= node.yhigh;y++) {
						availableY[x][y] += node.capacity;
						if(node.used()) {
							usageY[x][y]++;
						}
					}
				}
			}
		}
		float max_util = 0;
		int peakX = 0;
		int peakY = 0;
		for(int widthX = 0; widthX < (this.width); widthX++) {
			for(int heightY = 0; heightY < (this.height); heightY++) {
				float chanXUtil =  this.utilisation(usageX[widthX][heightY], availableX[widthX][heightY]);
				float chanYUtil = this.utilisation(usageY[widthX][heightY], availableY[widthX][heightY]);
				float[] utils = {chanXUtil, chanYUtil};
				for (float util : utils) {
					if(util > max_util) {
						max_util = util;
						peakX = widthX;
						peakY = heightY;
					}
				}
			}
		}
		System.out.print("\nMaximum utilisation of " + (max_util * 100) + "% at X = " + peakX + " and Y = " + peakY + "\n");
	}
	public void printWireUsage() {
		System.out.println("-------------------------------------------------------------------------------");
		System.out.println("|                              WIRELENGTH STATS                               |");
		System.out.println("-------------------------------------------------------------------------------");
		System.out.println("Total wirelength: " + this.totalWireLength());
		System.out.println("Total congested wirelength: " + this.congestedTotalWireLengt());
		System.out.println("Wire segments: " + this.wireSegmentsUsed());
		for(int i = 0; i < this.totDie; i++) {
			System.out.println("Maximum net length: " + this.circuit[i].maximumNetLength() + " for die " + i);
		}
		
		System.out.println();
		
		//Length of wire, Count of available wire
		Map<Integer, Integer> numWiresMap = new HashMap<Integer, Integer>();
		//Length of wire, Count of used wires.
		Map<Integer, Integer> numUsedWiresMap = new HashMap<Integer, Integer>();
		//Length of wire, Count of available wirelength
		Map<Integer, Integer> wirelengthMap = new HashMap<Integer, Integer>();
		//Length of wire, Count of used wirelength
		Map<Integer, Integer> UsedWirelengthMap = new HashMap<Integer, Integer>();
		int wireType = 0;
		int wireLength = 0;
		for(RouteNode node : this.routeNodes) {
			if((node.type == RouteNodeType.CHANX) || (node.type == RouteNodeType.CHANY)) {
				wireType = node.indexedData.length;
				numWiresMap.put(wireType, numWiresMap.getOrDefault(wireType, 0) + 1);
				wireLength = node.wireLength();
				wirelengthMap.put(wireType, wirelengthMap.getOrDefault(wireType, 0) + wireLength);
				if(node.used()) {
					numUsedWiresMap.put(wireType, numUsedWiresMap.getOrDefault(wireType, 0) + 1);
					UsedWirelengthMap.put(wireType, UsedWirelengthMap.getOrDefault(wireType, 0) + wireLength);
				}
			}
			
		}

		for(Integer wireTypes : numWiresMap.keySet()) {
			if(numUsedWiresMap.get(wireTypes) != null) {
				double averageLength = (double) wirelengthMap.get(wireTypes) / numWiresMap.get(wireTypes) ;
				System.out.printf("\nLength %8d (%5.2f) wires: %8d of %8d | %5.2f%% => Wire-length: %8d\n",
						wireTypes,
						averageLength, 
						numUsedWiresMap.get(wireTypes), 
						numWiresMap.get(wireTypes), 
						100.0 * numUsedWiresMap.get(wireTypes)/numWiresMap.get(wireTypes), 
						UsedWirelengthMap.get(wireTypes));
				System.out.printf("L%8d Wirelength: %8d\n",wireTypes, UsedWirelengthMap.get(wireTypes));
				System.out.printf("L%8d Usage: %5.2f\n",wireTypes, 100.0 * numUsedWiresMap.get(wireTypes)/numWiresMap.get(wireTypes));
			}

			
		}
	}
	public float utilisation(int usage, int available) {
		float Ulti = 0.0f;
		if(usage > 0) {
			
			Ulti = (float) usage/available;
			return Ulti;
		}
		return 0;
	}
}
