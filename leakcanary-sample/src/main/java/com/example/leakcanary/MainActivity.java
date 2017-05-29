/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.leakcanary;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

public class MainActivity extends Activity {
  int i = 1;
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main_activity);

    View button = findViewById(R.id.async_task);
    button.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        startAsyncTask();
      }
    });
  }

  void startAsyncTask() {
    // This async task is an anonymous class and therefore has a hidden reference to the outer
    // class MainActivity. If the activity gets destroyed before the task finishes (e.g. rotation),
    // the activity instance will leak.
    // 匿名类，匿名类的声明是在编译时进行的，实例化在运行时进行
    // 匿名类可被视为非静态的内部类，所以它们具有和方法内部声明的非静态内部类一样的权限和限制
    // AsyncTask匿名类能引用到MainActivity到i，说明持有了MainActivity的实例，如果MainActivity destroy时
    // 异步任务还引用到它，就释放不了，于是就内存泄漏了。
    new AsyncTask<Void, Void, Void>() {
      @Override protected Void doInBackground(Void... params) {
        // Do some slow work in background
        System.out.println("czs=======" + i); // 能访问到i，说明能引用到MainActivity到实例
        SystemClock.sleep(20000);
        return null;
      }
    }.execute();
  }
}


