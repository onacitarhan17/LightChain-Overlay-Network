/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.p2p;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import model.Entity;
import model.codec.EncodedEntity;
import model.lightchain.Identifier;
import modules.codec.JsonEncoder;
import network.p2p.proto.GetReply;
import network.p2p.proto.GetRequest;
import network.p2p.proto.Message;
import network.p2p.proto.MessengerGrpc;
import network.p2p.proto.PutMessage;
import network.p2p.proto.StorageGrpc;
import protocol.Engine;

/**
 * Includes the implementation of server side functionality of gRPC requests.
 */
public class MessageServer {
  private final Server server;
  private final HashMap<String, Engine> engineChannelTable;
  public ConcurrentMap<String, Set<Entity>> distributedStorageComponent;

  /**
   * Create a MessageServer using ServerBuilder as a base.
   *
   * @param port the TCP port of the target server.
   */
  public MessageServer(int port) {
    server = ServerBuilder.forPort(port)
            .addService(new MessengerImpl())
            .addService(new StorageImpl())
            .build();

    this.engineChannelTable = new HashMap<>();
    this.distributedStorageComponent = new ConcurrentHashMap<>();
  }

  /**
   * Registers an engine on the give channel.
   *
   * @param channel channel for which engine is registered on.
   * @param engine  the engine to be registered on this channel.
   * @throws IllegalStateException if an engine already exists on this channel.
   */
  public void setEngine(String channel, Engine engine) throws IllegalStateException {
    if (this.engineChannelTable.containsKey(channel)) {
      throw new IllegalStateException("channel already exist: " + channel);
    }
    this.engineChannelTable.put(channel, engine);
  }

  /**
   * Returns the port number on which this server is listening.
   *
   * @return the port number on which this server is listening.
   */
  public int getPort() {
    return this.server.getPort();
  }

  /**
   * Start serving requests.
   */
  public void start() throws IOException {
    server.start();
    // TODO: replace with info log
    System.out.println("server started, listening on " + this.getPort());
  }

  /**
   * Halts a running gRPC Server.
   *
   * @throws InterruptedException if the Server process gets interrupted abruptly.
   */
  public void stop() throws InterruptedException {
    server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
  }

  /**
   * Concrete implementation of the gRPC Serverside response methods.
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "meant to be externally mutable")
  public class MessengerImpl extends MessengerGrpc.MessengerImplBase {
    /**
     * Function for the gRPC server.
     *
     * @param responseObserver takes in the stream object to receive messages.
     * @return StreamObserver for the Client to facilitate response relaying.
     */
    @Override
    public StreamObserver<Message> deliver(StreamObserver<Empty> responseObserver) {
      return new StreamObserver<Message>() {
        @Override
        @SuppressFBWarnings(value = "DM_EXIT", justification = "meant to fail VM safely upon error")
        public void onNext(Message message) {
          // TODO: replace with info log
          System.out.println("Received Entity");
          System.out.println("OriginID: " + message.getOriginId().toStringUtf8());
          System.out.println("Channel: " + message.getChannel());
          System.out.println("Type: " + message.getType());

          // TODO: check that this node is among target ids
          if (engineChannelTable.containsKey(message.getChannel())) {
            JsonEncoder encoder = new JsonEncoder();
            EncodedEntity e = new EncodedEntity(message.getPayload().toByteArray(), message.getType());
            try {
              engineChannelTable.get(message.getChannel()).process(encoder.decode(e));
            } catch (ClassNotFoundException ex) {
              // TODO: replace with fatal log
              System.err.println("could not decode incoming message");
              ex.printStackTrace();
              System.exit(1);
            }
          } else {
            // TODO: replace with error log
            System.err.println("no channel found for incoming message: " + message.getChannel());
          }
        }

        @Override
        public void onError(Throwable t) {
          // TODO: replace with error log
          System.err.println("encountered error in deliver: " + t);
        }

        @Override
        public void onCompleted() {
          responseObserver.onNext(com.google.protobuf.Empty.newBuilder().build());
          responseObserver.onCompleted();
        }
      };

    }
  }

  public class StorageImpl extends StorageGrpc.StorageImplBase {

    @Override
    public StreamObserver<PutMessage> put(StreamObserver<Empty> responseObserver) {
      return new StreamObserver<PutMessage>() {
        @Override
        @SuppressFBWarnings(value = "DM_EXIT", justification = "meant to fail VM safely upon error")
        public void onNext(PutMessage putMessage) {

          JsonEncoder encoder = new JsonEncoder();
          EncodedEntity e = new EncodedEntity(putMessage.getPayload().toByteArray(), putMessage.getType());

          if (engineChannelTable.containsKey(putMessage.getChannel())) {
            try {

              // TODO: replace with info log
              System.out.println("Putting Entity");
              System.out.println("ID: " + encoder.decode(e).id());
              System.out.println("Channel: " + putMessage.getChannel());
              System.out.println("Type: " + putMessage.getType());

              // puts the incoming entity onto the distributedStorageComponent

              if (distributedStorageComponent.containsKey(putMessage.getChannel())) {
                distributedStorageComponent.get(putMessage.getChannel()).add(encoder.decode(e));
              } else {
                HashSet s = new HashSet<>();
                s.add(encoder.decode(e));
                distributedStorageComponent.put(putMessage.getChannel(), s);
              }

            } catch (ClassNotFoundException ex) {
              // TODO: replace with fatal log
              System.err.println("could not decode incoming put message");
              ex.printStackTrace();
              System.exit(1);
            }
          } else {
            // TODO: replace with error log
            System.err.println("no channel found for incoming put message: " + putMessage.getChannel());
          }
        }

        @Override
        public void onError(Throwable t) {
          // TODO: replace with error log
          System.err.println("encountered error in deliver: " + t);
        }

        @Override
        public void onCompleted() {
          responseObserver.onNext(com.google.protobuf.Empty.newBuilder().build());
          responseObserver.onCompleted();
        }
      };
    }

    @Override
    public StreamObserver<GetRequest> get(StreamObserver<GetReply> responseObserver) {
      return new StreamObserver<GetRequest>() {
        @Override
        public void onNext(GetRequest request) {

          Identifier id = new Identifier(request.getIdentifier().toByteArray());
          JsonEncoder encoder = new JsonEncoder();

          System.out.println("Getting Entity");
          System.out.println("ID: " + id);

          Entity entity = null;

          if (distributedStorageComponent.containsKey(request.getChannel())) {

            for (Entity e : distributedStorageComponent.get(request.getChannel())) {
              if (e.id().comparedTo(id) == 0) {
                entity = e;
                EncodedEntity encodedEntity = encoder.encode(entity);

                GetReply reply = GetReply
                        .newBuilder()
                        .setPayload(ByteString.copyFrom(encodedEntity.getBytes()))
                        .setType(encodedEntity.getType())
                        .build();

                responseObserver.onNext(reply);
              }
            }

          } else {
            System.out.println("CHANNEL NOT FOUND");
          }

        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onCompleted() {
          responseObserver.onCompleted();
        }
      };
    }

  }

}
