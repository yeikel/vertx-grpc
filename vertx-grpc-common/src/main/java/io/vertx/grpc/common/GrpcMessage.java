/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.grpc.common;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.impl.BaseGrpcMessage;

/**
 * A generic gRPC message
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface GrpcMessage {

  /**
   * Create a gRPC message.
   *
   * @param payload the message payload, usually in protobuf format
   * @return the message
   */
  static GrpcMessage message(Buffer payload) {
    return new BaseGrpcMessage(payload);
  }

  /**
   * @return wether the message is compressed
   */
  boolean isCompressed();

  /**
   * @return the message payload
   */
  Buffer payload();

  /**
   * Encode this message to the gRPC format
   * @return the encoded message
   */
  default Buffer encode() {
    return encode("identity");
  }

  /**
   * Like {@link #encode()} but with the specific {@code encoding}
   */
  Buffer encode(String encoding);

}
