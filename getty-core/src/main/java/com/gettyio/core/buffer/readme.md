# Getty Buffer 模块

## 一、模块总览

Buffer 模块是 Getty 框架的内存管理核心，负责缓冲区的分配、回收与内存池化。整体设计借鉴 Netty 并针对 AIO/NIO 双模型做了深度优化，追求**极致性能**与**零拷贝**。

```
buffer/
├── pool/                        # 内存池（三级架构）
│   ├── ByteBufferPool.java      # 池接口
│   ├── GettyByteBufferPool.java # 池默认实现
│   ├── PoolArena.java           # L2 - 区域管理器
│   ├── PoolChunk.java           # L3 - 二叉树内存块
│   ├── PoolThreadCache.java     # L1 - 线程本地缓存
│   ├── PooledByteBuffer.java    # 池化缓冲区（引用计数）
│   ├── RetainableByteBuffer.java# 缓冲区基类（双指针模型）
│   └── BufferUtil.java          # 底层工具方法
└── AutoByteBuffer.java          # 自动扩容缓冲区（byte[]）
```

---

## 二、核心架构

### 2.1 三级内存池架构

内存池采用经典的三级架构，核心目标是**极致性能**——通过线程本地缓存避免锁竞争，结合高效的二叉树切片算法减少内存碎片。

```
┌─────────────────────────────────────────────────────────┐
│              L1: PoolThreadCache                         │
│  线程私有 · 无锁 · ArrayDeque 栈缓存                     │
│  Tiny(≤496B): 256个  Small: 128个  Normal: 64个  Large: 16个 │
│  命中 → 零延迟返回（~0ns）                                │
│  未命中 ↓                                                 │
├─────────────────────────────────────────────────────────┤
│              L2: PoolArena                               │
│  区域管理器 · ReentrantLock 保护 Chunk 列表               │
│  Size Class 索引表: O(1) 大小分类                         │
│  Chunk 按使用率排序，优先选低使用率 Chunk                   │
│  缓存未命中 ↓                                             │
├─────────────────────────────────────────────────────────┤
│              L3: PoolChunk                               │
│  完全二叉树管理大块内存（默认 16MB）                       │
│  整页分配: O(log N) 二叉树搜索                            │
│  子页分配: 位图（bitmap）管理页内 slot                     │
│  支持堆内（byte[]）和堆外（DirectByteBuffer）              │
└─────────────────────────────────────────────────────────┘
```

**Size Class 体系：**

| 级别 | 大小范围 | 步长 | 说明 |
|------|---------|------|------|
| Tiny | 16 ~ 496 B | 16 B | 高频小缓冲区，31 个 class |
| Small | 512 B ~ 4 KB | 2 的幂 | 中等消息 |
| Normal | 8 KB ~ chunkSize/2 | 2 的幂 | 大消息 |
| Huge | > chunkSize/2 | — | 直接分配，不池化 |

### 2.2 缓冲区继承体系

```
RetainableByteBuffer          ← 基类：双指针模型 + 基础读写 API
    └── PooledByteBuffer      ← 池化实现：引用计数 + 自动归还
```

- **RetainableByteBuffer**：提供独立于 `ByteBuffer` 的 `readerIndex` / `writerIndex` 双指针模型，支持零拷贝切片（`slice()`）、深拷贝（`copy()`）、I/O 兼容翻转（`flipToFill()` / `flipToFlush()`）
- **PooledByteBuffer**：继承基类，增加引用计数（`retain()` / `release()`），引用计数归零时自动归还给线程缓存或 Arena

### 2.3 双指针模型

```
  +-------------------+------------------+------------------+
  | 0 <= readerIndex  | readerIndex <=   | writerIndex <=   |
  |                   | writerIndex      | capacity         |
  +-------------------+------------------+------------------+
  |   已读（discard）  |   可读数据        |   可写空间        |
  +-------------------+------------------+------------------+
```

独立读写指针避免了 `ByteBuffer` 的 `flip()` 操作，减少状态切换开销。

### 2.4 跨线程回收机制

```
业务线程 A（非 owner）                    IO 线程 B（owner）
    │                                       │
    │  release()                            │
    ├─→ crossThreadRecycle()                │
    │   └→ MPSC 无锁队列入队（~5ns CAS）     │
    │                                       │
    │                     allocate() 时     │
    │                       ↓               │
    │                  drain MPSC 队列       │
    │                  批量归还到本地缓存     │
```

- **同线程释放**：直接推入 `ArrayDeque`（无锁，~0ns）
- **跨线程释放**：通过 `MpscRecycleQueue` 无锁传递，owner 线程下次分配前批量 drain（零对象分配）

---

## 三、核心优势

### 3.1 极致性能

| 特性 | 说明 |
|------|------|
| **线程本地缓存** | Tiny 级缓存 256 个缓冲区，同线程分配/释放零锁竞争 |
| **无锁 MPSC 回收** | 跨线程回收通过 CAS 入队，~5ns 延迟，零临时对象分配 |
| **二叉树 O(log N) 分配** | PoolChunk 使用完全二叉树搜索，分配/释放高效 |
| **位图子页管理** | 小于 pageSize 的分配使用 bitmap 管理 slot，空间利用率高 |

### 3.2 零拷贝设计

- **slice()**：零拷贝切片，子缓冲区与父缓冲区共享底层数据
- **asByteBuffer()**：零拷贝视图，堆内存直接 `wrap` 底层数组，无数据拷贝
- **readArray()**：原子化获取底层数组并消费数据，避免临时 `byte[]` 分配

### 3.3 内存安全

- **引用计数**：`retain()` / `release()` 精确管理缓冲区生命周期
- **自动归还**：引用计数归零自动归还线程缓存或 Arena，无内存泄漏
- **双重检查**：重复 `release()` 或操作已释放缓冲区立即抛出异常，快速暴露 bug
- **LRU 淘汰**：`lastUpdateTime` 时间戳支持过期缓冲区淘汰

### 3.4 灵活的内存类型

- 支持**堆内内存**（`byte[]`）和**堆外直接内存**（`DirectByteBuffer`）
- 堆外内存减少 JVM 堆压力，适用于 I/O 密集型场景
- 同一套 API 无缝切换

### 3.5 自动扩容缓冲区

`AutoByteBuffer` 提供基于 `byte[]` 的自动扩容缓冲区：
- 指数增长策略（倍增），均摊 O(1) 追加
- 适用于长度未知的数据组装场景（如编解码器）
- 与池化缓冲区互补：池化缓冲区用于已知大小的 I/O 路径，AutoByteBuffer 用于动态组装

---

## 四、使用示例

### 4.1 从内存池分配缓冲区

```java
// 获取池实例
ByteBufferPool pool = new GettyByteBufferPool(new PoolArena(false), null);

// 分配 256 字节的堆内缓冲区
PooledByteBuffer buf = pool.acquire(256);

// 写入数据
buf.writeInt(42);
buf.writeBytes(new byte[]{1, 2, 3});

// 读取数据
int value = buf.readInt();    // 42

// 释放（归还给池）
buf.release();
```

### 4.2 共享缓冲区（引用计数）

```java
PooledByteBuffer buf = pool.acquire(128);
buf.writeBytes(data);

// 共享给另一个组件
buf.retain();       // refCount = 2
otherComponent.process(buf);

// 本组件用完释放
buf.release();      // refCount = 1，其他组件仍可使用

// 其他组件用完释放
// refCount = 0，自动归还池
```

### 4.3 零拷贝切片

```java
PooledByteBuffer buf = pool.acquire(1024);
buf.writeBytes(header);
buf.writeBytes(body);

// 创建零拷贝切片（共享底层数据）
RetainableByteBuffer headerSlice = buf.slice();  // [readerIndex, writerIndex)
```

### 4.4 使用自动扩容缓冲区

```java
AutoByteBuffer dynBuf = AutoByteBuffer.newByteBuffer();
dynBuf.writeBytes(chunk1);
dynBuf.writeBytes(chunk2);  // 自动扩容
byte[] result = dynBuf.allWriteBytesArray();
```
