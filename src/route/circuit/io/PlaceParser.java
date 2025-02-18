package route.circuit.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import route.circuit.Circuit;
import route.circuit.CircuitSLL;
import route.circuit.block.GlobalBlock;
import route.circuit.exceptions.PlacementException;
import route.circuit.resource.ResourceGraph;
import route.circuit.resource.Site;
import route.circuit.resource.Instance;

public class PlaceParser {

    private static Pattern sizePattern = Pattern.compile("Array size: (?<width>\\d+) x (?<height>\\d+).*");
    private static Pattern sitePattern = Pattern.compile("(?<block>\\S+)\\s+(?<x>\\d+)\\s+(?<y>\\d+)\\s+(?<z>\\d+).*");

    private Integer halfHeight;
    private Map<String, int[]> coordinates;
    private Circuit circuit;
    private CircuitSLL circuitSLL;
    private File file;
    private ResourceGraph rrg;
    private int dieID;

    public PlaceParser(Circuit circuit, CircuitSLL circuitSLL, File file, ResourceGraph rrg, int currentDie) {
        this.circuit = circuit;
        this.circuitSLL = circuitSLL;
        this.file = file;
        this.rrg = rrg;
        this.dieID = currentDie;
    }

    public void parse() throws IOException, PlacementException, BlockNotFoundException, IllegalSizeException {

        BufferedReader reader = new BufferedReader(new FileReader(this.file));

        // Read all the coordinates and store them
        this.coordinates = new HashMap<String, int[]>();

        String line;
        while((line = reader.readLine()) != null) {
            this.processLine(line);
        }


        // Loop over all the blocks in the circuit
        for(GlobalBlock block : this.circuit.getGlobalBlocks()) {

            // Get the coordinate of the block
            String blockName = block.getName();
            if(!this.coordinates.containsKey(blockName)) {
                reader.close();
                throw new BlockNotFoundException(blockName);
            }

            int[] coordinate = this.coordinates.get(blockName);
            int x = coordinate[0];
            int y = coordinate[1];
            int z = coordinate[2];
            // Bind the site and block to each other
            Site site = rrg.getSite(x, y);
            Instance siteInstance = site.getInstance(z);
            block.setSiteInstance(siteInstance);
            this.coordinates.remove(blockName);
        }
        
       
        for(String blockName: this.coordinates.keySet()) {
            int[] coordinate = this.coordinates.get(blockName);
        	String netName = blockName.substring(6);
        	this.circuitSLL.updateSLLCoordinates(netName, coordinate, this.dieID);
        }

        reader.close();
    }


    private void processLine(String line) throws IllegalSizeException {
        Matcher sizeMatcher = sizePattern.matcher(line);
        boolean sizeMatches = sizeMatcher.matches();

        Matcher siteMatcher = sitePattern.matcher(line);
        boolean siteMatches = siteMatcher.matches();

        if(sizeMatches) {
        	int width = Integer.parseInt(sizeMatcher.group("width"));
        	int height = Integer.parseInt(sizeMatcher.group("height"));
        	this.halfHeight = height;


        } else if(siteMatches) {

        	int dieNum = this.circuit.getCurrentDie();
            String blockName = siteMatcher.group("block");
            int x = Integer.parseInt(siteMatcher.group("x"));
            int y = Integer.parseInt(siteMatcher.group("y"));
            int z = Integer.parseInt(siteMatcher.group("z"));


            int[] coordinate = {x, y, z};
            this.coordinates.put(blockName, coordinate);
        }
    }
}
