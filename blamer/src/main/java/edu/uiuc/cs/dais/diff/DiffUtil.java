package edu.uiuc.cs.dais.diff;

import java.util.Map;

import edu.uiuc.cs.dais.hammocks.HammockNode;
import edu.uiuc.cs.dais.hammocks.Node;

public class DiffUtil {

	// make two entries in the pairMap; one for oldNode and one for newNode which map to the same pair
	static void putToPairMap(Map<String, ? super NodePair> pairMap, NodePair nodePair) {
		pairMap.put(getOldKey(nodePair.getOldNode()), nodePair);
		pairMap.put(getNewKey(nodePair.getNewNode()), nodePair);
	}

	static String getOldKey(Node aNode) {
		return "Old" + getFullName(aNode);
	}

	static String getNewKey(Node aNode) {
		return "New" + getFullName(aNode);
	}

	static String getFullName(Node aNode) {
		while (aNode instanceof HammockNode) {
			aNode = ((HammockNode) aNode).getActual();
		}
		return String.valueOf(aNode.getBlock().getNumber());
	}

}
