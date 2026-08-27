package in.qualtechedge.qcp.templates.enums;

/** Kafka producer {@code compression.type} a queue config can pick (V1_4_0 — queue_configs.producer_compression_type). */
public enum ProducerCompressionType {
    none,
    gzip,
    snappy,
    lz4,
    zstd
}
