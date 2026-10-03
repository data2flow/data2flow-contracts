package net.java21.data2flow.contracts.connector;

/** 커넥터가 다룰 수 있는 payload 형식(connector_catalogs.payload_formats) */
public enum PayloadFormat {
    JSON, CBOR, MSGPACK, PROTOBUF, AVRO, CSV, TEXT, BINARY, SPARKPLUG_B
}
