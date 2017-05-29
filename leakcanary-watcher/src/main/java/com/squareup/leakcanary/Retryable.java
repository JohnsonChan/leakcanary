package com.squareup.leakcanary;

/** A unit of work that can be retried later. */
// 类似Runable,有返回结果
public interface Retryable {

  enum Result {
    DONE, RETRY
  }

  Result run();
}
