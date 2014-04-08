package edu.uiuc.cs.dais.cfg;

import static org.eclipse.jdt.core.dom.AST.JLS4;

import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class ControlFlowGraphBuilder {

    final ASTParser parser = ASTParser.newParser(JLS4);

    public void build(String[] sourceFilePaths) {
        final FileASTRequestor requestor = new ASTListener();
//        parser.createASTs(sourceFilePaths, null, null, requestor, null);
    }

    class ASTListener extends FileASTRequestor {
        @Override
        public void acceptAST(String sourceFilePath, CompilationUnit ast) {

            ast.accept(new ASTVisitor() {

                @Override
                public boolean visit(TypeDeclaration node) {
                    System.out.println("Visit " + node.getName());
                    return true;
                }

                @Override
                public void endVisit(TypeDeclaration node) {
                    System.out.println("End " + node.getName());
                }
            });

            System.out.println(sourceFilePath);
        }
    }

}