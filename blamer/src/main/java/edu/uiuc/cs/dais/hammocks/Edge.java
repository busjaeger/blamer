package edu.uiuc.cs.dais.hammocks;

public class Edge {

	final Node source;
	final Node sink;
	final String label;

	public Edge(Node source, Node sink, String label) {
		super();
		this.source = source;
		this.sink = sink;
		this.label = label;
	}

	public Node getSource() {
		return source;
	}

	public Node getSink() {
		return sink;
	}

	public String getLabel() {
		return label;
	}

	@Override
	public String toString() {
		return getSource() + "-" + getLabel() + ">" + getSink();
	}
}