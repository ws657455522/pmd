/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.rule;

import java.util.Iterator;
import java.util.Set;

import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTAnyTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFormalParameter;
import net.sourceforge.pmd.lang.java.ast.ASTLocalVariableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclarator;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclaratorId;
import net.sourceforge.pmd.lang.java.ast.AccessNode;
import net.sourceforge.pmd.lang.java.ast.JavaNode;
import net.sourceforge.pmd.lang.java.symboltable.ClassNameDeclaration;
import net.sourceforge.pmd.lang.java.symboltable.ClassScope;
import net.sourceforge.pmd.lang.java.symboltable.MethodScope;
import net.sourceforge.pmd.lang.java.symboltable.SourceFileScope;
import net.sourceforge.pmd.lang.rule.ParametricRuleViolation;
import net.sourceforge.pmd.lang.symboltable.Scope;

/**
 * This is a Java RuleViolation. It knows how to try to extract the following
 * extra information from the violation node:
 * <ul>
 * <li>Package name</li>
 * <li>Class name</li>
 * <li>Method name</li>
 * <li>Variable name</li>
 * <li>Suppression indicator</li>
 * </ul>
 */
public class JavaRuleViolation extends ParametricRuleViolation<JavaNode> {

    public JavaRuleViolation(Rule rule, RuleContext ctx, JavaNode node, String message, int beginLine, int endLine) {
        this(rule, ctx, node, message);

        setLines(beginLine, endLine);
    }

    public JavaRuleViolation(Rule rule, RuleContext ctx, JavaNode node, String message) {
        super(rule, ctx, node, message);

        if (node != null) {
            final Scope scope = node.getScope();
            final SourceFileScope sourceFileScope = scope.getEnclosingScope(SourceFileScope.class);

            // Package name is on SourceFileScope
            packageName = sourceFileScope.getPackageName() == null ? "" : sourceFileScope.getPackageName();

            // Class name is built from enclosing ClassScopes
            setClassNameFrom(node);

            // Method name comes from 1st enclosing MethodScope
            if (scope.getEnclosingScope(MethodScope.class) != null) {
                methodName = scope.getEnclosingScope(MethodScope.class).getName();
            }
            // Variable name node specific
            setVariableNameIfExists(node);

            if (!suppressed) {
                suppressed = AnnotationSuppressionUtil.contextSuppresses(node, getRule());
            }
        }
    }


    private void setClassNameFrom(JavaNode node) {
        // TODO this can use regular qualified names (those would also consider anon classes)
        String qualifiedName = null;
        for (ASTAnyTypeDeclaration parent : node.getParentsOfType(ASTAnyTypeDeclaration.class)) {
            String clsName = parent.getScope().getEnclosingScope(ClassScope.class).getClassName();
            if (qualifiedName == null) {
                qualifiedName = clsName;
            } else {
                qualifiedName = clsName + '$' + qualifiedName;
            }
        }

        if (qualifiedName == null) {
            Set<ClassNameDeclaration> classes = node.getScope().getEnclosingScope(SourceFileScope.class)
                    .getClassDeclarations().keySet();
            for (ClassNameDeclaration c : classes) {
                // find the first public class/enum declaration
                if (c.getAccessNodeParent() instanceof AccessNode) {
                    if (((AccessNode) c.getAccessNodeParent()).isPublic()) {
                        qualifiedName = c.getImage();
                        break;
                    }
                }
            }
            
            // Still not found?
            if (qualifiedName == null) {
                for (ClassNameDeclaration c : classes) {
                    // find the first package-private class/enum declaration
                    if (c.getAccessNodeParent() instanceof AccessNode) {
                        if (((AccessNode) c.getAccessNodeParent()).isPackagePrivate()) {
                            qualifiedName = c.getImage();
                            break;
                        }
                    }
                }
            }
        }

        if (qualifiedName != null) {
            className = qualifiedName;
        }
    }

    private String getVariableNames(Iterable<ASTVariableDeclaratorId> iterable) {

        Iterator<ASTVariableDeclaratorId> it = iterable.iterator();
        StringBuilder builder = new StringBuilder();
        builder.append(it.next());

        while (it.hasNext()) {
            builder.append(", ").append(it.next());
        }
        return builder.toString();
    }

    private void setVariableNameIfExists(Node node) {
        if (node instanceof ASTFieldDeclaration) {
            variableName = getVariableNames((ASTFieldDeclaration) node);
        } else if (node instanceof ASTLocalVariableDeclaration) {
            variableName = getVariableNames((ASTLocalVariableDeclaration) node);
        } else if (node instanceof ASTVariableDeclarator) {
            variableName = node.jjtGetChild(0).getImage();
        } else if (node instanceof ASTVariableDeclaratorId) {
            variableName = node.getImage();
        } else if (node instanceof ASTFormalParameter) {
            setVariableNameIfExists(node.getFirstChildOfType(ASTVariableDeclaratorId.class));
        } else {
            variableName = "";
        }
    }
}
