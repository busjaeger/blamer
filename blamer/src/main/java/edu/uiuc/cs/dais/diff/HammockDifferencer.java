package edu.uiuc.cs.dais.diff;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import edu.uiuc.cs.dais.hammocks.Edge;
import edu.uiuc.cs.dais.hammocks.HammockGraph;
import edu.uiuc.cs.dais.hammocks.HammockNode;
import edu.uiuc.cs.dais.hammocks.Node;

public class HammockDifferencer {

	/*
	 * compare two hammocks that has oldStartNode and newStartNode as their headers. compareHammocks traverses hammocks
	 * and matches nodes in breadth-first order (worklist is modeled as a queue.) starting from start nodes to exit
	 * nodes of those hammocks.
	 */
	static Map<String, NodePair> compareHammocks(Node oldStartNode, Node newStartNode, HammockGraph oldHGraph,
			HammockGraph newHGraph) {

		/*
		 * Design decision: compare hammocks at each depth and return pairs of subhammocks to compare later vs. compare
		 * hammocks at all depths in one call - If we have to look inside hammocks to match them, one depth at a time
		 * may be violated anyway.
		 */
		Map<String, NodePair> localNodePairMap = new HashMap<>(oldHGraph.getNumberOfNodes());

		/*
		 * get the exit node, graph exit node if the start is the graph start node or hammock exit node if the start is
		 * the hammock start node
		 */
		Node oldExitNode = getExitNode(oldStartNode, oldHGraph);
		Node newExitNode = getExitNode(newStartNode, newHGraph);

		// match the exit nodes
		NodePair exitNodePair = new NodePair(oldExitNode, newExitNode);
		DiffUtil.putToPairMap(localNodePairMap, exitNodePair);

		// initialize worklist by putting the pair of start nodes in
		if (oldStartNode instanceof HammockNode) {
			oldStartNode = ((HammockNode) oldStartNode).getActual();
		}
		if (newStartNode instanceof HammockNode) {
			newStartNode = ((HammockNode) newStartNode).getActual();
		}
		NodePair startNodePair = new NodePair(oldStartNode, newStartNode);

		// the worklist holds pspair
		Vector<Pspair> worklist = new Vector<>();
		worklist.add(new Pspair(null, startNodePair));

		// loop through the worklist and match each pair (probably look ahead)
		// cannot use iterator since adding new elements into worklist will cause ConcurrentModificationException
		while (!worklist.isEmpty()) {
			Pspair currentPS = (Pspair) worklist.firstElement();
			worklist.removeElementAt(0);

			NodePair currentNP = currentPS.getSuccPair();
			Node currentOld = currentNP.getOldNode();
			Node currentNew = currentNP.getNewNode();

			// check if already matched
			if ((localNodePairMap.get(DiffUtil.getKey(currentOld)) != null)
					|| (localNodePairMap.get(DiffUtil.getKey(currentNew)) != null)) {
				if ((localNodePairMap.get(DiffUtil.getKey(currentOld)) != null)
						&& (localNodePairMap.get(DiffUtil.getKey(currentNew)) != null)
						&& (!localNodePairMap.get(DiffUtil.getKey(currentOld)).getNewNode().equals(currentNew))) {
					currentPS.getPredPair().setLabel(Label.MODIFIED);
				}
				continue;
			}

			NodePair MatchedNP = null;

			MatchResult mr = isMatched(currentNP, oldHGraph, newHGraph);
			// if the pair matches, make current node pair a matched pair and put all node pairs collected during
			// matching (in case of
			// inner-hammock matching) into this local pair map.
			if (mr.isMatch()) {
				MatchedNP = currentNP;
				int expectedSize = mr.getPairMap().size() + localNodePairMap.size(); // this line is for assertion
				localNodePairMap.putAll(mr.getPairMap());
				assert (localNodePairMap.size() == expectedSize); // this would be assertion
			} else { // the pair does not match, look ahead.
				Collection<String> oldVisitedNodeKeys = new HashSet<>();
				oldVisitedNodeKeys.addAll(localNodePairMap.keySet());
				Collection<String> newVisitedNodeKeys = new HashSet<>();
				newVisitedNodeKeys.addAll(localNodePairMap.keySet());
				// oldDescs and newDescs are the descendants of currentOld and currentNew
				Collection<Node> oldDescs = getDescendants(Collections.singleton(currentOld), oldHGraph, currentNew,
						newHGraph, oldVisitedNodeKeys, oldExitNode);
				Collection<Node> newDescs = getDescendants(Collections.singleton(currentNew), newHGraph, currentOld,
						oldHGraph, newVisitedNodeKeys, newExitNode);

				lookahead: while (!oldDescs.isEmpty() && !newDescs.isEmpty()) {
					// lhNPs - look ahead Node Pairs combines the look-ahead on both old and new sides.
					Collection<NodePair> lhNPs = createNodePairs(currentOld, newDescs);
					lhNPs.addAll(createNodePairs(oldDescs, currentNew));

					// iterate through all pairs and stop if there is one that matches.
					for (NodePair np : lhNPs) {
						mr = isMatched(np, oldHGraph, newHGraph);
						if (mr.isMatch()) {
							MatchedNP = np;
							localNodePairMap.putAll(mr.getPairMap()); // put node pair maps in case of inner-hammock
																		// matching
							break lookahead;
						}
					}

					// Successors of old/new nodes with the same mulitplicity of no. of outgoing edges and - same type
					// if predecessors are hammock start node.
					oldDescs = getDescendants(oldDescs, oldHGraph, currentNew, newHGraph, oldVisitedNodeKeys,
							oldExitNode);
					newDescs = getDescendants(newDescs, newHGraph, currentOld, oldHGraph, newVisitedNodeKeys,
							newExitNode);
				}
			}
			// check if any perfect matched pair is found
			if (MatchedNP != null) {
				// TODO if we decide to have added/deleted node collections, we need to put those nodes
				// into the collections here.
				DiffUtil.putToPairMap(localNodePairMap, MatchedNP);

				// TODO implement a way to match return nodes by their corresponding call nodes
				// The first attempt is to match the return nodes when its corresponding call nodes are matched.
				// This does not work since the return nodes may not be inside the current hammock.
				// The second attempt put the the return pair in another map and when a return node is found,
				// it looks up in that map. This fails since sometimes return node is found when traversing graphs
				// before its call node.

				// update currentNP so outgoing edge matching will start from this pair
				currentNP = MatchedNP;
			} else {
				// if not, keep the current pair as modified and move on
				if ((currentOld instanceof HammockNode) && (((HammockNode) currentOld).isHeader())
						&& (currentNew instanceof HammockNode) && (((HammockNode) currentNew).isHeader())) {
					mr = isMatched(currentNP, oldHGraph, newHGraph);
					int expectedSize = mr.getPairMap().size() + localNodePairMap.size(); // this line is for assertion
					localNodePairMap.putAll(mr.getPairMap());
					assert (localNodePairMap.size() == expectedSize);
				}

				DiffUtil.putToPairMap(localNodePairMap, currentNP);
			}

			// match the branches (if any)
			Map<Edge, Edge> edgeMap = matchOutEdges(currentNP, oldHGraph, newHGraph);

			// put the pair of their successors or the pairs of branch sinks in the worklist and continue the next
			// iteration
			for (Entry<Edge, Edge> entry : edgeMap.entrySet()) {
				Edge oldEdge = entry.getKey();
				Edge newEdge = entry.getValue();
				worklist.add(new Pspair(currentNP, new NodePair(oldEdge.getSink(), newEdge.getSink())));
			}
		}
		return localNodePairMap;
	}

	/*
	 * isMatched returns a match result which is a boolean indicating whether two nodes are matched, and a node pair map
	 * which includes all node pairs in inner-hammocks in case of hammock matching.
	 */
	private static MatchResult isMatched(NodePair np, HammockGraph oldHGraph, HammockGraph newHGraph) {
		Node oldNode = np.getOldNode();
		Node newNode = np.getNewNode();
		// If both nodes are hammock header, call compareHammocks to match
		if ((oldNode instanceof HammockNode) && (((HammockNode) oldNode).isHeader())
				&& (newNode instanceof HammockNode) && (((HammockNode) newNode).isHeader())) {
			Map<String, NodePair> pairMap = compareHammocks(oldNode, newNode, oldHGraph, newHGraph);

			// if the actual headers of the two hammocks match, we consider that the hammocks match.
			Node oldActualHeader = oldHGraph.getEdgeManager().getOutEdges(oldNode).iterator().next().getSink();
			Node newActualHeader = newHGraph.getEdgeManager().getOutEdges(newNode).iterator().next().getSink();

			if ((pairMap.get(oldActualHeader) != null) && (pairMap.get(oldActualHeader).equals(newActualHeader))) {
				return new MatchResult(true, pairMap);
			} else {
				return new MatchResult(false, pairMap);
			} // In case of other kinds of nodes, check their relationship. The pair map will be empty.
		} else {
			return new MatchResult(np.getLabel() == Label.UNCHANGED, new HashMap<String, NodePair>());
		}
	}

	// get NEXT descendants of nodes which have the same type.
	// If ref node is a hammock entry node, this will look only after its hammock exit
	// predGraph must contain all nodes in preds and refGraph must contain refNode
	private static Collection<Node> getDescendants(Collection<Node> preds, HammockGraph predGraph, Node refNode,
			HammockGraph refGraph, Collection<String> visitedNodeKeys, Node exitNode) {
		Collection<Node> descs = new Vector<>();

		// iterate through each predecessor
		for (Node pred : preds) {
			// stop if already visited
			assert (visitedNodeKeys.contains(DiffUtil.getKey(exitNode)));
			if (visitedNodeKeys.contains(DiffUtil.getKey(pred))) {
				continue;
			}

			visitedNodeKeys.add(DiffUtil.getKey(pred));
			// if pred is a node of type other than hammock node, get its descendants normally
			// if pred is a hammock entry, only look at after its hammock exit

			if ((pred instanceof HammockNode) && (((HammockNode) pred).isHeader())) {
				Node hammockExit = ((HammockNode) pred).getOther();
				descs.addAll(getDescendants(Collections.singleton(hammockExit), predGraph, refNode, refGraph,
						visitedNodeKeys, exitNode));
			} else {
				for (Edge outEdge : predGraph.getEdgeManager().getOutEdges(pred)) {
					Node currentDesc = outEdge.getSink();
					if (visitedNodeKeys.contains(DiffUtil.getKey(currentDesc))) {
						continue;
					}
					descs.add(currentDesc);
				}
			}
		}
		return descs;
	}

	// common (shared logic)

	// pspair is a simple class that holds two node pairs: predecessor pair and successor pair.
	// This is used to find which predecessor pair has target_diff when the successor pair
	// is processed and both nodes inside match with different nodes.
	protected static class Pspair {
		private NodePair predPair;
		private NodePair succPair;

		Pspair(NodePair _pred, NodePair _succ) {
			predPair = _pred;
			succPair = _succ;
		}

		NodePair getPredPair() {
			return predPair;
		}

		NodePair getSuccPair() {
			return succPair;
		}
	}

	protected static class MatchResult {
		private boolean isMatch;
		private Map<String, NodePair> pairMap;

		MatchResult(boolean _isMatch, Map<String, NodePair> _map) {
			isMatch = _isMatch;
			pairMap = _map;
		}

		boolean isMatch() {
			return isMatch;
		}

		Map<String, NodePair> getPairMap() {
			return pairMap;
		}
	}

	protected static Collection<NodePair> createNodePairs(Node oldNode, Collection<Node> newNodes) {
		Collection<NodePair> nps = new Vector<>();
		for (Node newNode : newNodes)
			nps.add(new NodePair(oldNode, newNode));
		return nps;
	}

	protected static Collection<NodePair> createNodePairs(Collection<Node> oldNodes, Node newNode) {
		Collection<NodePair> nps = new Vector<>();
		for (Node oldNode : oldNodes)
			nps.add(new NodePair(oldNode, newNode));
		return nps;
	}

	protected static Node getExitNode(Node startNode, HammockGraph hGraph) {
		if ((startNode instanceof HammockNode) && (((HammockNode) startNode).isHeader())) {
			return ((HammockNode) startNode).getOther();
		} else if (startNode.getBlock().isEntryBlock()) {
			return hGraph.getExitNode();
		}
		// if start node is not a hammock header or entry node
		assert (false);
		return null;
	}

	protected static Map<Edge, Edge> matchOutEdges(NodePair np, HammockGraph oldHGraph, HammockGraph newHGraph) {
		Node oldNode = np.getOldNode();
		if ((oldNode instanceof HammockNode) && (((HammockNode) oldNode).isHeader())) {
			oldNode = ((HammockNode) oldNode).getOther();
		}

		Node newNode = np.getNewNode();
		if ((newNode instanceof HammockNode) && (((HammockNode) newNode).isHeader())) {
			newNode = ((HammockNode) newNode).getOther();
		}
		Collection<Edge> oldEdges = oldHGraph.getEdgeManager().getOutEdges(oldNode);
		Collection<Edge> newEdges = newHGraph.getEdgeManager().getOutEdges(newNode);
		Map<Edge, Edge> edgeMap = new HashMap<>(oldEdges.size());

		// if assert's are true, each old edge should match to zero or one new edge and vice versa
		// this might pose a problem at call sites with hundreds of callable methods
		for (Edge oldEdge : oldEdges) {
			for (Edge newEdge : newEdges) {
				if (((oldEdge.getLabel() == null) && (newEdge.getLabel() == null))
						|| ((oldEdge.getLabel() != null) && (oldEdge.getLabel().equals(newEdge.getLabel())))) {
					edgeMap.put(oldEdge, newEdge);
					break;
				}
			}
		}

		// quick fix: when there is one outgoing edge for each version and they aren't matched.
		if (edgeMap.isEmpty() && oldEdges.size() == 1 && newEdges.size() == 1) {
			edgeMap.put(oldEdges.iterator().next(), newEdges.iterator().next());
		}

		return edgeMap;
	}

}
