# 第三方资料与依赖

本仓库的 Git 提交作者与第三方资料的著作权归属是不同概念。以下资料保留原有许可和归属；没有导入上游仓库的 Git 提交历史。

## ECDICT

- 来源：https://github.com/skywind3000/ECDICT
- 用途：离线英汉词典。
- 原始许可全文：[ECDICT-LICENSE.txt](app/src/main/assets/ECDICT-LICENSE.txt)。
- 当前数据版本与校验信息：[dictionary-source.json](app/src/main/assets/dictionary-source.json)。
- 转换后的数据库不存入 Git，通过 `scripts/prepare_dictionary.py` 生成；发布 APK 内含数据库和许可全文。

## 中文维基词典 / Kaikki / Wiktextract

- 词条原作者：中文维基词典贡献者。抽取：Kaikki.org / Wiktextract。[数据说明](https://kaikki.org/zhwiktionary/)。
- 原词条及其作者历史可从应用中对应的 `zh.wiktionary.org/wiki/...` 链接查阅；2026-09-01 dump，网页标记 2026-09-15 抽取。
- `references.db` 的 `definitions` 表及派生释义按 **CC BY-SA 4.0** 分发。保留原中文简繁文本，修改为过滤、去重、搜索键规范化、词性/标签显示，不声称为维基项目认可的产品。
- [许可全文](app/src/main/assets/WIKTIONARY-LICENSE.txt)随 APK 分发并可在应用中离线阅读；[版本、源文件哈希及处理记录](app/src/main/assets/references-source.json)。

## NETEMVocabulary

- 作者：exam-data/NETEMVocabulary 贡献者。[上游仓库](https://github.com/exam-data/NETEMVocabulary)，固定提交 `70dc6b68c855f21e666a7a291ff8ead5ca1f7b44`。
- 数据按 **CC BY-NC-SA 4.0** 分发，包含非商业条件。[许可全文](app/src/main/assets/NETEM-LICENSE.txt)随 APK 提供并可离线阅读。
- `references.db` 的 `netem_2024` 表仅提取、规范化和去重 2024 参考词表及原变体字段，派生表继续采用同一许可；不包含上游简略释义和混合考试词频，不表示已核验 2027 官方大纲。
- 以上数据表保持各自许可，本仓库的代码许可不取代这些数据许可。数据作者署名与 GitHub 提交贡献者不同；未导入上游 Git 历史。

## FSRS

调度逻辑参考 [FSRS 官方算法说明](https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm) 和 [py-fsrs](https://github.com/open-spaced-repetition/py-fsrs)，使用 FSRS-6 默认参数。工程中的实现和测试位于 `core/`。

## 项目编辑资料

`app/src/main/assets/editorial-notes.tsv` 包含项目内整理的学习优先释义及原创说明例句，未复制商业词典例句或历年真题。编辑顺序不表示真题统计频率，不构成对第三方资料许可的替代。

## 构建与运行依赖

AndroidX、Kotlin、kotlinx.coroutines、JUnit、Robolectric 等依赖通过各模块的 Gradle 文件声明，依赖库各自适用其原有许可证。构建工具、依赖缓存、个人签名文件不随源码提交。
