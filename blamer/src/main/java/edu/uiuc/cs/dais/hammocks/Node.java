package edu.uiuc.cs.dais.hammocks;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;

public class Node {

	private final ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg;
	private final IR ir;
	private final ISSABasicBlock block;

	public Node(ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg, IR ir, ISSABasicBlock block) {
		this.cfg = cfg;
		this.ir = ir;
		this.block = block;
	}

	public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCfg() {
		return cfg;
	}

	public IR getIR() {
		return ir;
	}

	public ISSABasicBlock getBlock() {
		return block;
	}

	@Override
	public String toString() {
		return Integer.toString(getBlock().getNumber());
	}

}
