package io.github.tt432.cor.annotation;

import java.lang.annotation.*;

/**
 * add Codec, toTag, fromTag for class, example:
 * <pre>{@code
 *   class A {
 *       int a;
 *       boolean b;
 *       List<String> c;
 *
 *       // getters and allArgsConstructor
 *   }
 * }</pre>
 * will be:
 * <pre>{@code
 *   class A {
 *       // sources
 *
 *       public static final Codec<A> CODEC = RecordCodecBuilder.create(ins -> ins.group(
 *                  Codec.INT.fieldOf("a").forGetter(A::getA),
 *                  Codec.BOOL.fieldOf("b").forGetter(A::isB),
 *                  Codec.STRING.listOf().fieldOf("c").forGetter(A::getC),
 *              ).apply(ins, A::new));
 *
 *       public static Tag toTag(A data) {
 *           return data.toTag();
 *       }
 *
 *       public static A fromTag(Tag tag) {
 *           return CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, tag)).result().orElseThrow();
 *       }
 *
 *       public Tag toTag() {
 *           return CODEC.encodeStart(NbtOps.INSTANCE, this).result().orElseThrow();
 *       }
 *   }
 * }</pre>
 *
 * @author DustW
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface CodecHolder {
}
