package route.route;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import route.circuit.Circuit;
import route.circuit.resource.Opin;
import route.circuit.resource.ResourceGraph;
import route.circuit.resource.RouteNode;
import route.circuit.resource.RouteNodeType;

public class ConnectionRouter {
	final ResourceGraph rrg;
	final Circuit circuit;
	
	private float pres_fac, pres_fac_mult = 2;
	private float alphaWLD = 1.4f;
	
	private float usage_multiplier = 10;
	
	private float MIN_REROUTE_CRITICALITY = 0.85f, REROUTE_CRITICALITY;
	private final List<Connection> criticalConnections;
	
	private int MAX_PERCENTAGE_CRITICAL_CONNECTIONS = 3;
	private float alphaTD = 0.7f;
	
	private final PriorityQueue<QueueElement> queue;
	
	private final Collection<RouteNodeData> nodesTouched;
	
	private final float COST_PER_DISTANCE_HORIZONTAL, COST_PER_DISTANCE_VERTICAL, DELAY_PER_DISTANCE_HORIZONTAL, DELAY_PER_DISTANCE_VERTICAL;
	private int distance_same_dir, distance_ortho_dir;
	private final float IPIN_BASE_COST;
	private static final double MAX_CRITICALITY = 0.99;
	private static double CRITICALITY_EXPONENT = 3;
	
	private int connectionsRouted, nodesExpanded;
	
	private int itry;
	private boolean td;
	
	public static final boolean DEBUG = true;
	
	public ConnectionRouter(ResourceGraph rrg, Circuit circuit, boolean td) {
		this.rrg = rrg;
		this.circuit = circuit;

		this.nodesTouched = new ArrayList<>();
		
		this.queue = new PriorityQueue<>(Comparators.PRIORITY_COMPARATOR);
		
		this.criticalConnections = new ArrayList<>();

		COST_PER_DISTANCE_HORIZONTAL = this.getAverageCost(RouteNodeType.CHANX);
		COST_PER_DISTANCE_VERTICAL = this.getAverageCost(RouteNodeType.CHANY);
		
		DELAY_PER_DISTANCE_HORIZONTAL = this.getAverageDelay(RouteNodeType.CHANX);
		DELAY_PER_DISTANCE_VERTICAL = this.getAverageDelay(RouteNodeType.CHANY);
		
		IPIN_BASE_COST = this.rrg.get_ipin_indexed_data().getBaseCost();
		
		this.td = td;
		
		this.connectionsRouted = 0;
		this.nodesExpanded = 0;
	}
	
	private float getAverageCost(RouteNodeType type) {
		double averageCost = 0;
		int divider = 0;
		for(RouteNode node : this.rrg.getRouteNodes()) {
			if(node.type.equals(type)) {
				averageCost += node.base_cost;
				divider += node.wireLength();
			}
		}
		return (float)(averageCost / divider);
	}
	private float getAverageDelay(RouteNodeType type) {
		double averageDelay = 0;
		int divider = 0;
		for(RouteNode node : this.rrg.getRouteNodes()) {
			if(node.type.equals(type)) {
				averageDelay += node.indexedData.t_linear;
				divider += node.wireLength();
			}
		}
		return (float)(averageDelay / divider);
	}
    
    public int route(float alphaWLD, float alphaTD, float presFacMult, float rerouteCriticality, float criticalityExponent, float usageMultiplier) {
    	if(alphaWLD > 0) this.alphaWLD = alphaWLD;
    	if(alphaTD > 0) this.alphaTD = alphaTD;
    	
    	if(usageMultiplier > 0) this.usage_multiplier = usageMultiplier;
     	
    	if(rerouteCriticality > 0) MIN_REROUTE_CRITICALITY = rerouteCriticality;
    	if(criticalityExponent > 0) CRITICALITY_EXPONENT = criticalityExponent;
    	
    	if(presFacMult > 0) this.pres_fac_mult = presFacMult;
    	
    	System.out.println("-------------------------------------------------------------------------------------------------");
    	System.out.println("|                                       CONNECTION ROUTER                                       |");
    	System.out.println("-------------------------------------------------------------------------------------------------");
    	System.out.println("Num nets: " + this.circuit.getNets().size());
		System.out.println("Num cons: " + this.circuit.getConnections().size());
	
		int timeMilliseconds = this.doRuntimeRouting(100, 4);
		
		//System.out.println(this.circuit.getTimingGraph().criticalPathToString());
		
		/***************************
		 * OPIN tester: test if each
		 * net uses only one OPIN
		 ***************************/
		for(Net net : this.circuit.getNets()) {
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
			} 
		}
		
		return timeMilliseconds;
	}
    private int doRuntimeRouting(int nrOfTrials, int fixOpins) {
    	System.out.printf("-------------------------------------------------------------------------------------------------\n");
    	long start = System.nanoTime();
    	this.doRouting(nrOfTrials, fixOpins);
    	long end = System.nanoTime();
    	int timeMilliseconds = (int)Math.round((end-start) * Math.pow(10, -6));
    	System.out.printf("-------------------------------------------------------------------------------------------------\n");
    	System.out.println("Runtime " + timeMilliseconds + " ms");
		System.out.println("Connections routed: " + this.connectionsRouted);
		System.out.println("Nodes expanded: " + this.nodesExpanded);
    	System.out.printf("-------------------------------------------------------------------------------------------------\n\n");
    	return timeMilliseconds;
    }
    private void doRouting(int nrOfTrials, int fixOpins) {
    	
    	this.nodesTouched.clear();
    	this.queue.clear();
		
    	float initial_pres_fac = 0.5f;
		float pres_fac_mult = this.pres_fac_mult;
		float acc_fac = 1;
		this.pres_fac = initial_pres_fac;
		
		this.itry = 1;
		
		List<Connection> sortedListOfConnections = new ArrayList<>();
		sortedListOfConnections.addAll(this.circuit.getConnections());
		Collections.sort(sortedListOfConnections, Comparators.FanoutConnection);
		
        List<Net> sortedListOfNets = new ArrayList<>();
        sortedListOfNets.addAll(this.circuit.getNets());
        Collections.sort(sortedListOfNets, Comparators.FanoutNet);
        
		this.circuit.getTimingGraph().calculatePlacementEstimatedWireDelay();
		this.circuit.getTimingGraph().calculateArrivalRequiredAndCriticality(MAX_CRITICALITY, CRITICALITY_EXPONENT);
        
		System.out.printf("%-22s | %s\n", "Timing Driven", this.td);
		System.out.printf("%-22s | %.1f\n", "Criticality Exponent", CRITICALITY_EXPONENT);
		System.out.printf("%-22s | %.2f\n", "Max Criticality", MAX_CRITICALITY);
		System.out.printf("%-22s | %.3e\n", "Cost per distance hor", COST_PER_DISTANCE_HORIZONTAL);
		System.out.printf("%-22s | %.3e\n", "Cost per distance ver", COST_PER_DISTANCE_VERTICAL);
		System.out.printf("%-22s | %.3e\n", "Delay per distance hor", DELAY_PER_DISTANCE_HORIZONTAL);
		System.out.printf("%-22s | %.3e\n", "Delay per distance ver", DELAY_PER_DISTANCE_VERTICAL);
		System.out.printf("%-22s | %.3e\n", "IPIN Base cost", IPIN_BASE_COST);
		System.out.printf("%-22s | %.2f\n", "WLD Alpha", this.alphaWLD);
		System.out.printf("%-22s | %.2f\n", "TD Alpha", this.alphaTD);
		System.out.printf("%-22s | %.2f\n", "Usage multiplier", this.usage_multiplier);
		System.out.printf("%-22s | %.2f\n", "Min reroute crit", MIN_REROUTE_CRITICALITY);
		System.out.printf("%-22s | %d\n", "Max per crit con", MAX_PERCENTAGE_CRITICAL_CONNECTIONS);
		System.out.printf("%-22s | %.1f\n", "Pres fac mult", pres_fac_mult);
		
        System.out.printf("-------------------------------------------------------------------------------------------------\n");
        System.out.printf("%9s  %8s  %8s  %12s  %9s  %17s  %11s  %9s\n", "Iteration", "AlphaWLD", "AlphaTD", "Reroute Crit", "Time (ms)", "Overused RR Nodes", "Wire-Length", "Max Delay");
        System.out.printf("---------  --------  --------  ------------  ---------  -----------------  -----------  ---------\n");
        
        boolean validRouting = false;
        
        while (this.itry <= nrOfTrials) {
        	validRouting = true;
        	long iterationStart = System.nanoTime();

        	//Fix opins in order of high fanout nets
        	if(this.itry >= fixOpins) {
            	for(Net net : sortedListOfNets) {
            		if(!net.hasOpin()) {
            			//TODO MOST USED OR MOST IMPORTANT?
            			//Opin opin = net.getMostImportantOpin();
                		Opin opin = net.getMostUsedOpin();
            			if(!opin.isOpin) {
                			net.setOpin(opin);
                			opin.isOpin = true;
                		}
                		validRouting = false;
            		}
            	}
        	} else if(this.itry < fixOpins){
        		validRouting = false;
        	}
        	
        	this.setRerouteCriticality(sortedListOfConnections);
        	
        	//Route Connections
        	for(Connection con : sortedListOfConnections) {
				if (this.itry == 1) {
					this.ripup(con);
					this.route(con);
					this.add(con);
					
					validRouting = false;

				} else if (con.congested()) {
					this.ripup(con);
					this.route(con);
					this.add(con);
					
					validRouting = false;
				
				}else if (con.net.hasOpin() && !con.getOpin().equals(con.net.getOpin())) {
					this.ripup(con);
					this.route(con);
					this.add(con);
					
					validRouting = false;
					
				} else if (con.getCriticality() > REROUTE_CRITICALITY) {
					this.ripup(con);
					this.route(con);
					this.add(con);
				}
			}
			
			String maxDelayString = String.format("%9s", "---");
			
			//Update timing and criticality
			if(this.td) {
				double old_delay = this.circuit.getTimingGraph().getMaxDelay();
				
				this.circuit.getTimingGraph().calculateActualWireDelay();
				this.circuit.getTimingGraph().calculateArrivalRequiredAndCriticality(MAX_CRITICALITY, CRITICALITY_EXPONENT);
				
				double maxDelay = this.circuit.getTimingGraph().getMaxDelay();
				if(maxDelay < old_delay) {
					validRouting = false;
				}
				
				maxDelayString = String.format("%9.3f", maxDelay);
			}
			
			int numRouteNodes = this.rrg.getRouteNodes().size();
			int overUsed = this.calculateNumOverusedNodes(sortedListOfConnections);
			double overUsePercentage = 100.0 * (double)overUsed / numRouteNodes;
			
			int wireLength = this.rrg.congestedTotalWireLengt();
			
			if(!this.td) {
				this.circuit.getTimingGraph().calculateActualWireDelay();
				this.circuit.getTimingGraph().calculateArrivalRequiredAndCriticality(1, 1);
				maxDelayString = String.format("%9.3f", this.circuit.getTimingGraph().getMaxDelay());
			}
			
			//Runtime
			long iterationEnd = System.nanoTime();
			int rt = (int) Math.round((iterationEnd-iterationStart) * Math.pow(10, -6));
			
			//Check if the routing is realizable, if realizable return, the routing succeeded 
			if (validRouting){
				this.circuit.setConRouted(true);

				return;
			} else {
				System.out.printf("%9d  %8.2f  %8.2f  %12.3f  %9d  %8d  %6.2f%%  %11d  %s\n", this.itry, this.alphaWLD, this.alphaTD, REROUTE_CRITICALITY, rt, overUsed, overUsePercentage, wireLength, maxDelayString);
			}
			
			//Updating the cost factors
			if (this.itry == 1) {
				this.pres_fac = initial_pres_fac;
			} else {
				this.pres_fac *= pres_fac_mult;
			}
			this.updateCost(this.pres_fac, acc_fac);
			
			this.itry++;
		}
        
		if (this.itry == nrOfTrials + 1) {
			System.out.println("Routing failled after " + this.itry + " trials!");
			
			int maxNameLength = 0;
			
			Set<RouteNode> overused = new HashSet<>();
			for (Connection conn: sortedListOfConnections) {
				for (RouteNode node: conn.routeNodes) {
					if (node.overUsed()) {
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
		return;
    }
    private int calculateNumOverusedNodes(List<Connection> connections) {
        Set<RouteNode> overUsed = new HashSet<>();
		for (Connection conn : connections) {
			for (RouteNode node : conn.routeNodes) {
				if (node.overUsed()) {
					overUsed.add(node);
				}
			}
		}
		return overUsed.size();
    }

    private void setRerouteCriticality(List<Connection> connections) {
    	//Limit number of critical connections
    	REROUTE_CRITICALITY = MIN_REROUTE_CRITICALITY;
    	this.criticalConnections.clear();
    	
    	int maxNumberOfCriticalConnections = (int) (this.circuit.getConnections().size() * 0.01 * MAX_PERCENTAGE_CRITICAL_CONNECTIONS);
    	
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
	private void ripup(Connection con) {
		for (RouteNode node : con.routeNodes) {
			RouteNodeData data = node.routeNodeData;
			
			data.removeSource(con.source);
			
			// Calculation of present congestion penalty
			node.updatePresentCongestionPenalty(this.pres_fac);
		}
	}
	private void add(Connection con) {
		for (RouteNode node : con.routeNodes) {
			RouteNodeData data = node.routeNodeData;

			data.addSource(con.source);

			// Calculation of present congestion penalty
			node.updatePresentCongestionPenalty(this.pres_fac);
		}
	}
	private boolean route(Connection con) {
		this.connectionsRouted++;
		
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
			} else if (child.type ==  RouteNodeType.IPIN) {
				if(child.children[0].target) {
					this.addNodeToQueue(node, child, con);
				}
				
			//SINK
			} else if (child.type == RouteNodeType.SINK) {
				this.addNodeToQueue(node, child, con);
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
			
			float distance_cost, expected_timing_cost;
			
			if(child.type.equals(RouteNodeType.CHANX)) {
				distance_cost = this.distance_same_dir * COST_PER_DISTANCE_HORIZONTAL + this.distance_ortho_dir * COST_PER_DISTANCE_VERTICAL;
				expected_timing_cost = this.distance_same_dir * DELAY_PER_DISTANCE_HORIZONTAL + this.distance_ortho_dir * DELAY_PER_DISTANCE_VERTICAL;
			} else {
				distance_cost = this.distance_same_dir * COST_PER_DISTANCE_VERTICAL + this.distance_ortho_dir * COST_PER_DISTANCE_HORIZONTAL;
				expected_timing_cost = this.distance_same_dir * DELAY_PER_DISTANCE_VERTICAL + this.distance_ortho_dir * DELAY_PER_DISTANCE_HORIZONTAL;
			}
			
			float expected_wire_cost = distance_cost / (1 + countSourceUses) + IPIN_BASE_COST;
			new_lower_bound_total_path_cost = new_partial_path_cost + this.alphaWLD * (1 - con.getCriticality()) * expected_wire_cost + this.alphaTD * con.getCriticality() * expected_timing_cost;
		
		} else {
			new_lower_bound_total_path_cost = new_partial_path_cost;
		}
		
		this.addNodeToQueue(child, node, new_partial_path_cost, new_lower_bound_total_path_cost);
	}
	
	public void set_expected_distance_to_target(RouteNode node, RouteNode target) {
		/* Returns the number of segments the same type as inode that will be needed *
		 * to reach target_node (not including inode) in each direction (the same    *
		 * direction (horizontal or vertical) as inode and the orthogonal direction).*/
		RouteNodeType type = node.type;
		
		short ylow, yhigh, xlow, xhigh;
		
		int no_need_to_pass_by_clb;
		
		short target_x = target.xlow;
		short target_y = target.ylow;
		
		if (type == RouteNodeType.CHANX) {
			ylow = node.ylow;
			xhigh = node.xhigh;
			xlow = node.xlow;

			/* Count vertical (orthogonal to inode) segs first. */

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

			/* Now count horizontal (same dir. as inode) segs. */

			if (xlow > target_x + no_need_to_pass_by_clb) {
				this.distance_same_dir = xlow - no_need_to_pass_by_clb - target_x;
			} else if (xhigh < target_x - no_need_to_pass_by_clb) {
				this.distance_same_dir = target_x - no_need_to_pass_by_clb - xhigh;
			} else {
				this.distance_same_dir = 0;
			}
			
			return;
			
		} else { /* inode is a CHANY */
			ylow = node.ylow;
			yhigh = node.yhigh;
			xlow = node.xlow;

			/* Count horizontal (orthogonal to inode) segs first. */

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

			/* Now count vertical (same dir. as inode) segs. */

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

	private float getRouteNodeCost(RouteNode node, Connection con, int countSourceUses) {
		RouteNodeData data = node.routeNodeData;
		
		boolean containsSource = countSourceUses != 0;
		
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

		return node.base_cost * data.acc_cost * pres_cost / (1 + (this.usage_multiplier * countSourceUses)) + bias_cost;
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
