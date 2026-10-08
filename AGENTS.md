# AGENTS.md — 本仓库协作红线（每次开工先读）

> 这份文件是给所有 AI 工具 / 协作者看的**硬规则**，不是建议。
> 起因：曾发生过"一次推送把计划外的提交一并发布"的情况，因此把提交范围与推送授权写成硬约束。

## 一、Git 红线（最重要）

1. **提交范围只许是用户点名的那几个文件。**
   - `git add` 必须逐个写出路径；**禁止** `git add -A`、`git add .`、`git commit -a`。
2. **禁止自行 push。**
   - 默认只 `commit`，不 `push`。push 必须由用户**当次明确**说"推"。
   - 仓库装了 pre-push 门锁（`scripts/git-hooks/pre-push`）：它**只拦 AI 进程**（靠 `DSH_SHELL` / `DSH_SESSION_ID` 判定）——
     AI 发起的推送必须带 `XG_ALLOW_PUSH=1`，否则一律拒绝；**用户自己在终端 / IDE 里推送不受影响**（只打印一份待推清单）。
   - 新克隆 / 换机器后执行一次：`git config core.hooksPath scripts/git-hooks`（这是本地 `.git/config` 配置，不进版本库）。
   - **AI 不得自行设置 `XG_ALLOW_PUSH=1`**，除非用户在当次对话里明确授权推送。
3. **动手前先看范围，越界就停下报告。**
   - 工作区里经常有用户自己的未提交改动（`git status` 里非本次任务的文件一律不动）。
   - 若本地领先 `origin/main`（含用户自己的提交），**先把"将要推上去的清单"报给用户**，由用户决定推什么；不要替用户判断"反正迟早要推"。
4. **禁止整文件还原。**
   - 用户常在同一个文件里有未提交改动，`git checkout -- <file>` / `git restore <file>` 会把他的活儿一起清掉。撤销只能撤销自己改的那几行。
5. **提交前把该文件行尾规范成 LF。**
   - 本仓库存在"HEAD 存 LF、工作区是 CRLF"的文件（`core.autocrlf=false`），直接 `git add` 会产出"整文件改写"的假 diff。
   - 规范方式：只删掉多余 CR（不动编码 / BOM），使 diff 只含真实改动。

## 二、构建与验证

- 用 `.\gradlew.bat`（Wrapper）；**禁止**直接调用系统 `gradle`。
- 轻量编译检查：`.\gradlew.bat :app:compileFullDebugKotlin --offline`。
- **先确认没有别人的 Gradle 构建在跑**（用户常在 IDE 里构建）。并发构建会让 KSP 互相踩生成目录，报
  `NoSuchFileException: app\build\generated\ksp\...` —— 那不是代码错误，别乱改代码。
- 只改一两个文件时，可绕开 Gradle：用 Kotlin 编译器 + SDK 的 `android.jar` 单独编译该文件（`.tmp/volcheck/` 里有现成做法与桩）。

## 三、沟通

- 强制中文。
- 用户标注"**仅问答**"时，只回答，不要顺手动代码。
- 不确定就停下来问，**不要替用户决定范围**。
