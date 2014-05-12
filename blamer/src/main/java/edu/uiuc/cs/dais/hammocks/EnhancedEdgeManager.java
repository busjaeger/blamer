package edu.uiuc.cs.dais.hammocks;

import java.util.Set;

import com.ibm.wala.util.graph.EdgeManager;

public interface EnhancedEdgeManager<T, E> extends EdgeManager<T> {
	Set<E> getEdges();

	Set<E> getInEdges(T node);

	Set<E> getOutEdges(T node);

	void addEdge(Edge edge);
}