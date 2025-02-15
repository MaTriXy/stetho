/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.common;

import android.os.Build;
import android.os.Process;
import android.os.UserManager;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.io.IOException;

public class ProcessUtil {
  /**
   * Maximum length allowed in {@code /proc/self/cmdline}.  Imposed to avoid a large buffer
   * allocation during the init path.
   */
  private static final int CMDLINE_BUFFER_SIZE = 64;

  private static String sProcessName;
  private static boolean sProcessNameRead;

  /**
   * Get process name by reading {@code /proc/self/cmdline}.
   *
   * @return Process name or null if there was an error reading from {@code /proc/self/cmdline}.
   *     It is unknown how this error can occur in practice and should be considered extremely
   *     rare.
   */
  @Nullable
  public static synchronized String getProcessName() {
    if (!sProcessNameRead) {
      sProcessNameRead = true;
      try {
        sProcessName = readProcessName();
      } catch (IOException e) {
      }
    }
    return sProcessName;
  }

  private static String readProcessName() throws IOException {
    byte[] cmdlineBuffer = new byte[CMDLINE_BUFFER_SIZE];

    // Avoid using a Reader to not pick up a forced 16K buffer.  Silly java.io...
    FileInputStream stream = new FileInputStream("/proc/self/cmdline");
    boolean success = false;
    try {
      int n = stream.read(cmdlineBuffer);
      success = true;
      int endIndex = indexOf(cmdlineBuffer, 0, n, (byte)0 /* needle */);
      return new String(cmdlineBuffer, 0, endIndex > 0 ? endIndex : n);
    } finally {
      Util.close(stream, !success);
    }
  }

  private static int indexOf(byte[] haystack, int offset, int length, byte needle) {
    for (int i = 0; i < haystack.length; i++) {
      if (haystack[i] == needle) {
        return i;
      }
    }
    return -1;
  }

  public static int getUserId() {
    // On multi-user devices, user id is calculated from process uid.
    // On single-user devices, user id is always 0.
    // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/os/UserHandle.java;l=282;drc=5a5038ddb87b9c4ac576935b77cab4688169ee48
    final boolean supportsMultipleUsers = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && UserManager21Impl.supportsMultipleUsers();
    return supportsMultipleUsers ? Process.myUid() / 100000 : 0;
  }

  @Keep
  @RequiresApi(Build.VERSION_CODES.N)
  private static class UserManager21Impl {
    public static boolean supportsMultipleUsers() {
      return UserManager.supportsMultipleUsers();
    }
  }
}
