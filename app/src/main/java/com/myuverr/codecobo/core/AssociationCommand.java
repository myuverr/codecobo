// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.core;
import java.util.List;
import java.util.Locale;
public final class AssociationCommand {
    private AssociationCommand() {}
    public static List<String> arguments(int userId, String packageName, String address) {
        if (userId < 0 || packageName == null
            || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") || address == null
            || !address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) {
            throw new IllegalArgumentException("Invalid association arguments");
        }
        return List.of("cmd", "companiondevice", "associate", Integer.toString(userId), packageName,
            address.toUpperCase(Locale.ROOT));
    }
}
