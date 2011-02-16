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
import com.sun.tools.javac.code.Symtab;
import javax.lang.model.util.Types;

/**
 *
 * @author simpatico
 */
public class Resolver {

    final JavacElements elementUtils;
    final Trees trees;
    private final TreeMaker tm;
    private final TypeElement encClass;
    protected final Types typeUtils;
    protected final Symtab symTable;

    public Resolver(JavacElements elementUtils, final Trees trees, final TreeMaker tm, TypeElement encClass, final Types typeUtils, final Symtab symTable) {
        this.elementUtils = elementUtils;
        this.trees = trees;
        this.tm = tm;
        this.encClass = encClass;
        this.typeUtils = typeUtils;
        this.symTable = symTable;

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
        return t;
    }

    public Symbol getSymbol(Name varName, Symbol accessor, Scope scope) {
        if (varName.contentEquals("class")) {
            JCIdent id = tm.Ident(accessor);
            JCExpression acccessor = tm.Select(id, elementUtils.getName("getClass"));
            JCMethodInvocation mi = tm.Apply(List.<JCExpression>nil(), acccessor, List.<JCExpression>nil());
            return getSymbol(mi, scope).getReturnType().tsym;
        }
        java.util.List<Symbol> enclosedElements = accessor.getEnclosedElements();
        for (Symbol symbol : enclosedElements) {
            if (symbol.getQualifiedName().equals(varName)) {
                return symbol;
            }
        }
        throw new NoSuchElementException(varName + " in " + accessor);
    }

    /**
     * cannot handle just like a fieldAccess? No, need to strip args and params
     * @param mi
     * @param scope
     * @return
     */
    public MethodSymbol getSymbol(final JCMethodInvocation mi, final Scope scope) {
        Symbol invTarget = getInvokationTarget(mi, scope);
        Name mName = getName(mi);
        java.util.List<Symbol> args = getArgs(mi.args, scope);
        java.util.List<Symbol> typeParams = getArgs(mi.typeargs, scope);
        if (invTarget instanceof VarSymbol) {//this, super,
            invTarget = invTarget.type.tsym;
        }
        MethodSymbol ms = (MethodSymbol) contains(elementUtils.getAllMembers((TypeElement) invTarget), typeParams, mName, args);
        if (ms == null) {
            throw new NoSuchElementException(mi.toString());
        }
        return ms;

    }

    public Symbol getSymbol(JCExpression exp, Scope scope) {
        if (exp instanceof JCIdent) {
            return getSymbol(scope, null, ((JCIdent) exp).name, null);
        } else if (exp instanceof JCFieldAccess) {
            Symbol acc = getAccessor((JCFieldAccess) exp, scope);
            return getSymbol(((JCFieldAccess) exp).name, acc, scope);
        } else if (exp instanceof JCNewClass) {
            final JCNewClass nc = (JCNewClass) exp;
            System.out.println(exp);
        } else if (exp instanceof JCMethodInvocation) {
            return getSymbol((JCMethodInvocation) exp, scope);
        } else if (exp instanceof JCLiteral) {
            return getType((JCLiteral) exp).tsym;
        } else if (exp instanceof JCNewArray) {
            JCNewArray arr = (JCNewArray) exp;
            arr = getTypedArray(arr);
            return arr.type.tsym;
        } else if (exp instanceof JCArrayTypeTree) {
            JCArrayTypeTree arr = (JCArrayTypeTree) exp;
            return getSymbol(arr.elemtype, scope);
        } else if (exp instanceof JCParens){
            return getSymbol(((JCParens)exp).expr, scope);
        } else if (exp instanceof JCTypeCast){
            return getSymbol(((JCTypeCast)exp).expr, scope);
        }
        throw new RuntimeException(exp.toString());
    }

    public Type getType(JCLiteral ifExp) {
        final int typetag = (ifExp).typetag;
        final Object value = (ifExp).value;
        if (value == null) {
            return (Type) typeUtils.getNullType();
        }
        final JCLiteral Literal = tm.Literal(value);
        if (typetag == TypeTags.BOOLEAN) { //bug fix http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6504896
            Literal.setType(symTable.booleanType.constType(value));
        }
        return Literal.type;
    }

    public Type getType(JCExpression exp) {
        if (exp instanceof JCLiteral) {
            return getType((JCLiteral) exp);
        }
        return null;
    }

    public JCNewArray getTypedArray(JCNewArray arr) {
        if (arr.elemtype == null) {
            JCExpression get = arr.elems.get(0); //FIXME: int[] f = {};
            arr.elemtype = tm.Type(getType(get));
            arr.type = arr.elemtype.type;
            assert (arr.type != null);
        }
        assert (arr.type != null);
        return arr;
    }

    public Symbol getAccessor(JCFieldAccess fa, Scope scope) {
        if (fa.selected instanceof JCIdent) {
            Symbol accessor = getSymbol(scope, null, ((JCIdent) fa.selected).name, null);
            return accessor;
        }
        if (fa.selected instanceof JCFieldAccess) {
            Symbol accessor = getSymbol(scope, null, elementUtils.getName(fa.selected.toString()), null);
            if(accessor != null) return accessor;
            accessor = getAccessor((JCFieldAccess) fa.selected, scope);
            return getSymbol(((JCFieldAccess) fa.selected).name, accessor, scope);
        }
        if (fa.selected instanceof JCMethodInvocation) {
            MethodSymbol s = getSymbol((JCMethodInvocation) fa.selected, scope);
            Type returnType = s.getReturnType();
            return returnType.asElement();
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
            Symbol s = (Symbol) getAccessor((JCFieldAccess) mi.meth, scope);
            return s;
        } else if (mi.meth instanceof JCNewClass) {
            final JCNewClass nc = (JCNewClass) mi.meth;
            final JCExpression clas = nc.clazz;
            Symbol symbol = getSymbol(scope, null, getName(clas), null);
            return symbol;
        } else if (mi.meth instanceof JCMethodInvocation) {
            MethodSymbol symbol = getSymbol(mi, scope);
            return symbol.getReturnType().tsym;
        }
        throw new NoSuchElementException(mi.toString());
    }

    public Symbol getBoxedSymbol(Symbol primitive) {
        if (primitive.type.isPrimitive()) {
            primitive = (Symbol) typeUtils.boxedClass(primitive.type);
        }
        return primitive;
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

    public Symbol contains(Iterable<? extends Element> list, java.util.List<Symbol> typeParams, Name varName, java.util.List<Symbol> args) {
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
                    if (!sameArgs(me.getParameters(), args, me.isVarArgs())) {
                        continue;
                    }
                    if (!sameArgs(me.getTypeParameters(), typeParams, me.isVarArgs())) {
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

    private boolean sameArgs(List<? extends Symbol> formal, java.util.List<Symbol> actual, final boolean varArgs) {
        if (formal == null || actual == null) {
            if (formal == null && actual == null) {
                return true;
            } else {
                if (formal != null) {
                    if (formal.size() == 1) {
                        Symbol get = formal.get(0);
                        //FIXME: handle varargs
                    }
                }
                return false;
            }
        }
        if (formal.size() == actual.size() || varArgs && ((formal.size() == actual.size() + 1) || actual.size() > formal.size())) {
            int i = 0;
            for (Symbol symbol : actual) {
                if (actual.size() > i) {
                    Symbol ts = formal.get(i++);

                    //subclass stuff
                }else{

                }
            }
            return true;
        }
        return false;
    }
}