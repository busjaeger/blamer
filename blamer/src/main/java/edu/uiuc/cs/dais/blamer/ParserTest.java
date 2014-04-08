package edu.uiuc.cs.dais.blamer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class ParserTest {

	public static void main(String[] args) throws IOException {
		final String[] classpath = {
				"/Users/bbusjaeger/.m2/repository/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar",
				"/Users/bbusjaeger/.m2/repository/org/eclipse/jetty/toolchain/jetty-test-helper/2.7/jetty-test-helper-2.7.jar",
				"/Users/bbusjaeger/.m2/repository/junit/junit/4.11/junit-4.11.jar",
				"/Users/bbusjaeger/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar",
				"/Users/bbusjaeger/.m2/repository/org/hamcrest/hamcrest-library/1.3/hamcrest-library-1.3.jar",
				"/Users/bbusjaeger/.m2/repository/org/slf4j/slf4j-api/1.6.1/slf4j-api-1.6.1.jar",
				"/Users/bbusjaeger/.m2/repository/org/slf4j/slf4j-jdk14/1.6.1/slf4j-jdk14-1.6.1.jar" };
		final File dir = new File(
				"/Users/bbusjaeger/projects/jetty.project/jetty-util");
		final String[] sourceFilePaths = toAbsolutePaths(findJavaFiles(dir));

		new JavaCore();
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setEnvironment(classpath, new String[0], null, true);
		parser.setResolveBindings(true);
		PrintRequestor req = new PrintRequestor();
		parser.createASTs(sourceFilePaths, null, new String[] { "" }, req, null);

		CompilationUnit ast = req.asts.get("/Users/bbusjaeger/projects/jetty.project/jetty-util/src/main/java/org/eclipse/jetty/util/TreeTrie.java");

		@SuppressWarnings("unchecked")
		List<TypeDeclaration> types = ast.types();

		for (TypeDeclaration type : types) {
			ITypeBinding typeBinding = type.resolveBinding();

			System.out.println(typeBinding.getQualifiedName());
			System.out.println(typeBinding.getSuperclass().getQualifiedName());
		}

	}

	static class PrintRequestor extends FileASTRequestor {
		final Map<String, CompilationUnit> asts = new HashMap<>();

		@Override
		public void acceptAST(String sourceFilePath, CompilationUnit ast) {
			asts.put(sourceFilePath, ast);
		}

		@Override
		public void acceptBinding(String bindingKey, IBinding binding) {
		}
	}

	static List<File> findJavaFiles(File dir) {
		final List<File> files = new ArrayList<>();
		findJavaFiles(dir, files);
		return files;
	}

	static void findJavaFiles(File dir, Collection<File> files) {
		final File[] descendants = dir.listFiles();
		if (descendants == null)
			return;
		for (File descendant : descendants)
			if (descendant.isDirectory())
				findJavaFiles(descendant, files);
			else if (descendant.getName().endsWith(".java"))
				files.add(descendant);
	}

	static String[] toAbsolutePaths(Collection<? extends File> files) {
		List<String> paths = new ArrayList<>(files.size());
		for (File file : files)
			paths.add(file.getAbsolutePath());
		paths.remove("/Users/bbusjaeger/projects/jetty.project/jetty-util/src/main/java/org/eclipse/jetty/util/IteratingNestedCallback.java");
		paths.remove("/Users/bbusjaeger/projects/jetty.project/jetty-util/src/main/java/org/eclipse/jetty/util/IteratingCallback.java");
		return paths.toArray(new String[0]);
	}

}