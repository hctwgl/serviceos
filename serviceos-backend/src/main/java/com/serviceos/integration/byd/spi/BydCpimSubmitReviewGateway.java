package com.serviceos.integration.byd.spi;

/** 单次 BYD 提审网络调用端口；实现不得在内部重试。 */
public interface BydCpimSubmitReviewGateway {
    Response send(Request request) throws TransportException;

    record Request(
            String appKey,
            String nonce,
            String currentDate,
            String signature,
            byte[] payload
    ) {
        public Request {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    record Response(int httpStatus, byte[] body) {
        public Response {
            body = body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    /** 任何无法证明“远端未产生副作用”的传输异常都必须按 UNKNOWN 处理。 */
    final class TransportException extends Exception {
        public enum Kind { NOT_SENT, UNKNOWN }

        private final String errorCode;
        private final Kind kind;

        public TransportException(Kind kind, String errorCode, Throwable cause) {
            super(errorCode, cause);
            if (errorCode == null || errorCode.isBlank()) {
                throw new IllegalArgumentException("errorCode must not be blank");
            }
            this.kind = java.util.Objects.requireNonNull(kind, "kind");
            this.errorCode = errorCode.trim();
        }

        public Kind kind() {
            return kind;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
