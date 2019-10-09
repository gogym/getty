// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: GroupChatReq.proto
package org.getty.test.packet;
public final class GroupChatReqClass {
  private GroupChatReqClass() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  public interface GroupChatReqOrBuilder extends
      // @@protoc_insertion_point(interface_extends:GroupChatReq)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <pre>
     * ������ID
     * </pre>
     *
     * <code>optional string senderId = 1;</code>
     */
    String getSenderId();
    /**
     * <pre>
     * ������ID
     * </pre>
     *
     * <code>optional string senderId = 1;</code>
     */
    com.google.protobuf.ByteString
        getSenderIdBytes();

    /**
     * <pre>
     * ȺID
     * </pre>
     *
     * <code>optional string groupId = 2;</code>
     */
    String getGroupId();
    /**
     * <pre>
     * ȺID
     * </pre>
     *
     * <code>optional string groupId = 2;</code>
     */
    com.google.protobuf.ByteString
        getGroupIdBytes();

    /**
     * <pre>
     *��Ϣ����
     * </pre>
     *
     * <code>optional int32 msgType = 3;</code>
     */
    int getMsgType();

    /**
     * <pre>
     *��Ϣ��
     * </pre>
     *
     * <code>optional string body = 4;</code>
     */
    String getBody();
    /**
     * <pre>
     *��Ϣ��
     * </pre>
     *
     * <code>optional string body = 4;</code>
     */
    com.google.protobuf.ByteString
        getBodyBytes();

    /**
     * <pre>
     *��Ҫ&#64;���˵�id
     * </pre>
     *
     * <code>optional string atUserId = 5;</code>
     */
    String getAtUserId();
    /**
     * <pre>
     *��Ҫ&#64;���˵�id
     * </pre>
     *
     * <code>optional string atUserId = 5;</code>
     */
    com.google.protobuf.ByteString
        getAtUserIdBytes();
  }
  /**
   * Protobuf type {@code GroupChatReq}
   */
  public  static final class GroupChatReq extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:GroupChatReq)
      GroupChatReqOrBuilder {
    // Use GroupChatReq.newBuilder() to construct.
    private GroupChatReq(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private GroupChatReq() {
      senderId_ = "";
      groupId_ = "";
      msgType_ = 0;
      body_ = "";
      atUserId_ = "";
    }

    @Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return com.google.protobuf.UnknownFieldSet.getDefaultInstance();
    }
    private GroupChatReq(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      int mutable_bitField0_ = 0;
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            default: {
              if (!input.skipField(tag)) {
                done = true;
              }
              break;
            }
            case 10: {
              String s = input.readStringRequireUtf8();

              senderId_ = s;
              break;
            }
            case 18: {
              String s = input.readStringRequireUtf8();

              groupId_ = s;
              break;
            }
            case 24: {

              msgType_ = input.readInt32();
              break;
            }
            case 34: {
              String s = input.readStringRequireUtf8();

              body_ = s;
              break;
            }
            case 42: {
              String s = input.readStringRequireUtf8();

              atUserId_ = s;
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return GroupChatReqClass.internal_static_GroupChatReq_descriptor;
    }

    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return GroupChatReqClass.internal_static_GroupChatReq_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              GroupChatReq.class, Builder.class);
    }

    public static final int SENDERID_FIELD_NUMBER = 1;
    private volatile Object senderId_;
    /**
     * <pre>
     * ������ID
     * </pre>
     *
     * <code>optional string senderId = 1;</code>
     */
    public String getSenderId() {
      Object ref = senderId_;
      if (ref instanceof String) {
        return (String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        String s = bs.toStringUtf8();
        senderId_ = s;
        return s;
      }
    }
    /**
     * <pre>
     * ������ID
     * </pre>
     *
     * <code>optional string senderId = 1;</code>
     */
    public com.google.protobuf.ByteString
        getSenderIdBytes() {
      Object ref = senderId_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (String) ref);
        senderId_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    public static final int GROUPID_FIELD_NUMBER = 2;
    private volatile Object groupId_;
    /**
     * <pre>
     * ȺID
     * </pre>
     *
     * <code>optional string groupId = 2;</code>
     */
    public String getGroupId() {
      Object ref = groupId_;
      if (ref instanceof String) {
        return (String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        String s = bs.toStringUtf8();
        groupId_ = s;
        return s;
      }
    }
    /**
     * <pre>
     * ȺID
     * </pre>
     *
     * <code>optional string groupId = 2;</code>
     */
    public com.google.protobuf.ByteString
        getGroupIdBytes() {
      Object ref = groupId_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (String) ref);
        groupId_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    public static final int MSGTYPE_FIELD_NUMBER = 3;
    private int msgType_;
    /**
     * <pre>
     *��Ϣ����
     * </pre>
     *
     * <code>optional int32 msgType = 3;</code>
     */
    public int getMsgType() {
      return msgType_;
    }

    public static final int BODY_FIELD_NUMBER = 4;
    private volatile Object body_;
    /**
     * <pre>
     *��Ϣ��
     * </pre>
     *
     * <code>optional string body = 4;</code>
     */
    public String getBody() {
      Object ref = body_;
      if (ref instanceof String) {
        return (String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        String s = bs.toStringUtf8();
        body_ = s;
        return s;
      }
    }
    /**
     * <pre>
     *��Ϣ��
     * </pre>
     *
     * <code>optional string body = 4;</code>
     */
    public com.google.protobuf.ByteString
        getBodyBytes() {
      Object ref = body_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (String) ref);
        body_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    public static final int ATUSERID_FIELD_NUMBER = 5;
    private volatile Object atUserId_;
    /**
     * <pre>
     *��Ҫ&#64;���˵�id
     * </pre>
     *
     * <code>optional string atUserId = 5;</code>
     */
    public String getAtUserId() {
      Object ref = atUserId_;
      if (ref instanceof String) {
        return (String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        String s = bs.toStringUtf8();
        atUserId_ = s;
        return s;
      }
    }
    /**
     * <pre>
     *��Ҫ&#64;���˵�id
     * </pre>
     *
     * <code>optional string atUserId = 5;</code>
     */
    public com.google.protobuf.ByteString
        getAtUserIdBytes() {
      Object ref = atUserId_;
      if (ref instanceof String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (String) ref);
        atUserId_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    private byte memoizedIsInitialized = -1;
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      if (!getSenderIdBytes().isEmpty()) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 1, senderId_);
      }
      if (!getGroupIdBytes().isEmpty()) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 2, groupId_);
      }
      if (msgType_ != 0) {
        output.writeInt32(3, msgType_);
      }
      if (!getBodyBytes().isEmpty()) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 4, body_);
      }
      if (!getAtUserIdBytes().isEmpty()) {
        com.google.protobuf.GeneratedMessageV3.writeString(output, 5, atUserId_);
      }
    }

    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      if (!getSenderIdBytes().isEmpty()) {
        size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, senderId_);
      }
      if (!getGroupIdBytes().isEmpty()) {
        size += com.google.protobuf.GeneratedMessageV3.computeStringSize(2, groupId_);
      }
      if (msgType_ != 0) {
        size += com.google.protobuf.CodedOutputStream
          .computeInt32Size(3, msgType_);
      }
      if (!getBodyBytes().isEmpty()) {
        size += com.google.protobuf.GeneratedMessageV3.computeStringSize(4, body_);
      }
      if (!getAtUserIdBytes().isEmpty()) {
        size += com.google.protobuf.GeneratedMessageV3.computeStringSize(5, atUserId_);
      }
      memoizedSize = size;
      return size;
    }

    private static final long serialVersionUID = 0L;
    @Override
    public boolean equals(final Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof GroupChatReq)) {
        return super.equals(obj);
      }
      GroupChatReq other = (GroupChatReq) obj;

      boolean result = true;
      result = result && getSenderId()
          .equals(other.getSenderId());
      result = result && getGroupId()
          .equals(other.getGroupId());
      result = result && (getMsgType()
          == other.getMsgType());
      result = result && getBody()
          .equals(other.getBody());
      result = result && getAtUserId()
          .equals(other.getAtUserId());
      return result;
    }

    @Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptorForType().hashCode();
      hash = (37 * hash) + SENDERID_FIELD_NUMBER;
      hash = (53 * hash) + getSenderId().hashCode();
      hash = (37 * hash) + GROUPID_FIELD_NUMBER;
      hash = (53 * hash) + getGroupId().hashCode();
      hash = (37 * hash) + MSGTYPE_FIELD_NUMBER;
      hash = (53 * hash) + getMsgType();
      hash = (37 * hash) + BODY_FIELD_NUMBER;
      hash = (53 * hash) + getBody().hashCode();
      hash = (37 * hash) + ATUSERID_FIELD_NUMBER;
      hash = (53 * hash) + getAtUserId().hashCode();
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static GroupChatReq parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static GroupChatReq parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static GroupChatReq parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static GroupChatReq parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static GroupChatReq parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static GroupChatReq parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static GroupChatReq parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static GroupChatReq parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static GroupChatReq parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static GroupChatReq parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(GroupChatReq prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code GroupChatReq}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:GroupChatReq)
        GroupChatReqOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return GroupChatReqClass.internal_static_GroupChatReq_descriptor;
      }

      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return GroupChatReqClass.internal_static_GroupChatReq_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                GroupChatReq.class, Builder.class);
      }

      // Construct using GroupChatReqClass.GroupChatReq.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      public Builder clear() {
        super.clear();
        senderId_ = "";

        groupId_ = "";

        msgType_ = 0;

        body_ = "";

        atUserId_ = "";

        return this;
      }

      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return GroupChatReqClass.internal_static_GroupChatReq_descriptor;
      }

      public GroupChatReq getDefaultInstanceForType() {
        return GroupChatReq.getDefaultInstance();
      }

      public GroupChatReq build() {
        GroupChatReq result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      public GroupChatReq buildPartial() {
        GroupChatReq result = new GroupChatReq(this);
        result.senderId_ = senderId_;
        result.groupId_ = groupId_;
        result.msgType_ = msgType_;
        result.body_ = body_;
        result.atUserId_ = atUserId_;
        onBuilt();
        return result;
      }

      public Builder clone() {
        return (Builder) super.clone();
      }
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          Object value) {
        return (Builder) super.setField(field, value);
      }
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return (Builder) super.clearField(field);
      }
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return (Builder) super.clearOneof(oneof);
      }
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, Object value) {
        return (Builder) super.setRepeatedField(field, index, value);
      }
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          Object value) {
        return (Builder) super.addRepeatedField(field, value);
      }
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof GroupChatReq) {
          return mergeFrom((GroupChatReq)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(GroupChatReq other) {
        if (other == GroupChatReq.getDefaultInstance()) return this;
        if (!other.getSenderId().isEmpty()) {
          senderId_ = other.senderId_;
          onChanged();
        }
        if (!other.getGroupId().isEmpty()) {
          groupId_ = other.groupId_;
          onChanged();
        }
        if (other.getMsgType() != 0) {
          setMsgType(other.getMsgType());
        }
        if (!other.getBody().isEmpty()) {
          body_ = other.body_;
          onChanged();
        }
        if (!other.getAtUserId().isEmpty()) {
          atUserId_ = other.atUserId_;
          onChanged();
        }
        onChanged();
        return this;
      }

      public final boolean isInitialized() {
        return true;
      }

      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        GroupChatReq parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (GroupChatReq) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }

      private Object senderId_ = "";
      /**
       * <pre>
       * ������ID
       * </pre>
       *
       * <code>optional string senderId = 1;</code>
       */
      public String getSenderId() {
        Object ref = senderId_;
        if (!(ref instanceof String)) {
          com.google.protobuf.ByteString bs =
              (com.google.protobuf.ByteString) ref;
          String s = bs.toStringUtf8();
          senderId_ = s;
          return s;
        } else {
          return (String) ref;
        }
      }
      /**
       * <pre>
       * ������ID
       * </pre>
       *
       * <code>optional string senderId = 1;</code>
       */
      public com.google.protobuf.ByteString
          getSenderIdBytes() {
        Object ref = senderId_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (String) ref);
          senderId_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <pre>
       * ������ID
       * </pre>
       *
       * <code>optional string senderId = 1;</code>
       */
      public Builder setSenderId(
          String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        senderId_ = value;
        onChanged();
        return this;
      }
      /**
       * <pre>
       * ������ID
       * </pre>
       *
       * <code>optional string senderId = 1;</code>
       */
      public Builder clearSenderId() {
        
        senderId_ = getDefaultInstance().getSenderId();
        onChanged();
        return this;
      }
      /**
       * <pre>
       * ������ID
       * </pre>
       *
       * <code>optional string senderId = 1;</code>
       */
      public Builder setSenderIdBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        
        senderId_ = value;
        onChanged();
        return this;
      }

      private Object groupId_ = "";
      /**
       * <pre>
       * ȺID
       * </pre>
       *
       * <code>optional string groupId = 2;</code>
       */
      public String getGroupId() {
        Object ref = groupId_;
        if (!(ref instanceof String)) {
          com.google.protobuf.ByteString bs =
              (com.google.protobuf.ByteString) ref;
          String s = bs.toStringUtf8();
          groupId_ = s;
          return s;
        } else {
          return (String) ref;
        }
      }
      /**
       * <pre>
       * ȺID
       * </pre>
       *
       * <code>optional string groupId = 2;</code>
       */
      public com.google.protobuf.ByteString
          getGroupIdBytes() {
        Object ref = groupId_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (String) ref);
          groupId_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <pre>
       * ȺID
       * </pre>
       *
       * <code>optional string groupId = 2;</code>
       */
      public Builder setGroupId(
          String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        groupId_ = value;
        onChanged();
        return this;
      }
      /**
       * <pre>
       * ȺID
       * </pre>
       *
       * <code>optional string groupId = 2;</code>
       */
      public Builder clearGroupId() {
        
        groupId_ = getDefaultInstance().getGroupId();
        onChanged();
        return this;
      }
      /**
       * <pre>
       * ȺID
       * </pre>
       *
       * <code>optional string groupId = 2;</code>
       */
      public Builder setGroupIdBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        
        groupId_ = value;
        onChanged();
        return this;
      }

      private int msgType_ ;
      /**
       * <pre>
       *��Ϣ����
       * </pre>
       *
       * <code>optional int32 msgType = 3;</code>
       */
      public int getMsgType() {
        return msgType_;
      }
      /**
       * <pre>
       *��Ϣ����
       * </pre>
       *
       * <code>optional int32 msgType = 3;</code>
       */
      public Builder setMsgType(int value) {
        
        msgType_ = value;
        onChanged();
        return this;
      }
      /**
       * <pre>
       *��Ϣ����
       * </pre>
       *
       * <code>optional int32 msgType = 3;</code>
       */
      public Builder clearMsgType() {
        
        msgType_ = 0;
        onChanged();
        return this;
      }

      private Object body_ = "";
      /**
       * <pre>
       *��Ϣ��
       * </pre>
       *
       * <code>optional string body = 4;</code>
       */
      public String getBody() {
        Object ref = body_;
        if (!(ref instanceof String)) {
          com.google.protobuf.ByteString bs =
              (com.google.protobuf.ByteString) ref;
          String s = bs.toStringUtf8();
          body_ = s;
          return s;
        } else {
          return (String) ref;
        }
      }
      /**
       * <pre>
       *��Ϣ��
       * </pre>
       *
       * <code>optional string body = 4;</code>
       */
      public com.google.protobuf.ByteString
          getBodyBytes() {
        Object ref = body_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (String) ref);
          body_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <pre>
       *��Ϣ��
       * </pre>
       *
       * <code>optional string body = 4;</code>
       */
      public Builder setBody(
          String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        body_ = value;
        onChanged();
        return this;
      }
      /**
       * <pre>
       *��Ϣ��
       * </pre>
       *
       * <code>optional string body = 4;</code>
       */
      public Builder clearBody() {
        
        body_ = getDefaultInstance().getBody();
        onChanged();
        return this;
      }
      /**
       * <pre>
       *��Ϣ��
       * </pre>
       *
       * <code>optional string body = 4;</code>
       */
      public Builder setBodyBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        
        body_ = value;
        onChanged();
        return this;
      }

      private Object atUserId_ = "";
      /**
       * <pre>
       *��Ҫ&#64;���˵�id
       * </pre>
       *
       * <code>optional string atUserId = 5;</code>
       */
      public String getAtUserId() {
        Object ref = atUserId_;
        if (!(ref instanceof String)) {
          com.google.protobuf.ByteString bs =
              (com.google.protobuf.ByteString) ref;
          String s = bs.toStringUtf8();
          atUserId_ = s;
          return s;
        } else {
          return (String) ref;
        }
      }
      /**
       * <pre>
       *��Ҫ&#64;���˵�id
       * </pre>
       *
       * <code>optional string atUserId = 5;</code>
       */
      public com.google.protobuf.ByteString
          getAtUserIdBytes() {
        Object ref = atUserId_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (String) ref);
          atUserId_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <pre>
       *��Ҫ&#64;���˵�id
       * </pre>
       *
       * <code>optional string atUserId = 5;</code>
       */
      public Builder setAtUserId(
          String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  
        atUserId_ = value;
        onChanged();
        return this;
      }
      /**
       * <pre>
       *��Ҫ&#64;���˵�id
       * </pre>
       *
       * <code>optional string atUserId = 5;</code>
       */
      public Builder clearAtUserId() {
        
        atUserId_ = getDefaultInstance().getAtUserId();
        onChanged();
        return this;
      }
      /**
       * <pre>
       *��Ҫ&#64;���˵�id
       * </pre>
       *
       * <code>optional string atUserId = 5;</code>
       */
      public Builder setAtUserIdBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  checkByteStringIsUtf8(value);
        
        atUserId_ = value;
        onChanged();
        return this;
      }
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return this;
      }

      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return this;
      }


      // @@protoc_insertion_point(builder_scope:GroupChatReq)
    }

    // @@protoc_insertion_point(class_scope:GroupChatReq)
    private static final GroupChatReq DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new GroupChatReq();
    }

    public static GroupChatReq getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<GroupChatReq>
        PARSER = new com.google.protobuf.AbstractParser<GroupChatReq>() {
      public GroupChatReq parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
          return new GroupChatReq(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<GroupChatReq> parser() {
      return PARSER;
    }

    @Override
    public com.google.protobuf.Parser<GroupChatReq> getParserForType() {
      return PARSER;
    }

    public GroupChatReq getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_GroupChatReq_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_GroupChatReq_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    String[] descriptorData = {
      "\n\022GroupChatReq.proto\"b\n\014GroupChatReq\022\020\n\010" +
      "senderId\030\001 \001(\t\022\017\n\007groupId\030\002 \001(\t\022\017\n\007msgTy" +
      "pe\030\003 \001(\005\022\014\n\004body\030\004 \001(\t\022\020\n\010atUserId\030\005 \001(\t" +
      "B\023B\021GroupChatReqClassb\006proto3"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
    internal_static_GroupChatReq_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_GroupChatReq_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_GroupChatReq_descriptor,
        new String[] { "SenderId", "GroupId", "MsgType", "Body", "AtUserId", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
