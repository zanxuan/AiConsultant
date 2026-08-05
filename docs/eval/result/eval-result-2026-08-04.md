# RAG Evaluation Result

## Summary

Total Cases: 50

Hit Rate@5: 92%

Recall@5: 92%

MRR: 0.84

---

# Failed Cases

## Case 1

Case Id: 16

### Query

服务器异常关闭后，之前的数据如何恢复？

### Expected Documents

- redis持久化.pdf

### Retrieved Documents

1. 缓存和数据库的一致性.pdf
2. 日志.pdf
3. redis高可用.pdf
4. 消息可靠性与消息消费失败重试.pdf
5. 缓存击穿穿透雪崩.pdf

### Top Score

0.02

---

## Case 2

Case Id: 29

### Query

如何在不改业务代码的情况下统一加日志或权限校验？

### Expected Documents

- AOP.pdf

### Retrieved Documents

1. 日志.pdf
2. 缓存和数据库的一致性.pdf
3. 消息幂等性.pdf
4. 缓存击穿穿透雪崩.pdf
5. 消息可靠性与消息消费失败重试.pdf

### Top Score

0.02

---

## Case 3

Case Id: 34

### Query

如何避免线程之间互相污染对方的用户上下文数据？

### Expected Documents

- ThreadLocal.pdf

### Retrieved Documents

1. 缓存和数据库的一致性.pdf
2. 线程池.pdf
3. 消息的顺序性.pdf

### Top Score

0.02

---

## Case 4

Case Id: 37

### Query

如何同时保证缓存不丢数据和数据库事务不丢已提交结果？

### Expected Documents

- redis持久化.pdf
- 日志.pdf

### Retrieved Documents

1. 缓存和数据库的一致性.pdf

### Top Score

0.02

---

