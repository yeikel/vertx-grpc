package io.vertx.grpc.common.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.grpc.common.GrpcMessage;

public class CompressedGrpcMessage implements GrpcMessage {

  private String compression;
  private Buffer compressed;
  private Buffer payload;

  public CompressedGrpcMessage(Buffer compressed, String compression) {
    this.compression = compression;
    this.compressed = compressed;
  }

  @Override
  public boolean isCompressed() {
    return true;
  }

  @Override
  public Buffer payload() {
    if (payload == null) {
      if ("gzip".equals(compression)) {
        EmbeddedChannel channel = new EmbeddedChannel(ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP));
        try {
          channel.writeOneInbound(compressed.getByteBuf());
          channel.flushInbound();
          int o = channel.inboundMessages().size();
          if (o > 0) {
            payload = Buffer.buffer((ByteBuf) channel.inboundMessages().poll());
          }
        } finally {
          channel.close();
        }
      }
    }
    return payload;
  }

  @Override
  public Buffer encode(String encoding) {
    throw new UnsupportedOperationException();
  }
}
