# 🫧 泡泡热榜

> 一个聚合多平台热点的 Android 应用：知乎 / 微博 / 贴吧 / 虎扑 / 抖音 / B站 / 百度 / 豆瓣 / 掘金 / IT之家，各平台当日最热的 20 条话题，一个 App 全看完。

## 功能

- **紧凑榜单**：排名 + 单行标题 + 热度值右对齐的信息流式布局，一屏尽可能多看几条
- **应用内阅读**：点击条目在 App 内直接打开原文（WebView），不打断浏览；可一键复制链接、系统分享或切换外部浏览器
- **长按操作**：长按任意条目弹出「应用内打开 / 复制链接 / 分享 / 浏览器打开」菜单
- **今日榜**：各平台实时热榜 Top 20，支持下拉刷新、平台筛选、「全部」跨平台合并视图
- **同题合并**：「全部」视图自动把多个平台收录的同一话题合并成一条，标注「等 N 个平台」，跨平台热点一眼可见
- **顶栏搜索**：输入关键词即时过滤当前榜单
- **本周榜 / 本月榜**：基于本地历史快照做跨天聚合，按「上榜天数 → 最新热度」排序，能看到哪些话题是真正持续发酵的长尾热点
- **多实例容灾**：内置多个公共 API 实例，主实例不可用时自动切换备用实例（带粘性记忆），并支持在设置中替换为自建服务
- **记住浏览状态**：上次看的周期与平台，下次打开原样恢复；切换页面不再整页闪加载
- **每日后台同步**：WorkManager 并行抓取快照，无需打开 App 也能积累周/月数据
- **动态取色**：Android 12+ 跟随系统壁纸配色，低版本使用品牌靛蓝主题，深色模式完整适配
- **本地优先**：所有数据只存本机（Room），不上传任何信息

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin 2.1 + Jetpack Compose + Material 3 |
| 架构 | MVVM（ViewModel + StateFlow），手写轻量 DI |
| 网络 | Retrofit + OkHttp + Gson |
| 存储 | Room（每日快照）+ DataStore（设置） |
| 后台 | WorkManager（24h 周期同步） |

## 数据来源

热榜数据来自 [DailyHotApi](https://github.com/imsyy/DailyHotApi)（开源热榜聚合服务，GPL-3.0，感谢原作者 [imsyy](https://github.com/imsyy) 与社区维护者）。默认接入社区公共实例 `https://hot-api.liiiu.cn`，也可在 App 设置中替换：

- **Vercel 部署**：Fork DailyHotApi 仓库后一键部署，免费
- **Docker 自建**：`docker run -d -p 6688:6688 imsyy/dailyhot-api`

> 说明：小红书没有公开热榜接口，v1 暂不支持，已在「设置 → 关于」中标注，后续视可行方案加入。

## 构建

要求 JDK 17+、Android SDK（compileSdk 36）：

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

CI 使用 GitHub Actions（`.github/workflows/android.yml`），push 到 `main` 自动构建并上传 APK 产物；打 `v*` tag 自动发布 Release。

## 隐私与免责声明

- 不收集任何用户数据，榜单快照仅保存在手机本地数据库
- 榜单内容版权归各平台所有，仅供个人阅读参考，请勿用于商业用途
- 公共 API 实例由社区维护，可用性不作保证；介意者建议自建数据源

## License

[MIT](LICENSE)（数据源 DailyHotApi 为 GPL-3.0，如需分发其衍生服务请注意相应义务）
