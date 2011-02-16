/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dp4j.ast;

import com.sun.source.tree.Scope;
import com.sun.tools.javac.util.Name;


import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import javax.lang.model.type.*;
import javax.tools.Diagnostic.Kind.*;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree.*;
import javax.lang.model.element.*;
import com.sun.tools.javac.tree.TreeMaker;
import java.util.*;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.util.List;

/**
 *
 * @author simpatico
 */
public class Resolver {

    final JavacElements elementUtils;
    final Trees trees;
    private final TreeMaker tm;
    private final TypeElement encClass;

    public Resolver(JavacElements elementUtils, final Trees trees, final TreeMaker tm, TypeElement encClass) {
        this.elementUtils = elementUtils;
        this.trees = trees;
        this.tm = tm;
        this.encClass = encClass;
    }

    public Symbol getSymbol(Scope scope, java.util.List<Symbol> typeParams, Name varName, java.util.List<Symbol> args) {
        Symbol t = contains(scope, typeParams, varName, args); //first lookup scope for all public identifiers
        TypeElement cl = scope.getEnclosingClass();
        while (t == null && cl != null) { //lookup hierarchy for inacessible identifiers too
            t = contains(elementUtils.getAllMembers(cl), typeParams, varName, args);
            final TypeMirror superclass = cl.getSuperclass();
            if (superclass != null) {
                cl = (TypeElement) ((Type) superclass).asElement();
            }
        }
        if (t == null) {
            throw new NoSuchElementException(varName.toString());
        }
        return t;
    }

    public Symbol getSymbol(JCFieldAccess fa, Symbol accessor, Scope scope) {
        if (fa.selected instanceof JCIdent) {
            if(fa.name.contentEquals("class")){
                JCExpression acccessor = tm.Select(fa.selected, elementUtils.getName("getClass"));
                JCMethodInvocation mi = tm.Apply(List.<JCExpression>nil(), acccessor, List.<JCExpression>nil());
                return getSymbol(mi, scope).getReturnType().tsym;
            }
            java.util.List<Symbol> enclosedElements = accessor.getEnclosedElements();
            for (Symbol symbol : enclosedElements) {
                if (symbol.getQualifiedName().equals(fa.name)) {
                    return symbol;
                }
            }
        }
        if (fa.selected instanceof JCFieldAccess) {
            Symbol acc = getAccessor((JCFieldAccess) fa.selected, scope);
            return getSymbol((JCFieldAccess) fa.selected, acc, scope);
        }
        throw new NoSuchElementException(fa.toString());
    }

    /**
     * cannot handle just like a fieldAccess? No, need to strip args and params
     * @param mi
     * @param scope
     * @return
     */
    public MethodSymbol getSymbol(final JCMethodInvocation mi, final Scope scope){
        Symbol invTarget = getInvokationTarget(mi, scope);
        Name mName = getName(mi);
        java.util.List<Symbol> args = getArgs(mi.args, scope);
        java.util.List<Symbol> typeParams = getArgs(mi.typeargs, scope);
        return (MethodSymbol) contains(elementUtils.getAllMembers((TypeElement) invTarget), typeParams, mName, args);
    }

    public Symbol getSymbol(JCExpression exp, Scope scope) {
        if (exp instanceof JCIdent) {
            return getSymbol(scope, null, ((JCIdent) exp).name, null);
        }
        if (exp instanceof JCFieldAccess) {
            Symbol acc = getAccessor((JCFieldAccess) exp, scope);
            return getSymbol((JCFieldAccess) exp, acc, scope);
        } else if (exp instanceof JCNewClass) {
            final JCNewClass nc = (JCNewClass) exp;
            System.out.println(exp);
        } else if (exp instanceof JCMethodInvocation) {
            return getSymbol((JCMethodInvocation) exp, scope);
        }
        throw new RuntimeException(exp.toString());
    }

    public Symbol getAccessor(JCFieldAccess fa, Scope scope){
        if (fa.selected instanceof JCIdent) {
            Symbol accessor = getSymbol(scope, null, ((JCIdent) fa.selected).name, null);
            return accessor;
        }
        if (fa.selected instanceof JCFieldAccess) {
            Symbol accessor = getAccessor((JCFieldAccess) fa.selected, scope);
            return getSymbol(fa, accessor, scope);
        }
        if(fa.selected instanceof JCMethodInvocation){
            return getInvokationTarget((JCMethodInvocation)fa.selected, scope);
        }
        throw new NoSuchElementException(fa.toString());
    }

    public Name getName(final JCMethodInvocation mi) {
        return getName(mi.meth);
    }

    public Name getName(final JCExpression exp) {
        if (exp instanceof JCIdent) {
            return elementUtils.getName(exp.toString());
        }
        if (exp instanceof JCFieldAccess) {
            return ((JCFieldAccess) exp).name;
        } else if (exp instanceof JCNewClass) {
            final JCNewClass nc = (JCNewClass) exp;
            System.out.println(exp);
        } else if (exp instanceof JCMethodInvocation) {
            System.out.println(exp);
        }
        throw new NoSuchElementException(exp.toString());
    }

    public Symbol getInvokationTarget(JCMethodInvocation mi, Scope scope) {
        if (mi.meth instanceof JCIdent) { //method name ==> invoked as member of enclosing class
            JCExpression thisExp = tm.This((Type) encClass.asType());
            JCExpression acccessor = tm.Select(thisExp, getName(mi));
            mi = tm.Apply(mi.typeargs, acccessor, mi.args);
            return getInvokationTarget(mi, scope);
        }
        if (mi.meth instanceof JCFieldAccess) {
            return (Symbol) getAccessor((JCFieldAccess) mi.meth, scope);
        } else if (mi.meth instanceof JCNewClass){
            final JCNewClass nc = (JCNewClass) mi.meth;
            final JCExpression clas = nc.clazz;
            Symbol symbol = getSymbol(scope, null, getName(clas), null);
            return symbol;
        } else if (mi.meth instanceof JCMethodInvocation){
            MethodSymbol symbol = getSymbol(mi, scope);
            return symbol.getReturnType().tsym;
        }
        throw new NoSuchElementException(mi.toString());
    }

    private Symbol contains(Scope scope, java.util.List<Symbol> typeParams, Name varName, java.util.List<Symbol> args) {
        Symbol t = null;
        while (t == null && scope != null) {
            Iterable<? extends Element> localElements = scope.getLocalElements();
            t = contains(localElements, typeParams, varName, args);
            scope = scope.getEnclosingScope();
        }
        return t;
    }

    private Symbol contains(Iterable<? extends Element> list, java.util.List<Symbol> typeParams, Name varName, java.util.List<Symbol> args) {
        for (Element e : list) {
            final Name elName;
            if (e instanceof ClassSymbol) {
                ClassSymbol ct = (ClassSymbol) e;
                elName = ct.getQualifiedName();
            } else {
                elName = (Name) e.getSimpleName();
            }
            if (elName.equals(varName) || e.getSimpleName().equals(varName)) {
                if (e.getKind().equals(ElementKind.METHOD)) {
                    MethodSymbol me = (MethodSymbol) e;
                    if (!sameArgs(me.getParameters(), args)) {
                        continue;
                    }
                    if (!sameArgs(me.getTypeParameters(), typeParams)) {
                        continue;
                    }
                }
                return (Symbol) e;
            }
        }
        return null;
    }

    public java.util.List<Symbol> getArgs(List<JCExpression> args, Scope scope) {
        java.util.List<Symbol> syms = new ArrayList<Symbol>();
        for (JCExpression arg : args) {
            Symbol s = getSymbol(arg, scope);
            syms.add(s);
        }
        return syms;
    }

    private boolean sameArgs(List<? extends Symbol> formal, java.util.List<Symbol> actual) {
        int i = 0;
        for (Symbol symbol : actual) {
            Symbol ts = formal.get(i++);
            //subclass stuff
        }
        return true;
    }
}
