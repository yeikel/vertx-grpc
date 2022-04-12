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
package io.vertx.grpc.client;

import io.grpc.Grpc;
import io.grpc.ServerCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.streaming.Empty;
import io.grpc.examples.streaming.Item;
import io.grpc.examples.streaming.StreamingGrpc;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcStatus;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ClientRequestTest extends ClientTestBase {

  protected void testUnary(TestContext should, String requestEncoding, String responseEncoding) throws IOException {

    super.testUnary(should, requestEncoding, responseEncoding);

    Async test = should.async();
    GrpcClient client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), GreeterGrpc.getSayHelloMethod())
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.encoding(requestEncoding);
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          should.assertEquals(responseEncoding, callResponse.encoding());
          AtomicInteger count = new AtomicInteger();
          callResponse.messageHandler(reply -> {
            should.assertEquals(1, count.incrementAndGet());
            should.assertEquals("Hello Julien", reply.getMessage());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals(1, count.get());
            test.complete();
          });
        }));
        callRequest.end(HelloRequest.newBuilder().setName("Julien").build());
      }));
  }

  @Test
  public void testSSL(TestContext should) throws IOException {

    GreeterGrpc.GreeterImplBase called = new GreeterGrpc.GreeterImplBase() {
      @Override
      public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build());
        responseObserver.onCompleted();
      }
    };

    SelfSignedCertificate cert = SelfSignedCertificate.create();
    ServerCredentials creds = TlsServerCredentials
      .newBuilder()
      .keyManager(new File(cert.certificatePath()), new File(cert.privateKeyPath()))
      .build();
    startServer(called, Grpc.newServerBuilderForPort(8443, creds));

    Async test = should.async();
    GrpcClient client = GrpcClient.client(vertx, new HttpClientOptions().setSsl(true)
      .setUseAlpn(true)
      .setPemTrustOptions(cert.trustOptions()));
    client.request(SocketAddress.inetSocketAddress(8443, "localhost"), GreeterGrpc.getSayHelloMethod())
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          AtomicInteger count = new AtomicInteger();
          callResponse.messageHandler(reply -> {
            should.assertEquals(1, count.incrementAndGet());
            should.assertEquals("Hello Julien", reply.getMessage());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals(1, count.get());
            test.complete();
          });
        }));
        callRequest.end(HelloRequest.newBuilder().setName("Julien").build());
      }));
  }

  @Test
  public void testStatus(TestContext should) throws IOException {

    super.testStatus(should);

    Async test = should.async();
    GrpcClient client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), GreeterGrpc.getSayHelloMethod())
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          callResponse.messageHandler(reply -> {
            should.fail();
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.UNAVAILABLE, callResponse.status());
            test.complete();
          });
        }));
        callRequest.end(HelloRequest.newBuilder().setName("Julien").build());
      }));
  }

  @Test
  public void testServerStreaming(TestContext should) throws IOException {

    super.testServerStreaming(should);

    final Async test = should.async();
    GrpcClient client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), StreamingGrpc.getSourceMethod())
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          AtomicInteger count = new AtomicInteger();
          callResponse.messageHandler(item -> {
            int i = count.getAndIncrement();
            should.assertEquals("the-value-" + i, item.getValue());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals(NUM_ITEMS, count.get());
            test.complete();
          });
        }));
        callRequest.end(Empty.getDefaultInstance());
      }));
  }

  @Test
  public void testServerStreamingBackPressure(TestContext should) throws IOException {
    super.testServerStreamingBackPressure(should);

    Async test = should.async();
    GrpcClient client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), StreamingGrpc.getSourceMethod())
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          callResponse.pause();
          AtomicInteger num = new AtomicInteger();
          Runnable readBatch = () -> {
            vertx.<Integer>executeBlocking(p -> {
              while (batchQueue.size() == 0) {
                try {
                  Thread.sleep(10);
                } catch (InterruptedException e) {
                }
              }
              p.complete(batchQueue.poll());;
            }).onSuccess(toRead -> {
              num.set(toRead);
              callResponse.resume();
            });
          };
          readBatch.run();
          callResponse.messageHandler(item -> {
            if (num.decrementAndGet() == 0) {
              callResponse.pause();
              readBatch.run();
            }
          });
          callResponse.endHandler(v -> {
            should.assertEquals(-1, num.get());
            test.complete();
          });
        }));
        callRequest.end(Empty.getDefaultInstance());
      }));
  }

  @Override
  public void testClientStreaming(TestContext should) throws Exception {

    super.testClientStreaming(should);

    Async done = should.async();
    GrpcClient client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), StreamingGrpc.getSinkMethod())
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          AtomicInteger count = new AtomicInteger();
          callResponse.messageHandler(item -> {
            count.incrementAndGet();
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals(1, count.get());
            done.complete();
          });
        }));
        AtomicInteger count = new AtomicInteger(NUM_ITEMS);
        vertx.setPeriodic(10, id -> {
          int val = count.decrementAndGet();
          if (val >= 0) {
            callRequest.write(Item.newBuilder().setValue("the-value-" + (NUM_ITEMS - val - 1)).build());
          } else {
            vertx.cancelTimer(id);
            callRequest.end();
          }
        });
      }));
  }

  @Test
  public void testClientStreamingBackPressure(TestContext should) throws Exception {

    super.testClientStreamingBackPressure(should);

    Async done = should.async();
    GrpcClient client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), StreamingGrpc.getSinkMethod())
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          callResponse.endHandler(v -> {
            done.complete();
          });
        }));
        AtomicInteger batchCount = new AtomicInteger(0);
        Runnable[] write = new Runnable[1];
        AtomicInteger written = new AtomicInteger();
        write[0] = () -> {
          if (callRequest.writeQueueFull()) {
            batchQueue.add(written.get());
            callRequest.drainHandler(v -> {
              written.set(0);
              if (batchCount.incrementAndGet() < NUM_BATCHES) {
                write[0].run();
              } else {
                callRequest.end();
              }
            });
          } else {
            callRequest.write(Item.newBuilder().setValue("the-value-" + batchCount).build());
            written.incrementAndGet();
            vertx.runOnContext(v -> {
              write[0].run();
            });
          }
        };
        write[0].run();
      }));
  }

  @Test
  public void testBidiStreaming(TestContext should) throws Exception {

    super.testBidiStreaming(should);

    Async done = should.async();
    GrpcClient client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), StreamingGrpc.getPipeMethod())
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          AtomicInteger count = new AtomicInteger();
          callResponse.messageHandler(item -> {
            int i = count.getAndIncrement();
            should.assertEquals("the-value-" + i, item.getValue());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals(NUM_ITEMS, count.get());
            done.complete();
          });
        }));
        AtomicInteger count = new AtomicInteger(NUM_ITEMS);
        vertx.setPeriodic(10, id -> {
          int val = count.decrementAndGet();
          if (val >= 0) {
            callRequest.write(Item.newBuilder().setValue("the-value-" + (NUM_ITEMS - val - 1)).build());
          } else {
            vertx.cancelTimer(id);
            callRequest.end();
          }
        });
      }));
  }

  @Test
  public void testReset(TestContext should) throws Exception {

    super.testReset(should);

    GrpcClient client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), StreamingGrpc.getPipeMethod())
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.write(Item.newBuilder().setValue("item").build());
        callRequest.response().onComplete(should.asyncAssertSuccess(resp -> {
          AtomicInteger count = new AtomicInteger();
          resp.handler(item -> {
            if (count.getAndIncrement() == 0) {
              callRequest.reset();
            }
          });
        }));
      }));
  }

  @Test
  public void testMetadata(TestContext should) throws Exception {

    super.testMetadata(should);

    Async test = should.async();
    GrpcClient client = GrpcClient.client(vertx);
    client.request(SocketAddress.inetSocketAddress(port, "localhost"), GreeterGrpc.getSayHelloMethod())
      .onComplete(should.asyncAssertSuccess(callRequest -> {
        callRequest.headers().set("custom_request_header", "custom_request_header_value");
        callRequest.response().onComplete(should.asyncAssertSuccess(callResponse -> {
          should.assertEquals("custom_response_header_value", callResponse.headers().get("custom_response_header"));
          should.assertEquals(3, testMetadataStep.getAndIncrement());
          AtomicInteger count = new AtomicInteger();
          callResponse.messageHandler(reply -> {
            should.assertEquals(1, count.incrementAndGet());
            should.assertEquals("Hello Julien", reply.getMessage());
          });
          callResponse.endHandler(v2 -> {
            should.assertEquals(GrpcStatus.OK, callResponse.status());
            should.assertEquals("custom_response_trailer_value", callResponse.trailers().get("custom_response_trailer"));
            should.assertEquals(1, count.get());
            should.assertEquals(4, testMetadataStep.getAndIncrement());
            test.complete();
          });
        }));
        callRequest.end(HelloRequest.newBuilder().setName("Julien").build());
      }));
  }
}
