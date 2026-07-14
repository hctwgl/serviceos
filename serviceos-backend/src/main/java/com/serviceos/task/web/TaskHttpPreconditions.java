package com.serviceos.task.web;

/** Task HTTP 乐观锁头的唯一解析入口。 */
final class TaskHttpPreconditions {
    private TaskHttpPreconditions() {
    }

    static long version(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\"[1-9][0-9]*\"")) {
            throw new IllegalArgumentException("If-Match must contain one quoted positive aggregate version");
        }
        try {
            return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match aggregate version is too large", exception);
        }
    }
}
