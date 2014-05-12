package edu.uiuc.cs.dais.hammocks;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;

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
		super(null, null, null);
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

	@Override
	public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCfg() {
		return getActual().getCfg();
	}

	@Override
	public IR getIR() {
		return getActual().getIR();
	}

	@Override
	public String toString() {
		return (isHeader() ? "HEADER" : "EXIT") + "(" + super.toString() + ")";
	}
}