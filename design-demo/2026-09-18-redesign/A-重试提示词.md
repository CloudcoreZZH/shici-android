# A 方案单独重试提示词

前两次 A 请求遇到网络错误。本次仍使用内置 image_gen，压缩提示内容，保留六屏和书刊方向。

```text
Create ONE complete high-fidelity UI design board for Chinese Android vocabulary app 拾词.
Portrait 3:4 canvas. Exactly SIX tall mobile screen artboards in a 3-column × 2-row grid, fully visible, no overlap. Narrow board heading “A · 墨白书页”, a tiny color/type strip at bottom. Large readable Chinese UI, no physical phone renders.
VISUAL IDENTITY: sophisticated literary journal. Warm ivory #F7F3EA, ink #232524, restrained vermilion #AF493A. English words in elegant editorial SERIF; Chinese in legible modern sans-serif. Generous whitespace, refined hairline rules, asymmetric left alignment, minimal 6dp corners, flat paper-like surfaces. Avoid rounded bubble cards, shadows, gradients and decorative illustrations. Distinct, beautiful book typography; not a generic dashboard.
The six screens, labelled above each:
1 学习首页: 拾词, 9月18日 周五, 考研生词本. Large typographic “12 待学习” and “24 到期复习”, vermilion button “开始学习”, a small 7-day calendar, “今日已学 8”. Bottom nav 查词/学习/词书/我的.
2 查词详情: word “address”, phonetic /əˈdres/, audio icon. 学习释义 / 全部释义 tabs. Open typographic definitions “v. 处理；设法解决”, “n. 地址”, “v. 向…发表讲话”. Small “编辑整理 · 非频率统计”. Sticky footer “已加入 3 次” and button “再次加入 +1”.
3 主动回忆: top back arrow, “3 / 10”, undo icon. Large left-aligned SERIF “address”, phonetic, sound. ONLY prompt “回忆它的含义”. Large bottom “显示答案”. NO Chinese meaning or translation on this screen, no bottom navigation.
4 揭晓评分: same word and progress, now definitions “v. 处理；设法解决”, “n. 地址”. Example “We need to address this problem.” with “我们需要处理这个问题。” and “原创例句”. Bottom FOUR comfortable rating buttons in 2×2 grid: “忘记 1天”, “困难 1天”, “记得 2天”, “轻松 8天”. Understated editorial styling, no emoji faces or trash icons.
5 复习安排: “今天到期 24”, “加入次数多的优先”. Three PREVIEW rows: address 加入5次, issue 加入3次, approach 加入2次. A small forecast and “开始复习”. NO Chinese definitions. Bottom navigation.
6 我的词书: 考研生词本, add-book plus, “128词 · 待学12次”, search, elegant index rows for address / issue / approach / maintain with short Chinese meanings and distinct right-column 加入5次 / 3次 / 2次 / 1次. Bottom navigation with 词书 selected.
Cohesive professionally typeset six-screen system, ample thumb targets and high text contrast. No weather, accounts, social features, ads, fake frequency statistics or unrelated widgets. All visible UI text in Simplified Chinese except English words and phonetics.
```
