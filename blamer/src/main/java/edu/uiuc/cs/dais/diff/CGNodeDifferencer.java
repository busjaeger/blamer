package edu.uiuc.cs.dais.diff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.collections.Pair;

import edu.uiuc.cs.dais.hammocks.HammockGraph;

public class CGNodeDifferencer {

	public static Collection<Pair<Label, Pair<SSAInstruction, SSAInstruction>>> difference(CGNode oldNode,
			CGNode newNode) {
		Collection<Pair<Label, Pair<SSAInstruction, SSAInstruction>>> matches = matchDirectly(oldNode, newNode);
		if (matches != null && !oldNode.getMethod().getName().equals("binarySearch"))
			return matches;
		HammockGraph oldHammock = HammockGraph.load(oldNode);
		HammockGraph newHammock = HammockGraph.load(newNode);

		// TODO actual transform match into changed statements (doesn't impact perf eval, so do later)
		Map<String, NodePair> match = HammockDifferencer.compareHammocks(oldHammock.getEntryNode(),
				newHammock.getEntryNode(), oldHammock, newHammock);

		return Collections.emptyList();
	}

	// technically we should consider sub-typing here
	static Collection<Pair<Label, Pair<SSAInstruction, SSAInstruction>>> matchDirectly(CGNode oldNode, CGNode newNode) {
		IR oldIR = oldNode.getIR();
		IR newIR = newNode.getIR();
		SSAInstruction[] oldInstructions = oldIR.getInstructions();
		SSAInstruction[] newInstructions = newIR.getInstructions();

		if (oldInstructions.length != newInstructions.length)
			return null;

		Collection<Pair<Label, Pair<SSAInstruction, SSAInstruction>>> matches = new ArrayList<>();
		for (int i = 0; i < oldInstructions.length; i++) {
			if (oldInstructions[i] == null && newInstructions[i] == null)
				continue;
			if (oldInstructions[i] == null || newInstructions[i] == null)
				return null;
			String l1 = oldInstructions[i].toString(oldNode.getIR().getSymbolTable());
			String l2 = newInstructions[i].toString(newNode.getIR().getSymbolTable());
			if (!l1.equals(l2))
				return null;
			matches.add(Pair.make(Label.UNCHANGED, Pair.make(oldInstructions[i], newInstructions[i])));
		}
		return matches;
	}

}
