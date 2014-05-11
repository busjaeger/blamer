package edu.uiuc.cs.dais.hammocks;

import com.ibm.wala.ssa.ISSABasicBlock;

public class HammockNode extends Node {

	public static HammockNode setUpPair(Node header, Node exit, int depth) {
		HammockNode headerNode = new HammockNode(true, header, depth);
		HammockNode exitNode = new HammockNode(false, exit, depth);

		headerNode.setOther(exitNode);
		exitNode.setOther(headerNode);

		headerNode.setNestingDepth(depth);
		exitNode.setNestingDepth(depth);

		return headerNode;
	}

	private final boolean header;
	private final Node actual;
	private int nestingDepth;
	private HammockNode other;

	public HammockNode(boolean header, Node actual, int nestingDepth) {
		super(null);
		this.actual = actual;
		this.header = header;
		this.nestingDepth = nestingDepth;
	}

	public boolean isHeader() {
		return header;
	}

	public boolean isExit() {
		return !isHeader();
	}

	public Node getActual() {
		return actual;
	}

	public HammockNode getOther() {
		return other;
	}

	public void setOther(HammockNode other) {
		this.other = other;
	}

	public int getNestingDepth() {
		return nestingDepth;
	}

	public void setNestingDepth(int nestingDepth) {
		this.nestingDepth = nestingDepth;
	}

	@Override
	public ISSABasicBlock getBlock() {
		return getActual().getBlock();
	}

}