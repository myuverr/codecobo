// SPDX-License-Identifier: MPL-2.0

package com.myuverr.codecobo.platform;

import android.content.Context;
import android.os.Binder;
import com.myuverr.codecobo.core.AssociationCommand;
import com.myuverr.codecobo.core.CommandRunner;

/** Shizuku user service, not an Android Service component. */
public final class AssociationUserService extends IAssociationService.Stub {
    private final int ownerUid;
    private final String packageName;
    public AssociationUserService() {
        ownerUid = -1;
        packageName = "";
    }
    public AssociationUserService(Context context) {
        ownerUid = context.getApplicationInfo().uid;
        packageName = context.getPackageName();
    }
    private void enforceOwner(int userId) {
        if (ownerUid < 0 || Binder.getCallingUid() != ownerUid || userId != ownerUid / 100000)
            throw new SecurityException("Only the owning app and user may request this operation");
    }
    @Override
    public String associate(int userId, String address) {
        enforceOwner(userId);
        try {
            CommandRunner.Result r =
                CommandRunner.run(AssociationCommand.arguments(userId, packageName, address), 5000);
            return (r.timedOut() ? "Command timed out" : "Command exit code: " + r.exitCode()) + "\n"
                + r.output();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Association interrupted";
        } catch (Exception e) {
            return e.toString();
        }
    }
    @Override
    public void destroy() {
        System.exit(0);
    }
}
