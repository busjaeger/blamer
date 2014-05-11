package edu.uiuc.cs.dais.hammocks;

import com.ibm.wala.ssa.ISSABasicBlock;

public class Node {

	private final ISSABasicBlock block;

	public Node(ISSABasicBlock block) {
		this.block = block;
	}

	public ISSABasicBlock getBlock() {
		return block;
	}

	@Override
	public String toString() {
		return Integer.toString(getBlock().getNumber());
	}

}
