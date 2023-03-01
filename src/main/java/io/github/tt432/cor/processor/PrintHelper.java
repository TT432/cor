package io.github.tt432.cor.processor;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;

import java.util.ArrayList;

/**
 * @author DustW
 */
public class PrintHelper {
    static class Push {
        int num;
        ArrayList<String> infos = new ArrayList<>();

        String str() {
            return "-" + "-".repeat(num);
        }

        void push(String v) {
            num++;
            System.out.println(str() + v + "  Push");
            infos.add(v);
        }

        void printObj(JCTree jt, Push push, String info) {
            push(info);
            System.out.println(jt);

            if (jt != null) {
                System.out.println(jt.getClass());
                printJTree(jt, push);
            }

            pop();
        }

        void print(String info, Object o) {
            if (o != null)
                System.out.println(info + " : " + o + "     Class: " + o.getClass());

            if (o instanceof Iterable<?> it)
                it.forEach(o1 -> print("ListElement", o1));
        }

        void pop() {
            System.out.println(str() + infos.remove(infos.size() - 1) + "  Pop");
            num--;
        }
    }

    static void printSymbol(Symbol symbol, Push push) {
        push.print("symbol", symbol);
        push.print("type ", symbol.type );
        push.print("kind ", symbol.kind );
        push.print("owner", symbol.owner);
    }

    static void printIdent(JCTree.JCIdent jci, Push push) {
        push.print("name", jci.name);
        push.print("sym", jci.sym);

        if (jci.sym != null) {
            printSymbol(jci.sym, push);
        }
    }

    public static void printVar(JCTree.JCVariableDecl var, Push push) {
        push.push("Var");

        push.print("name", var.name);
        push.print("nameexpr", var.nameexpr);
        push.print("mods", var.mods);
        if (var.mods != null)
            System.out.println("ModsFlag " + var.mods.flags);
        push.print("declaredUsingVar", var.declaredUsingVar());
        push.print("startPos", var.startPos);
        push.print("kind", var.getKind());

        if (var.sym != null) {
            push.push("Sym");
            printSymbol(var.sym, push);
            push.pop();
        }

        if (var.vartype != null) {
            push.push("VarType");
            push.print("vartype", var.vartype);

            if (var.vartype instanceof JCTree.JCTypeApply jta) {
                push.push("TypeApply");
                push.print("clazz", jta.clazz);

                if (jta.clazz instanceof JCTree.JCIdent ji) {
                    printIdent(ji, push);
                }

                push.pop();
            } else if (var.vartype instanceof JCTree.JCIdent jci) {
                printIdent(jci, push);
            }

            push.pop();
        }

        if (var.init != null) {
            push.push("Init");
            push.print("init", var.init);

            if (var.init instanceof JCTree.JCMethodInvocation jmi) {
                push.push("MethodInvocation");
                printMethodInvocation(jmi, push);
                push.pop();
            }
            push.pop();
        }

        push.pop();
    }

    static void printMethodInvocation(JCTree.JCMethodInvocation jmi, Push push) {
        push.print("jmi.typeargs", jmi.typeargs);
        push.print("jmi.meth", jmi.meth);

        if (jmi.meth instanceof JCTree.JCFieldAccess jfa) {
            push.push("Meth");
            printFieldAccess(jfa, push);
            push.pop();
        }

        push.print("jmi.args", jmi.args);

        for (JCTree.JCExpression arg : jmi.args) {
            push.push("Args");
            System.out.println(arg);
            System.out.println(arg.getClass());

            if (arg instanceof JCTree.JCLambda lambda) {
                push.push("LambdaArg");
                printLambda(lambda, push);
                push.pop();
            } else if (arg instanceof JCTree.JCFieldAccess jfa) {
                push.push("FieldAccessArg");
                printFieldAccess(jfa, push);
                push.pop();
            } else if (arg instanceof JCTree.JCMemberReference jmr) {
                push.push("MemberReferenceArg");
                push.print("jmr.mode", jmr.mode);
                push.print("jmr.name", jmr.name);
                push.print("jmr.expr", jmr.expr);
                push.print("jmr.typeargs", jmr.typeargs);
                push.pop();
            } else if (arg instanceof JCTree.JCMethodInvocation jmi1) {
                push.push("MethodInvocationArg");
                printMethodInvocation(jmi1, push);
                push.pop();
            } else if (arg instanceof JCTree.JCIdent ji) {
                push.push("IdentArg");
                printIdent(ji, push);
                push.pop();
            }

            push.pop();
        }

        System.out.println(jmi.varargsElement);
    }

    public static void printLambda(JCTree.JCLambda lambda, Push push) {
        push.print("params", lambda.params);

        for (JCTree.JCVariableDecl param : lambda.params) {
            printVar(param, push);
        }

        push.print("body",lambda.body);

        if (lambda.body != null) {
            push.push("Body");

            if (lambda.body instanceof JCTree.JCMethodInvocation mi) {
                push.push("Body");
                printMethodInvocation(mi, push);
                push.pop();
            }

            push.pop();
        }

        push.print("canCompleteNormally", lambda.canCompleteNormally);
        push.print("paramKind", lambda.paramKind);
    }

    static void printFieldAccess(JCTree.JCFieldAccess jfa, Push push) {
        push.print("selected", jfa.selected);

        if (jfa.selected instanceof JCTree.JCMethodInvocation jmi) {
            push.push("Selected");
            printMethodInvocation(jmi, push);
            push.pop();
        }

        push.print("name", jfa.name);
        push.print("sym", jfa.sym);
    }

    static void printJTree(JCTree jt, Push push) {
        if (jt instanceof JCTree.JCVariableDecl var) {
            printVar(var, push);

            //maker.Ident();
            //jtree.defs = jtree.defs.append(makeGetterMethodDecl(var));
        } else if (jt instanceof JCTree.JCIdent ji) {
            printIdent(ji, push);
        }
        //else if (member instanceof JCTree.JCMethodDecl jmd) {
        //    System.out.println("method");
        //    System.out.println(jmd);
        //    System.out.println(jmd.body);
        //    jmd.body.stats.forEach(s -> {
        //        System.out.println(s);
        //        System.out.println(s.getClass());
        //        if (s instanceof JCTree.JCReturn jr) {
        //            System.out.println(jr.expr);
        //            System.out.println(jr.expr.getClass());
        //            if (jr.expr instanceof JCTree.JCLiteral jl) {
        //                System.out.println(jl.typetag);
        //            }
        //        }
        //    });
        //    System.out.println(jmd.restype);
        //}
    }

    public static void print(JCTree.JCClassDecl jtree) {
        Push push = new Push();
        System.out.println("+++++++++++++++++++++++++");
        for (JCTree member : jtree.getMembers())
            push.print("Members", member);
        System.out.println("+++++++++++++++++++++++++");

        for (JCTree member : jtree.getMembers()) {
            System.out.println("----------");

            printJTree(member, push);
        }
    }

}
