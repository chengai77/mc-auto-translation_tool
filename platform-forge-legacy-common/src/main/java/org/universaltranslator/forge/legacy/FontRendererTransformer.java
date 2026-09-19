package org.universaltranslator.forge.legacy;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** 字体替换注入 */
public final class FontRendererTransformer implements IClassTransformer {
    private static final String FONT_RENDERER = "net.minecraft.client.gui.FontRenderer";
    private static final String CHAT_HUD = "net.minecraft.client.gui.GuiNewChat";
    private static final String GUI_SCREEN = "net.minecraft.client.gui.GuiScreen";
    private static final String GUI_TEXT_FIELD = "net.minecraft.client.gui.GuiTextField";
    private static final String GUI_EDIT_SIGN = "net.minecraft.client.gui.inventory.GuiEditSign";
    private static final String GUI_INGAME = "net.minecraft.client.gui.GuiIngame";
    private static final String SIGN_RENDERER =
            "net.minecraft.client.renderer.tileentity.TileEntitySignRenderer";
    private static final String BRIDGE =
            "org/universaltranslator/forge/legacy/LegacyRenderedTextBridge";
    private static final String VERSION_ACCESS =
            "org/universaltranslator/forge/legacy/LegacyVersionAccess";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        boolean fontRenderer = FONT_RENDERER.equals(name) || FONT_RENDERER.equals(transformedName);
        boolean chatHud = CHAT_HUD.equals(name) || CHAT_HUD.equals(transformedName);
        boolean guiScreen = GUI_SCREEN.equals(name) || GUI_SCREEN.equals(transformedName);
        boolean guiTextField = GUI_TEXT_FIELD.equals(name) || GUI_TEXT_FIELD.equals(transformedName);
        boolean guiEditSign = GUI_EDIT_SIGN.equals(name) || GUI_EDIT_SIGN.equals(transformedName);
        boolean guiIngame = GUI_INGAME.equals(name) || GUI_INGAME.equals(transformedName);
        boolean signRenderer = SIGN_RENDERER.equals(name) || SIGN_RENDERER.equals(transformedName);
        if (!fontRenderer && !chatHud && !guiScreen && !guiTextField
                && !guiEditSign && !guiIngame && !signRenderer) {
            return basicClass;
        }
        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            ClassVisitor visitor = fontRenderer
                    ? new FontRendererVisitor(writer)
                    : chatHud ? new ChatHudVisitor(writer)
                    : guiScreen ? new TooltipVisitor(writer)
                    : guiTextField ? new TextInputVisitor(writer)
                    : guiEditSign ? new SignInputVisitor(writer)
                    : new LegacyPlatformClassVisitor(writer, guiIngame);
            reader.accept(visitor, 0);
            int modifiedMethods = visitor instanceof CountingVisitor
                    ? ((CountingVisitor) visitor).modifiedMethods()
                    : ((LegacyPlatformClassVisitor) visitor).modifiedMethods();
            String hookName = fontRenderer ? "font rendering"
                    : chatHud ? "chat context"
                    : guiScreen ? "tooltip context"
                    : guiTextField ? "text input context"
                    : guiEditSign ? "sign input context"
                    : guiIngame ? "urgent HUD context" : "sign render context";
            if (modifiedMethods == 0) {
                System.err.println("[MC Auto Translation Tool] No compatible " + hookName
                        + " methods were found; text translation hook is inactive");
                return basicClass;
            }
            System.out.println("[MC Auto Translation Tool] Installed " + modifiedMethods
                    + " " + hookName + " hook(s)");
            return writer.toByteArray();
        } catch (Throwable error) {
            System.err.println("[MC Auto Translation Tool] FontRenderer transformation failed: " + error);
            return basicClass;
        }
    }

    /** 签名预览本地 */
    private static final class SignInputVisitor extends CountingVisitor {
        private static final String CONTEXT =
                "org/universaltranslator/forge/legacy/LegacyRenderContext";

        private SignInputVisitor(ClassVisitor delegate) {
            super(delegate);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            boolean drawScreen = "(IIF)V".equals(descriptor)
                    && ("drawScreen".equals(name)
                    || "func_73863_a".equals(name)
                    || "a".equals(name));
            if (!drawScreen) {
                return delegate;
            }
            markModified();
            return inputContextVisitor(delegate);
        }

        private MethodVisitor inputContextVisitor(MethodVisitor delegate) {
            return new MethodVisitor(Opcodes.ASM5, delegate) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC, CONTEXT, "pushTextInput", "()V", false);
                }

                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.RETURN || opcode == Opcodes.ATHROW) {
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC, CONTEXT, "popTextInput", "()V", false);
                    }
                    super.visitInsn(opcode);
                }
            };
        }
    }

    /** 文本框本地化 */
    private static final class TextInputVisitor extends CountingVisitor {
        private static final String CONTEXT =
                "org/universaltranslator/forge/legacy/LegacyRenderContext";

        private TextInputVisitor(ClassVisitor delegate) {
            super(delegate);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            boolean drawTextBox = "()V".equals(descriptor)
                    && ("drawTextBox".equals(name)
                    || "func_146194_f".equals(name)
                    || "g".equals(name));
            if (!drawTextBox) {
                return delegate;
            }
            markModified();
            return new MethodVisitor(Opcodes.ASM5, delegate) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC, CONTEXT, "pushTextInput", "()V", false);
                }

                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.RETURN || opcode == Opcodes.ATHROW) {
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC, CONTEXT, "popTextInput", "()V", false);
                    }
                    super.visitInsn(opcode);
                }
            };
        }
    }

    /** 聊天整体翻译 */
    private abstract static class CountingVisitor extends ClassVisitor {
        private int modifiedMethods;

        private CountingVisitor(ClassVisitor delegate) {
            super(Opcodes.ASM5, delegate);
        }

        final void markModified() {
            modifiedMethods++;
        }

        final int modifiedMethods() {
            return modifiedMethods;
        }
    }

    private static final class ChatHudVisitor extends CountingVisitor {
        private static final String CONTEXT =
                "org/universaltranslator/forge/legacy/LegacyRenderContext";

        private ChatHudVisitor(ClassVisitor delegate) {
            super(delegate);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            boolean drawChat = "(I)V".equals(descriptor)
                    && ("drawChat".equals(name) || "func_146230_a".equals(name) || "a".equals(name));
            if (drawChat) {
                markModified();
                return chatRenderContextVisitor(delegate);
            }
            boolean setChatLine = descriptor.startsWith("(L") && descriptor.endsWith(";IIZ)V")
                    && ("setChatLine".equals(name)
                    || "func_146237_a".equals(name)
                    || "a".equals(name));
            return setChatLine ? chatWrappingVisitor(delegate) : delegate;
        }

        private MethodVisitor chatRenderContextVisitor(MethodVisitor delegate) {
            return new MethodVisitor(Opcodes.ASM5, delegate) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC, CONTEXT, "pushChat", "()V", false);
                }

                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.RETURN || opcode == Opcodes.ATHROW) {
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC, CONTEXT, "pop", "()V", false);
                    }
                    super.visitInsn(opcode);
                }
            };
        }

        private MethodVisitor chatWrappingVisitor(MethodVisitor delegate) {
            return new MethodVisitor(Opcodes.ASM5, delegate) {
                private boolean injected;

                @Override
                public void visitMethodInsn(
                        int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    boolean splitText = opcode == Opcodes.INVOKESTATIC
                            && descriptor.startsWith("(L")
                            && descriptor.endsWith(";ZZ)Ljava/util/List;")
                            && ("splitText".equals(name)
                            || "func_178908_a".equals(name)
                            || "a".equals(name));
                    if (splitText) {
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                VERSION_ACCESS,
                                "splitTranslatedChat",
                                descriptor,
                                false);
                        if (!injected) {
                            markModified();
                            injected = true;
                        }
                        return;
                    }
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            };
        }
    }

    private static final class FontRendererVisitor extends CountingVisitor {
        private FontRendererVisitor(ClassVisitor delegate) {
            super(delegate);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            boolean rendersString = "(Ljava/lang/String;FFIZ)I".equals(descriptor);
            boolean measuresString = "(Ljava/lang/String;)I".equals(descriptor)
                    && ("getStringWidth".equals(name) || "func_78256_a".equals(name) || "a".equals(name));
            if (!rendersString && !measuresString) {
                return delegate;
            }
            markModified();
            return new MethodVisitor(Opcodes.ASM5, delegate) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    super.visitVarInsn(Opcodes.ALOAD, 1);
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            BRIDGE,
                            "translate",
                            "(Ljava/lang/String;)Ljava/lang/String;",
                            false);
                    super.visitVarInsn(Opcodes.ASTORE, 1);
                }
            };
        }
    }

    /** 提示前翻译 */
    private static final class TooltipVisitor extends CountingVisitor {
        private static final String CONTEXT =
                "org/universaltranslator/forge/legacy/LegacyRenderContext";

        private TooltipVisitor(ClassVisitor delegate) {
            super(delegate);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
            boolean tooltip = "(Ljava/util/List;II)V".equals(descriptor)
                    && ("drawHoveringText".equals(name)
                    || "func_146283_a".equals(name)
                    || "a".equals(name));
            if (tooltip) {
                markModified();
                return hoveringTextVisitor(delegate);
            }
            boolean itemTooltipFactory = (descriptor.equals(
                    "(Lnet/minecraft/item/ItemStack;)Ljava/util/List;")
                    || descriptor.equals("(Laip;)Ljava/util/List;"))
                    && ("getItemToolTip".equals(name)
                    || "func_191927_a".equals(name)
                    || "a".equals(name));
            if (itemTooltipFactory) {
                return itemTooltipFactoryVisitor(delegate);
            }
            // 保留原类名
            // 版本类名差异
            boolean itemTooltip = (descriptor.startsWith("(Lnet/minecraft/item/ItemStack;")
                    || descriptor.startsWith("(Lzx;")
                    || descriptor.startsWith("(Laip;"))
                    && descriptor.endsWith(";II)V")
                    && ("renderToolTip".equals(name)
                    || "func_146285_a".equals(name)
                    || "a".equals(name));
            if (!itemTooltip) {
                return delegate;
            }
            return itemTooltipProducerVisitor(delegate);
        }

        private MethodVisitor hoveringTextVisitor(MethodVisitor delegate) {
            return new MethodVisitor(Opcodes.ASM5, delegate) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    super.visitVarInsn(Opcodes.ALOAD, 1);
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            BRIDGE,
                            "translateTooltipLines",
                            "(Ljava/util/List;)Ljava/util/List;",
                            false);
                    super.visitVarInsn(Opcodes.ASTORE, 1);
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC, CONTEXT, "pushTooltip", "()V", false);
                }

                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.RETURN || opcode == Opcodes.ATHROW) {
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC, CONTEXT, "pop", "()V", false);
                    }
                    super.visitInsn(opcode);
                }
            };
        }

        private MethodVisitor itemTooltipProducerVisitor(MethodVisitor delegate) {
            return new MethodVisitor(Opcodes.ASM5, delegate) {
                private boolean injected;

                @Override
                public void visitMethodInsn(
                        int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    boolean itemStackTooltip = isItemStackOwner(owner)
                            // 版本签名差异
                            // 均返回标准列表
                            && descriptor.endsWith(")Ljava/util/List;")
                            && ("getTooltip".equals(name)
                            || "func_82840_a".equals(name)
                            || "a".equals(name));
                    if (itemStackTooltip) {
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                BRIDGE,
                                "translateItemTooltipLines",
                                "(Ljava/util/List;)Ljava/util/List;",
                                false);
                        if (!injected) {
                            markModified();
                            injected = true;
                        }
                    }
                }
            };
        }

        /** 1.12.2提示入口 */
        private MethodVisitor itemTooltipFactoryVisitor(MethodVisitor delegate) {
            return new MethodVisitor(Opcodes.ASM5, delegate) {
                private boolean injected;

                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.ARETURN) {
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                BRIDGE,
                                "translateItemTooltipLines",
                                "(Ljava/util/List;)Ljava/util/List;",
                                false);
                        if (!injected) {
                            markModified();
                            injected = true;
                        }
                    }
                    super.visitInsn(opcode);
                }
            };
        }

        private static boolean isItemStackOwner(String owner) {
            return "net/minecraft/item/ItemStack".equals(owner)
                    || "zx".equals(owner)
                    || "aip".equals(owner);
        }
    }
}
