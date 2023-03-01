package io.github.tt432.cor.processor;

import com.google.auto.service.AutoService;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;
import io.github.tt432.cor.annotation.CodecHolder;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * @author DustW
 */
@AutoService(CodecHolder.class)
public class CodecHolderProcessor extends AbstractProcessor {
    Messager messager; // 编译时期输入日志的
    Trees trees; // 提供了待处理的抽象语法树
    TreeMaker maker; // 封装了创建AST节点的一些方法
    Names names; // 提供了创建标识符的方法
    Context context;
    JavaCompiler javaCompiler;
    JavacElements elementUtils;
    Attr attr;
    Types types;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.trees = Trees.instance(processingEnv);
        types = processingEnv.getTypeUtils();
        try {
            var f = processingEnv.getClass().getDeclaredField("context"); // 得到 context
            f.setAccessible(true);
            context = (Context) f.get(processingEnv);
            this.maker = TreeMaker.instance(context);
            this.names = Names.instance(context);
            javaCompiler = JavaCompiler.instance(context);
            elementUtils = JavacElements.instance(context);
            attr = Attr.instance(context);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(CodecHolder.class)) {
            @SuppressWarnings("all")
            Pair<JCTree, JCTree.JCCompilationUnit> treeAndTopLevel = elementUtils.getTreeAndTopLevel(element, null, null);
            JCTree.JCClassDecl jtree = (JCTree.JCClassDecl) treeAndTopLevel.fst;
            JCTree.JCCompilationUnit unit = treeAndTopLevel.snd;

            Symbol codec = javaCompiler.resolveBinaryNameOrIdent("com.mojang.serialization.Codec");
            Symbol recordCodecBuilder = javaCompiler.resolveBinaryNameOrIdent("com.mojang.serialization.codecs.RecordCodecBuilder");
            Symbol mapCodec = javaCompiler.resolveBinaryNameOrIdent("com.mojang.serialization.MapCodec");

            addAll(unit, codec, recordCodecBuilder, mapCodec);

            JCTree.JCVariableDecl x = makeCodec(jtree, codec, recordCodecBuilder, mapCodec, jtree);
            System.out.println(x);
            jtree.defs = jtree.defs.append(x);

            //PrintHelper.print(jtree);
        }

        return true;
    }

    void addAll(JCTree.JCCompilationUnit unit, Symbol... imports) {
        ArrayList<JCTree> defs = new ArrayList<>(unit.defs);
        defs.addAll(1, Arrays.stream(imports)
                .map(i -> maker.Import(maker.QualIdent(i), false))
                .toList());
        unit.defs = List.from(defs);
    }

    JCTree.JCMemberReference invoke(JCTree.JCIdent jc, Name name) {
        return maker.Reference(
                MemberReferenceTree.ReferenceMode.INVOKE,
                name, jc, null);
    }

    JCTree.JCFieldAccess getCodec(Symbol codec, JCTree.JCExpression vartype) {
        if (vartype instanceof JCTree.JCPrimitiveTypeTree jpt) {
            if (jpt.typetag == TypeTag.BOOLEAN)
                return maker.Select(maker.Ident(codec), names.fromString("BOOL"));
            return maker.Select(maker.Ident(codec), names.fromString(jpt.typetag.name()));
        } else if (vartype instanceof JCTree.JCIdent ji) {
            return getFromIdent(codec, ji);
        } else if (vartype instanceof JCTree.JCTypeApply jta && jta.clazz instanceof JCTree.JCIdent ji) {
            return getFromIdent(codec, ji);
        }

        return null;
    }

    JCTree.JCFieldAccess getFromIdent(Symbol codec, JCTree.JCIdent ji) {
        String name = ji.name.toString();

        String in = switch (name) {
            case "LongStream" -> "LONG_STREAM";
            case "ByteBuffer" -> "BYTE_BUFFER";
            case "String" -> "STRING";
            case "Double" -> "DOUBLE";
            case "Float" -> "FLOAT";
            case "Long" -> "LONG";
            case "Integer" -> "INT";
            case "Short" -> "SHORT";
            case "Byte" -> "BYTE";
            case "Boolean" -> "BOOL";
            default -> null;
        };

        if (in != null) {
            return maker.Select(maker.Ident(codec), names.fromString(in));
        } else {
            return maker.Select(ji, names.fromString("CODEC"));
        }
    }

    /**
     * public static final Codec<A> CODECA = RecordCodecBuilder.create(ins -> ins.group(
     * Codec.BOOL.fieldOf("value").forGetter(A::getValue)
     * ).apply(ins, A::new));
     */
    private JCTree.JCVariableDecl makeCodec(JCTree.JCClassDecl jc, Symbol codec, Symbol builder, Symbol mapCodec, JCTree.JCClassDecl jtree) {
        JCTree.JCIdent currClass = maker.Ident(jc.sym);
        JCTree.JCVariableDecl ins = maker.VarDef(maker.Modifiers(8589934592L), names.fromString("ins"), null, null);

        //maker.VarDef(new Symbol.VarSymbol(
        //        0, names.fromString("ins"), null, jc.sym
        //), null);

        List<JCTree.JCExpression> groupArgs = List.nil();

        for (JCTree member : jc.getMembers()) {
            if (member instanceof JCTree.JCVariableDecl var && (var.mods.flags & Flags.STATIC) == 0) {
                JCTree.JCExpression vartype = var.vartype;

                JCTree.JCFieldAccess fieldOf;

                if (vartype instanceof JCTree.JCTypeApply jta && ((JCTree.JCIdent) jta.clazz).name.toString().equals("java.util.List")) {
                    JCTree.JCFieldAccess codecIns = getCodec(codec, jta.arguments.get(0));

                    if (codecIns == null)
                        messager.printMessage(Diagnostic.Kind.ERROR, "can't found Codec for " + vartype);

                    fieldOf = maker.Select(maker.Apply(null, maker.Select(
                            codecIns, names.fromString("listOf")
                    ), List.nil()), names.fromString("fieldOf"));
                } else {
                    JCTree.JCFieldAccess codecIns = getCodec(codec, vartype);
                    fieldOf = maker.Select(codecIns, names.fromString("fieldOf"));
                }

                JCTree.JCMethodInvocation value = maker.Apply(null, fieldOf, List.of(maker.Literal(var.name.toString())));
                JCTree.JCFieldAccess forGetter = maker.Select(value, names.fromString("forGetter"));
                JCTree.JCMethodInvocation apply = maker.Apply(List.of(currClass), forGetter,
                        List.of(invoke(currClass, getNewMethodName(var.name,
                                vartype instanceof JCTree.JCPrimitiveTypeTree jpt
                                        && jpt.typetag == TypeTag.BOOLEAN))));
                groupArgs = groupArgs.isEmpty() ? List.of(apply) : groupArgs.append(apply);
            }
        }

        JCTree.JCExpression ident = maker.Ident(ins.name);

        JCTree.JCTypeApply vartype = maker.TypeApply(maker.Ident(codec), List.of(currClass));
        JCTree.JCFieldAccess group1 = maker.Select(ident, names.fromString("group"));
        JCTree.JCMethodInvocation group = maker.Apply(null, group1, groupArgs);
        JCTree.JCFieldAccess select = maker.Select(group, names.fromString("apply"));
        JCTree.JCMemberReference reference = maker.Reference(MemberReferenceTree.ReferenceMode.NEW,
                names.fromString("<init>"), currClass, null);
        JCTree.JCLambda lambda = maker.Lambda(List.of(ins),
                maker.Apply(null, select, List.of(ident, reference)));
        JCTree.JCMethodInvocation app = maker.Apply(List.of(currClass),
                maker.Select(maker.Ident(builder), names.fromString("create")), List.of(lambda));
        Symbol.ClassSymbol typeElement = elementUtils.getTypeElement("com.mojang.serialization.Codec");
        TypeMirror type = types.erasure(typeElement.type);
        JCTree.JCVariableDecl result = maker.VarDef(
                new Symbol.VarSymbol(Flags.PUBLIC | Flags.STATIC | Flags.FINAL,
                        names.fromString("CODEC"), (Type) type, jc.sym),
                app);
        result.vartype = vartype;
        return result;
    }

    private Name getNewMethodName(Name name, boolean isBool) {
        String s = name.toString();
        return names.fromString((isBool ? "is" : "get") + s.substring(0, 1).toUpperCase() + s.substring(1, name.length()));
    }

    private JCTree.JCExpressionStatement makeAssignment(JCTree.JCExpression lhs, JCTree.JCExpression rhs) {
        return maker.Exec(
                maker.Assign(
                        lhs,
                        rhs
                )
        );
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(
                CodecHolder.class.getCanonicalName()
        );
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_17;
    }
}
