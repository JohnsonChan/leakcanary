#demo工程
从gradle配置：
 debugCompile project(':leakcanary-android')
 releaseCompile project(':leakcanary-android-no-op');
可以知道debug环境引用leakcanary-android工程
release环境引用leakcanary-android-no-op工程

debug和release的区别：
1）release环境isInAnalyzerProcess直接返回false,不会启动检测进程

