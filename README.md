
# LeakCanary

一个内存泄漏检测库，适用于Android和Java环境

*“A small leak will sink a great ship.”* - Benjamin Franklin

小漏不补沉大船

使用效果如下图：

![demo效果截图.png](https://github.com/JohnsonChan/leakcanary/blob/decode-leakcanary/assets/screenshot.png?raw=true)

## 接入步骤如下：

在你项目对应 `build.gradle`添加:

```gradle
 dependencies {
   debugCompile 'com.squareup.leakcanary:leakcanary-android:1.5.1'
   releaseCompile 'com.squareup.leakcanary:leakcanary-android-no-op:1.5.1'
   testCompile 'com.squareup.leakcanary:leakcanary-android-no-op:1.5.1'
 }
```

在你项目对应 `Application` 添加初始化:

```java
public class ExampleApplication extends Application {

  @Override public void onCreate() {
    super.onCreate();
    if (LeakCanary.isInAnalyzerProcess(this)) {
      // This process is dedicated to LeakCanary for heap analysis.
      // You should not init your app in this process.
      return;
    }
    LeakCanary.install(this);
    // Normal app init code...
  }
}
```

**接入完成!** LeakCanary在发现内存泄漏的时候会弹出通直栏以及创建一个快捷方式，每个泄漏对应一个快捷方式，快捷方式图标图标如下：[有问题请点击](https://github.com/square/leakcanary/wiki/FAQ)!

![内存泄露图标.png](https://github.com/JohnsonChan/leakcanary/blob/decode-leakcanary/assets/icon_512.png?raw=true)

## LeakCanray实现原理

### 基础概念

#### [ActivityLifecycleCallbacks](http://blog.csdn.net/tongcpp/article/details/40344871)
```java
public interface ActivityLifecycleCallbacks {
    void onActivityCreated(Activity activity, Bundle savedInstanceState);
    void onActivityStarted(Activity activity);
    void onActivityResumed(Activity activity);
    void onActivityPaused(Activity activity);
    void onActivityStopped(Activity activity);
    void onActivitySaveInstanceState(Activity activity, Bundle outState);
    void onActivityDestroyed(Activity activity);
}
```
Application通过此接口提供了一套回调方法，用于让开发者对Activity的生命周期事件进行集中处理,可用于替换runningProcess或者runningTasks，判断 app 是否在后台运行
**Android 4.0 (API Level 14) 引入**

#### [Java对象的强、软、弱和虚引用](http://blog.csdn.net/coolwxb/article/details/7939246)

**强引用（StrongReference）**
强引用是使用最普遍的引用。如果一个对象具有强引用，那垃圾回收器绝不会回收它。当内存空间不足，Java虚拟机宁愿抛出OutOfMemoryError错误，使程序异常终止，也不会靠随意回收具有强引用的对象来解决内存不足的问题。

**软引用（SoftReference）**
如果一个对象只具有软引用，则内存空间足够，垃圾回收器就不会回收它；如果内存空间不足了，就会回收这些对象的内存。只要垃圾回收器没有回收它，该对象就可以被程序使用。软引用可用来实现内存敏感的高速缓存（下文给出示例）。软引用可以和一个引用队列（ReferenceQueue）联合使用，如果软引用所引用的对象被垃圾回收器回收，Java虚拟机就会把这个软引用加入到与之关联的引用队列中。

**弱引用（WeakReference）**
弱引用与软引用的区别在于：只具有弱引用的对象拥有更短暂的生命周期。在垃圾回收器线程扫描它所管辖的内存区域的过程中，一旦发现了只具有弱引用的对象，不管当前内存空间足够与否，都会回收它的内存。不过，由于垃圾回收器是一个优先级很低的线程，因此不一定会很快发现那些只具有弱引用的对象。**弱引用可以和一个引用队列（ReferenceQueue）联合使用，如果弱引用所引用的对象被垃圾回收，Java虚拟机就会把这个弱引用加入到与之关联的引用队列中。**

**虚引用（PhantomReference）**
“虚引用”顾名思义，就是形同虚设，与其他几种引用都不同，虚引用并不会决定对象的生命周期。如果一个对象仅持有虚引用，那么它就和没有任何引用一样，在任何时候都可能被垃圾回收器回收。虚引用主要用来跟踪对象被垃圾回收器回收的活动。虚引用与软引用和弱引用的一个区别在于：虚引用必须和引用队列 （ReferenceQueue）联合使用。当垃圾回收器准备回收一个对象时，如果发现它还有虚引用，就会在回收对象的内存之前，把这个虚引用加入到与之 关联的引用队列中。

### 工作机制
#### 在`LeakCannary`检测泄露主要分三步:
**1.关联需要监听的对象** [**leakcanary-android**](https://github.com/JohnsonChan/leakcanary/tree/master/leakcanary-android)

**2.检测监听对象是否存在泄露嫌疑，如果存在嫌疑，就`dump`出内存快照到`*.hprof`文件** [**leakcanary-watcher**](https://github.com/JohnsonChan/leakcanary/tree/master/leakcanary-watcher) 

**3.通过分析`*.hprof`文件确认是否真正泄露，泄露了就保存并展示结果** [**leakcanary-analyzer**](https://github.com/JohnsonChan/leakcanary/tree/master/leakcanary-analyzer)

#### [关联监听对象关键类ActivityRefWatcher](https://github.com/JohnsonChan/leakcanary/blob/decode-leakcanary/leakcanary-android/src/main/java/com/squareup/leakcanary/ActivityRefWatcher.java)
默认情况下，我们监听的是项目里的Activity.
利用ActivityLifecycleCallbacks的监听所有Activity生命周期，在Activity被销毁时进行泄露检测
```java
private final Application.ActivityLifecycleCallbacks lifecycleCallbacks =
      new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            // 最关键的，activity被销毁时，进行检查
          ActivityRefWatcher.this.onActivityDestroyed(activity);
        }
      };
```

我们也可以使用[RefWatcher](https://github.com/JohnsonChan/leakcanary/blob/master/leakcanary-watcher/src/main/java/com/squareup/leakcanary/RefWatcher.java)关联其他任何我们想要监听的对象：
```java
// 使用 RefWatcher 监控 Fragment：
public abstract class BaseFragment extends Fragment {
  @Override
  public void onDestroy() {
    super.onDestroy();
    RefWatcher refWatcher = ExampleApplication.getRefWatcher(getActivity());
    refWatcher.watch(this); // watch传入的是Object对象
  }
}
```

#### [确认是否进行分析，关键类RefWatcher](https://github.com/JohnsonChan/leakcanary/blob/master/leakcanary-watcher/src/main/java/com/squareup/leakcanary/RefWatcher.java)
我们知道**弱引用可以和一个引用队列（ReferenceQueue）联合使用，如果弱引用所引用的对象被垃圾回收，Java虚拟机就会把这个弱引用加入到与之关联的引用队列中。**
LeakCanary利用弱引用这个特性，将要监听的对象和ReferenceQueue队列联合使用，如果对象被垃圾系统回收，就会放到队列里，就可以利用这个队列找出没有被回收的对象，没有被回收的则怀疑可能发生了内存泄露，如下面的`removeWeaklyReachableReferences()`
```java
// 这个方法执行完，retainedKeys列表里就剩下没有被系统回收的
private void removeWeaklyReachableReferences() {
    // WeakReferences are enqueued as soon as the object to which they point to becomes weakly
    // reachable. This is before finalization or garbage collection has actually happened.
    // queue里装的都算被垃圾系统回收的
    KeyedWeakReference ref;
    while ((ref = (KeyedWeakReference) queue.poll()) != null) {
      retainedKeys.remove(ref.key); // 删除已经被系统回收
    }
  }
```

下面看完整的流程，主要在`ensureGone()`里
```java
Retryable.Result ensureGone(final KeyedWeakReference reference, final long watchStartNanoTime) {
    long gcStartNanoTime = System.nanoTime();
    long watchDurationMs = NANOSECONDS.toMillis(gcStartNanoTime - watchStartNanoTime);

    // 1.删除已经被系统回收的对象
    removeWeaklyReachableReferences(); 

    if (debuggerControl.isDebuggerAttached()) {
      // The debugger can create false leaks.
      return RETRY;
    }
    // 2.判断没被回收的列表retainedKeys是否有reference对象
    if (gone(reference)) {
      return DONE;
    }
    // 3.调用gc
    gcTrigger.runGc();
    // 4.再次删除已经被系统回收的对象
    removeWeaklyReachableReferences();
    if (!gone(reference)) {
      // 5.retainedKeys列表里存在reference对象，存在泄漏嫌疑
      long startDumpHeap = System.nanoTime();
      long gcDurationMs = NANOSECONDS.toMillis(startDumpHeap - gcStartNanoTime);
      // 6.dump出内存快照到*.hprof文件
      File heapDumpFile = heapDumper.dumpHeap();
      if (heapDumpFile == RETRY_LATER) {
        // Could not dump the heap.
        return RETRY;
      }
      long heapDumpDurationMs = NANOSECONDS.toMillis(System.nanoTime() - startDumpHeap);
      // 7.触发对.hprof文件进行分析
      heapdumpListener.analyze(
          new HeapDump(heapDumpFile, reference.key, reference.name, excludedRefs, watchDurationMs,
              gcDurationMs, heapDumpDurationMs));
    }
    return DONE;
  }

  private boolean gone(KeyedWeakReference reference) {
    return !retainedKeys.contains(reference.key);
  }
```

### [分析*.hprof文件，找到泄露对象，并展示](https://github.com/JohnsonChan/leakcanary/blob/master/leakcanary-android/src/main/java/com/squareup/leakcanary/internal/HeapAnalyzerService.java)
**ServiceHeapDumpListener**实现了`HeapDump.Listener`接口,当`RefWatcher`发现可疑引用的之后，它将`dump`出来的`*.hprof`文件通过`ServiceHeapDumpListener`传递到`HeapAnalyzerService`

**HeapAnalyzerService**它主要是通过`HeapAnalyzer.checkForLeak`分析对象的引用，计算出到GC root的最短强引用路径。然后将分析结果传递给DisplayLeakService

```java
public AnalysisResult checkForLeak(File heapDumpFile, String referenceKey) {
    long analysisStartNanoTime = System.nanoTime();
    // 判断*.hprof是否存在
    if (!heapDumpFile.exists()) {
      Exception exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
      return failure(exception, since(analysisStartNanoTime));
    }

    try {
      // 利用HAHA（基于MAT的堆栈解析库）将之前dump出来的内存文件解析成Snapshot对象
      HprofBuffer buffer = new MemoryMappedFileBuffer(heapDumpFile);
      HprofParser parser = new HprofParser(buffer);
      Snapshot snapshot = parser.parse(); 
      deduplicateGcRoots(snapshot);

      // 找到泄露对象，LeakCanary通过被泄漏对象的弱引用来在Snapshot中定位它。
      // 如果一个对象被泄漏，一定也可以在内存中找到这个对象的弱引用，再通过弱引用对象的referent就可以直接定位被泄漏对象
      Instance leakingRef = findLeakingReference(referenceKey, snapshot);

      // False alarm, weak reference was cleared in between key check and heap dump.
      if (leakingRef == null) {
        return noLeak(since(analysisStartNanoTime));
      }
      
      // 计算出一条有效的到被泄漏对象的最短的引用
      return findLeakTrace(analysisStartNanoTime, snapshot, leakingRef);
    } catch (Throwable e) {
      return failure(e, since(analysisStartNanoTime));
    }
  }
```

**DisplayLeakService**继承了`AbstractAnalysisResultService`。它主要是用来处理分析结果，将结果写入文件，然后在通知栏报警
```java
// listenerClassName对应的就是DisplayLeakService
AbstractAnalysisResultService.sendResultToListener(this, listenerClassName, heapDump, result);
```

### 下面是以上步骤的时序图

![泄漏监听时序图.jpg](https://github.com/JohnsonChan/leakcanary/blob/decode-leakcanary/assets/%E6%B3%84%E6%BC%8F%E7%9B%91%E5%90%AC%E6%97%B6%E5%BA%8F%E5%9B%BE.jpg?raw=true)

![泄漏处理时序图.jpg](https://github.com/JohnsonChan/leakcanary/blob/decode-leakcanary/assets/%E6%B3%84%E6%BC%8F%E5%A4%84%E7%90%86%E6%97%B6%E5%BA%8F%E5%9B%BE.jpg?raw=true)

### 其他说明
`LeakCanary`提供了`ExcludedRefs`来灵活控制是否需要将一些对象排除在考虑之外，因为在`Android Framework`,手机厂商rom自身也存在一些内存泄漏，对于开发者来说这些泄漏是我们无能为力的，所以在`AndroidExcludedRefs`中定义了很多排除考虑的类


风格：

1、所有接口内部都有个一个默认的实现

2、final类

3、没有util,Preconditions,AndroidDebuggerControl

4、import方式import static com.squareup.leakcanary.AnalysisResult.leakDetected

### 参考文章
http://vjson.com/wordpress/leakcanary%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90%E7%AC%AC%E4%B8%80%E8%AE%B2.html

http://www.jianshu.com/p/5032c52c6b0a

http://coolpers.github.io/leakcanary%7Cmat/2015/06/04/LeakCanary-Brief.html

http://www.jianshu.com/p/481775d198f0

https://www.liaohuqiu.net/cn/posts/leak-canary-read-me/
