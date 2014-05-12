package edu.uiuc.cs.dais.hammocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.ExceptionPrunedCFG;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.collections.HashSetMultiMap;
import com.ibm.wala.util.collections.MultiMap;
import com.ibm.wala.util.graph.AbstractGraph;
import com.ibm.wala.util.graph.NodeManager;
import com.ibm.wala.util.graph.dominators.Dominators;
import com.ibm.wala.util.graph.impl.GraphInverter;
import com.ibm.wala.util.graph.traverse.DFS;

/**
 * This implementation is based on JDiff by Taweesup Apiwattanapong, Alessandro Orso, Mary Jean Harrold. Thanks to
 * Alessandro for sharing the implementation of JDiff so we could port it to WALA!
 * 
 * @author bbusjaeger
 */
public class HammockGraph extends AbstractGraph<Node> {

	private final ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg;
	private final Map<ISSABasicBlock, Node> nodeMap;
	private final Set<Node> nodes;
	private final MultiMap<Node, Edge> outEdges;
	private final MultiMap<Node, Edge> inEdges;
	private final Set<Edge> edges;
	private final Map<HammockNode, Collection<HammockNode>> nestingMap;
	private final int maxNestingDepth;
	private final NodeManager<Node> nodeManager;
	private final EnhancedEdgeManager<Node, Edge> edgeManager;

	HammockGraph(ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg) {
		this(cfg, Collections.<HammockNode, Collection<HammockNode>> emptyMap(), 0);
	}

	HammockGraph(ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg,
			Map<HammockNode, Collection<HammockNode>> nestingMap, int maxNestingDepth) {
		this.cfg = cfg;
		this.outEdges = new HashSetMultiMap<>();
		this.inEdges = new HashSetMultiMap<>();
		this.edges = new HashSet<>();
		this.nodes = new HashSet<>();
		this.nodeMap = new HashMap<>();
		this.nestingMap = nestingMap;
		this.maxNestingDepth = maxNestingDepth;
		this.nodeManager = new NodeManager<Node>() {
			@Override
			public Iterator<Node> iterator() {
				return nodes.iterator();
			}

			@Override
			public int getNumberOfNodes() {
				return nodes.size();
			}

			@Override
			public void addNode(Node n) {
				nodes.add(n);
				nodeMap.put(n.getBlock(), n);
			}

			@Override
			public void removeNode(Node n) throws UnsupportedOperationException {
				nodes.remove(n);
			}

			@Override
			public boolean containsNode(Node n) {
				return nodes.contains(n);
			}
		};
		this.edgeManager = new EnhancedEdgeManager<Node, Edge>() {

			@Override
			public Iterator<Node> getPredNodes(Node n) {
				final Iterator<Edge> it = inEdges.get(n).iterator();
				return new Iterator<Node>() {
					@Override
					public boolean hasNext() {
						return it.hasNext();
					}

					@Override
					public Node next() {
						return it.next().source;
					}

					@Override
					public void remove() {
						it.remove();
					}
				};
			}

			@Override
			public int getPredNodeCount(Node n) {
				return inEdges.get(n).size();
			}

			@Override
			public Iterator<Node> getSuccNodes(Node n) {
				final Iterator<Edge> it = outEdges.get(n).iterator();
				return new Iterator<Node>() {
					@Override
					public boolean hasNext() {
						return it.hasNext();
					}

					@Override
					public Node next() {
						return it.next().sink;
					}

					@Override
					public void remove() {
						it.remove();
					}
				};
			}

			@Override
			public int getSuccNodeCount(Node n) {
				return outEdges.get(n).size();
			}

			@Override
			public Set<Edge> getEdges() {
				return edges;
			}

			@Override
			public Set<Edge> getInEdges(Node node) {
				return inEdges.get(node);
			}

			@Override
			public Set<Edge> getOutEdges(Node node) {
				return outEdges.get(node);
			}

			@Override
			public void addEdge(Edge edge) {
				if (edges.add(edge)) {
					nodeManager.addNode(edge.source);
					nodeManager.addNode(edge.sink);
					outEdges.put(edge.source, edge);
					inEdges.put(edge.sink, edge);
				}
			}

			@Override
			public boolean hasEdge(Node src, Node dst) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void addEdge(Node src, Node dst) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void removeEdge(Node src, Node dst) throws UnsupportedOperationException {
				throw new UnsupportedOperationException();
			}

			@Override
			public void removeAllIncidentEdges(Node node) throws UnsupportedOperationException {
				throw new UnsupportedOperationException();
			}

			@Override
			public void removeIncomingEdges(Node node) throws UnsupportedOperationException {
				throw new UnsupportedOperationException();
			}

			@Override
			public void removeOutgoingEdges(Node node) throws UnsupportedOperationException {
				throw new UnsupportedOperationException();
			}

		};
	}

	public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCfg() {
		return cfg;
	}

	public Map<HammockNode, Collection<HammockNode>> getNestingMap() {
		return nestingMap;
	}

	public int getMaxNestingDepth() {
		return maxNestingDepth;
	}

	public Node getNode(ISSABasicBlock block) {
		return nodeMap.get(block);
	}

	public Node getEntryNode() {
		return nodeMap.get(cfg.entry());
	}

	public Node getExitNode() {
		return nodeMap.get(cfg.exit());
	}

	@Override
	public NodeManager<Node> getNodeManager() {
		return nodeManager;
	}

	@Override
	public EnhancedEdgeManager<Node, Edge> getEdgeManager() {
		return edgeManager;
	}

	public static HammockGraph load(CGNode cgNode) {
		ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = cgNode.getIR().getControlFlowGraph();
		// prune exception edges for now
		cfg = ExceptionPrunedCFG.make(cfg);
		HammockGraph original = initHammock(cfg, cgNode.getIR());

		Set<Edge> addedEdgesSet = new HashSet<>();
		Set<Edge> notCopiedEdgesSet = new HashSet<>();

		MultiMap<Node, Edge> inEdgesMap = new HashSetMultiMap<>();
		MultiMap<Node, Edge> outEdgesMap = new HashSetMultiMap<>();

		Dominators<Node> doms = Dominators.make(original, original.getEntryNode());
		Dominators<Node> pDoms = Dominators.make(GraphInverter.invert(original), original.getExitNode());

		Collection<Node> enclosedDecNodesSet = new HashSet<>();
		Map<HammockNode, Collection<HammockNode>> nestingMap = new HashMap<>();
		AtomicInteger maxNestingDepth = new AtomicInteger(0);

		SortedSet<Node> nodes = DFS.sortByDepthFirstOrder(original, original.getEntryNode());
		for (Node node : nodes) {
			// not a decision node
			if (original.getSuccNodeCount(node) <= 1)
				continue;
			// node already in hammock
			if (enclosedDecNodesSet.contains(node))
				continue;

			Set<Node> visitedDecNodesSet = new HashSet<Node>();
			Set<Node> uncollapsibleDecNodesSet = new HashSet<Node>();
			collapseMinHammock(node, doms, pDoms, addedEdgesSet, notCopiedEdgesSet, inEdgesMap, outEdgesMap,
					visitedDecNodesSet, enclosedDecNodesSet, uncollapsibleDecNodesSet, original, nestingMap,
					maxNestingDepth);
		}

		HammockGraph hammock = new HammockGraph(cfg, nestingMap, maxNestingDepth.get());
		// copy parent graph edges that are not in notCopied set
		for (Edge edge : original.getEdgeManager().getEdges())
			if (!notCopiedEdgesSet.contains(edge))
				hammock.getEdgeManager().addEdge(edge);

		// insert added edges
		for (Edge edge : addedEdgesSet)
			hammock.getEdgeManager().addEdge(edge);

		return hammock;
	}

	private static boolean collapseMinHammock(Node decNode,//
			Dominators<Node> dominators,//
			Dominators<Node> postDominators, //
			Set<Edge> addedEdgesSet, //
			Set<Edge> notCopiedEdgesSet, //
			MultiMap<Node, Edge> inEdgesMap, //
			MultiMap<Node, Edge> outEdgesMap, //
			Collection<Node> visitedDecNodesSet, //
			Collection<Node> enclosedDecNodesSet, //
			Collection<Node> uncollapsibleDecNodesSet, //
			HammockGraph original, Map<HammockNode, Collection<HammockNode>> nestingMap,//
			AtomicInteger maxNestingDepth) {

		Node possibleActualHeader = decNode;
		Node possibleActualExit = getPossibleActualExit(decNode, postDominators, inEdgesMap, original);

		Collection<Node> enclosedByThisDecNodesSet = new HashSet<>();
		enclosedByThisDecNodesSet.add(decNode);

		// whether a hammock is collapsed since this method is called
		boolean isCollapse = false;

		while (possibleActualExit != null) {
			// collection of hammock headers one level below
			Collection<HammockNode> enclosedHeaders = new ArrayList<>();

			// map from each node in hammock to its number of unvisited incoming edges
			Map<Node, Integer> numUnvisitedInEdgesMap = new HashMap<Node, Integer>();

			Stack<Node> workList = new Stack<Node>();
			initializeWorkListAndMap(workList, inEdgesMap, outEdgesMap, possibleActualHeader, numUnvisitedInEdgesMap,
					enclosedHeaders, original);
			while (!workList.isEmpty()) {
				Node curNode = workList.pop();
				if (curNode == possibleActualExit)
					continue;

				// At each node, initialize number of incoming edges left unvisited on the first visit,
				// and decrement it by one on each subsequent visit
				if (numUnvisitedInEdgesMap.containsKey(curNode)) {
					numUnvisitedInEdgesMap.put(curNode, numUnvisitedInEdgesMap.get(curNode) - 1);
				} else {
					// in case we found a decision node during traversal
					if (simGetOutEdges(curNode, outEdgesMap, original).size() > 1) {
						// if this decision node is tangled with previous decision nodes; give up
						if (visitedDecNodesSet.contains(curNode)) {
							return isCollapse;
						}

						// try to collapse it (if it is not tried and failed before)
						visitedDecNodesSet.add(decNode);
						boolean isSubHammockCollapse = !uncollapsibleDecNodesSet.contains(curNode)
								&& collapseMinHammock(curNode, dominators, postDominators, addedEdgesSet,
										notCopiedEdgesSet, inEdgesMap, outEdgesMap, visitedDecNodesSet,
										enclosedDecNodesSet, uncollapsibleDecNodesSet, original, nestingMap,
										maxNestingDepth);

						visitedDecNodesSet.remove(decNode);
						isCollapse = isCollapse || isSubHammockCollapse;
						// if succeed; reset the traversal state and start over again. Because it is possible that the
						// collapsed hammock
						// changes the number of incoming edges of some nodes inside this hammock.
						if (isSubHammockCollapse) {
							initializeWorkListAndMap(workList, inEdgesMap, outEdgesMap, possibleActualHeader,
									numUnvisitedInEdgesMap, enclosedHeaders, original);
							continue;
						} else {
							// else, we know that the hammock of this decsion node is not collapsible.
							// so the current decision node is inside this hammock
							uncollapsibleDecNodesSet.add(curNode);
							enclosedByThisDecNodesSet.add(curNode);
						}
					}

					numUnvisitedInEdgesMap.put(curNode, simGetInEdges(curNode, inEdgesMap, original).size() - 1);
					// then add its successors to worklist (skip the nodes inside the embedded hammocks)
					Collection<Edge> outEdges = null;
					if (curNode instanceof HammockNode && ((HammockNode) curNode).isHeader()) {
						outEdges = simGetOutEdges(((HammockNode) curNode).getOther(), outEdgesMap, original);
						enclosedHeaders.add((HammockNode) curNode);
					} else {
						outEdges = simGetOutEdges(curNode, outEdgesMap, original);
					}

					for (Edge outEdge : outEdges)
						workList.add(outEdge.getSink());
				}
			}

			// count the number of nodes that have incoming edges from nodes outside hammock
			// and also include the entry node of the parent graph if it is inside this hammock
			Set<Node> possibleHeadersSet = new HashSet<>();
			if (numUnvisitedInEdgesMap.containsKey(original.getEntryNode())) {
				possibleHeadersSet.add(original.getEntryNode());
			}

			for (Node node : numUnvisitedInEdgesMap.keySet()) {
				if (numUnvisitedInEdgesMap.get(node) > 0)
					possibleHeadersSet.add(node);
			}

			// if hammock found, i.e., there is only one such node.
			if (possibleHeadersSet.size() == 1) {
				Node actualHeader = possibleHeadersSet.iterator().next();

				HammockNode hmHeader = HammockNode.setUpPair(actualHeader, possibleActualExit, 1);

				redirectEdgesToAndFromHammockNode(addedEdgesSet, notCopiedEdgesSet, inEdgesMap, outEdgesMap,
						numUnvisitedInEdgesMap, hmHeader, original);
				redirectEdgesToAndFromHammockNode(addedEdgesSet, notCopiedEdgesSet, inEdgesMap, outEdgesMap,
						numUnvisitedInEdgesMap, hmHeader.getOther(), original);

				// add enclosed decision nodes to the main set
				enclosedDecNodesSet.addAll(enclosedByThisDecNodesSet);

				// update header map
				nestingMap.put(hmHeader, enclosedHeaders);

				// update nesting depths
				incEnclosedHammockNodesNestingDepth(hmHeader, nestingMap, maxNestingDepth);

				return true;
			} else {
				// else (not found)
				// find the closest common ancester of all possible headers
				// since our dominator tree is built from the parent graph, we must
				// find the actual node if a hammock node is in the set
				Collection<Node> possibleActualHeadersSet = new HashSet<Node>();
				for (Node posHdr : possibleHeadersSet) {
					while (posHdr instanceof HammockNode) {
						posHdr = ((HammockNode) posHdr).getActual();
					}
					possibleActualHeadersSet.add(posHdr);
				}

				Node closestCommonDominator = getClosestCommonAncestor(possibleActualHeadersSet, dominators);
				// if that ancestor is already in the header set, we need to move the possible actual exit to its post
				// dominator
				if (possibleHeadersSet.contains(closestCommonDominator)) {
					possibleActualExit = getPossibleActualExit(possibleActualExit, postDominators, inEdgesMap, original);
				} else // this means this decision node is tangled with others so give up and try other decision nodes
				{
					return isCollapse;
				}
			}
		}

		// the possible actual exit should not be null because a decision node should introduce
		// one hammock. Therefore throw an exception here. This indicates *BUGS* in hammock graph creation
		throw new IllegalStateException("A bug in hammock gragh creation is found!");
	}

	private static Node getPossibleActualExit(Node possibleActualHeader, Dominators<Node> postDominators,
			MultiMap<Node, Edge> inEdgesMap, HammockGraph original) {
		Node possibleActualExit = postDominators.getIdom(possibleActualHeader);
		boolean headerFound = true;
		while (headerFound) {
			headerFound = false;
			for (Edge inEdge : simGetInEdges(possibleActualExit, inEdgesMap, original)) {
				Node source = inEdge.getSource();
				if (source instanceof HammockNode && ((HammockNode) source).isHeader()) {
					possibleActualExit = source;
					headerFound = true;
					break;
				}
			}
		}
		return possibleActualExit;
	}

	private static Node getClosestCommonAncestor(Collection<Node> nodeSet, Dominators<Node> dominators) {
		Iterator<Node> nodeSetItr = nodeSet.iterator();
		if (!nodeSetItr.hasNext()) {
			return null;
		}

		Node ccAncestor = nodeSetItr.next();
		while (nodeSetItr.hasNext()) {
			Node node = nodeSetItr.next();
			Node tmpAncestor = ccAncestor;
			Collection<Node> visited = new Vector<>();
			while ((tmpAncestor != null) || (node != null)) {
				if (tmpAncestor != null) {
					if (visited.contains(tmpAncestor)) {
						ccAncestor = tmpAncestor;
						break;
					} else {
						visited.add(tmpAncestor);
						tmpAncestor = dominators.getIdom(tmpAncestor);
					}
				}

				if (node != null) {
					if (visited.contains(node)) {
						ccAncestor = node;
						break;
					} else {
						visited.add(node);
						node = dominators.getIdom(node);
					}
				}
			}
		}

		return ccAncestor;
	}

	private static void incEnclosedHammockNodesNestingDepth(HammockNode hmHeader,
			Map<HammockNode, Collection<HammockNode>> hmHdrMap, AtomicInteger maxNestingDepth) {
		Stack<HammockNode> worklist = new Stack<>();
		worklist.addAll(hmHdrMap.get(hmHeader));
		while (!worklist.isEmpty()) {
			HammockNode hmNode = worklist.pop();
			int newNestingDepth = hmNode.getNestingDepth() + 1;
			hmNode.setNestingDepth(newNestingDepth);
			hmNode.getOther().setNestingDepth(newNestingDepth);
			if (maxNestingDepth.get() < newNestingDepth) {
				maxNestingDepth.set(newNestingDepth);
			}
			worklist.addAll(hmHdrMap.get(hmNode));
		}
	}

	private static void initializeWorkListAndMap(Stack<Node> workList, MultiMap<Node, Edge> inEdgesMap,
			MultiMap<Node, Edge> outEdgesMap, Node possibleActualHeader, Map<Node, Integer> numUnvisitedInEdgesMap,
			Collection<?> enclosedHeaders, HammockGraph original) {
		numUnvisitedInEdgesMap.clear();
		numUnvisitedInEdgesMap.put(possibleActualHeader,
				new Integer(simGetInEdges(possibleActualHeader, inEdgesMap, original).size()));

		workList.clear();
		for (Edge outEdge : simGetOutEdges(possibleActualHeader, outEdgesMap, original))
			workList.add(outEdge.getSink());
		enclosedHeaders.clear();
	}

	private static Collection<Edge> simGetInEdges(Node n, MultiMap<Node, Edge> inEdgesMap, HammockGraph original) {
		// check the inEdgeMap of this object, if n is the key of the map,
		if (inEdgesMap.containsKey(n)) {
			// use the value
			return inEdgesMap.get(n);
		} else {
			// otherwise use real getInEdges ()
			return original.getEdgeManager().getInEdges(n);
		}
	}

	private static Collection<Edge> simGetOutEdges(Node n, MultiMap<Node, Edge> outEdgesMap, HammockGraph original) {
		// check the outEdgeMap of this object, if n is the key of the map,
		if (outEdgesMap.containsKey(n)) {
			// use the value
			return outEdgesMap.get(n);
		} else {
			// otherwise use real getInEdges ()
			return original.getEdgeManager().getOutEdges(n);
		}
	}

	private static void redirectEdgesToAndFromHammockNode(Set<Edge> addedEdgesSet, Set<Edge> notCopiedEdgesSet,
			MultiMap<Node, Edge> inEdgesMap, MultiMap<Node, Edge> outEdgesMap,
			Map<Node, Integer> numUnvisitedInEdgesMap, HammockNode hmNode, HammockGraph original) {

		// it is a hammock exit if it is not a header
		boolean isHeader = hmNode.isHeader();

		// if hammock node is a header, redirect its incoming edges if that edge is from a node outside the hammock,
		// or if hammock node is an exit, redirect its incoming edgesif that edge is from a node inside the hammock.
		for (Edge inEdge : new HashSet<Edge>(simGetInEdges(hmNode.getActual(), inEdgesMap, original))) {
			Node source = inEdge.getSource();
			if ((source instanceof HammockNode) && (((HammockNode) source).isExit())) {
				source = ((HammockNode) source).getOther();
			}

			if ((isHeader && !numUnvisitedInEdgesMap.containsKey(source))
					|| (!isHeader && numUnvisitedInEdgesMap.containsKey(source))) {
				simAddEdge(new Edge(inEdge.getSource(), hmNode, inEdge.getLabel()), addedEdgesSet, inEdgesMap,
						outEdgesMap, original);
				simRemoveEdge(inEdge, addedEdgesSet, notCopiedEdgesSet, inEdgesMap, outEdgesMap, original);
			}
		}
		// add edge from hammock node to actual node
		simAddEdge(new Edge(hmNode, hmNode.getActual(), null), addedEdgesSet, inEdgesMap, outEdgesMap, original);
	}

	/** simAddEdge (Edge e) and simRemoveEdge (Edge e) */
	private static void simAddEdge(Edge e, Collection<Edge> addedEdgesSet, MultiMap<Node, Edge> inEdgesMap,
			MultiMap<Node, Edge> outEdgesMap, HammockGraph original) {
		// simAddEdge adds e to addedEdgesSet and simRemoveEdge adds to notCopiedEdgesSet
		addedEdgesSet.add(e);

		// check if source is in the outEdgeMap, if not
		Node source = e.getSource();
		if (!outEdgesMap.containsKey(source)) {
			// copy all edges from the original out edges and add/remove this one
			outEdgesMap.putAll(source, original.getEdgeManager().getOutEdges(source));
			outEdgesMap.put(source, e);
		} else {
			// otherwise, add it to the existing set
			outEdgesMap.put(source, e);
		}

		// check if sink is in the inEdgeMap, if not
		Node sink = e.getSink();
		if (!inEdgesMap.containsKey(sink)) {
			// copy all edges from the original in edges and add/remove this one
			inEdgesMap.putAll(sink, original.getEdgeManager().getInEdges(sink));
			inEdgesMap.put(sink, e);
		} else {
			inEdgesMap.put(sink, e);
		}
	}

	/** simAddEdge (Edge e) and simRemoveEdge (Edge e) */
	private static void simRemoveEdge(Edge e, Collection<Edge> addedEdgesSet, Collection<Edge> notCopiedEdgesSet,
			MultiMap<Node, Edge> inEdgesMap, MultiMap<Node, Edge> outEdgesMap, HammockGraph original) {
		// simAddEdge adds e to addedEdgesSet and simRemoveEdge adds to notCopiedEdgesSet
		if (addedEdgesSet.contains(e)) {
			addedEdgesSet.remove(e);
		} else {
			notCopiedEdgesSet.add(e);
		}

		// check if source is in the outEdgeMap, if not
		Node source = e.getSource();
		if (!outEdgesMap.containsKey(source)) {
			// copy all edges from the original out edges and add/remove this one
			outEdgesMap.putAll(source, original.getEdgeManager().getOutEdges(source));
			outEdgesMap.get(source).remove(e);
		} else {
			// otherwise, add it to the existing set
			outEdgesMap.get(source).remove(e);
		}

		// check if sink is in the inEdgeMap, if not
		Node sink = e.getSink();
		if (!inEdgesMap.containsKey(sink)) {
			// copy all edges from the original in edges and add/remove this one
			inEdgesMap.putAll(sink, original.getEdgeManager().getInEdges(sink));
			inEdgesMap.get(sink).remove(e);
		} else {
			// otherwise, add it to the existing set
			inEdgesMap.get(sink).remove(e);
		}
	}

	private static HammockGraph initHammock(ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg, IR ir) {
		// compute initial unmodified hammock graph (mostly to make this similar to JDiff)
		Map<ISSABasicBlock, Node> nodes = new HashMap<>(cfg.getNumberOfNodes());
		HammockGraph h = new HammockGraph(cfg);
		for (Iterator<ISSABasicBlock> it = cfg.iterator(); it.hasNext();) {
			ISSABasicBlock block = it.next();
			Node node = getOrCreate(nodes, cfg, ir, block);
			h.addNode(node);

			String label = cfg.getSuccNodeCount(block) > 1 ? "T" : null;
			for (Iterator<ISSABasicBlock> succs = cfg.getSuccNodes(block); succs.hasNext();) {
				Node succ = getOrCreate(nodes, cfg, ir, succs.next());
				h.getEdgeManager().addEdge(new Edge(node, succ, label));
				if (label != null)
					label = "F";
			}
		}
		return h;
	}

	private static Node getOrCreate(Map<ISSABasicBlock, Node> nodes,
			ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg, IR ir, ISSABasicBlock block) {
		Node node = nodes.get(block);
		if (node == null)
			nodes.put(block, node = new Node(cfg, ir, block));
		return node;
	}

}
