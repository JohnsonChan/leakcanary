package com.squareup.leakcanary;

import java.io.Serializable;

// 被排除的数据结构，包括名称，理由等
public final class Exclusion implements Serializable {
  public final String name;
  public final String reason;
  public final boolean alwaysExclude;
  public final String matching;

  Exclusion(ExcludedRefs.ParamsBuilder builder) {
    this.name = builder.name;
    this.reason = builder.reason;
    this.alwaysExclude = builder.alwaysExclude;
    this.matching = builder.matching;
  }
}
