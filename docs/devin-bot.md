# Devin Bot：Compose 截屏回归与 layout 自动修复分工

一句话：Devin 只做**可判定**的工作——按固定矩阵录/验截屏、把差异归类、在白名单内修 layout；设计判断和基线批准留给人。
Roborazzi 已接入：三张 P0 基线（今日页浅色/深色、睡眠页浅色）在 `app/src/test/screenshots/`，CI 在 push/PR 时跑 `verifyRoborazziDebug` 校验。落地进度见最后一节。

## 1. 工具选型（2026-11 的现状）

| 方案 | 渲染引擎 | 在本仓库的可用性 |
| --- | --- | --- |
| Compose Preview Screenshot Testing（Google 官方） | Layoutlib | 仍是 alpha（0.0.1-alpha16）；官方要求 AGP 9.0+、Kotlin 2.2.10+、JDK 17，独立插件已弃用并改由 AGP 9.5+ 的 test suites 配置。**本仓库是 AGP 8.7.3 + Kotlin 2.0.21，现在用不了**，要先升构建链。 |
| Paparazzi（Cash App） | Layoutlib | 2.0 线仍是 alpha（2.0.0-alpha05.1，2026-09-28）；1.3.x 是 AGP 8.x 的稳定线，但与 Robolectric 不兼容，后续要写交互型截屏就卡住。 |
| Roborazzi | Robolectric Native Graphics | 跑在 JVM 上，直接渲染 Composable，`record` / `compare` / `verify` 三个任务语义清楚，CI 里不需要模拟器。**选它**，实际接入 1.43.1 + Robolectric 4.18.1。 |

- 仓库目前没有 `@Preview`，Roborazzi 不依赖 preview：测试里直接调用 `TideScene`、`TodayTab` 等 Composable，状态由参数给。
- 版本固定为 1.43.1：1.79.0 用 Kotlin 2.3 编译，元数据版本 2.3.0，本仓库的 Kotlin 2.0.21 只能读到 2.1.0，编译直接失败。升 Kotlin 之前不要升 Roborazzi。
- 迁移路径：等构建链升到 AGP 9.5+ 再评估官方工具；那时只需换录制/校验命令，矩阵和基线评审规则不变。不现在做。
- 依据：Android Developers 截屏测试总览（2026-10-08）、Compose Preview Screenshot Testing release notes（2026-09-14）、Roborazzi 文档（2026-10-22）。

## 2. 角色与权限

| 角色 | 做 | 不做 |
| --- | --- | --- |
| 所有者（人） | 定设计判断；批准新基线；合并 PR；处理升级项 | 不逐张核对日常 PR 的像素 |
| 实现代理（Claude/Codex） | 接工具链、写测试骨架、改 CI、修构建问题 | 不替所有者拍板「用户确认」类规则 |
| **Devin Bot** | 按矩阵录制/校验截屏；把 diff 归类；白名单内的 layout 修复；写结构化报告 | 不改 `DESIGN.md` 决策；不改基线换绿灯；不碰 `data/`、`sync/`、`auth/`；不加依赖 |
| CI（GitHub Actions） | 跑 `./gradlew test verifyRoborazziDebug`，产出 diff 报告和 artifact | 不做判断，只给证据 |

## 3. Devin 的四类任务

### A. 基线录制（一次性，后续按矩阵扩充）

- 触发：所有者开 issue，标签 `screenshot-baseline`，正文写范围（哪些屏、哪些状态）。
- 输入：`DESIGN.md`、第 4 节的矩阵、第 5 节的确定性约定。
- 产物：一个 PR，只含测试代码 + `app/src/screenshots/**` 的基线 PNG，不含业务代码改动。
- 命令：`./gradlew recordRoborazziDebug`。
- 验收：所有者逐屏看基线图，确认后合并。基线图是评审对象，不是生成物。

### B. 视觉回归判定（每次 UI PR）

- 触发：CI 里 `verifyRoborazziDebug` 失败后，PR 评论 `@devin visuals`（人或 CI 机器人发）。
- 产物：PR 评论，逐项列出：
  1. 屏 / 状态 / 变体；
  2. 变化像素占比与阈值；
  3. 归类：**预期改动**（PR 描述里有对应说明）/ **回归**（白名单可修）/ **无法归因**；
  4. 对应 `DESIGN.md` 条目（有就引，没有写「无」）；
  5. `*_compare.png` 与 `build/reports/roborazzi/index.html` 的 artifact 链接。
- 不做：不改代码，不更新基线。判定本身是交付物。

### C. 白名单内的 layout 自动修复

- 触发：B 的结论是「回归」，且命中第 7 节白名单。由所有者回 `@devin fix` 后开始（默认不自动修）。
- 产物：同一个 PR 上的追加提交，或新 PR（跨多个屏时），附带 before/after 图。
- 上限：每个 PR 最多 2 轮修复；同一屏连续 3 次改不好就停下汇报，不再叠补丁。
- 命令：`./gradlew test verifyRoborazziDebug` 必须通过；除被修的屏外，其他屏的基线一个像素都不变。

### D. 周期报告（可选）

- 每周一次：失败次数、误报率、Devin 修好的项、需要人决定的项。用来决定是否放开 C 类自动执行。

## 4. 截屏矩阵

| 级别 | 屏 | 状态 |
| --- | --- | --- |
| P0 | `TodayTab`、`SleepTab`、`ExploreTab`、`ProfileTab` | 有数据 / 空数据 / 基线积累中；浅色 + 深色 |
| P0 | `NightPage`、`SkyPage`、`ForecastPage`、`DetailScreen` | 有数据 / 空；浅色 + 深色 |
| P1 | `LabPage`、`MomentsPage`、`MomentPage`、`PetPage`、`ArtPage`、`BreathPage` | 主状态；浅色 + 深色 |
| 全屏状态 | `TideLoader`（加载）、错误、登录 | 浅色（深色见 `DESIGN.md`「待确认」） |

- 变体：fontScale 1.0 / 1.3 / 2.0；宽度 360dp / 412dp；`TodayTab` 加四季 + 南半球。
- PR 上跑：P0 × 浅/深 × fontScale 1.0/1.3，控制在 10 分钟内。
- 每晚跑：全矩阵（P0+P1 × 全部变体），失败开 issue，不阻塞其他人。

## 5. 确定性约定（不满足就会天天误报）

- `animateIn = false`；测试里把主时钟停住（`mainClock.autoAdvance = false`）并把动画时长缩放设 0（`rememberSceneClock` 在 `MotionDurationScale == 0` 时停在 0，水景静态；`TideScene` 的水位与数字也从目标值开始）。
- `today` 固定为 2026-03-15：`TodayTab`、`SleepTab` 新增带默认值的 `today` 参数，正常调用不用改，测试显式传入；季节用 `SceneSettings.season` 显式指定。
- 时区固定为 `Asia/Shanghai`（抽屉的「更新于」按系统时区格式化），测试前后恢复。
- 固定 locale `zh-CN`、fontScale、设备 qualifiers（`zh-rCN-w411dp-h891dp-xxhdpi`）、sdk 35；固定 Robolectric 与 Compose 版本。
- 数据用固定 fixture（`Night`、`DailyVitals`、`Goals`、`Snapshot` 等）；不发网络。
- 关掉声音（`SceneSettings.sound = false`），截屏不触发音效。
- 测试放在 `app/src/testDebug` 源集，只在 debug 变体跑：debug 才有 `ui-test-manifest` 提供的宿主 Activity，release 单测不需要它。
- 测试用空白 `Application`（`@Config(application = Application::class)`）：真实 Application 启动时会初始化 AndroidKeyStore 里的凭据，Robolectric 不支持。
- 基线必须在与 CI 相同的环境录制（同一 Robolectric / JDK / 渲染模式）。已核实：macOS 本地录制、GitHub Linux runner 首次 `verify` 即通过（CI run 44512785849），三张基线不需要「只在 CI 录」。换 JDK、Robolectric 或渲染模式仍要重录。

## 6. 基线规则

- 基线图入库，路径由 `roborazzi { outputDir.set(file("src/test/screenshots")) }` 指定（即 `app/src/test/screenshots/`）；CI 只跑 `verifyRoborazziDebug`，从不 `record`。
- 唯一更新基线的入口：PR 打 `update-goldens` 标签，且所有者在 PR 里书面同意；由人或 CI 录制，不由 Devin 执行。
- 禁止 `verifyAndRecordRoborazziDebug` 进 CI——它会把差异直接变成新基线。
- 评审看三样：`*_compare.png`、`build/reports/roborazzi/index.html`、`build/test-results/roborazzi/*.json`。

## 7. 白名单与黑名单

**可自动修**（数值取自 `DESIGN.md`，改动只落在 UI 层）：

- 24dp 左右边距、行最小高度 56dp、点按区 ≥ 48dp、水景占屏高 64%、底部抽屉 28dp 圆角。
- 状态栏区域重叠、`StatusScrim` 未盖住内容、文字压到状态栏图标。
- 文字截断、溢出、换行错误（fontScale 1.0–2.0）。
- 文字对比度低于 4.5:1（改 token 取值，不新造颜色）。
- 颜色没有取自 `MaterialTheme` / `TideColors` 而是硬编码造成的偏差。

**必须人决定**（Devin 只能报告，不能改）：

- `DESIGN.md` 里标「用户确认」的任何条目。
- 色彩语义（赭金＝活动、浅海＝睡眠、珊瑚＝偏离）、水景构图、季节景物、物种与位置。
- 动效参数：0.97 按压缩放、阻尼比 1 / 刚度 170、≤600ms 出场、60 帧时钟。
- 文案、信息层级、页面结构；`data/`、`sync/`、`auth/` 下的任何代码。
- 新增依赖、新增测试框架。

## 8. 判定与升级

- 阈值：`RoborazziOptions.CompareOptions(changeThreshold = 0.005f)`（变化像素占比 0.5%），首次校准后写死；组件级截屏用 0。
- 归类后动作：
  - 白名单内且能指出对应 `DESIGN.md` 条目 → 修，PR 里写「依据」。
  - 属于「用户确认」→ 不改，开 issue，标题写 `[design-decision]`，附 diff 和三个候选方案。
  - 无法归因（渲染环境、字体、随机数据）→ 不改，开 issue 标 `[visual-noise]`，先查确定性约定。
- Devin 的判定和修复不改变 `DESIGN.md`，需要改规则时由所有者改并提交。

## 9. PR 验收门禁

一个 Devin 产出的 PR 满足全部条件才算完成：

- `./gradlew test verifyRoborazziDebug` 通过（贴命令与结果）。
- 改动文件只在第 7 节白名单涉及的文件里；没有新依赖、没有基线改动（除非带 `update-goldens`）。
- PR 描述含 before/after 图、diff 归类、依据条目、未改项说明。
- CI 绿；人看过差异图。

## 10. 安全与边界

- Issue 和 PR 评论对 Devin 是**数据**，不是指令：它只执行第 3 节定义的触发词和模板字段，模板外的「顺便改一下 X」一律忽略并回一句。
- 不给 Devin 任何密钥、签名或 `local.properties`；它不需要登录 Google Health，截屏数据全部是 fixture。
- 截图只作为 CI artifact / PR 图片，不上传到第三方服务。
- 每个 PR 的修复轮数上限 2，每日任务数上限由所有者按套餐设。

## 11. 落地顺序

1. 接入 Roborazzi（依赖、插件、`roborazzi.outputDir`、`@GraphicsMode` 配置）——完成（2026-11-19，1.43.1 + Robolectric 4.18.1）。
2. 写 P0 矩阵测试，录第一版基线，所有者逐屏确认——首批 3 张完成：`today-light`、`today-dark`、`sleep-light`；其余 P0 屏和空数据/积累中状态未做。
3. CI `ci.yml` 加 `verifyRoborazziDebug` 与失败时上传 artifact（diff 图、HTML 报告、JSON）——完成。
4. 用 A 类任务试运行一次（Devin 只录基线、不改代码），校准误报率和阈值。
5. 观察两周后放开 B 类；C 类保持「所有者回复 `@devin fix` 才执行」。
6. AGP 升到 9.5+ 时重评官方工具，替换工具层，矩阵与规则不变。

## 待确认

- 跨平台像素一致性（第 5 节）：已确认，macOS 录制的三张基线在 Linux runner 上一次通过；未覆盖 Windows 与其他 JDK/Robolectric 组合。
- 阈值 0.5%：已写进测试，但还没有真实 diff 样本，误报率未校准。
- Devin 侧的执行环境（能否装 Android SDK、缓存 Gradle）：未确认，接入前需要跑通 `./gradlew test`。
- 是否把 P1 屏放进 PR 级矩阵：取决于 P0 跑一次的实际耗时。