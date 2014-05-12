package edu.uiuc.cs.dais.diff;

import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;

import edu.uiuc.cs.dais.hammocks.HammockNode;
import edu.uiuc.cs.dais.hammocks.Node;

public class NodePair {

	private Label label;
	private final Node oldNode;
	private final Node newNode;

	public NodePair(Node oldNode, Node newNode) {
		this.oldNode = oldNode;
		this.newNode = newNode;
	}

	public Node getOldNode() {
		return oldNode;
	}

	public Node getNewNode() {
		return newNode;
	}

	public Label getLabel() {
		if (label == null)
			label = computeLabel();
		return label;
	}

	public void setLabel(Label label) {
		this.label = label;
	}

	private Label computeLabel() {
		if (oldNode instanceof HammockNode && newNode instanceof HammockNode)
			return ((HammockNode) oldNode).isHeader() && ((HammockNode) newNode).isHeader() ? Label.UNCHANGED
					: Label.MODIFIED;
		if (oldNode instanceof HammockNode || newNode instanceof HammockNode)
			return Label.MODIFIED;

		ISSABasicBlock oldBlock = oldNode.getBlock();
		ISSABasicBlock newBlock = newNode.getBlock();

		int oldFirst = oldBlock.getFirstInstructionIndex();
		int oldLast = oldBlock.getLastInstructionIndex();
		int oldLength = oldLast - oldFirst + 1;

		int newFirst = newBlock.getFirstInstructionIndex();
		int newLast = newBlock.getLastInstructionIndex();
		int newLength = newLast - newFirst + 1;

		if (newLength != oldLength)
			return Label.MODIFIED;

		IR oldIR = oldNode.getIR();
		IR newIR = newNode.getIR();

		SSAInstruction[] oldInstructions = oldNode.getCfg().getInstructions();
		SSAInstruction[] newInstructions = newNode.getCfg().getInstructions();
		for (int i = oldFirst, j = newFirst; i < oldFirst + oldLength && j < newFirst + newLength; i++, j++) {
			if (oldInstructions[i] == null && newInstructions[j] == null)
				continue;
			if (oldInstructions[i] == null || newInstructions[j] == null)
				return Label.MODIFIED;
			String l1 = oldInstructions[i].toString(oldIR.getSymbolTable());
			String l2 = newInstructions[i].toString(newIR.getSymbolTable());
			if (!l1.equals(l2))
				return Label.MODIFIED;
		}
		return Label.UNCHANGED;
	}

	@Override
	public String toString() {
		return label + "(" + oldNode + "," + newNode + ")";
	}

}
