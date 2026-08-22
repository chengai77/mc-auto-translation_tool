package org.universaltranslator.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/** 配置仅属主可写 */
public final class LocalConfigSecurity {
    private LocalConfigSecurity() {
    }

    public static void restrictToOwner(Path file) {
        Set<PosixFilePermission> permissions = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE);
        try {
            Files.setPosixFilePermissions(file, permissions);
        } catch (IOException ignored) {
            // 无POSIX权限
        } catch (UnsupportedOperationException ignored) {
            // 沿用系统ACL
        } catch (SecurityException ignored) {
            // 改权限失败可用
        }
    }
}
