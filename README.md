# 投注额度服务 (Betting Stake Service)

## 概述

这是一个轻量级HTTP服务，用于管理投注额度，支持会话验证、高并发处理和安全操作。服务提供以下3个核心接口：
- 为客户生成并管理10分钟有效期的会话（使用该会话不会延长有效期）
- 接收并存储某个投注项下客户投注额度
- 查询特定投注项的前20名最高额度

本服务基于Java原生的`com.sun.net.httpserver.HttpServer`构建，不依赖任何外部框架，确保最小化依赖和高效的资源使用。


## 设计思路

### 基础架构
- 基于 Java 原生 HTTP 服务器（无外部框架）
- 配置固定线程池（CPU 核心数 ×2）处理并发请求，平衡性能与资源占用。
### 会话管理
- 会话存储包含其过期时间
- 每个客户拥有唯一会话，有效期为10分钟，会话密钥使用加密安全的随机生成器`java.security.SecureRandom`
- 用双重 ConcurrentHashMap 存储客户与 Session 的双向映射
- 定时任务每分钟清理过期 Session，避免内存泄漏

### 投注处理
- 提交投注时验证 Session 有效性，防止未授权操作
- 用嵌套的 ConcurrentHashMap 存储数据（投注项 ID→{客户 ID→最高额度}）
- 原子更新（`computeIfAbsent`、`compute`）确保只保留最高额度

### 最高下注额度列表查询
- 对指定投注项的所有客户额度按降序排序。截取前 20 条结果返回，保证查询效率

### 资源管理
- JVM关闭钩子用于优雅清理资源
- 正确关闭HTTP服务器和执行器服务

## 非功能性需求实现思路

### 高并发处理

1. **线程安全的数据结构**
    - 所有共享数据存储均使用`ConcurrentHashMap`，对值的修改均使用原子性的compute()或computeIfAbsent()
    - 支持原子操作，无需显式同步
    - 防止多线程访问/修改数据时出现竞态条件
    - 所有关键操作使用原子方法（`computeIfAbsent`、`compute`），最小化锁竞争和阻塞时间

2. **高效的线程管理**
    - 固定大小的线程池（大小 = CPU核心数 × 2）
    - 在并发性能和资源消耗之间取得平衡，避免高负载下的线程爆炸问题


### 安全性设计

1. **会话管理**
    - 10分钟会话过期机制(无续期)防止永久访问
    - 使用`java.security.SecureRandom`生成安全的随机会话密钥
    - 自动清理过期会话，防止内存泄漏
    - 所有投注提交都需要会话验证

2. **输入验证**
    - 对所有输入参数进行严格验证
    - 防止无效值（负额度、溢出数值、非数字标识符等）





## API使用说明

### 1. 获取/创建会话
- **接口地址**: `GET /{customerId}/session`
- **描述**: 获取客户的现有有效会话，若不存在则创建新会话
- **参数**:
    - `customerId`: 客户ID（非负整型）
- **响应**:
    - 200 OK: 8位字母数字组合的会话id
    - 400 Bad Request: 无效的客户ID格式
    - 404 Not Found: customerId错误时，会导致无法匹配到现有api

**示例**:
```bash
curl http://localhost:8001/1001/session
# 返回: AB34CD56
```

### 2. 提交投注额度
- **接口地址**: `POST /{betOfferId}/stake?sessionkey={sessionKey}`
- **描述**: 为特定投注项提交额度（同一客户保留最高额度）
- **参数**:
    - `betOfferId`: 投注项ID（非负整型）
    - `sessionkey`: 有效的会话id（字符串）
    - 请求体: 数字型额度值（正整数）
- **响应**:
    - 204 No Content: 成功下注，无返回值
    - 400 Bad Request: 无效的额度值或参数
    - 401 Unauthorized: 会话无效或已过期

**示例**:
```bash
curl -X POST -d "1500" http://localhost:8001/5001/stake?sessionkey=AB34CD56
```

### 3. 查询最高20个下注额度列表
- **接口地址**: `GET /{betOfferId}/highstakes`
- **描述**: 获取特定投注项的前20名最高额度，按降序排列
- **参数**:
    - `betOfferId`: 数字型投注项ID（路径参数）
- **响应**:
    - 200 OK: 逗号分隔的"客户ID=额度"键值对列表
    - 400 Bad Request: 无效的投注项ID格式

**示例**:
```bash
curl http://localhost:8001/5001/highstakes
# 返回: 1001=2000,1002=1800,1003=1500
```

## 部署说明

### 前置条件
- JDK 17或更高版本

### 构建与运行
   运行服务：
   ```bash
   java -jar target/betting-stake-service.jar
   ```
   服务将在8001端口启动

## 注意事项

- 所有数据存储在内存中，服务重启后数据将丢失
- 会话过期时间固定为10分钟
- 任何投注项最多返回前20名最高额度

## 错误处理

| 状态码 | 描述 |
|--------|------|
| 400 Bad Request | 无效的输入参数或畸形请求 |
| 401 Unauthorized | 会话密钥无效或已过期 |
| 404 Not Found | 请求的接口不存在 |
| 500 Internal Server Error | 服务器意外错误 |

