package route.route;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import route.circuit.Circuit;
import route.circuit.CircuitSLL;
import route.circuit.resource.Opin;
import route.circuit.resource.ResourceGraph;
import route.circuit.resource.RouteNode;
import route.circuit.resource.RouteNodeType;
import route.circuit.timing.TimingGraphSLL;

public class ConnectionRouter {
	final ResourceGraph rrg;
	final Circuit[] circuitDie;
	final CircuitSLL circuitSLL;
	final TimingGraphSLL timingGraphSLL;
	
	final int totDie;
	private int currentDie = 0;
	
	private float pres_fac, pres_fac_mult = 2;
	private float alphaWLD = 1.4f;
	private float alphaTD = 0.7f;
	
	private float MIN_REROUTE_CRITICALITY = 0.85f, REROUTE_CRITICALITY;
	private final List<Connection> criticalConnections;
	
	private int MAX_PERCENTAGE_CRITICAL_CONNECTIONS = 3;
	
	private final PriorityQueue<QueueElement> queue;

	
	private final Collection<RouteNodeData> nodesTouched;
	
	private final float COST_PER_DISTANCE_HORIZONTAL, COST_PER_DISTANCE_VERTICAL, DELAY_PER_DISTANCE_HORIZONTAL, DELAY_PER_DISTANCE_VERTICAL;
	private int distance_same_dir, distance_ortho_dir;
	private final float IPIN_BASE_COST;
	private static final float MAX_CRITICALITY = 0.99f;
	private static final float CRITICALITY_EXPONENT = 3;
	
	private int connectionsRouted, nodesExpanded;
	private int connectionsRoutedIteration;
	
	private int itry;
	
	private RouteTimers routeTimers;
	
	public static final boolean DEBUG = true;
	
	public ConnectionRouter(ResourceGraph rrg, Circuit[] circuitDie, CircuitSLL circuitSLL, int totDie, TimingGraphSLL timingGraphSLL) {
		this.rrg = rrg;
		this.circuitDie = circuitDie;
		this.circuitSLL = circuitSLL;
		
		this.timingGraphSLL = timingGraphSLL;
		
		this.totDie = totDie;
		this.nodesTouched = new ArrayList<>();
		
		this.queue = new PriorityQueue<>(Comparators.PRIORITY_COMPARATOR);

		this.criticalConnections = new ArrayList<>();


		COST_PER_DISTANCE_HORIZONTAL = this.getAverageCost(RouteNodeType.CHANX);
		COST_PER_DISTANCE_VERTICAL = this.getAverageCost(RouteNodeType.CHANY);
		
		DELAY_PER_DISTANCE_HORIZONTAL = this.getAverageDelay(RouteNodeType.CHANX);
		DELAY_PER_DISTANCE_VERTICAL = this.getAverageDelay(RouteNodeType.CHANY);
		
		System.out.print("The delay per horizontal is " + DELAY_PER_DISTANCE_HORIZONTAL + "\n");
		System.out.print("The delay per vertical is " + DELAY_PER_DISTANCE_VERTICAL+ "\n");
		
		IPIN_BASE_COST = this.rrg.get_ipin_indexed_data().getBaseCost();
		
		this.connectionsRouted = 0;
		this.nodesExpanded = 0;
		this.routeTimers = new RouteTimers();
	}
	
	private float getAverageCost(RouteNodeType type) {
		float averageCost = 0;
		int divider = 0;
		for(RouteNode node : this.rrg.getRouteNodes()) {
			if(node.type.equals(type)) {
				averageCost += node.base_cost;
				divider += node.wireLength();
			}
		}
		return averageCost / divider;
	}
	private float getAverageDelay(RouteNodeType type) {
		float averageDelay = 0;
		int divider = 0;
		for(RouteNode node : this.rrg.getRouteNodes()) {
			if(node.type.equals(type)) {
				averageDelay += node.getDelay();
				if(node.getDelay() < 0) {
					System.out.print("\nThe node is " + node + " delay is " + node.getDelay());
				}
				divider += node.wireLength();
			}
		}
		return averageDelay / divider;
	}
	
	private void printManhattanDistance() {
		for(int numDie = 0; numDie < this.totDie; numDie++) {
			int[] distanceArray = new int[this.circuitDie[numDie].getWidth() + this.circuitDie[numDie].getHeight() + 10];
			for(int i = 0; i < distanceArray.length; i++) {
				distanceArray[i] = 0;
			}
			for(Connection conn : this.circuitDie[numDie].getConnections()) {
				int manhattanDistance = conn.getManhattanDistance();
				distanceArray[manhattanDistance]++;
			}
			
			int maxDistance = 0;
			for(int i = 0; i < distanceArray.length; i++) {
				if(distanceArray[i] > 0) {
					maxDistance = i;
				}
			}
			System.out.print("\nThe max distance is " + maxDistance);
			System.out.print("\nManhattan Distance;");
			for(int i = 0; i <= maxDistance; i++) {
				System.out.print(distanceArray[i]);
				if(i < maxDistance) {
					System.out.print(";");
				} else {
					System.out.print("\n");
				}
			}
		}
		
		System.out.println();
	}
    
    public int route() {
    	
    	this.printManhattanDistance();
    	
    	System.out.println("--------------------------------------------------------------------------------------------------------------");
    	System.out.println("|                                             CONNECTION ROUTER                                              |");
    	System.out.println("--------------------------------------------------------------------------------------------------------------");

    	System.out.println("\tNum Interposer nets: " + this.circuitSLL.getSLLNets().size());
		System.out.println("\tNum Interposer cons: " + this.circuitSLL.getSLLConnections().size());		
    	
    	for(int i = 0; i < this.totDie; i++) {
 
    		System.out.println("Num nets: " + this.circuitDie[i].getNets().size());
    		System.out.println("Num cons: " + this.circuitDie[i].getConnections().size());
    	}

    		int timeMilliseconds = this.doRuntimeRouting(100, 4);
    		
    		System.out.println("Run testers");
    		int errors = 0;
    		/***************************
    		 * OPIN tester: test if each
    		 * net uses only one OPIN
    		 ***************************/
    		for(int i = 0; i < this.totDie; i++) {
    			for(Net net : this.circuitDie[i].getNets()) {
        			Set<Opin> opins = new HashSet<>();
        			String name = null;
        			for(Connection con : net.getConnections()) {
        				Opin opin = con.getOpin();
        				if(opin == null) {
        					System.out.println("Connection has no opin!");
        				} else {
        					opins.add(opin);
        				}
        			}
        			if(opins.size() != 1) {
        				System.out.println("Net " + name + " has " + opins.size() + " opins");
        				errors += 1;
        			} 
        		}
    		}
    		
    		
    		/*******************************
    		 * Check if the routing is valid
    		 *******************************/
    		for(RouteNode node : this.rrg.getRouteNodes()) {
    			if(node.overUsed() || node.illegal()) {
    				System.out.println(node);
    				errors += 1;
    			}
    		}
    		if (errors == 0) {
    			System.out.println("No errors found\n");
    		} else {
    			System.out.println("ERRORS FOUND! => The routing has " + errors + " errors\n");
    		}
    		System.out.println();

		
		this.calculateExtraNode();
		return timeMilliseconds;
		
	}
    
    private int doRuntimeRouting(int nrOfTrials, int fixOpins) {
    	System.out.printf("--------------------------------------------------------------------------------------------------------------\n");
    	long start = System.nanoTime();
    	System.out.println("Starting with routing Interposer nets");
    	this.doRouting(nrOfTrials, fixOpins);
    	long end = System.nanoTime();
    	int timeMilliseconds = (int)Math.round((end-start) * Math.pow(10, -6));
    	System.out.printf("--------------------------------------------------------------------------------------------------------------\n");
    	System.out.println("Runtime " + timeMilliseconds + " ms");
    	System.out.println("Num iterations: " + this.itry);
		System.out.println("Connections routed: " + this.connectionsRouted);

		System.out.println("Connections rerouted: " + (this.connectionsRouted - this.circuitSLL.getSLLConnections().size()));
		for(int i = 0; i < this.totDie; i++) {
			System.out.println("Connections rerouted: " + (this.connectionsRouted - this.circuitDie[i].getConnections().size()) + " for die " + i);			
		}
		
		System.out.println("Nodes expanded: " + this.nodesExpanded);
		System.out.printf("--------------------------------------------------------------------------------------------------------------\n");
		System.out.print(this.routeTimers);
		System.out.printf("--------------------------------------------------------------------------------------------------------------\n\n");
		
    	return timeMilliseconds;
    }
    
    private void doRouting(int nrOfTrials, int fixOpins) {
    	

       final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
       this.timingGraphSLL.calculatePlacementEstimatedWireDelay();
       this.timingGraphSLL.calculateArrivalRequiredAndCriticality(MAX_CRITICALITY, CRITICALITY_EXPONENT);

        
		System.out.printf("%-22s | %.1f\n", "Criticality Exponent", CRITICALITY_EXPONENT);
		System.out.printf("%-22s | %.2f\n", "Max Criticality", MAX_CRITICALITY);
		System.out.printf("%-22s | %.3e\n", "Cost per distance hor", COST_PER_DISTANCE_HORIZONTAL);
		System.out.printf("%-22s | %.3e\n", "Cost per distance ver", COST_PER_DISTANCE_VERTICAL);
		System.out.printf("%-22s | %.3e\n", "Delay per distance hor", DELAY_PER_DISTANCE_HORIZONTAL);
		System.out.printf("%-22s | %.3e\n", "Delay per distance ver", DELAY_PER_DISTANCE_VERTICAL);
		System.out.printf("%-22s | %.3e\n", "IPIN Base cost", IPIN_BASE_COST);
		System.out.printf("%-22s | %.2f\n", "WLD Alpha", this.alphaWLD);
		System.out.printf("%-22s | %.2f\n", "TD Alpha", this.alphaTD);
		System.out.printf("%-22s | %.2f\n", "Min reroute crit", MIN_REROUTE_CRITICALITY);
		System.out.printf("%-22s | %d\n", "Max per crit con", MAX_PERCENTAGE_CRITICAL_CONNECTIONS);
		System.out.printf("%-22s | %.1f\n", "Pres fac mult", this.pres_fac_mult);
		
        System.out.printf("-----------------------------------------------------------------------------------------------------------------------\n");
        System.out.printf("%9s  %9s  %8s  %8s  %12s  %9s  %11s  %17s  %11s  %9s\n", "Iteration", "Die Number",  "AlphaWLD", "AlphaTD", "Reroute Crit", "Time (ms)", "Conn routed", "Overused RR Nodes", "Wire-Length", "Max Delay");
        System.out.printf("--------- --------- -------- --------  --------  ------------  ---------  -----------  -----------------  -----------  ---------\n");
        

		
        this.runningCroute(nrOfTrials, fixOpins, this.totDie);
		for(int i = 0; i < this.totDie; i++) {
			this.currentDie = i;

			this.runningCroute(nrOfTrials, fixOpins, this.currentDie);
		}


		return;
    }
    
    
    
    private void runningCroute(int nrOfTrials, int fixOpins, int dieNumber) {
        boolean validRouting;
    	this.nodesTouched.clear();
    	this.queue.clear();
		
    	float initial_pres_fac = 0.5f;
		float pres_fac_mult = this.pres_fac_mult;
		float acc_fac = 1;
		this.pres_fac = initial_pres_fac;
		
		this.itry = 1;
		List<Connection> sortedListOfConnections = new ArrayList<>();
		List<Net> sortedListOfNets = new ArrayList<>();

		if(dieNumber == this.totDie) {
			sortedListOfConnections.addAll(this.circuitSLL.getSLLConnections());
			sortedListOfNets.addAll(this.circuitSLL.getSLLNets());
		}else {
			sortedListOfConnections.addAll(this.circuitDie[this.currentDie].getConnections());
			sortedListOfNets.addAll(this.circuitDie[this.currentDie].getNets());
		}

		//Sorting the list in ascending order
		Collections.sort(sortedListOfConnections, Comparators.FanoutConnection);
		Collections.sort(sortedListOfNets, Comparators.FanoutNet);
        
        while (this.itry <= nrOfTrials) {
        	long iterationStart = System.nanoTime();

        	this.connectionsRoutedIteration = 0;
        	validRouting = true;
        	
        	//Fix opins in order of high fanout nets
        	this.routeTimers.fixOpin.start();
        	if(this.itry >= fixOpins) {
            	for(Net net : sortedListOfNets) {
            		if(!net.hasOpin()) {
                		Opin opin = net.getMostUsedOpin();
            			if(!opin.isOpin) {
                			net.setOpin(opin);
                			opin.isOpin = true;
                		}
            		}
            	}
        	}
        	this.routeTimers.fixOpin.finish();
        	
        	this.routeTimers.setRerouteCriticality.start();
        	this.setRerouteCriticality(sortedListOfConnections);
        	this.routeTimers.setRerouteCriticality.finish();

        	//Route Connections
        	for(Connection con : sortedListOfConnections) {
					if (this.itry == 1) {
						this.routeTimers.firstIteration.start();
						this.routeConnection(con);
						this.routeTimers.firstIteration.finish();
					}else if (con.congested()) {
	
						this.routeTimers.rerouteCongestion.start();
						this.routeConnection(con);
						this.routeTimers.rerouteCongestion.finish();

						
					}else if (con.net.hasOpin() && !con.getOpin().equals(con.net.getOpin())) {
						this.routeTimers.rerouteOpin.start();
						this.routeConnection(con);
						this.routeTimers.rerouteOpin.finish();
						
					} else if (con.getCriticality() > REROUTE_CRITICALITY) {

						this.routeTimers.rerouteCritical.start();
						this.routeConnection(con);
						this.routeTimers.rerouteCritical.finish();
					}

				if(con.congested()) validRouting = false;
				if(!con.net.hasOpin()) validRouting = false;		
			}

        	if(validRouting) this.fixIllegalTrees(sortedListOfConnections, dieNumber);

			String maxDelayString = String.format("%9s", "---");
			this.routeTimers.updateTiming.start();

			float maxDelay = 0;

			this.timingGraphSLL.calculateActualWireDelay();
			this.timingGraphSLL.calculateArrivalRequiredAndCriticality(MAX_CRITICALITY, CRITICALITY_EXPONENT);
			maxDelay = this.timingGraphSLL.getMaxDelay();
					
			maxDelayString = String.format("%9.3f", maxDelay);

			this.routeTimers.updateTiming.finish();
			
			//Calculate statistics
			this.routeTimers.calculateStatistics.start();
			
			int numRouteNodes = this.rrg.getRouteNodes().size();
			int overUsed = this.getNumOverusedAndIllegalNodes(sortedListOfConnections);
			double overUsePercentage = 100.0 * (double)overUsed / numRouteNodes;
			
			int wireLength = this.rrg.congestedTotalWireLengt();
			
			this.routeTimers.calculateStatistics.finish();
			
			//Runtime
			long iterationEnd = System.nanoTime();
			int rt = (int) Math.round((iterationEnd-iterationStart) * Math.pow(10, -6));
			
			System.out.printf("%9d  %9d  %8.2f  %8.2f  %12.3f  %9d  %11d  %8d  %6.2f%%  %11d  %s\n", this.itry, dieNumber, this.alphaWLD, this.alphaTD, REROUTE_CRITICALITY, rt, this.connectionsRoutedIteration, overUsed, overUsePercentage, wireLength, maxDelayString);
	        

			//Check if the routing is valid, if realizable return, the routing succeeded
			if (validRouting) {

				for(Connection con : sortedListOfConnections) {
		        	int wirelength = 0;
		        	for(RouteNode nodes: con.routeNodes) {
		        		if(nodes.isWire) {
		        			wirelength = wirelength + nodes.wireLength();
		        		}
		        	}

		        }
				return;
			}
			
			//Updating the cost factors
			this.routeTimers.updateCost.start();
			if (this.itry == 1) {
				this.pres_fac = initial_pres_fac;
			} else {
				this.pres_fac *= pres_fac_mult;
			}
			this.updateCost(this.pres_fac, acc_fac);
			this.routeTimers.updateCost.finish();
			
			this.itry++;

		}


		if (this.itry == nrOfTrials + 1) {
			System.out.println("Routing failled after " + this.itry + " trials!");
			
			int maxNameLength = 0;
			
			Set<RouteNode> overused = new HashSet<>();
			for (Connection conn: sortedListOfConnections) {
				for (RouteNode node: conn.routeNodes) {
					if (node.overUsed() || node.illegal()) {
						overused.add(node);
					}
				}
			}
			for (RouteNode node: overused) {
				if (node.overUsed()) {
					if(node.toString().length() > maxNameLength) {
						maxNameLength = node.toString().length();
					}
				}
			}
			
			for (RouteNode node: overused) {
				if (node.overUsed()) {
					System.out.println(node.toString());
				}
			}
			System.out.println();
		}
    }
    
    private int getNumOverusedAndIllegalNodes(List<Connection> connections) {
        Set<Integer> overUsed = new HashSet<>();
		for (Connection conn : connections) {
			for (RouteNode node : conn.routeNodes) {
				if (node.overUsed() || node.illegal()) {
					overUsed.add(node.hashCode());
				}
			}
		}
		return overUsed.size();
    }
    private int getNumIllegalNodes(List<Connection> connections) {
        Set<Integer> illegal = new HashSet<>();
		for (Connection con : connections) {
			for (RouteNode node : con.routeNodes) {
				if (node.illegal()) {
					illegal.add(node.hashCode());
				}
			}
		}
		return illegal.size();
    }
    
    private void fixIllegalTrees(List<Connection> connections, int dieNumber) {
		this.routeTimers.rerouteIllegal.start();
		List<Net> allNets = new ArrayList<>();
		int numIllegalNodes = this.getNumIllegalNodes(connections);
		
		if (numIllegalNodes > 0) {
			
			//Fix the illegal tree by following the highest criticality paths
			List<Net> illegalTrees = new ArrayList<>();
			if(dieNumber == this.totDie) {
				allNets = this.circuitSLL.getSLLNets();
			}else {
				allNets = this.circuitDie[dieNumber].getNets();
			}
			for(Net net : allNets) {
				boolean illegal = false;
				for(Connection con : net.getConnections()) {
					if(con.illegal()) {
						illegal = true;
					}
				}
				if(illegal) {
					illegalTrees.add(net);
				}
			}

			for(Net illegalTree : illegalTrees) {
				RouteNode illegalNode;
				while((illegalNode = illegalTree.getIllegalNode()) != null) {
					List<Connection> illegalConnections = new ArrayList<>();
					for(Connection con : illegalTree.getConnections()) {
						for(RouteNode node : con.routeNodes) {
							if(node.equals(illegalNode)) {
								illegalConnections.add(con);
							}
						}
					}

					//Find the illegal connection with maximum criticality
					Connection maxCriticalityConnection = illegalConnections.get(0);
					for(Connection illegalConnection : illegalConnections) {
						if(illegalConnection.getCriticality() > maxCriticalityConnection.getCriticality()) {
							maxCriticalityConnection = illegalConnection;
						}
					}
					
					//Get the path from the connection with maximum criticality
					List<RouteNode> newRouteNodes = new ArrayList<>();
					boolean add = false;
					for(RouteNode newRouteNode : maxCriticalityConnection.routeNodes) {
						if(newRouteNode.equals(illegalNode)) add = true;
						if(add) newRouteNodes.add(newRouteNode);
					}
					//Replace the path with the path from the connection with maximum criticality
					for(Connection illegalConnection : illegalConnections) {
						this.ripup(illegalConnection);
						
						//Remove illegal path from routing tree
						while(!illegalConnection.routeNodes.remove(illegalConnection.routeNodes.size() - 1).equals(illegalNode));
						
						//Add new path to routing tree
						for(RouteNode newRouteNode : newRouteNodes) {
							illegalConnection.addRouteNode(newRouteNode);
						}
						
						this.add(illegalConnection);
					}

				}
				
			}
		}
		this.routeTimers.rerouteIllegal.finish();
    }

    private void setRerouteCriticality(List<Connection> connections) {
    	//Limit number of critical connections
    	REROUTE_CRITICALITY = MIN_REROUTE_CRITICALITY;
    	this.criticalConnections.clear();

    	int maxNumberOfCriticalConnections = (int) (connections.size() * 0.01 * MAX_PERCENTAGE_CRITICAL_CONNECTIONS);
    	
    	for(Connection con : connections) {
    		if(con.getCriticality() > REROUTE_CRITICALITY) {
    			this.criticalConnections.add(con);
    		}
    	}
    	
    	if(this.criticalConnections.size() > maxNumberOfCriticalConnections) {
    		Collections.sort(this.criticalConnections, Comparators.ConnectionCriticality);
    		REROUTE_CRITICALITY = this.criticalConnections.get(maxNumberOfCriticalConnections).getCriticality();
    	}
    }
    
    private void routeConnection(Connection con) {

    	this.ripup(con);
    	this.route(con);
    	this.add(con);
    }
	private void ripup(Connection con) {

		RouteNode parent = null;
		for(int i = con.routeNodes.size() - 1; i >=0; i--) {
			RouteNode node = con.routeNodes.get(i);
			
			RouteNodeData data = node.routeNodeData;
			
			data.removeSource(con.source);
			
			if (parent == null) {
				parent = node;
			} else {
				data.removeParent(parent);
				parent = node;
			}
			
			// Calculation of present congestion penalty
			node.updatePresentCongestionPenalty(this.pres_fac);
		}
	}
	private void add(Connection con) {

		RouteNode parent = null;
		for(int i = con.routeNodes.size() - 1; i >=0; i--) {
			RouteNode node = con.routeNodes.get(i);
			
			RouteNodeData data = node.routeNodeData;

			data.addSource(con.source);

			if (parent == null) {
				parent = node;
			} else {
				data.addParent(parent);
				parent = node;
			}
			
			// Calculation of present congestion penalty
			node.updatePresentCongestionPenalty(this.pres_fac);
		}
	}
	private boolean route(Connection con) {
		this.connectionsRouted++;
		this.connectionsRoutedIteration++;
	
		// Clear Routing
		con.resetConnection();

		// Clear Queue
		this.queue.clear();
		
		// Set target flag sink
		RouteNode sink = con.sinkRouteNode;
		sink.target = true;
		
		// Add source to queue
		RouteNode source = con.sourceRouteNode;

		this.addNodeToQueue(source, null, 0, 0);

		// Start Dijkstra / directed search
		while (!targetReached()) {

			this.expandFirstNode(con);
		}
		
		// Reset target flag sink
		sink.target = false;
		
		// Save routing in connection class
		this.saveRouting(con);
		
		// Reset path cost from Dijkstra Algorithm
		this.resetPathCost();

		return true;
	}
	

	private void saveRouting(Connection con) {
		RouteNode rn = con.sinkRouteNode;
		
		while (rn != null) {
			con.addRouteNode(rn);
			rn = rn.routeNodeData.prev;
		}
	}

	private boolean targetReached() {
		RouteNode queueHead = this.queue.peek().node;
		if(queueHead == null){
			System.out.println("queue is empty");			
			return false;
		} else {
			return queueHead.target;
		}
	}
	
	private void resetPathCost() {
		for (RouteNodeData node : this.nodesTouched) {
			node.touched = false;
		}
		this.nodesTouched.clear();
	}

	private void expandFirstNode(Connection con) {
		this.nodesExpanded++;
		
		if (this.queue.isEmpty()) {
			System.out.println(con.netName + " " + con.source.getPortName() + " " + con.sink.getPortName());
			throw new RuntimeException("Queue is empty: target unreachable?");
		}

		RouteNode node = this.queue.poll().node;

		for (RouteNode child : node.children) {
			
			if(child != null) {

				//CHANX OR CHANY
				if (child.isWire) {
					if (con.isInBoundingBoxLimit(child)) {
						this.addNodeToQueue(node, child, con);

					}
				
				//OPIN
				} else if (child.type == RouteNodeType.OPIN) {
					if(con.net.hasOpin()) {
						if (child.equals(con.net.getOpin())) {
							this.addNodeToQueue(node, child, con);
						}
					} else if (!child.isOpin) {
						this.addNodeToQueue(node, child, con);
					}
				
				//IPIN
				} else if (child.type == RouteNodeType.IPIN) {
					if(child.children[0].target) {
						this.addNodeToQueue(node, child, con);
					}else{
						this.addNodeToQueue(node, child, con);
					}
					
				//SINK
				} else if (child.type == RouteNodeType.SINK) {
					this.addNodeToQueue(node, child, con);
				}	
			}

		}
	}
	
	private void addNodeToQueue(RouteNode node, RouteNode child, Connection con) {
		RouteNodeData data = child.routeNodeData;
		int countSourceUses = data.countSourceUses(con.source);
		
		float partial_path_cost = node.routeNodeData.getPartialPathCost();
		
		// PARTIAL PATH COST
		float new_partial_path_cost = partial_path_cost + (1 - con.getCriticality()) * this.getRouteNodeCost(child, con, countSourceUses) + con.getCriticality() * child.getDelay();
		
		// LOWER BOUND TOTAL PATH COST
		// This is just an estimate and not an absolute lower bound.
		// The routing algorithm is therefore not A* and optimal.
		// It's directed search and heuristic.
		float new_lower_bound_total_path_cost;
		if(child.isWire) {
			//Expected remaining cost
			RouteNode target = con.sinkRouteNode;
			
			this.set_expected_distance_to_target(child, target);
			
			float expected_distance_cost, expected_timing_cost;
			
			//assumption that all wires have the same base cost, change it to the actual cost read from the file.
			if(child.type.equals(RouteNodeType.CHANX)) {
				expected_distance_cost = this.distance_same_dir * COST_PER_DISTANCE_HORIZONTAL + this.distance_ortho_dir * COST_PER_DISTANCE_VERTICAL;
				expected_timing_cost = this.distance_same_dir * DELAY_PER_DISTANCE_HORIZONTAL + this.distance_ortho_dir * DELAY_PER_DISTANCE_VERTICAL;
			} else {
				expected_distance_cost = this.distance_same_dir * COST_PER_DISTANCE_VERTICAL + this.distance_ortho_dir * COST_PER_DISTANCE_HORIZONTAL;
				expected_timing_cost = this.distance_same_dir * DELAY_PER_DISTANCE_VERTICAL + this.distance_ortho_dir * DELAY_PER_DISTANCE_HORIZONTAL;
			}

			float expected_wire_cost = expected_distance_cost / (1 + countSourceUses) + IPIN_BASE_COST;
			new_lower_bound_total_path_cost = new_partial_path_cost + this.alphaWLD * (1 - con.getCriticality()) * expected_wire_cost + this.alphaTD * con.getCriticality() * expected_timing_cost;

		} else {
			new_lower_bound_total_path_cost = new_partial_path_cost;
		}
		
		this.addNodeToQueue(child, node, new_partial_path_cost, new_lower_bound_total_path_cost);
	}


	
	public void set_expected_distance_to_target(RouteNode node, RouteNode target) {
		/*************************************************
		 * Function adapted and modified from VPR 7.0.7, *
		 * get_expected_segs_to_target in route_timing.c *
		 *************************************************/
		RouteNodeType type = node.type;
		short ylow, yhigh, xlow, xhigh;
		int no_need_to_pass_by_clb;
		
		short target_x = target.xlow;
		short target_y = target.ylow;
		
		if (type == RouteNodeType.CHANX) { 
			ylow = node.ylow;
			xhigh = node.xhigh;
			xlow = node.xlow;

			if (ylow > target_y) { /* Coming from a row above target? */
				this.distance_ortho_dir = ylow - target_y + 1;
				no_need_to_pass_by_clb = 1;
			} else if (ylow < target_y - 1) { /* Below the CLB bottom? */
				this.distance_ortho_dir = target_y - ylow;
				no_need_to_pass_by_clb = 1;
			} else { /* In a row that passes by target CLB */
				this.distance_ortho_dir = 0;
				no_need_to_pass_by_clb = 0;
			}

			if (xlow > target_x + no_need_to_pass_by_clb) {
				this.distance_same_dir = xlow - no_need_to_pass_by_clb - target_x;
			} else if (xhigh < target_x - no_need_to_pass_by_clb) {
				this.distance_same_dir = target_x - no_need_to_pass_by_clb - xhigh;
			} else {
				this.distance_same_dir = 0;
			}
			
			return;
			
		} else { /* CHANY */
			ylow = node.ylow;
			yhigh = node.yhigh;
			xlow = node.xlow;

			if (xlow > target_x) { /* Coming from a column right of target? */
				this.distance_ortho_dir = xlow - target_x + 1;
				no_need_to_pass_by_clb = 1;
			} else if (xlow < target_x - 1) { /* Left of and not adjacent to the CLB? */
				this.distance_ortho_dir = target_x - xlow;
				no_need_to_pass_by_clb = 1;
			} else { /* In a column that passes by target CLB */
				this.distance_ortho_dir = 0;
				no_need_to_pass_by_clb = 0;
			}

			if (ylow > target_y + no_need_to_pass_by_clb) {
				this.distance_same_dir = ylow - no_need_to_pass_by_clb - target_y;
			} else if (yhigh < target_y - no_need_to_pass_by_clb) {
				this.distance_same_dir = target_y - no_need_to_pass_by_clb - yhigh;
			} else {
				this.distance_same_dir = 0;
			}
			
			return;
		}
	}
	
	
	private void addNodeToQueue(RouteNode node, RouteNode prev, float new_partial_path_cost, float new_lower_bound_total_path_cost) {
		RouteNodeData data = node.routeNodeData;
		//source node data
		if(!data.touched) {
			this.nodesTouched.add(data);
			data.setLowerBoundTotalPathCost(new_lower_bound_total_path_cost);
			data.setPartialPathCost(new_partial_path_cost);
			data.prev = prev;
			this.queue.add(new QueueElement(node, new_lower_bound_total_path_cost));
			
		} else if (data.updateLowerBoundTotalPathCost(new_lower_bound_total_path_cost)) { //queue is sorted by lower bound total cost
			data.setPartialPathCost(new_partial_path_cost);
			data.prev = prev;
			this.queue.add(new QueueElement(node, new_lower_bound_total_path_cost));
		}
	}

	
    public void calculateExtraNode() {
    	for(Net sllNets: this.circuitSLL.getSLLNets()) {
    		RouteNode oldNode = null, currentNode;
    		int counter = 0;
    		List<RouteNode> allNodes = new ArrayList<>();
    		
    		for(Connection conn: sllNets.getConnections()) {
    			for(RouteNode nodes: conn.routeNodes) {
    				if(nodes.isSLL) {
    					if(!allNodes.contains(nodes)) {
    						allNodes.add(nodes);
    					}
    					if(counter == 0) {
    						currentNode = nodes;
    						oldNode = nodes;
    						counter++;
    					}else {
    						currentNode = nodes;
    						if(currentNode != oldNode) {
    							counter++;	
    						}
    					}
    				}
    			}
    		}
    	}

    }
    
    
	private float getRouteNodeCost(RouteNode node, Connection con, int countSourceUses) {
		RouteNodeData data = node.routeNodeData;
		
		boolean containsSource = countSourceUses != 0;
		
		//Present congestion cost
		float pres_cost;
		if (containsSource) {
			int overoccupation = data.numUniqueSources() - node.capacity;
			if (overoccupation < 0) {
				pres_cost = 1;
			} else {
				pres_cost = 1 + overoccupation * this.pres_fac;
			}
		} else {
			pres_cost = data.pres_cost;
		}
		
		//Bias cost
		float bias_cost = 0;
		if(node.isWire) {
			Net net = con.net;
			bias_cost = 0.5f * node.base_cost / net.fanout * (Math.abs(node.centerx - net.x_geo) + Math.abs(node.centery - net.y_geo)) / net.hpwl;
		}

		return node.base_cost * data.acc_cost * pres_cost / (1 + countSourceUses) + bias_cost;
	}
	
	private void updateCost(float pres_fac, float acc_fac){
		for (RouteNode node : this.rrg.getRouteNodes()) {
			RouteNodeData data = node.routeNodeData;

			int overuse = data.occupation - node.capacity;
			
			//Present congestion penalty
			if(overuse == 0) {
				data.pres_cost = 1 + pres_fac;
			} else if (overuse > 0) {
				data.pres_cost = 1 + (overuse + 1) * pres_fac;
				data.acc_cost = data.acc_cost + overuse * acc_fac;
			}
		}
	}
	
}
