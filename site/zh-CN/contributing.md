---
title: 贡献指南
---


感谢你有意参与贡献！本文档涵盖了一些基本内容。

## 开发环境配置

1. **前置依赖**：Java 17+、Maven 3.6+、Redis（用于集成测试）
2. **克隆代码**：`git clone https://github.com/your-org/retask4j.git`
3. **编译**：`mvn clean package -DskipTests`
4. **运行测试**：
   - 单元测试：`mvn test -pl retask4j-core,retask4j-http`
   - 端到端测试（需要 Redis）：`mvn test -pl retask4j-core -Dtest=EndToEnd* -Dredis.host=localhost`

## 项目结构

- `retask4j-core/` — 基于 Redisson 的纯队列引擎（无 Spring 依赖）
- `retask4j-http/` — HTTP 代理层（Caller + Worker 两侧）
- `retask4j-http-server/` — 可运行的 Demo 服务器
- `retask4j-demo-taskcaller/` / `retask4j-demo-taskworker/` — 独立的 Demo
- `documents/` — 所有文档

## 编码规范

- 推荐使用 Java 17 特性（records、sealed 类型、适当场景的模式匹配）
- 使用 Lombok（`@Getter`、`@Setter`、`@Slf4j`）减少样板代码
- 测试使用 JUnit 5 + Mockito；集成测试使用 Redisson 连接真实 Redis
- 注释使用英文
- 公共 API：在 setter 中校验入参，抛出带有描述性信息的 `IllegalArgumentException`
- Redis 键名必须安全（不允许 `:`, `{`, `}`, 控制字符）；请使用 `FuTaskMessage` 的 setter，会强制执行该约束

## Pull Request 流程

1. **从 `main` 拉分支**：`git checkout -b feature/your-feature`
2. **编写测试**覆盖任何新行为（至少单元测试）
3. **推送前运行全部测试**
4. **更新文档**：如果你修改了公共 API，请同步更新 `documents/` 下的文档
5. **保持 PR 聚焦** — 一个 PR 只做一项功能/修复
6. **撰写清晰的 PR 描述**，解释为什么这样做，而不只是做了什么

## 报告问题

报告 Bug 时，请附上：
- retask4j 版本（commit hash 或 release tag）
- Java 版本（`java -version`）
- Redisson 版本（来自你的 pom）
- 最小复现样例（配置 + 代码）
- 期望行为 vs 实际行为
- 必要时附上堆栈信息

## 欢迎参与的领域

- Bug 报告与修复
- 文档改进
- 性能基准测试
- 多语言绑定（Python、Go、Node 等）
- 更多端到端集成测试
- Web 仪表板增强
