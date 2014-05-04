package edu.uiuc.cs.dais.blamer;

import static com.ibm.wala.core.tests.callGraph.CallGraphTestUtil.REGRESSION_EXCLUSIONS;
import static java.util.Collections.singleton;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetMultiMap;
import com.ibm.wala.util.collections.MultiMap;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.ImmutableByteArray;
import com.ibm.wala.util.strings.UTF8Convert;

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
			System.err.println("usage: {comma separated list program versions (= directories containing jars)} "
					+ "{test-class} {test-method} {failure-class} {failure-method} {failure-line-number}");
			System.exit(-1);
		}

		final String[] versions = args[0].split(",");
		final String testClassName = args[1];
		final String testMethodName = args[2];
		final String failureClassName = args[3];
		final String failureMethodName = args[4];
		final int failureLineNumber = Integer.parseInt(args[5]);

		String v0 = versions[0];
		final AnalysisScope scope0 = createAnalysisScope(v0);
		final ClassHierarchy cha0 = makeClassHierachy(scope0).timed();

		// 2. build call graph
		final AnalysisOptions options0 = createAnalysisOptions(testClassName, testMethodName, scope0, cha0);
		final CallGraphBuilder builder0 = createCallGraphBuilder(scope0, cha0, options0);
		final CallGraph callGraph0 = makeCallGraph(builder0, options0).timed();

		CallGraphBuilder prevBuilder = builder0;
		CallGraph prevCallGraph = callGraph0;
		AnalysisScope prevScope = scope0;
		MultiMap<String, Integer> changedNodes = new HashSetMultiMap<String, Integer>();
		for (int i = 1; i < versions.length; i++) {
			// 1. create class hierarchy
			final AnalysisScope scope = createAnalysisScope(versions[i]);
			final ClassHierarchy cha = makeClassHierachy(scope).timed();

			// 2. build call graph
			final AnalysisOptions options = createAnalysisOptions(testClassName, testMethodName, scope, cha);
			final CallGraphBuilder builder = createCallGraphBuilder(scope, cha, options);
			final CallGraph callGraph = makeCallGraph(builder, options).timed();

			addChangedNodes(prevCallGraph, callGraph, i, changedNodes);
		}

//		printCallGraph(prevCallGraph);

		// 3. compute backward slice
		final PointerAnalysis pointerAnalysis = prevBuilder.getPointerAnalysis();
		final Statement failureStatement = findStatement(prevScope, prevCallGraph, failureClassName, failureMethodName,
				failureLineNumber);
		final Collection<Statement> slice = makeSlice(pointerAnalysis, prevCallGraph, failureStatement).timed();

//		for (Statement s : slice)
//			if (s.getNode().getMethod().getDeclaringClass().getClassLoader().getReference()
//					.equals(ClassLoaderReference.Application))
//				System.out.println(s);
	}

	private static void addChangedNodes(CallGraph oldGraph, CallGraph newGraph, int i,
			MultiMap<String, Integer> changedNodes) {
		Map<String, CGNode> oldNodesById = new HashMap<String, CGNode>(oldGraph.getNumberOfNodes());
		for (CGNode node : DFS.getReachableNodes(oldGraph, oldGraph.getEntrypointNodes()))
			oldNodesById.put(getId(node), node);

		for (CGNode newNode : DFS.getReachableNodes(newGraph, newGraph.getEntrypointNodes())) {
			String id = getId(newNode);
			CGNode oldNode = oldNodesById.get(id);
			if (oldNode == null) {
				System.out.println("Changelist " + i + " created node: " + newNode);
				changedNodes.put(id, i);
			} else {

			}
		}
	}

	private static String getId(CGNode node) {
		IMethod m = node.getMethod();
		MethodReference methodRef = m.getReference();
		TypeReference typeRef = methodRef.getDeclaringClass();
		String typeName = typeRef.getName().toString();
		String selector = methodRef.getSelector().toString();
		return typeName + "#" + selector;
	}

	private static void printCallGraph(CallGraph callGraph) {
		// System.out.println(PDFCallGraph.pruneForAppLoader(callGraph));
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
