// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: message.proto

package network.p2p.proto;

public interface MessageOrBuilder extends
    // @@protoc_insertion_point(interface_extends:network.p2p.proto.Message)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>bytes OriginId = 1;</code>
   * @return The originId.
   */
  com.google.protobuf.ByteString getOriginId();

  /**
   * <code>string Channel = 2;</code>
   * @return The channel.
   */
  String getChannel();
  /**
   * <code>string Channel = 2;</code>
   * @return The bytes for channel.
   */
  com.google.protobuf.ByteString
      getChannelBytes();

  /**
   * <code>repeated bytes TargetIds = 3;</code>
   * @return A list containing the targetIds.
   */
  java.util.List<com.google.protobuf.ByteString> getTargetIdsList();
  /**
   * <code>repeated bytes TargetIds = 3;</code>
   * @return The count of targetIds.
   */
  int getTargetIdsCount();
  /**
   * <code>repeated bytes TargetIds = 3;</code>
   * @param index The index of the element to return.
   * @return The targetIds at the given index.
   */
  com.google.protobuf.ByteString getTargetIds(int index);

  /**
   * <code>bytes Payload = 4;</code>
   * @return The payload.
   */
  com.google.protobuf.ByteString getPayload();

  /**
   * <code>string Type = 5;</code>
   * @return The type.
   */
  String getType();
  /**
   * <code>string Type = 5;</code>
   * @return The bytes for type.
   */
  com.google.protobuf.ByteString
      getTypeBytes();
}
