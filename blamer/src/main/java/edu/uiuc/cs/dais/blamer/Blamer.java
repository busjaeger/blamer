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
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
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
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.ImmutableByteArray;
import com.ibm.wala.util.strings.UTF8Convert;

public class Blamer {

    public static void main(String[] args) throws Exception {
        final String jars = "/home/bbusjaeger/jars";
        final String klass = "Lcommon/util/collection/AdjacentDuplicateRemovingIteratorTest";
        final String methodName = "test";

        // 1. try to build graph from source code

        final String[] path = { jars };
        final String classpath = PDFCallGraph.findJarFiles(path);
        final File exclusions = new FileProvider().getFile(REGRESSION_EXCLUSIONS);

        final AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(classpath, exclusions);

        long before_cha = System.currentTimeMillis();
        final ClassHierarchy cha = ClassHierarchy.make(scope);
        System.err.println("Class Hierarchy: " + (System.currentTimeMillis() - before_cha));

        final MethodReference method = scope.findMethod(AnalysisScope.APPLICATION, klass,
                Atom.findOrCreateUnicodeAtom(methodName), new ImmutableByteArray(UTF8Convert.toUTF8("()V")));

        final Entrypoint entrypoint = new DefaultEntrypoint(method, cha);
        final AnalysisOptions options = new AnalysisOptions(scope, singleton(entrypoint));
        options.setReflectionOptions(ReflectionOptions.NONE);

        final AnalysisCache cache = new AnalysisCache();
        final CallGraphBuilder builder = Util.makeZeroCFABuilder(options, cache, cha, scope);

        long before_cg = System.currentTimeMillis();
        final CallGraph callGraph = builder.makeCallGraph(options, null);
        final PointerAnalysis pointerAnalysis = builder.getPointerAnalysis();
        System.err.println("Call Graph: " + (System.currentTimeMillis() - before_cg));

        long before_slice = System.currentTimeMillis();
        final IMethod m = cha.resolveMethod(method);
        CGNode node = callGraph.getNode(m, Everywhere.EVERYWHERE);
        final Statement s = new NormalStatement(node, 8);
        final Collection<Statement> slice = Slicer.computeBackwardSlice(s, callGraph, pointerAnalysis,
                DataDependenceOptions.FULL, ControlDependenceOptions.FULL);
        System.err.println("Slice: " + (System.currentTimeMillis() - before_slice));

        System.err.println("Call graph: " + callGraph);

        SlicerTest.dumpSlice(slice);
    }
}
