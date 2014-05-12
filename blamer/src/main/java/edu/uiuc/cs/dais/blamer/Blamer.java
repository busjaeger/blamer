package edu.uiuc.cs.dais.blamer;

import static com.ibm.wala.core.tests.callGraph.CallGraphTestUtil.REGRESSION_EXCLUSIONS;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.HashSetMultiMap;
import com.ibm.wala.util.collections.MultiMap;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.ImmutableByteArray;
import com.ibm.wala.util.strings.UTF8Convert;

import edu.uiuc.cs.dais.diff.CGNodeDifferencer;
import edu.uiuc.cs.dais.diff.Label;

public class Blamer {

	/**
	 * Sample parameters:
	 * <ol>
	 * <li>
	 * <code></li>
	 * <li>Lorg/eclipse/jetty/util/ArrayQueueTest</li>
	 * <li>testWrap</li>
	 * </ol>
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 6) {
			System.err.println("usage: {comma separated list of directories containing program versions} "
					+ "{test-class} {test-method} {failure-class} {failure-method} {failure-line-number}");
			System.exit(-1);
		}

		final String[] versions = args[0].split(",");
		final String testClassName = args[1];
		final String testMethodName = args[2];
		final String failureClassName = args[3];
		final String failureMethodName = args[4];
		final int failureLineNumber = Integer.parseInt(args[5]);

		System.err.println("Initializing initial version");
		String v0 = versions[0];
		final AnalysisScope scope0 = createAnalysisScope(v0);
		final ClassHierarchy cha0 = makeClassHierachy(scope0).timed();

		// 2. build call graph
		final AnalysisOptions options0 = createAnalysisOptions(testClassName, testMethodName, scope0, cha0);
		final CallGraphBuilder builder0 = createCallGraphBuilder(scope0, cha0, options0);
		final CallGraph callGraph0 = makeCallGraph(builder0, options0).timed();
		System.err.println("Call graph size: " + callGraph0.getNumberOfNodes());

		CallGraphBuilder prevBuilder = builder0;
		CallGraph prevCallGraph = callGraph0;
		AnalysisScope prevScope = scope0;
		Map<CGNode, MultiMap<SSAInstruction, Integer>> changes = new HashMap<>();
		for (int i = 1; i < versions.length; i++) {
			System.err.println("Processing version " + i);
			// 1. create class hierarchy
			final AnalysisScope scope = createAnalysisScope(versions[i]);
			final ClassHierarchy cha = makeClassHierachy(scope).timed();

			// 2. build call graph
			final AnalysisOptions options = createAnalysisOptions(testClassName, testMethodName, scope, cha);
			final CallGraphBuilder builder = createCallGraphBuilder(scope, cha, options);
			final CallGraph callGraph = makeCallGraph(builder, options).timed();

			changes = makeDiff(prevCallGraph, callGraph, i, changes).timed();
			prevScope = scope;
			prevBuilder = builder;
			prevCallGraph = callGraph;
			System.err.println();
		}

		for (CGNode changedNode : changes.keySet())
			for (SSAInstruction changedInst : changes.get(changedNode).keySet())
				System.out.println(changedNode.getMethod().getDeclaringClass().getName() + "."
						+ changedNode.getMethod().getSignature() + "#" + changedInst.toString() + " modified by "
						+ changes.get(changedNode).get(changedInst));

		// 3. compute backward slice
		final PointerAnalysis pointerAnalysis = prevBuilder.getPointerAnalysis();
		final Statement failureStatement = findStatement(prevScope, prevCallGraph, failureClassName, failureMethodName,
				failureLineNumber);
		final Collection<Statement> slice = makeSlice(pointerAnalysis, prevCallGraph, failureStatement).timed();

		for (Statement s : slice) {
			if (s.getNode().getMethod().getDeclaringClass().getClassLoader().getReference()
					.equals(ClassLoaderReference.Application)) {
				MultiMap<SSAInstruction, Integer> nodeChanges = changes.get(s.getNode());
				if (nodeChanges == null)
					continue;

				if (s instanceof StatementWithInstructionIndex) {
					StatementWithInstructionIndex sii = (StatementWithInstructionIndex) s;
					SSAInstruction inst = sii.getInstruction();
					Set<Integer> instChanges = nodeChanges.get(inst);
					if (!instChanges.isEmpty())
						System.out.println("Statement " + s + " changed by " + instChanges);
				}
			}
		}
	}

	private static Map<CGNode, MultiMap<SSAInstruction, Integer>> callGraphDiff(final CallGraph oldGraph,
			final CallGraph newGraph, final int delta, final Map<CGNode, MultiMap<SSAInstruction, Integer>> prevChanges) {

		// only application-level nodes can change (skip JVM classes)
		final Filter<CGNode> loaderFilter = new Filter<CGNode>() {
			@Override
			public boolean accepts(CGNode node) {
				return node.getMethod().getDeclaringClass().getClassLoader().getReference()
						.equals(ClassLoaderReference.Application);
			}
		};

		Map<String, CGNode> oldNodesById = createIdMap(DFS.getReachableNodes(oldGraph, oldGraph.getEntrypointNodes(),
				loaderFilter));
		Map<String, CGNode> newNodesById = createIdMap(DFS.getReachableNodes(newGraph, newGraph.getEntrypointNodes(),
				loaderFilter));
		Map<CGNode, MultiMap<SSAInstruction, Integer>> nodeChanges = new HashMap<>();
		for (Pair<CGNode, CGNode> pair : diff(oldNodesById, newNodesById)) {
			if (pair.fst == null) {
				assert pair.snd != null;
				putAll(getOrCreateSSAChanges(nodeChanges, pair.snd), asList(pair.snd.getIR().getInstructions()), delta);
			} else if (pair.snd != null) {
				for (Pair<Label, Pair<SSAInstruction, SSAInstruction>> match : CGNodeDifferencer.difference(pair.fst,
						pair.snd)) {
					switch (match.fst) {
					case CREATED:
						getOrCreateSSAChanges(nodeChanges, pair.snd).put(match.snd.snd, delta);
						break;
					case DELETED:
						break;
					case MODIFIED:
					case UNCHANGED:
						MultiMap<SSAInstruction, Integer> prevSSAChanges = prevChanges.get(pair.fst);
						if (prevSSAChanges != null) {
							Set<Integer> values = prevSSAChanges.get(match.snd.fst);
							if (!values.isEmpty()) {
								getOrCreateSSAChanges(nodeChanges, pair.snd).putAll(match.snd.snd, values);
							}
						}
						if (match.fst == Label.MODIFIED)
							getOrCreateSSAChanges(nodeChanges, pair.snd).put(match.snd.snd, delta);
						break;
					default:
						break;
					}
				}
			}
		}
		return nodeChanges;
	}

	private static MultiMap<SSAInstruction, Integer> getOrCreateSSAChanges(
			Map<CGNode, MultiMap<SSAInstruction, Integer>> nodeChanges, CGNode node) {
		MultiMap<SSAInstruction, Integer> ssaChanges;
		if ((ssaChanges = nodeChanges.get(node)) == null)
			nodeChanges.put(node, ssaChanges = new HashSetMultiMap<>());
		return ssaChanges;
	}

	static <K, V> Collection<Pair<V, V>> diff(Map<K, V> oldMap, Map<K, V> newMap) {
		Collection<Pair<V, V>> diff = new ArrayList<>(newMap.size());
		for (K key : newMap.keySet())
			diff.add(Pair.make(oldMap.get(key), newMap.get(key)));
		for (K key : oldMap.keySet())
			if (!newMap.containsKey(key))
				diff.add(Pair.make(oldMap.get(key), newMap.get(key)));
		return diff;
	}

	static <K, V> void putAll(MultiMap<K, V> map, Iterable<K> keys, V value) {
		for (K key : keys)
			if (key != null)
				map.put(key, value);
	}

	static <K, V> void diff(Map<K, V> oldMap, Map<K, V> newMap, Differ<K, V> d) {
		Map<K, V> tmp = new HashMap<>(newMap);
		for (Entry<K, V> oldEntry : oldMap.entrySet()) {
			K key = oldEntry.getKey();
			V oldValue = oldEntry.getValue();
			V newValue = tmp.remove(key);
			if (newValue == null)
				d.deleted(key, oldValue);
			else
				d.updated(key, oldValue, newValue);
		}
		for (Entry<K, V> newEntry : tmp.entrySet())
			d.created(newEntry.getKey(), newEntry.getValue());
	}

	static abstract class Differ<K, V> {
		abstract void created(K key, V value);

		abstract void updated(K key, V oldValue, V newValue);

		abstract void deleted(K key, V value);
	}

	private static Map<String, CGNode> createIdMap(Iterable<CGNode> nodes) {
		return idmap(nodes.iterator());
	}

	private static Map<String, CGNode> idmap(Iterator<CGNode> nodes) {
		Map<String, CGNode> nodesById = new HashMap<String, CGNode>();
		while (nodes.hasNext()) {
			CGNode node = nodes.next();
			nodesById.put(getId(node), node);
		}
		return nodesById;
	}

	private static String getId(CGNode node) {
		IMethod m = node.getMethod();
		MethodReference methodRef = m.getReference();
		TypeReference typeRef = methodRef.getDeclaringClass();
		String typeName = typeRef.getName().toString();
		String selector = methodRef.getSelector().toString();
		return typeName + "#" + selector;
	}

	static void printCallGraph(CallGraph callGraph) {
		Set<CGNode> current = new TreeSet<>(new Comparator<CGNode>() {
			@Override
			public int compare(CGNode o1, CGNode o2) {
				return Integer.valueOf(o1.getGraphNodeId()).compareTo(o2.getGraphNodeId());
			}
		});
		current.addAll(callGraph.getEntrypointNodes());
		Set<CGNode> visited = new HashSet<>();
		while (!current.isEmpty()) {
			Iterator<CGNode> it = current.iterator();
			CGNode node = it.next();
			it.remove();
			if (!visited.add(node))
				continue;
			System.out.println(callGraph.getNumber(node) + " " + node.getMethod());
			for (Iterator<CGNode> sit = callGraph.getSuccNodes(node); sit.hasNext();) {
				CGNode successor = sit.next();
				System.out.println("  " + callGraph.getNumber(successor) + ':' + successor.getMethod());
				current.add(successor);
			}
			System.out.println();
		}
	}

	private static CallGraphBuilder createCallGraphBuilder(final AnalysisScope scope, final ClassHierarchy cha,
			final AnalysisOptions options) {
		final AnalysisCache cache = new AnalysisCache();
		return Util.makeZeroCFABuilder(options, cache, cha, scope);
	}

	private static AnalysisScope createAnalysisScope(final String directory) throws WalaException, IOException {
		final File exclusions = new FileProvider().getFile(REGRESSION_EXCLUSIONS);
		File dir = new File(directory);
		if (!dir.isDirectory())
			throw new IOException("Directory " + directory + " is not a directory");
		File[] files = dir.listFiles();
		if (files.length == 0)
			throw new IOException("Directory " + directory + " is emtpy");
		StringBuilder b = new StringBuilder();
		for (File file : files) {
			if (file.isDirectory() || file.getName().endsWith(".jar"))
				b.append(file.getCanonicalPath()).append(File.pathSeparator);
			else
				System.err.println("File " + file + " is neither jar or directory - excluding from classpath");
		}
		String classpath = b.deleteCharAt(b.length() - 1).toString();
		return AnalysisScopeReader.makeJavaBinaryAnalysisScope(classpath, exclusions);
	}

	private static AnalysisOptions createAnalysisOptions(final String className, final String methodName,
			final AnalysisScope scope, final ClassHierarchy cha) {
		final MethodReference method = scope.findMethod(AnalysisScope.APPLICATION, className,
				Atom.findOrCreateUnicodeAtom(methodName), new ImmutableByteArray(UTF8Convert.toUTF8("()V")));
		final Entrypoint entrypoint = new DefaultEntrypoint(method, cha);
		final AnalysisOptions options = new AnalysisOptions(cha.getScope(), singleton(entrypoint));
		options.setReflectionOptions(ReflectionOptions.NONE);
		return options;
	}

	private static Statement findStatement(AnalysisScope scope, CallGraph callGraph, String className,
			String methodName, int lineNumber) {
		TypeName typeName = TypeName.string2TypeName(className);
		TypeReference type = TypeReference.findOrCreate(scope.getApplicationLoader(), typeName);
		IClass cls = callGraph.getClassHierarchy().lookupClass(type);
		Atom mn = Atom.findOrCreateUnicodeAtom(methodName);
		IBytecodeMethod method = null;
		for (IMethod m : cls.getAllMethods())
			if (m.getName().equals(mn) && m instanceof IBytecodeMethod)
				method = (IBytecodeMethod) m;
		if (method == null)
			return null;
		final CGNode node = callGraph.getNode(method, Everywhere.EVERYWHERE);
		SSAInstruction[] is = node.getIR().getInstructions();
		for (int i = 0; i < is.length; i++) {
			if (is[i] == null)
				continue;
			try {
				if (lineNumber == method.getLineNumber(method.getBytecodeIndex(i))) {
					return new NormalStatement(node, i);
				}
			} catch (InvalidClassFileException e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	static TimedCallable<ClassHierarchy> makeClassHierachy(final AnalysisScope scope) {
		return new TimedCallable<ClassHierarchy>(ClassHierarchy.class) {
			@Override
			public ClassHierarchy call() throws Exception {
				return ClassHierarchy.make(scope);
			}
		};
	}

	static TimedCallable<CallGraph> makeCallGraph(final CallGraphBuilder builder, final AnalysisOptions options) {
		return new TimedCallable<CallGraph>(CallGraph.class) {
			@Override
			public CallGraph call() throws Exception {
				return builder.makeCallGraph(options, null);
			}
		};
	}

	static TimedCallable<Map<CGNode, MultiMap<SSAInstruction, Integer>>> makeDiff(final CallGraph prevCallGraph,
			final CallGraph callGraph, final int i, final Map<CGNode, MultiMap<SSAInstruction, Integer>> changes) {
		return new TimedCallable<Map<CGNode, MultiMap<SSAInstruction, Integer>>>("Diff") {
			@Override
			public Map<CGNode, MultiMap<SSAInstruction, Integer>> call() throws Exception {
				return callGraphDiff(prevCallGraph, callGraph, i, changes);
			}
		};
	}

	static TimedCallable<Collection<Statement>> makeSlice(final PointerAnalysis pointerAnalysis,
			final CallGraph callGraph, final Statement statement) {
		return new TimedCallable<Collection<Statement>>("Slice") {
			@Override
			public Collection<Statement> call() throws Exception {
				return Slicer.computeBackwardSlice(statement, callGraph, pointerAnalysis, DataDependenceOptions.NONE,
						ControlDependenceOptions.FULL);
			}
		};
	}

	/**
	 * Utility class time time stuff.
	 * 
	 * @param <T>
	 *            return type
	 */
	static abstract class TimedCallable<T> implements Callable<T> {

		private final String label;

		TimedCallable(String label) {
			this.label = label;
		}

		TimedCallable(Class<T> klass) {
			this(klass.getSimpleName());
		}

		public T timed() throws Exception {
			long before = System.currentTimeMillis();
			try {
				return call();
			} finally {
				System.err.println(label + ": " + (System.currentTimeMillis() - before));
			}
		}
	}
}
