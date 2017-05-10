# ipsc-ccf-java

IPSC 通用CTI流程(Common Cti Flow，简称 CCF)的 Java SDK。

通过这个SDK，开发者使用 Java 程序“遥控”使用 IPSC 的通用 CTI 流程(CCF，需要配合CCF流程定义，一同使用)，如呼叫、电话会议等。

## 编译
首先安装 maven (在编译这个包之前，需要首先生成 `ipsc-bus-client-java` 项目的 jar 包)，然后：

### 1. 安装依赖包
```
$ mvn install
```

### 2. 生成 jar 包
```
$ mvn package
```
