package edu.uiuc.cs.dais.blamer;

import static com.ibm.wala.core.tests.callGraph.CallGraphTestUtil.REGRESSION_EXCLUSIONS;
import static java.util.Collections.singleton;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.slicer.SlicerTest;
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
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.ImmutableByteArray;
import com.ibm.wala.util.strings.UTF8Convert;

public class Blamer {

	/**
	 * Sample jetty parameters:
	 * <ol>
	 * <li>
	 * <code>/Users/bbusjaeger/projects/jetty.project/jetty-util:/Users/bbusjaeger/projects/jetty.project/jetty-util:/Users/bbusjaeger/.m2/repository/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar:/Users/bbusjaeger/.m2/repository/org/eclipse/jetty/toolchain/jetty-test-helper/2.7/jetty-test-helper-2.7.jar:/Users/bbusjaeger/.m2/repository/junit/junit/4.11/junit-4.11.jar:/Users/bbusjaeger/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar:/Users/bbusjaeger/.m2/repository/org/hamcrest/hamcrest-library/1.3/hamcrest-library-1.3.jar:/Users/bbusjaeger/.m2/repository/org/slf4j/slf4j-api/1.6.1/slf4j-api-1.6.1.jar:/Users/bbusjaeger/.m2/repository/org/slf4j/slf4j-jdk14/1.6.1/slf4j-jdk14-1.6.1.jar</code>
	 * </li>
	 * <li>Lorg/eclipse/jetty/util/ArrayQueueTest</li>
	 * <li>testWrap</li>
	 * </ol>
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			System.err.println("usage: {jar-directory} {test-class} {test-method}");
			System.exit(-1);
		}

		final String classpath = args[0];
		final String className = args[1];
		final String methodName = args[2];

		// 1. create class hierarchy
		final AnalysisScope scope = createAnalysisScope(classpath);
		final ClassHierarchy cha = makeClassHierachy(scope).timed();

		// 2. build call graph
		final AnalysisOptions options = createAnalysisOptions(className, methodName, scope, cha);
		final CallGraphBuilder builder = createCallGraphBuilder(scope, cha, options);
		final CallGraph callGraph = makeCallGraph(builder, options).timed();

		// 3. compute forward slice
		final PointerAnalysis pointerAnalysis = builder.getPointerAnalysis();
		final IMethod method = options.getEntrypoints().iterator().next().getMethod();
		final Collection<Statement> slice = makeBackwardSlice(pointerAnalysis, callGraph, method).timed();

		SlicerTest.dumpSlice(slice);
	}

	private static CallGraphBuilder createCallGraphBuilder(final AnalysisScope scope, final ClassHierarchy cha,
			final AnalysisOptions options) {
		final AnalysisCache cache = new AnalysisCache();
		final CallGraphBuilder builder = Util.makeZeroCFABuilder(options, cache, cha, scope);
		return builder;
	}

	private static AnalysisScope createAnalysisScope(final String classpath) throws WalaException, IOException {
		final File exclusions = new FileProvider().getFile(REGRESSION_EXCLUSIONS);
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

	static TimedCallable<Collection<Statement>> makeBackwardSlice(final PointerAnalysis pointerAnalysis,
			final CallGraph callGraph, final IMethod method) {
		return new TimedCallable<Collection<Statement>>("Slice") {
			@Override
			public Collection<Statement> call() throws Exception {
				final CGNode node = callGraph.getNode(method, Everywhere.EVERYWHERE);
				final Statement s = new NormalStatement(node, 10);
				return Slicer.computeBackwardSlice(s, callGraph, pointerAnalysis, DataDependenceOptions.FULL,
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
