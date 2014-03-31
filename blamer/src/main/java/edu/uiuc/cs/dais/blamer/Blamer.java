package edu.uiuc.cs.dais.blamer;

import static com.ibm.wala.core.tests.callGraph.CallGraphTestUtil.REGRESSION_EXCLUSIONS;
import static java.util.Collections.singleton;

import java.io.File;
import java.util.Collection;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.slicer.SlicerTest;
import com.ibm.wala.examples.drivers.PDFCallGraph;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
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
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.ImmutableByteArray;
import com.ibm.wala.util.strings.UTF8Convert;

public class Blamer {

	public static void main(String[] args) throws Exception {
		// 1. try to build graph from source code
		long before = System.currentTimeMillis();

		final String[] path = { "/Users/bbusjaeger/projects/jetty.project/jetty-util/target/dependency" };
		final String classpath = PDFCallGraph.findJarFiles(path);
		final File exclusions = new FileProvider()
				.getFile(REGRESSION_EXCLUSIONS);

		final AnalysisScope scope = AnalysisScopeReader
				.makeJavaBinaryAnalysisScope(classpath, exclusions);

		final ClassHierarchy cha = ClassHierarchy.make(scope);

		final MethodReference method = scope.findMethod(
				AnalysisScope.APPLICATION,
				"Lorg/eclipse/jetty/util/ArrayQueueTest",
				Atom.findOrCreateUnicodeAtom("testWrap"),
				new ImmutableByteArray(UTF8Convert.toUTF8("()V")));

		final Entrypoint entrypoint = new DefaultEntrypoint(method, cha);
		final AnalysisOptions options = new AnalysisOptions(scope,
				singleton(entrypoint));

		final AnalysisCache cache = new AnalysisCache();
		final CallGraphBuilder builder = Util.makeZeroCFABuilder(options,
				cache, cha, scope);
		final CallGraph callGraph = builder.makeCallGraph(options, null);
		final PointerAnalysis pointerAnalysis = builder.getPointerAnalysis();

		final IMethod m = cha.resolveMethod(method);
		CGNode node = callGraph.getNode(m, Everywhere.EVERYWHERE);

		final Statement s = new NormalStatement(node, 8);
		final Collection<Statement> slice = Slicer.computeBackwardSlice(s,
				callGraph, pointerAnalysis);
		SlicerTest.dumpSlice(slice);

		System.out.println(System.currentTimeMillis() - before);
	}
}
