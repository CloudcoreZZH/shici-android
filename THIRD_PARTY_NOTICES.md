# 第三方资料与依赖

本仓库的 Git 提交作者与第三方资料的著作权归属是不同概念。以下资料保留原有许可和归属；没有导入上游仓库的 Git 提交历史。

## ECDICT

- 来源：https://github.com/skywind3000/ECDICT
- 用途：离线英汉词典。
- 原始许可全文：[ECDICT-LICENSE.txt](app/src/main/assets/ECDICT-LICENSE.txt)。
- 当前数据版本与校验信息：[dictionary-source.json](app/src/main/assets/dictionary-source.json)。
- 转换后的数据库不存入 Git，通过 `scripts/prepare_dictionary.py` 生成；发布 APK 内含数据库和许可全文。

## FSRS

调度逻辑参考 [FSRS 官方算法说明](https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm) 和 [py-fsrs](https://github.com/open-spaced-repetition/py-fsrs)，使用 FSRS-6 默认参数。工程中的实现和测试位于 `core/`。

## 项目编辑资料

`app/src/main/assets/editorial-notes.tsv` 包含项目内整理的学习优先释义及原创说明例句，未复制商业词典例句或历年真题。编辑顺序不表示真题统计频率，不构成对第三方资料许可的替代。

## 构建与运行依赖

AndroidX、Kotlin、kotlinx.coroutines、JUnit、Robolectric 等依赖通过各模块的 Gradle 文件声明，依赖库各自适用其原有许可证。构建工具、依赖缓存、个人签名文件不随源码提交。
