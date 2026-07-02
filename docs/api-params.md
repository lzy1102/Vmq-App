# Vmq-App 内部请求参数文档

## 概述

本文档描述手机监控端（Vmq-App）与 PC 服务端之间的通信接口参数。

**基础地址**：`http://{host}`（host 由用户配置，格式为 `IP:端口`）

**请求方式**：GET

---

## 1. 心跳接口

### 接口地址

```
GET http://{host}/appHeart?t={timestamp}&sign={sign}
```

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `t` | String | 是 | 当前时间戳（毫秒），如 `1719734400000` |
| `sign` | String | 是 | 签名，计算方式见下方 |

### 签名算法

```
sign = MD5(t + key)
```

- `t`：时间戳字符串
- `key`：通讯密钥（用户配置）
- `MD5`：标准 MD5 哈希，32位小写十六进制

### 示例

假设：
- host = `38.165.23.28:80`
- key = `0e65796f0b035c9a1fc7f86179da0c5b`
- t = `1719734400000`

则：
```
sign = MD5("1719734400000" + "0e65796f0b035c9a1fc7f86179da0c5b")
```

完整 URL：
```
http://38.165.23.28:80/appHeart?t=1719734400000&sign=xxxxxxxx
```

### 响应格式

```json
{
  "code": 1,
  "msg": "成功"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 状态码，1=成功 |
| `msg` | String | 状态消息 |

### 调用频率

每 **50 秒** 发送一次（在 `PayNotificationListenerService` 心跳线程中）

---

## 2. 收款推送接口

### 接口地址

```
GET http://{host}/appPush?t={timestamp}&type={type}&price={price}&sign={sign}
```

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `t` | String | 是 | 当前时间戳（毫秒），如 `1719734400000` |
| `type` | int | 是 | 支付类型：`1`=微信，`2`=支付宝 |
| `price` | String | 是 | 收款金额，保留两位小数，如 `10.00` |
| `sign` | String | 是 | 签名，计算方式见下方 |

### 签名算法

```
sign = MD5(type + price + t + key)
```

- `type`：支付类型（字符串形式）
- `price`：金额字符串
- `t`：时间戳字符串
- `key`：通讯密钥
- `MD5`：标准 MD5 哈希，32位小写十六进制

### 示例

假设：
- host = `38.165.23.28:80`
- key = `0e65796f0b035c9a1fc7f86179da0c5b`
- type = `1`（微信）
- price = `10.00`
- t = `1719734400000`

则：
```
sign = MD5("1" + "10.00" + "1719734400000" + "0e65796f0b035c9a1fc7f86179da0c5b")
```

完整 URL：
```
http://38.165.23.28:80/appPush?t=1719734400000&type=1&price=10.00&sign=xxxxxxxx
```

### 响应格式

```json
{
  "code": 1,
  "msg": "成功"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 状态码，1=成功 |
| `msg` | String | 状态消息 |

### 调用时机

当 App 监听到微信或支付宝的收款通知，解析出金额后立即调用。

### 重试机制

- 如果请求失败或 JSON 解析失败，**1 秒后自动重试一次**
- 重试时签名中的时间戳会更新

---

## 3. 参数说明

### type 支付类型

| 值 | 说明 | 来源 |
|----|------|------|
| `1` | 微信支付 | 包名 `com.tencent.mm` |
| `2` | 支付宝 | 包名 `com.eg.android.AlipayGphone` |

### price 金额格式

- 必须保留两位小数，如 `10.00`、`0.01`、`999999.99`
- 代码使用 `String.format("%.2f", price)` 格式化
- 金额范围：0.01 ~ 999999.99

### sign 签名

- 使用标准 MD5 算法
- 输出 32 位小写十六进制字符串
- 示例：`md5("110.001719734400000abc123")` → `a3f2b8c9d4e5f6a7b8c9d0e1f2a3b4c5`

---

## 4. 监听的消息来源

### 微信

监听以下标题的通知：
- 微信支付
- 微信收款助手
- 微信收款商业版
- 对外收款
- 企业微信
- Weixin Cashier Assistant

### 支付宝

监听以下类型的通知：
- 普通收款：标题包含「成功收款」且内容包含「已转入余额」
- 店员通：标题包含「店员通」或内容包含「支付宝成功收款」
- 扫码付款：内容包含「通过扫码向你付款」

---

## 5. 错误处理

| 场景 | 处理方式 |
|------|----------|
| 网络请求失败 | 1 秒后自动重试 |
| JSON 解析失败 | 1 秒后自动重试 |
| 金额解析失败 | Toast 提示「监听到xx收款消息但未匹配到金额」 |
| 签名验证失败 | 服务端返回 code != 1 |

---

## 6. 完整流程

```
1. App 启动 → 授权通知监听权限
2. 用户配置 host 和 key
3. NotificationListenerService 连接成功
4. 启动心跳线程（每50秒）
5. 收到通知 → 判断包名 → 解析金额 → 调用 /appPush
6. 服务端匹配订单 → 回调通知
```

---

## 文件位置

- 心跳逻辑：`app/src/main/java/com/shinian/pay/service/PayNotificationListenerService.java`
- 推送逻辑：`app/src/main/java/com/shinian/pay/service/PayNotificationListenerService.java`
- 配置存储：`SharedPreferences("shinian")`，key 为 `host` 和 `key`
