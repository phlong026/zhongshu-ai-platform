# 2026-10-11 恢复后验收记录

本记录只收录当前重建源码的新测试。源码基线为 `000e1dfb77d0b46f58a4a8993c4e4011a90a699d`；执行环境切换前结果仅在整改台账中作为历史记录保留。实际业务决策仍以主台账为准。

## 已核对的前端与治理结果

- Mini认证：`node --test scripts/test-miniapp-auth.mjs`，19项通过；直接测试实际源文件及模拟框架调用，不是微信真机端到端测试。
- Web登录辅助：`node --test scripts/test-admin-login.test.mjs`，5项通过；覆盖重入、防重复、成功/失败清理，不证明页面已打开。
- 来源校验器：`node --test scripts/verify-source-copy.test.mjs`，13项通过；历史迁入清单保持不变，产品改动由Git跟踪。
- 文档治理：`node --test scripts/verify-foundation.test.mjs`，12项通过；`node scripts/verify-foundation.mjs`通过，含版本/决策/链接/编号/证据/默认模块白名单及234供体文件。
- 以上独立聚合重跑49项全部通过。
- Web：进入 `apps/zhongshu-admin-web`，官方 registry 的 `pnpm install --frozen-lockfile --ignore-scripts` 成功；1033包，1141锁项供应链检查通过。159个旧tarball URL规范化后版本及integrity保持一致，官方元数据逐项一致；没有证据将旧镜像称为污染。
- `pnpm run ts:check` 全量通过；`pnpm run build:prod` 通过，8640模块、31.48秒。两份类型声明由真实构建生成，未用类型断言或放宽tsconfig掩盖基线错误。
- 改动源文件ESLint/Prettier、实际Vite五mode配置检查及 `git diff --check` 通过。

远程已核验的前端tree为 `bd8c00561bb93e85e487bd0ce80be8565f983a55`，整改分支提交 `a53f174750d0102b728ad13a2ad7789c17dc5c71`。后续后端提交按独立记录验收。

## 后端分阶段入口

正式可复现说明位于 [PostgreSQL产品基线说明](../../services/zhongshu-core/sql/postgresql/README-product.md)。需要JDK17、Maven、独立空的PostgreSQL17测试库和分离的迁移/运行角色，不能连接生产。

1. PG-only阶段不启动真实Redis，运行 `mvn -Ppostgres-it -pl yudao-server -am verify`。真实PG测试需要显式TEST_PG变量，不可用H2代替。
2. API阶段再启动独立带密码的loopback Redis（本次26379），保留现有单测jedismock的16379。按照产品基线说明执行 `bash script/postgresql/run-api-smoke.sh`。
3. 本地云容器若需要显式Mockito agent，先按基线说明解析同固定版本官方依赖，不依赖旧环境缓存；skipTests package只能表示编译/打包准备，不能充当测试通过。
4. 工具链来源及校验见 [新环境工具链清单](toolchain-manifest-20261011.json)。该清单的工具启动smoke不代表产品测试通过。

本次新结果：JDK17完整Maven verify成功（21模块），Surefire848项：830执行通过、18既有跳过、0失败/错误；Failsafe16项真实PostgreSQL17.11全部通过。总计846实际执行通过。日志安全16项、技术tenant/部门/私有文件42项、安全配置4项、迁移契约1项包含在全量内，不重复累加。测试jar SHA256为 `e174ddcc76a0a93d51bf723154077313b01487239e8ee7b7017096b82b31b3fe`。

真实HTTP API已通过两技术租户登录、6导航+1运维测试权限按钮、范围分页、伪造tenant、A授权visit阳性后仍拒绝私有附件、B未授权visit拒绝、无业务authorizer拒绝上传/下载、公开素材匿名字节核验与私有UUID无公共绕过、token刷新及退出失效。运维visit仅为隔离测试角色，不是总部业务授权模型。

新增Node日志监督器8项及seed/target guards3项共11项通过，覆盖正常停止后的末尾日志泄露、压缩分片、缺失/链接文件、意外退出及超时失败等。正式API runner启动自己的Java子进程，完成API后等待graceful close，再扫描每轮独立目录中的stdout/rolling/全部同前缀分片；敏感值只在内存保留。最终实际运行exit0：完成所有API断言后观察到Java正常SIGTERM退出及close，本轮所有日志及分片中均未发现捕获的测试密码、accessToken或refreshToken。该结论仅覆盖本轮执行路径，不等于任意业务未来均不会泄露日志。

## 浏览器与Mini完整构建尚未完成

- 真实浏览器被环境socket权限/网络隔离阻碍，已经停止该路径，没有关闭sandbox或修改网络安全配置。
- `scripts/test-admin-ui.mjs` 是可复现的真实浏览器验收脚本，包含表单拒绝、登录失败重试、真实登录、刷新和退出/取消退出等。仅允许隔离loopback环境与一次性账号；尚未执行，不能提供“已进入后台”的截图。
- Mini完整依赖安装曾被取消，没有重试，类型及H5/微信构建未验收。CI只保留明确手动选择的可选门禁，默认不运行，不能通过PR间接重试已取消动作。
- 根CI仅PR和手动触发，不随每个检查点push执行。CI定义检查不等于远程CI通过；当前未开PR、未合并、未部署。
