/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dp4j.processors;

import com.sun.source.util.Trees;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree.*;
import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import java.util.HashSet;
import com.dp4j.templateMethod;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import javax.lang.model.util.Types;

/**
 *
 * @author simpatico
 */
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public abstract class DProcessor extends AbstractProcessor {

    protected Trees trees;
    protected TreeMaker tm;
    protected static JavacElements elementUtils;
    protected Messager msgr;
    protected Types typeUtils;

    public JCVariableDecl getVarDecl(JCModifiers mods, final String varName, final String idName, final String methodName, final String... params) {
        JCMethodInvocation valueSetter = (methodName != null) ? getMethodInvoc(methodName, params) : null;
        return tm.VarDef(mods, elementUtils.getName(varName), getId(idName), valueSetter);
    }

    public JCVariableDecl getVarDecl(final String varName, final String idName, final String methodName, final String... params) {
        return getVarDecl(tm.Modifiers(Flags.FINAL), varName, idName, methodName, params);
    }

    public JCVariableDecl getVarDecl(final String varName, final String idName, final String methodName, final Name stingParam, final JCExpression... params) {
        final JCMethodInvocation valueSetter = (methodName != null) ? getMethodInvoc(methodName, stingParam, params) : null;
        return tm.VarDef(tm.Modifiers(Flags.FINAL), elementUtils.getName(varName), getId(idName), valueSetter);
    }

    public JCVariableDecl getArrayDecl(JCModifiers mods, final String varName, final String idName, final JCNewArray array) {
        return tm.VarDef(mods, elementUtils.getName(varName), getId(idName), array);
    }

    public JCVariableDecl getArrayDecl(final String varName, final String idName, final JCNewArray array) {
        return tm.VarDef(tm.Modifiers(Flags.FINAL), elementUtils.getName(varName), getId(idName), array);
    }

    private List<JCExpression> getParamsList(final Boolean... params) {
        final ListBuffer<JCExpression> lb = ListBuffer.lb();
        for (boolean param : params) {

            int v = ((Boolean) param) ? 1 : 0;
            lb.append(tm.Literal(TypeTags.BOOLEAN, v));
        }
        final List<JCExpression> paramsList = lb.toList();
        return paramsList;
    }

    private List<JCExpression> getParamsList(final Name... params) {
        final ListBuffer<JCExpression> lb = ListBuffer.lb();
        for (Name param : params) {
            lb.append(tm.Ident(param));
        }
        final List<JCExpression> paramsList = lb.toList();
        return paramsList;
    }

    private List<JCExpression> getParamsList(final JCExpression... params) {
        final ListBuffer<JCExpression> lb = ListBuffer.lb();
        for (JCExpression param : params) {
            if (param instanceof JCNewClass || param instanceof JCNewArray) {
                lb.append(tm.Exec(param).getExpression());
            } else if (param instanceof JCIdent) {
                JCIdent id = (JCIdent) param;
                lb.append(tm.Ident(id.name));
            } else {
                throw new RuntimeException();
            }
        }
        final List<JCExpression> paramsList = lb.toList();
        return paramsList;
    }

    public JCMethodInvocation getMethodInvoc(final String methodName, final JCExpression... exps) {
        final JCExpression methodN = getIdAfterImporting(methodName);
        final List<JCExpression> paramsList = getParamsList(exps);
        final JCMethodInvocation mInvoc = tm.Apply(List.<JCExpression>nil(), methodN, paramsList);
        return mInvoc;
    }

    public JCMethodInvocation getMethodInvoc(final String methodName, final Name stringParam, final JCExpression... exps) {
        final JCExpression methodN = getIdAfterImporting(methodName);
        final JCExpression lit = tm.Ident(stringParam);
        final List<JCExpression> paramsList = injectBefore(exps[0], getParamsList(exps), lit);
        final JCMethodInvocation mInvoc = tm.Apply(List.<JCExpression>nil(), methodN, paramsList);
        return mInvoc;
    }

    public JCMethodInvocation getMethodInvoc(final String methodName, final Boolean... boolParams) {
        final JCExpression methodN = getIdAfterImporting(methodName);
        final List<JCExpression> paramsList = getParamsList(boolParams);
        final JCMethodInvocation mInvoc = tm.Apply(List.<JCExpression>nil(), methodN, paramsList);
        return mInvoc;
    }

    public JCMethodInvocation getMethodInvoc(final String methodName, final Name... objs) {
        final JCExpression methodN = getIdAfterImporting(methodName);
        final List<JCExpression> paramsList = getParamsList(objs);
        final JCMethodInvocation mInvoc = tm.Apply(List.<JCExpression>nil(), methodN, paramsList);
        return mInvoc;
    }

    public JCMethodInvocation getMethodInvoc(final String methodName, final String... stringParams) {
        JCExpression methodN = getIdAfterImporting(methodName);
        final ListBuffer<JCExpression> lb = ListBuffer.lb();
        for (String param : stringParams) {
            lb.append(tm.Literal(param));
        }
        final List<JCExpression> paramsList = lb.toList();
        final JCMethodInvocation mInvoc = tm.Apply(List.<JCExpression>nil(), methodN, paramsList);
        return mInvoc;
    }

    public JCExpression getId(final String typeName) {
        return getIdAfterImporting(typeName);
    }

    public JCExpression getId(final Name typeName) {
        return getId(typeName.toString());
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        final Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        trees = Trees.instance(processingEnv);
        elementUtils = JavacElements.instance(context);
        msgr = processingEnv.getMessager();
        tm = TreeMaker.instance(((JavacProcessingEnvironment) processingEnv).getContext());
        typeUtils = processingEnv.getTypeUtils();
    }

    protected Set<? extends Element> getElementsAnnotated(final RoundEnvironment roundEnv, Set<? extends TypeElement> annotations) {
        final Set<Element> annotatatedElements = new HashSet<Element>();
        for (TypeElement ann : annotations) {
            final Set<? extends Element> annElements = roundEnv.getElementsAnnotatedWith(ann);
            annotatatedElements.addAll(annElements);
        }
        return annotatatedElements;
    }

    @templateMethod
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        for (final Element e : getElementsAnnotated(roundEnv, annotations)) {
            processElement(e);
        }
        return onlyHandler(annotations);
    }

    protected boolean onlyHandler(Set<? extends TypeElement> annotations){
        return true;
    }

    protected JCExpression getIdentAfterImporting(final Class clazz) {
        return getIdAfterImporting(clazz.getCanonicalName());
    }

    protected JCExpression getIdAfterImporting(final String methodName) {
        final String[] names = methodName.split("\\.");
        JCExpression e = tm.Ident(elementUtils.getName(names[0]));

        for (int i = 1; i < names.length; i++) {
            String name = names[i];
            e = tm.Select(e, elementUtils.getName(name));
        }
        return e;
    }

    public List<JCStatement> emptyList() {
        final ListBuffer<JCStatement> lb = ListBuffer.lb();
        return lb.toList();
    }

    protected static <T> com.sun.tools.javac.util.List<T> injectBefore(T stmt, final com.sun.tools.javac.util.List<T> stats, T... newStmts) {
        final ListBuffer<T> lb = ListBuffer.lb();
        int i = 0;
        final int index = stats.indexOf(stmt);
        for (; i < index; i++) {
            lb.append(stats.get(i));
        }
        for (T newStmt : newStmts) {
            if (newStmt != null) {
                lb.append(newStmt);
            }
        }
        for (i = index; i < stats.size(); i++) {
            lb.append(stats.get(i));
        }
        return lb.toList();
    }

    protected abstract void processElement(final Element e);
}