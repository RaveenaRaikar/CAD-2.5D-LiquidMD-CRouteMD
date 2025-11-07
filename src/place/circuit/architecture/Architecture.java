package place.circuit.architecture;

import org.w3c.dom.*;
import org.xml.sax.SAXException;
import place.circuit.exceptions.InvalidFileFormatException;
import place.util.Pair;
import place.util.Triple;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * We make a lot of assumptions while parsing an architecture XML file.
 * I tried to document these assumptions by commenting them with the
 * prefix ASM.
 * First assumption: the architecture file is entirely valid. No checks
 * on duplicate or illegal values are made in this parser.
 *
 * Only a subset of the VPR architecture specs is supported.
 * TODO: document this subset.
 *
 * This parser does not store interconnections. We just assume that all
 * connections in the net file are legal. Should someone ever want to
 * write a packer, then this feature has to be implemented.
 *
 * ASM: block types have unique names. Two blocks with the same type
 * are exactly equal, regardless of their parent block(s)
 */
public class Architecture implements Serializable {

    private static final long serialVersionUID = -5436935126902935000L;


    private boolean autoSize;
    public int width, height, totDie, archRows, archCols;
    private double autoRatio;

    private int sllRows;
    public float sllDelay;
    private File architectureFile, blifFile, netFile;
    private String circuitName;

    private transient Map<String, Boolean> modelIsClocked = new HashMap<>();
    private transient List<Pair<PortType, Double>> setupTimes = new ArrayList<>();
    private transient List<Triple<PortType, PortType, Double>> delays = new ArrayList<>();
    private transient Map<String, Integer> directs = new HashMap<>();
    private transient Map<String, Map<String, String>> directMap = new HashMap();

    private DelayTables delayTables;

    private int ioCapacity;

    private Document xmlDocument;
    private XPath xPath = XPathFactory.newInstance().newXPath();

    public Architecture(
            String circuitName,
            File architectureFile,
            File blifFile,
            File netFile,
            int totDie,
            int sllRows,
            float sllDelay,
            int archRows,
            int archCols) {

        this.architectureFile = architectureFile;

        this.blifFile = blifFile;
        this.netFile = netFile;
        this.totDie = totDie;
        this.circuitName = circuitName;
        this.sllRows = sllRows;
        this.sllDelay = sllDelay;
        this.archCols = archCols;
        this.archRows = archRows;
    }

    public void parse() throws ParseException, IOException, InvalidFileFormatException, InterruptedException, ParserConfigurationException, SAXException {

        // Build a XML root
        DocumentBuilder xmlBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        this.xmlDocument = xmlBuilder.parse(this.architectureFile);
        Element root = this.xmlDocument.getDocumentElement();
        
        // Get the architecture size (fixed or automatic)
        this.processLayout(root);

        // Store the models to know which ones are clocked
        this.processModels(root);

        this.processDirects(root);

        // Get all the complex block types and their ports and delays
        this.processBlocks(root);

        BlockTypeData.getInstance().postProcess();
        // All delays have been cached in this.delays, process them now
        this.processDelays();

        this.delayTables = new DelayTables();

    }


    private void processLayout(Element root) {
        Element layoutElement = this.getFirstChild(root, "layout");
        NodeList autoLayoutList = layoutElement.getElementsByTagName("auto_layout");
        NodeList fixedLayoutList = layoutElement.getElementsByTagName("fixed_layout");
        if (autoLayoutList.getLength() > 0 && autoLayoutList.item(0).getNodeType() == Node.ELEMENT_NODE){
            this.autoSize = true;
            Element autoLayout = (Element) autoLayoutList.item(0);
            this.autoRatio = Double.parseDouble(autoLayout.getAttribute("aspect_ratio"));
        } else if(fixedLayoutList.getLength() > 0) {
        	Element fixedSizeLayout = (Element) fixedLayoutList.item(0);
        	
        	this.width = Integer.parseInt(fixedSizeLayout.getAttribute("width"));
            this.height = Integer.parseInt(fixedSizeLayout.getAttribute("height"));
            System.out.print("\nDimensions of chip: " + this.width +" x " + this.height);
            if(this.archCols == 2) {
            	this.height = this.height/this.archRows;
            	this.width = this.width/this.archCols;
            }else {
            	this.height = this.height/this.totDie;	
            }
            
            System.out.print("\nArchitecture Configuration: " + this.archRows +" x " + this.archCols);
            System.out.print("\nDimensions of die: " + this.width +" x " + this.height);

        } else {
            NodeList fixedLayout = layoutElement.getElementsByTagName("fixed_layout ");
            this.autoSize = layoutElement.hasAttribute("auto");
            if (this.autoSize) {
                this.autoRatio = Double.parseDouble(layoutElement.getAttribute("auto"));
            } else {
                this.width = Integer.parseInt(layoutElement.getAttribute("width"));
                this.height = Integer.parseInt(layoutElement.getAttribute("height"));
            }
        }
    
    }



    private void processModels(Element root) {
        Element modelsElement = this.getFirstChild(root, "models");
        List<Element> modelElements = this.getChildElementsByTagName(modelsElement, "model");

        for(Element modelElement : modelElements) {
            this.processModel(modelElement);
        }
    }

    private void processModel(Element modelElement) {
        String modelName = modelElement.getAttribute("name");

        Element inputPortsElement = this.getFirstChild(modelElement, "input_ports");
        List<Element> portElements = this.getChildElementsByTagName(inputPortsElement, "port");

        boolean isClocked = false;
        for(Element portElement : portElements) {
            String isClock = portElement.getAttribute("is_clock");
            if(isClock.equals("1")) {
                isClocked = true;
            }
        }

        this.modelIsClocked.put(modelName, isClocked);
    }



    private void processDirects(Element root) {
        Element directsElement = this.getFirstChild(root, "directlist");
        if(directsElement == null) {
            return;
        }

        List<Element> directElements = this.getChildElementsByTagName(directsElement, "direct");
        for(Element directElement : directElements) {
        	Map<String, String> portMap = new HashMap<>();
            String[] fromPort = directElement.getAttribute("from_pin").split("\\.");
            String[] toPort = directElement.getAttribute("to_pin").split("\\.");

            int offsetX = Integer.parseInt(directElement.getAttribute("x_offset"));
            int offsetY = Integer.parseInt(directElement.getAttribute("y_offset"));
            int offsetZ = Integer.parseInt(directElement.getAttribute("z_offset"));
            
            portMap.put(fromPort[1], toPort[1]);
            this.directMap.put(fromPort[0], portMap);

            // Directs can only go between blocks of the same type
            assert(fromPort[0].equals(toPort[0]));

            if(offsetX == 0 && offsetZ == 0) {
                String id = this.getDirectId(fromPort[0], fromPort[1], toPort[1]);
                this.directs.put(id, offsetY);
            }
        }
    }

    private String getDirectId(String blockName, String portNameFrom, String portNameTo) {
        return String.format("%s.%s-%s", blockName, portNameFrom, portNameTo);
    }



    private void processBlocks(Element root) throws ParseException {

        Element blockListElement = this.getFirstChild(root, "complexblocklist");
        List<Element> blockElements = this.getChildElementsByTagName(blockListElement, "pb_type");

        Element tilesElement = this.getFirstChild(root, "tiles");
        if (tilesElement == null) {
            for(Element blockElement : blockElements) {
                this.processBlockElement_v7(null, blockElement);
            }
        } else {
            List<Element> tileElements = this.getChildElementsByTagName(blockListElement, "tile");
            Element layoutElement = this.getFirstChild(root, "layout");
            
                for(Element blockElement : blockElements) {
                	
                	if(blockElement.getAttribute("name").equals("alb")) {	
                		this.CreateVirtualSllBlocks(null,blockElement);
                		this.processBlockElement_v8(null, blockElement);
                		
                	}else {
                		this.processBlockElement_v8(null, blockElement);
                	}
                }
                this.createEmptySite();
        }


    }
    private BlockType createEmptySite() {
    	BlockCategory blockCategory = BlockCategory.EMPTY;
    	int start = 0, repeat = this.width-1, height = 1, priority = 500;
    	Boolean isClocked = false;
        String blockName = "EMPTY";
        BlockType blockType = BlockTypeData.getInstance().addType(
                null,
                blockName,
                blockCategory,
                height,
                start,
                repeat,
                priority,
                isClocked,
                null,
                null,
                null);
        
        return blockType;
    }

    private BlockType CreateVirtualSllBlocks(BlockType parentBlockType, Element blockElement) throws ParseException{
    	String blockName = "alb";
    	BlockCategory blockCategory = BlockCategory.SLLDUMMY;
    	int start = -1, repeat = -1, height = -1, priority = -1;
    	Boolean isClocked = true;
    	
    	NodeList heightResult = this.xPathQuery(String.format("//tile[descendant::site[@pb_type='%s']][1]//@height", blockName));
        if (heightResult != null){
            Attr heightAttr = (Attr) heightResult.item(0);
            height = Integer.parseInt(heightAttr.getValue());
        } else {
            height = 1;
        }

        // Query layout tag associated with given pb_type. (ex: <fill .. />, <col ../> etc.)
        NodeList layoutResult = this.xPathQuery(String.format("//layout//*[@type=//tile[descendant::site[@pb_type='%s']][1]/@name]", blockName));
        if (layoutResult == null){
            System.out.println(String.format("Missing layout tag for pb_type '%s'", blockName));
            System.exit(1);
        }

        // We only look at the first element (tool can't handle e.g. multiple columns)
        Element layout = (Element)layoutResult.item(0);

        // Query for layout type tag
        start=0;
        repeat =1;
        priority =49;
        
        blockName = "DummyAlb";
     // Build maps of inputs and outputs
        Map<String, Integer> inputs = this.getPorts(blockElement, "input");
        Map<String, Integer> outputs = this.getPorts(blockElement, "output");
        Map<String, Integer> clockPorts = this.getPorts(blockElement, "clock");
        
       // if(blockCategory == BlockCategory.IO)

        BlockType blockType = BlockTypeData.getInstance().addType(
                parentBlockType,
                blockName,
                blockCategory,
                height,
                start,
                repeat,
                priority,
                isClocked,
                inputs,
                outputs,
                clockPorts);
        
        return blockType;
    	
    }
    private BlockType processBlockElement_v7(BlockType parentBlockType, Element blockElement) throws ParseException {
        String blockName = blockElement.getAttribute("name");

        boolean isGlobal = this.isGlobal(blockElement);
        boolean isLeaf = this.isLeaf(blockElement);
        boolean isClocked = this.isClocked(blockElement);


        // Set block category, and some related properties
        BlockCategory blockCategory;

        // CLB blocks fill the rest of the FPGA
        int start = -1, repeat = -1, height = -1, priority = -1;


        if(isLeaf) {
            blockCategory = BlockCategory.LEAF;

        } else if(!isGlobal) {
            blockCategory = BlockCategory.INTERMEDIATE;

        } else {
            // Get some extra properties that relate to the placement
            // of global blocks

            blockCategory = this.getGlobalBlockCategory_v7(blockElement);

            if(blockCategory == BlockCategory.IO) {
                this.ioCapacity = Integer.parseInt(blockElement.getAttribute("capacity"));

            } else {

                if(blockElement.hasAttribute("height")) {
                    height = Integer.parseInt(blockElement.getAttribute("height"));
                } else {
                    height = 1;
                }

                Element gridLocationsElement = this.getFirstChild(blockElement, "gridlocations");
                Element locElement = this.getFirstChild(gridLocationsElement, "loc");
                priority = Integer.parseInt(locElement.getAttribute("priority"));

                // ASM: the loc type "rel" is not used
                String type = locElement.getAttribute("type");
                assert(!type.equals("rel"));

                if(type.equals("col")) {
                    start = Integer.parseInt(locElement.getAttribute("start"));

                    if(locElement.hasAttribute("repeat")) {
                        repeat = Integer.parseInt(locElement.getAttribute("repeat"));
                    } else {
                        repeat = -1;

                        // If start is 0 and repeat is -1, the column calculation in Circuit.java fails
                        assert(start != 0);
                    }



                } else if(type.equals("fill")) {
                    start = 0;
                    repeat = 1;

                } else {
                    assert(false);
                }
            }

        }

        // Build maps of inputs and outputs
        Map<String, Integer> inputs = this.getPorts(blockElement, "input");
        Map<String, Integer> outputs = this.getPorts(blockElement, "output");
        Map<String, Integer> clockPorts = this.getPorts(blockElement, "clock");

        BlockType blockType = BlockTypeData.getInstance().addType(
                parentBlockType,
                blockName,
                blockCategory,
                height,
                start,
                repeat,
                priority,
                isClocked,
                inputs,
                outputs,
                clockPorts);

        // If block type is null, this means that there are two block
        // types in the architecture file that we cannot differentiate.
        // This should of course never happen.
        assert(blockType != null);

        // Set the carry chain, if there is one
        Pair<PortType, PortType> direct = this.getDirect(blockType, blockElement);

        if(direct != null) {
            PortType carryFromPort = direct.getFirst();
            PortType carryToPort = direct.getSecond();

            String directId = this.getDirectId(
                    blockType.getName(),
                    carryFromPort.getName(),
                    carryToPort.getName());
            int offsetY = this.directs.get(directId);

            PortTypeData.getInstance().setCarryPorts(carryFromPort, carryToPort, offsetY);
        }

        // Add the different modes and process the children for that mode
        /* Ugly data structure, but otherwise we would have to split it up
         * in multiple structures. Each pair in the list represents a mode
         * of this block type and its properties.
         *   - the first part is a pair that contains a mode name and the
         *     corresponding mode element
         *   - the second part is a list of child types. For each child
         *     type the number of children and the corresponding child
         *     Element are stored.
         */
        List<Pair<Pair<BlockType, Element>, List<Pair<Integer, BlockType>>>> modesAndChildren = this.getModesAndChildren(blockType, blockElement);

        for(Pair<Pair<BlockType, Element>, List<Pair<Integer, BlockType>>> modeAndChildren : modesAndChildren) {

            Pair<BlockType, Element> mode = modeAndChildren.getFirst();
            BlockType blockTypeWithMode = mode.getFirst();
            Element modeElement = mode.getSecond();

            List<BlockType> blockTypes = new ArrayList<>();
            blockTypes.add(blockType);

            // Add all the child types
            for(Pair<Integer, BlockType> child : modeAndChildren.getSecond()) {
                Integer numChildren = child.getFirst();
                BlockType childBlockType = child.getSecond();
                blockTypes.add(childBlockType);

                BlockTypeData.getInstance().addChild(blockTypeWithMode, childBlockType, numChildren);
            }

            // Cache delays to and from this element
            // We can't store them in PortTypeData until all blocks have been stored
            this.cacheDelays(blockTypes, modeElement);
        }

        // Cache setup times (time from input to clock, and from clock to output)
        this.cacheSetupTimes(blockType, blockElement);


        return blockType;
    }

    private BlockType processBlockElement_v8(BlockType parentBlockType, Element blockElement) throws ParseException {
    	//Identify all the blocks in the architecture file and build the blocktype category.
        String blockName = blockElement.getAttribute("name");

        boolean isGlobal = this.isGlobal(blockElement);
        boolean isLeaf = this.isLeaf(blockElement);
        boolean isClocked = this.isClocked(blockElement);


        // Set block category, and some related properties
        BlockCategory blockCategory;

        // CLB blocks fill the rest of the FPGA
        int start = -1, repeat = -1, height = -1, priority = -1;


        if(isLeaf) {
            blockCategory = BlockCategory.LEAF;

        } else if(!isGlobal) {
            blockCategory = BlockCategory.INTERMEDIATE;

        } else {
            // Get some extra properties that relate to the placement
            // of global blocks
        	    	
            blockCategory = this.getGlobalBlockCategory_v8(blockElement);

            if(blockCategory == BlockCategory.IO) {
                // Query for optional capacity attribute, default is 1
                NodeList capacityResult = this.xPathQuery(String.format("//tile[descendant::site[@pb_type='%s']][1]//@capacity", blockName));
                if (capacityResult != null){
                    Attr capacityAttr = (Attr) capacityResult.item(0);
                    this.ioCapacity = Integer.parseInt(capacityAttr.getValue());
                } else {
                    this.ioCapacity = 1;
                }

            } else {

                // Query for optional height attribute, default is 1
                NodeList heightResult = this.xPathQuery(String.format("//tile[descendant::site[@pb_type='%s']][1]//@height", blockName));
                if (heightResult != null){
                    Attr heightAttr = (Attr) heightResult.item(0);
                    height = Integer.parseInt(heightAttr.getValue());
                   // System.out.print("\nThe height is " + height + " and the blockName is " + blockName);
                } else {
                    height = 1;
                }

                // Query layout tag associated with given pb_type. (ex: <fill .. />, <col ../> etc.)
                NodeList layoutResult = this.xPathQuery(String.format("//layout//*[@type=//tile[descendant::site[@pb_type='%s']][1]/@name]", blockName));
                if (layoutResult == null){
                    System.out.println(String.format("Missing layout tag for pb_type '%s'", blockName));
                    System.exit(1);
                }

                // We only look at the first element (tool can't handle e.g. multiple columns)
                Element layout = (Element)layoutResult.item(0);

                // Query for required priority attribute
                priority = Integer.parseInt(layout.getAttribute("priority"));

                // Query for layout type tag
                String type = layout.getNodeName();

                assert(!type.equals("rel"));

                if(type.equals("col")) {
                    start = Integer.parseInt(layout.getAttribute("startx"));

                    if(layout.hasAttribute("repeatx")) {
                        repeat = Integer.parseInt(layout.getAttribute("repeatx"));
                    } else {
                        repeat = -1;
                        // If start is 0 and repeat is -1, the column calculation in Circuit.java fails
                        assert(start != 0);
                    }
                } else if(type.equals("fill")) {
                    start = 0;
                    repeat = 1;

                } else {
                    assert(false);
                }
            }

        }

        // Build maps of inputs and outputs
        Map<String, Integer> inputs = this.getPorts(blockElement, "input");
        Map<String, Integer> outputs = this.getPorts(blockElement, "output");
        Map<String, Integer> clockPorts = this.getPorts(blockElement, "clock");
        
       // if(blockCategory == BlockCategory.IO)

        BlockType blockType = BlockTypeData.getInstance().addType(
                parentBlockType,
                blockName,
                blockCategory,
                height,
                start,
                repeat,
                priority,
                isClocked,
                inputs,
                outputs,
                clockPorts);

        // If block type is null, this means that there are two block
        // types in the architecture file that we cannot differentiate.
        // This should of course never happen.
        assert(blockType != null);

        // Set the carry chain, if there is one
        Pair<PortType, PortType> direct = this.getDirect(blockType, blockElement);

        if(direct != null) {
//        	System.out.print("\nIs this true");
            PortType carryFromPort = direct.getFirst();
            PortType carryToPort = direct.getSecond();

            String directId = this.getDirectId(
                    blockType.getName(),
                    carryFromPort.getName(),
                    carryToPort.getName());
            int offsetY = this.directs.get(directId);

            PortTypeData.getInstance().setCarryPorts(carryFromPort, carryToPort, offsetY);
        }

        // Add the different modes and process the children for that mode
        /* Ugly data structure, but otherwise we would have to split it up
         * in multiple structures. Each pair in the list represents a mode
         * of this block type and its properties.
         *   - the first part is a pair that contains a mode name and the
         *     corresponding mode element
         *   - the second part is a list of child types. For each child
         *     type the number of children and the corresponding child
         *     Element are stored.
         */
        List<Pair<Pair<BlockType, Element>, List<Pair<Integer, BlockType>>>> modesAndChildren = this.getModesAndChildren(blockType, blockElement);

        for(Pair<Pair<BlockType, Element>, List<Pair<Integer, BlockType>>> modeAndChildren : modesAndChildren) {

            Pair<BlockType, Element> mode = modeAndChildren.getFirst();
            BlockType blockTypeWithMode = mode.getFirst();
            Element modeElement = mode.getSecond();

            List<BlockType> blockTypes = new ArrayList<>();
            blockTypes.add(blockType);

            // Add all the child types
            for(Pair<Integer, BlockType> child : modeAndChildren.getSecond()) {
                Integer numChildren = child.getFirst();
                BlockType childBlockType = child.getSecond();
                blockTypes.add(childBlockType);

                BlockTypeData.getInstance().addChild(blockTypeWithMode, childBlockType, numChildren);
            }

            // Cache delays to and from this element
            // We can't store them in PortTypeData until all blocks have been stored
            this.cacheDelays(blockTypes, modeElement);
        }

        // Cache setup times (time from input to clock, and from clock to output)
        this.cacheSetupTimes(blockType, blockElement);
       

        return blockType;
    }



    
    private boolean isLeaf(Element blockElement) {
        if(this.hasImplicitChildren(blockElement)) {
            return false;

        } else {
            return blockElement.hasAttribute("blif_model");
        }
    }
    private boolean hasImplicitChildren(Element blockElement) {
        if(blockElement.hasAttribute("class")) {
            String blockClass = blockElement.getAttribute("class");
            return blockClass.equals("lut") || blockClass.equals("memory");

        } else {
            return false;
        }
    }

    private boolean isGlobal(Element blockElement) {
        Element parentElement = (Element) blockElement.getParentNode();
        return parentElement.getTagName().equals("complexblocklist");
    }

    private boolean isClocked(Element blockElement) {
        if(!isLeaf(blockElement)) {
            return false;
        }

        String blifModel = blockElement.getAttribute("blif_model");
        switch(blifModel) {
            case ".names":
                return false;

            case ".latch":
            case ".input":
            case ".output":
                return true;

            default:
                // blifModel starts with .subckt, we are interested in the second part
                String modelName = blifModel.substring(8);
                return this.modelIsClocked.get(modelName);
        }
    }


    private BlockCategory getGlobalBlockCategory_v7(Element blockElement) throws ParseException {
   
        Element gridLocations = this.getFirstChild(blockElement, "gridlocations");
        if(gridLocations != null){
            Element locElement = this.getFirstChild(gridLocations, "loc");
            String type = locElement.getAttribute("type");
            if(type.equals("fill")) {
                return BlockCategory.CLB;
            }
        } else {
            return null;
        }

        Stack<Element> elements = new Stack<Element>();
        elements.add(blockElement);

        while(elements.size() > 0) {
            Element element = elements.pop();
            String name = element.getAttribute("name");
            String blifModel = element.getAttribute("blif_model");
            if(blifModel.equals(".input") || blifModel.equals(".output")) {
                return BlockCategory.IO;
            }


            List<Element> modeElements = this.getChildElementsByTagName(element, "mode");
            modeElements.add(element);

            for(Element modeElement : modeElements) {
                name = modeElement.getAttribute("name");
                elements.addAll(this.getChildElementsByTagName(modeElement, "pb_type"));
            }
        }

        return BlockCategory.HARDBLOCK;
    }

    private BlockCategory getGlobalBlockCategory_v8(Element blockElement) throws ParseException {
        // Check if this block type should fill the FPGA, in v8 this is found under layout
        // If it does, we call this the CLB type (there can
        // only be one clb type in an architecture)

        // To find layout type for block element:
        // first step: which tile implements pb_type?
        // second step: get layout type for tile
        String pbTypeName = blockElement.getAttribute("name");
        String layoutType;
        try {
            XPathExpression expr = this.xPath.compile(String.format("//layout//*[@type=//tile[descendant::site[@pb_type='%s']][1]/@name][1]", pbTypeName));
            NodeList result = (NodeList) expr.evaluate(this.xmlDocument, XPathConstants.NODESET);
            layoutType = result.item(0).getNodeName();
        } catch (XPathExpressionException e) {
            layoutType = "";
            System.out.println(String.format("Found no layout for pb_type name=\"%s\"", pbTypeName));
            System.exit(1);
        }


        if(layoutType.equals("fill")) {
            return BlockCategory.CLB;
        }


        // Descend down until a leaf block is found
        // If the leaf block has has blif_model .input or
        // .output, this is the io block type (there can
        // only be one io type in an architecture)
        Stack<Element> elements = new Stack<Element>();
        elements.add(blockElement);

        while(elements.size() > 0) {
            Element element = elements.pop();
            String name = element.getAttribute("name");
            String blifModel = element.getAttribute("blif_model");
            if(blifModel.equals(".input") || blifModel.equals(".output")) {
                return BlockCategory.IO;
            }

            List<Element> modeElements = this.getChildElementsByTagName(element, "mode");
            modeElements.add(element);

            for(Element modeElement : modeElements) {
                name = modeElement.getAttribute("name");
                elements.addAll(this.getChildElementsByTagName(modeElement, "pb_type"));
            }
        }

        return BlockCategory.HARDBLOCK;
    }

    private Map<String, Integer> getPorts(Element blockElement, String portType) {
        Map<String, Integer> ports = new HashMap<>();

        List<Element> portElements = this.getChildElementsByTagName(blockElement, portType);
        for(Element portElement : portElements) {
            String portName = portElement.getAttribute("name");

            int numPins = Integer.parseInt(portElement.getAttribute("num_pins"));
            ports.put(portName, numPins);
        }

        return ports;
    }


    private Pair<PortType, PortType> getDirect(BlockType blockType, Element blockElement) {

    	
    	//The architecture file description has changed and we need to use a new method.
    	//the directMap has name of the block followed by the output and then input.
        PortType carryFromPort = null, carryToPort = null;

        if(this.directMap.keySet().contains(blockType.getName())) {
        	Map<String, String> portMap = this.directMap.get(blockType.getName());
        	for(String portnames: portMap.keySet()) {
        		String outPortName = portnames;
        		String inPortName = portMap.get(outPortName);
        		PortType outPortType = new PortType(blockType, outPortName);
        		PortType inPortType = new PortType(blockType, inPortName);
        		
        		assert(carryToPort == null);
        		carryToPort = inPortType;
        		assert(carryFromPort == null);
        		carryFromPort = outPortType;
                
        	}
        }
        assert(!(carryFromPort == null ^ carryToPort == null));
        if(carryFromPort == null) {
            return null;
        } else {
            return new Pair<PortType, PortType>(carryFromPort, carryToPort);
        }
        
    }


    private List<Pair<Pair<BlockType, Element>, List<Pair<Integer, BlockType>>>> getModesAndChildren(BlockType blockType, Element blockElement) throws ParseException {

        List<Pair<Pair<BlockType, Element>, List<Pair<Integer, BlockType>>>> modesAndChildren = new ArrayList<>();

        String blockName = blockElement.getAttribute("name");

        if(this.hasImplicitChildren(blockElement)) {

            switch(blockElement.getAttribute("class")) {
                case "lut":
                {

                    BlockType blockTypeWithMode = BlockTypeData.getInstance().addMode(blockType, blockName);
                    Pair<BlockType, Element> mode = new Pair<>(blockTypeWithMode, blockElement);

                    BlockType childBlockType = this.addImplicitLut(blockTypeWithMode, blockElement);
                    List<Pair<Integer, BlockType>> modeChildren = new ArrayList<>();
                    modeChildren.add(new Pair<Integer, BlockType>(1, childBlockType));
                    modesAndChildren.add(new Pair<>(mode, modeChildren));


                    blockTypeWithMode = BlockTypeData.getInstance().addMode(blockType, "wire");
                    mode = new Pair<>(blockTypeWithMode, blockElement);

                    modeChildren = new ArrayList<>();
                    modesAndChildren.add(new Pair<>(mode, modeChildren));

                    break;
                }

                case "memory":
                {

                    // Determine the number of children
                    int numChildren = -1;
                    for(Element outputElement : this.getChildElementsByTagName(blockElement, "output")) {
                        String portClass = outputElement.getAttribute("port_class");
                        if(portClass.startsWith("data_out")) {
                            int numPins = Integer.parseInt(outputElement.getAttribute("num_pins"));

                            if(numChildren == -1) {
                                numChildren = numPins;

                            } else if(numChildren != numPins) {
                                throw new ArchitectureException(String.format(
                                        "inconsistend number of data output pins in memory: %d and %d",
                                        numChildren, numPins));
                            }
                        }
                    }

                    BlockType blockTypeWithMode = BlockTypeData.getInstance().addMode(blockType, "memory_slice");
                    Pair<BlockType, Element> mode = new Pair<>(blockTypeWithMode, blockElement);

                    BlockType childBlockType = this.addImplicitMemorySlice(blockTypeWithMode, blockElement);
                    List<Pair<Integer, BlockType>> modeChildren = new ArrayList<>();
                    modeChildren.add(new Pair<Integer, BlockType>(numChildren, childBlockType));
                    modesAndChildren.add(new Pair<>(mode, modeChildren));

                    break;
                }

                default:
                    throw new ArchitectureException("Unknown block type with implicit children: " + blockName);
            }

        } else if(this.isLeaf(blockElement)) {
          
            BlockType blockTypeWithMode = BlockTypeData.getInstance().addMode(blockType, "");
            Pair<BlockType, Element >mode = new Pair<>(blockTypeWithMode, blockElement);
            List<Pair<Integer, BlockType>> modeChildren = new ArrayList<>();

            modesAndChildren.add(new Pair<>(mode, modeChildren));

        } else {
            List<Element> modeElements = new ArrayList<>();
            modeElements.addAll(this.getChildElementsByTagName(blockElement, "mode"));

            // There is 1 mode with the same name as the block
            // There is 1 child block type
            if(modeElements.size() == 0) {
                modeElements.add(blockElement);
            }

            // Add the actual modes and their children
            for(Element modeElement : modeElements) {
                String modeName = modeElement.getAttribute("name");
                BlockType blockTypeWithMode = BlockTypeData.getInstance().addMode(blockType, modeName);
                Pair<BlockType, Element> mode = new Pair<>(blockTypeWithMode, modeElement);

                List<Pair<Integer, BlockType>> modeChildren = new ArrayList<>();
                List<Element> childElements = this.getChildElementsByTagName(modeElement, "pb_type");
                for(Element childElement : childElements) {
                    int numChildren = Integer.parseInt(childElement.getAttribute("num_pb"));
                    BlockType childBlockType = this.processBlockElement_v7(blockTypeWithMode, childElement);
                    modeChildren.add(new Pair<>(numChildren, childBlockType));
                }
                modesAndChildren.add(new Pair<>(mode, modeChildren));
            }
        }

        return modesAndChildren;
    }


    private BlockType addImplicitLut(BlockType parentBlockType, Element parentBlockElement) {
        // A lut has exactly the same ports as its parent lut5/lut6/... block
        Map<String, Integer> inputPorts = this.getPorts(parentBlockElement, "input");
        Map<String, Integer> outputPorts = this.getPorts(parentBlockElement, "output");
        Map<String, Integer> clockPorts = this.getPorts(parentBlockElement, "clock");
        String lutName = "lut";

        BlockType lutBlockType = BlockTypeData.getInstance().addType(
                parentBlockType,
                lutName,
                BlockCategory.LEAF,
                -1,
                -1,
                -1,
                -1,
                false,
                inputPorts,
                outputPorts,
                clockPorts);

        // A lut has one unnamed mode without children
        BlockTypeData.getInstance().addMode(lutBlockType, "");

        // Process delays
        Element delayMatrixElement = this.getFirstChild(parentBlockElement, "delay_matrix");

 
        String sourcePortName = delayMatrixElement.getAttribute("in_port").split("\\.")[1];
        PortType sourcePortType = new PortType(lutBlockType, sourcePortName);
        String sinkPortName = delayMatrixElement.getAttribute("out_port").split("\\.")[1];
        PortType sinkPortType = new PortType(lutBlockType, sinkPortName);

        String[] delays = delayMatrixElement.getTextContent().split("\\s+");
        int index = delays[0].length() > 0 ? 0 : 1;
        double delay = Double.parseDouble(delays[index]);

        this.delays.add(new Triple<PortType, PortType, Double>(sourcePortType, sinkPortType, delay));

        return lutBlockType;
    }


    private BlockType addImplicitMemorySlice(BlockType parentBlockType, Element parentBlockElement) {
        // The memory slice has exactly the same ports as its parent,
        // except there is only one data_in and data_out

        Map<String, Integer> inputPorts = this.getPorts(parentBlockElement, "input");
        for(Element inputElement : this.getChildElementsByTagName(parentBlockElement, "input")) {
            if(inputElement.getAttribute("port_class").startsWith("data_in")) {
                inputPorts.put(inputElement.getAttribute("name"), 1);
            }
        }

        Map<String, Integer> outputPorts = this.getPorts(parentBlockElement, "output");
        for(Element outputElement : this.getChildElementsByTagName(parentBlockElement, "output")) {
            if(outputElement.getAttribute("port_class").startsWith("data_out")) {
                inputPorts.put(outputElement.getAttribute("name"), 1);
            }
        }

        Map<String, Integer> clockPorts = this.getPorts(parentBlockElement, "clock");

        String memorySliceName = "memory_slice";

        // ASM: memory slices are clocked
        BlockType memoryBlockType = BlockTypeData.getInstance().addType(
                parentBlockType,
                memorySliceName,
                BlockCategory.LEAF,
                -1,
                -1,
                -1,
                -1,
                true,
                inputPorts,
                outputPorts,
                clockPorts);

        // Add the new memory type as a child of the parent type
        BlockTypeData.getInstance().addChild(parentBlockType, memoryBlockType, 1);

        // A memory_slice has one unnamed mode without children
        BlockTypeData.getInstance().addMode(memoryBlockType, "");


        // Process setup times
        // These are added as delays between the parent and child element
        List<Element> setupElements = this.getChildElementsByTagName(parentBlockElement, "T_setup");
        for(Element setupElement : setupElements) {
            String sourcePortName = setupElement.getAttribute("port").split("\\.")[1];
            PortType sourcePortType = new PortType(parentBlockType, sourcePortName);
            PortType sinkPortType = new PortType(memoryBlockType, sourcePortName);

            double delay = Double.parseDouble(setupElement.getAttribute("value"));
            this.delays.add(new Triple<PortType, PortType, Double>(sourcePortType, sinkPortType, delay));
        }

        // Process clock to port times
        // These are added as delays between the child and parent element
        List<Element> clockToPortElements = this.getChildElementsByTagName(parentBlockElement, "T_clock_to_Q");
        for(Element clockToPortElement : clockToPortElements) {
            String sinkPortName = clockToPortElement.getAttribute("port").split("\\.")[1];
            PortType sourcePortType = new PortType(memoryBlockType, sinkPortName);
            PortType sinkPortType = new PortType(parentBlockType, sinkPortName);

            double delay = Double.parseDouble(clockToPortElement.getAttribute("max"));
            this.delays.add(new Triple<PortType, PortType, Double>(sourcePortType, sinkPortType, delay));
        }


        return memoryBlockType;
    }


    private void cacheSetupTimes(BlockType blockType, Element blockElement) {
        // Process setup times
        List<Element> setupElements = this.getChildElementsByTagName(blockElement, "T_setup");
        for(Element setupElement : setupElements) {
            String portName = setupElement.getAttribute("port").split("\\.")[1];
            PortType portType = new PortType(blockType, portName);
            double delay = Double.parseDouble(setupElement.getAttribute("value"));

            this.setupTimes.add(new Pair<PortType, Double>(portType, delay));
        }

        // Process clock to port times
        List<Element> clockToPortElements = this.getChildElementsByTagName(blockElement, "T_clock_to_Q");
        for(Element clockToPortElement : clockToPortElements) {
            String portName = clockToPortElement.getAttribute("port").split("\\.")[1];
            PortType portType = new PortType(blockType, portName);
            double delay = Double.parseDouble(clockToPortElement.getAttribute("max"));

            this.setupTimes.add(new Pair<PortType, Double>(portType, delay));
        }


    }


    private void cacheDelays(List<BlockType> blockTypes, Element modeElement) {



        List<Element> elements = new ArrayList<Element>();
        elements.add(modeElement);
        Element interconnectElement = this.getFirstChild(modeElement, "interconnect");
        if(interconnectElement != null) {
            elements.addAll(this.getChildElementsByTagName(interconnectElement, "direct"));
            elements.addAll(this.getChildElementsByTagName(interconnectElement, "complete"));
            elements.addAll(this.getChildElementsByTagName(interconnectElement, "mux"));
        }

        for(Element element : elements) {
            for(Element delayConstantElement : this.getChildElementsByTagName(element, "delay_constant")) {

                double delay = Double.parseDouble(delayConstantElement.getAttribute("max"));
                String[] sourcePorts = delayConstantElement.getAttribute("in_port").split("\\s+");
                String[] sinkPorts = delayConstantElement.getAttribute("out_port").split("\\s+");

                for(String sourcePort : sourcePorts) {
                    for(String sinkPort : sinkPorts) {
                        if(!(sourcePort.length() == 0 || sinkPort.length() == 0)) {
                            this.cacheDelay(blockTypes, sourcePort, sinkPort, delay);
                        }
                    }
                }
            }
        }
    }

    private void cacheDelay(List<BlockType> blockTypes, String sourcePort, String sinkPort, double delay) {

        String[] ports = {sourcePort, sinkPort};
        List<PortType> portTypes = new ArrayList<PortType>(2);

        for(String port : ports) {
            String[] portParts = port.split("\\.");
            String blockName = portParts[0].split("\\[")[0];
            String portName = portParts[1].split("\\[")[0];

            BlockType portBlockType = null;
            for(BlockType blockType : blockTypes) {
                if(blockName.equals(blockType.getName())) {
                    portBlockType = blockType;
                }
            }

            portTypes.add(new PortType(portBlockType, portName));
        }

        this.delays.add(new Triple<PortType, PortType, Double>(portTypes.get(0), portTypes.get(1), delay));
    }


    private void processDelays() {
        for(Pair<PortType, Double> setupTimeEntry : this.setupTimes) {
            PortType portType = setupTimeEntry.getFirst();
            double delay = setupTimeEntry.getSecond();

            portType.setSetupTime(delay);
        }

        for(Triple<PortType, PortType, Double> delayEntry : this.delays) {
            PortType sourcePortType = delayEntry.getFirst();
            PortType sinkPortType = delayEntry.getSecond();
            double delay = delayEntry.getThird();
 
            sourcePortType.setDelay(sinkPortType, delay);
        }
    }



    private Element getFirstChild(Element blockElement, String tagName) {
        NodeList childNodes = blockElement.getChildNodes();
        for(int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);

            if(childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) childNode;
                if(childElement.getTagName().equals(tagName)) {
                    return childElement;
                }
            }
        }

        return null;
    }

    private List<Element> getChildElementsByTagName(Element blockElement, String tagName) {
        List<Element> childElements = new ArrayList<Element>();

        NodeList childNodes = blockElement.getChildNodes();
        for(int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);

            if(childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) childNode;
                if(childElement.getTagName().equals(tagName)) {
                    childElements.add(childElement);
                }
            }
        }

        return childElements;
    }





    public void getVprTiming(String vprCommand) throws IOException, InterruptedException, InvalidFileFormatException {
        // For this method to work, the macro PRINT_ARRAYS should be defined

        String command = String.format(
                "%s %s %s --blif_file %s --net_file %s --place_file vpr_tmp.place --place --init_t 1 --exit_t 1",
                vprCommand, this.architectureFile, this.circuitName, this.blifFile, this.netFile);
        System.out.print(command +"\n");
        
        Process process = null;
        process = Runtime.getRuntime().exec(command);


        // Read output to avoid buffer overflow and deadlock
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        while ((reader.readLine()) != null) {}
        process.waitFor();


        // Build delay tables
        String lookupDumpPath = "placement_delta_delay_model.echo";
        File lookupDumpFile = new File(lookupDumpPath);
        this.buildDelayTables(lookupDumpFile);

        // Clean up
        this.deleteFile("vpr_tmp.place");
        this.deleteFile("vpr_stdout.log");
        this.deleteFile(lookupDumpPath);
    }

    public void getVprTiming(File lookupDumpFile) throws IOException, InvalidFileFormatException {
        this.buildDelayTables(lookupDumpFile);
    }

    private void buildDelayTables(File lookupDumpFile) throws IOException, InvalidFileFormatException {
        // Parse the delay tables
        this.delayTables = new DelayTables(lookupDumpFile);
        this.delayTables.parse();
    }

    private void deleteFile(String path) throws IOException {
        Files.deleteIfExists(new File(path).toPath());
    }



    public boolean isAutoSized() {
        return this.autoSize;
    }
    public double getAutoRatio() {
        return this.autoRatio;
    }
    public int getWidth() {
        return this.width;
    }
    public int getHeight() {
        return this.height;
    }

    public DelayTables getDelayTables() {
        return this.delayTables;
    }



    public int getIoCapacity() {
        return this.ioCapacity;
    }


    public boolean isImplicitBlock(String blockTypeName) {
        // ASM: lut and memory_slice are the only possible implicit blocks
        return blockTypeName.equals("lut") || blockTypeName.equals("memory_slice");
    }
    public String getImplicitBlockName(String parentBlockTypeName, String blockTypeName) {
        return parentBlockTypeName + "." + blockTypeName;
    }


    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(BlockTypeData.getInstance());
        out.writeObject(PortTypeData.getInstance());
    }

    private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
        in.defaultReadObject();
        BlockTypeData.setInstance((BlockTypeData) in.readObject());
        PortTypeData.setInstance((PortTypeData) in.readObject());
    }


    public class ArchitectureException extends RuntimeException {
        private static final long serialVersionUID = 5348447367650914363L;

        ArchitectureException(String message) {
            super(message);
        }
    }

    private NodeList xPathQuery(String query){
        try {
            XPathExpression expr = this.xPath.compile(query);
            NodeList result = (NodeList) expr.evaluate(this.xmlDocument, XPathConstants.NODESET);
            return result;
        } catch (XPathExpressionException e) {
            System.out.println(String.format("XPath query: \"%s\" failed.", query));
            return null;
        }
    }
}
