package edu.uiuc.cs.dais.hammocks;

public class Edge {

	final Node source;
	final Node sink;

	public Edge(Node source, Node sink) {
		super();
		this.source = source;
		this.sink = sink;
	}

	public Node getSource() {
		return source;
	}

	public Node getSink() {
		return sink;
	}

	@Override
	public String toString() {
		return getSource() + ">" + getSink();
	}
}