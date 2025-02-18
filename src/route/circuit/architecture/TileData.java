package route.circuit.architecture;

public class TileData {
    private String tileName;
    private float inVal;
    private float outVal;

    public TileData(String tileName, float inVal, float outVal) {
        this.tileName = tileName;
        this.inVal = inVal;
        this.outVal = outVal;
    }

    public String getTileName() {
        return tileName;
    }

    public float getInVal() {
        return inVal;
    }

    public float getOutVal() {
        return outVal;
    }
}