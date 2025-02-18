package pack.partition;

import java.io.*;
import java.util.*;

import pack.netlist.B;
import pack.util.Output;


public class MoveHardBlock {
	private Part[] result;
	private HashMap<B,Integer> blocks;
	private java.util.Stack<B> SortedBlocks; 
	
	public MoveHardBlock(Part[] result){
		this.result = result;
		this.blocks = new HashMap<B,Integer>();
		this.SortedBlocks = new java.util.Stack<> ();
	}

	public void moveDSP(int count){
		//Move DSP block that leads to the smallest increase in terminal count
		// Since the blocks have to be balanced in both the partitions, we have to arrange the blocks in an ascending order to ensure that
		//that the blocks with lost terminal increase are considered.
		B moveBlock = null;
		Part from,to = null;
		
		if(this.result[0].numDSPPrimitives() > this.result[1].numDSPPrimitives()) {
			from = this.result[0];
			to = this.result[1];
		}else {
			from = this.result[1];
			to = this.result[0];
		}
		HashSet<B> primitives = new HashSet<B>(from.getDSPPrimitives());
		for(B b:primitives){
			int terminalIncrease = from.connections(b) - to.connections(b);
			this.blocks.put(b,terminalIncrease);

		}

		this.SortedBlocks = this.SortingBlocks(this.blocks);
		while(count != 0) {
			moveBlock = this.SortedBlocks.pop();
			moveBlock.move(from, to);

			count--;
		}

	}
	public void moveRAM(int count){
		//Move DSP block that leads to the smallest increase in terminal count
		// Since the blocks have to be balanced in both the partitions, we have to arrange the blocks in an ascending order to ensure that
		//that the blocks with lost terminal increase are considered.
		B moveBlock = null;
		Part from,to = null;
		int RAMCount = count;

		
		if(this.result[0].numDieRAMPrimitives() > this.result[1].numDieRAMPrimitives()) {
			from = this.result[0];
			to = this.result[1];
		}else {
			from = this.result[1];
			to = this.result[0];
		}
		HashSet<B> primitives = new HashSet<B>(from.getprimitivesDieRAM());

		for(B b:primitives){
			int terminalIncrease = from.connections(b) - to.connections(b);

			this.blocks.put(b,terminalIncrease);
		}
		this.SortedBlocks = this.SortingBlocks(this.blocks);

		
		while(RAMCount != 0) {
			moveBlock = this.SortedBlocks.pop();
			moveBlock.move(from, to);
			RAMCount--;
		}

	}
	
	public java.util.Stack<B> SortingBlocks(HashMap<B,Integer> result) {
		HashMap<B,Integer> unsortedMap = result;
		List<HashMap.Entry<B,Integer>> list = new LinkedList<HashMap.Entry<B,Integer>>(unsortedMap.entrySet());
		// Sort the list
		Collections.sort(list, new Comparator<Map.Entry<B, Integer> >() {
			public int compare(Map.Entry<B, Integer> o1,
							Map.Entry<B, Integer> o2)
			{
				return (o1.getValue()).compareTo(o2.getValue());
			}
		});
		
		ListIterator<Map.Entry<B,Integer>> reverseList = list.listIterator(list.size());

		java.util.Stack<B> stack = new java.util.Stack<> ();
		while(reverseList.hasPrevious()) {
			stack.push(reverseList.previous().getKey());
		}

		return stack;
		
	}
}