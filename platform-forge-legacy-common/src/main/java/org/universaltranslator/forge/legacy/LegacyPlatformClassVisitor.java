package org.universaltranslator.forge.legacy;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** HUD与告示牌注入 */
final class LegacyPlatformClassVisitor extends ClassVisitor {
    private static final String BRIDGE =
            "org/universaltranslator/forge/legacy/LegacyRenderedTextBridge";
    private static final String SIGN_CONTEXT =
            "org/universaltranslator/forge/legacy/LegacySignTranslationContext";
    private final boolean hud;
    private int modifiedMethods;

    LegacyPlatformClassVisitor(ClassVisitor delegate, boolean hud) {
        super(Opcodes.ASM5, delegate);
        this.hud = hud;
    }

    int modifiedMethods() {
        return modifiedMethods;
    }

    @Override
    public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions
    ) {
        MethodVisitor delegate = super.visitMethod(
                access, name, descriptor, signature, exceptions);
        if (!hud) {
            return signRenderVisitor(name, descriptor, delegate);
        }
        if (isTitleMethod(name, descriptor)) {
            modifiedMethods++;
            return titleVisitor(delegate);
        }
        if (isOverlayMethod(name, descriptor)) {
            modifiedMethods++;
            return overlayVisitor(delegate);
        }
        // 覆盖全部物品名入口
        return itemNameVisitor(delegate);
    }

    private MethodVisitor signRenderVisitor(
            String name, String descriptor, MethodVisitor delegate) {
        boolean render = descriptor.contains(
                "Lnet/minecraft/tileentity/TileEntitySign;")
                && descriptor.endsWith(")V")
                && ("render".equals(name)
                || "renderTileEntityAt".equals(name)
                || "func_192841_a".equals(name)
                || "func_180535_a".equals(name)
                || "a".equals(name));
        if (!render) {
            return delegate;
        }
        modifiedMethods++;
        return new MethodVisitor(Opcodes.ASM5, delegate) {
            @Override
            public void visitCode() {
                super.visitCode();
                super.visitVarInsn(Opcodes.ALOAD, 1);
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        SIGN_CONTEXT,
                        "push",
                        "(Ljava/lang/Object;)V",
                        false);
            }

            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.RETURN || opcode == Opcodes.ATHROW) {
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            SIGN_CONTEXT,
                            "pop",
                            "()V",
                            false);
                }
                super.visitInsn(opcode);
            }
        };
    }

    private MethodVisitor titleVisitor(MethodVisitor delegate) {
        return new MethodVisitor(Opcodes.ASM5, delegate) {
            @Override
            public void visitCode() {
                super.visitCode();
                Label continueLabel = new Label();
                super.visitVarInsn(Opcodes.ALOAD, 1);
                super.visitVarInsn(Opcodes.ALOAD, 2);
                super.visitVarInsn(Opcodes.ILOAD, 3);
                super.visitVarInsn(Opcodes.ILOAD, 4);
                super.visitVarInsn(Opcodes.ILOAD, 5);
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        BRIDGE,
                        "preloadTitle",
                        "(Ljava/lang/String;Ljava/lang/String;III)Z",
                        false);
                super.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                super.visitInsn(Opcodes.RETURN);
                super.visitLabel(continueLabel);
                super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
        };
    }

    private MethodVisitor overlayVisitor(MethodVisitor delegate) {
        return new MethodVisitor(Opcodes.ASM5, delegate) {
            @Override
            public void visitCode() {
                super.visitCode();
                Label continueLabel = new Label();
                super.visitVarInsn(Opcodes.ALOAD, 1);
                super.visitVarInsn(Opcodes.ILOAD, 2);
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        BRIDGE,
                        "preloadOverlay",
                        "(Ljava/lang/String;Z)Z",
                        false);
                super.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                super.visitInsn(Opcodes.RETURN);
                super.visitLabel(continueLabel);
                super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
        };
    }

    private MethodVisitor itemNameVisitor(MethodVisitor delegate) {
        return new MethodVisitor(Opcodes.ASM5, delegate) {
            private boolean injected;

            @Override
            public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String name,
                    String descriptor,
                    boolean isInterface
            ) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                if (opcode == Opcodes.INVOKEVIRTUAL
                        && isItemStackOwner(owner)
                        && "()Ljava/lang/String;".equals(descriptor)) {
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            BRIDGE,
                            "translateItemName",
                            "(Ljava/lang/String;)Ljava/lang/String;",
                            false);
                    if (!injected) {
                        modifiedMethods++;
                        injected = true;
                    }
                }
            }
        };
    }

    private static boolean isTitleMethod(String name, String descriptor) {
        return "(Ljava/lang/String;Ljava/lang/String;III)V".equals(descriptor)
                && ("displayTitle".equals(name)
                || "func_175178_a".equals(name)
                || "a".equals(name));
    }

    private static boolean isOverlayMethod(String name, String descriptor) {
        return "(Ljava/lang/String;Z)V".equals(descriptor)
                && ("setRecordPlaying".equals(name)
                || "setOverlayMessage".equals(name)
                || "func_110326_a".equals(name)
                || "a".equals(name));
    }

    private static boolean isItemStackOwner(String owner) {
        return "net/minecraft/item/ItemStack".equals(owner)
                || "zx".equals(owner)
                || "aip".equals(owner);
    }
}
