package io.vertx.grpc.client;

import io.grpc.MethodDescriptor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.impl.GrpcClientImpl;
import io.vertx.grpc.common.GrpcMessage;

/**
 * A gRPC client for Vert.x
 *
 * Unlike traditional gRPC clients, this client does not rely on a generated RPC interface to interact with the service.
 *
 * Instead, you can interact with the service with a request/response interfaces and gRPC messages, very much like
 * a traditional client.
 *
 * The client exposes 2 levels of API
 *
 * <ul>
 *   <li>a Protobuf message {@link #request(SocketAddress) API}: {@link GrpcClientRequest}/{@link GrpcClientResponse} with Protobuf messages to call any gRPC service in a generic way</li>
 *   <li>a gRPC message {@link #request(SocketAddress, MethodDescriptor)}: {@link GrpcClientRequest}/{@link GrpcClientRequest} with gRPC messages to call a given method of a gRPC service</li>
 * </ul>
 */
public interface GrpcClient {

  /**
   * Create a new client
   *
   * @param vertx the vertx instance
   * @return the created client
   */
  static GrpcClient client(Vertx vertx) {
    return new GrpcClientImpl(vertx);
  }

  /**
   * Create a new client
   *
   * @param vertx the vertx instance
   * @param options the client options
   * @return the created client
   */
  static GrpcClient client(Vertx vertx, HttpClientOptions options) {
    return new GrpcClientImpl(options, vertx);
  }

  /**
   * Connect to the remote {@code server} and create a request for any hosted gRPC service.
   *
   * @param server the server hosting the service
   * @return a future request
   */
  Future<GrpcClientRequest<GrpcMessage, GrpcMessage>> request(SocketAddress server);

  /**
   * Connect to the remote {@code server} and create a request for given {@code method} of a hosted gRPC service.
   *
   * @param server the server hosting the service
   * @return a future request
   */
  <Req, Resp> Future<GrpcClientRequest<Req, Resp>> request(SocketAddress server, MethodDescriptor<Req, Resp> method);

}
