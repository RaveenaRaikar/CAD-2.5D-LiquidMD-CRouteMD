package place.circuit.pin;

import java.util.ArrayList;
import java.util.List;

import place.circuit.architecture.PortType;
import place.circuit.block.AbstractBlock;
import place.circuit.timing.TimingNode;

public abstract class AbstractPin {

    protected AbstractBlock owner;
    private PortType portType;
    private int index;

    private AbstractPin source, sink;
    private ArrayList<AbstractPin> sinks;
    private int numSinks = 0;
    
    private TimingNode timingNode;

    public AbstractPin(AbstractBlock owner, PortType portType, int index) {
        this.owner = owner;
        this.portType = portType;
        this.index = index;
    }

    public void setTimingNode(TimingNode timingNode) {
        this.timingNode = timingNode;
    }
    public TimingNode getTimingNode() {
        return this.timingNode;
    }
    public boolean hasTimingNode() {
    	return this.timingNode != null;
    }
    
    public AbstractBlock getOwner() {
        return this.owner;
    }
    public PortType getPortType() {
        return this.portType;
    }

    public String getPortName() {
        return this.portType.getName();
    }
    public int getIndex() {
        return this.index;
    }


    public boolean isInput() {
        return this.portType.isInput();
    }
    public boolean isOutput() {
        return this.portType.isOutput();
    }
    public boolean isClock() {
        return this.portType.isClock();
    }


    public AbstractPin getSource() {
        return this.source;
    }
    public void setSource(AbstractPin source) {
        this.source = source;
    }
    public void removeSource() {
        this.source = null;
    }
  
    public int getNumSinks() {
        return this.numSinks;
    }
    public List<AbstractPin> getSinks() {
        switch(this.numSinks) {
        case 0:
            return new ArrayList<AbstractPin>();

        case 1:
            List<AbstractPin> pins = new ArrayList<AbstractPin>(1);
            pins.add(this.sink);
            return pins;

        default:
            return this.sinks;
        }
    }
    public AbstractPin getSink(int index) {
        if(this.numSinks == 1) {
            return this.sink;

        } else {
            return this.sinks.get(index);
        }
    }

    public void addSink(AbstractPin sink) {
        switch(this.numSinks) {
        case 0:
            this.sink = sink;
            break;

        case 1:
            this.sinks = new ArrayList<AbstractPin>();
            this.sinks.add(this.sink);

        default:
            this.sinks.add(sink);
        }

        this.numSinks++;
    }
    
    public void removeSink(AbstractPin sink) {
    	this.sinks.remove(sink);
        this.numSinks--;
    }
    

    public void compact() {
        if(this.numSinks > 1) {
            this.sinks.trimToSize();
        }
    }


    @Override
    public String toString() {
        return this.owner.toString() + "." + this.portType.getName() + "[" + this.index + "]";
    }

	public AbstractPin getSLLSource() {
		return (AbstractPin) this.source;
	}


}
