package com.homework.asset.ingest.normalizer;

/** ETL 字段归一化异常，用于标记无法处理的脏数据。 */
public class EtlNormalizeException extends RuntimeException {

  public EtlNormalizeException(String message) {
    super(message);
  }

  public EtlNormalizeException(String message, Throwable cause) {
    super(message, cause);
  }
}
