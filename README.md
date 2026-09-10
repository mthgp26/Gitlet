# Gitlet — 用纯 Java 实现的迷你版 Git

Gitlet 是一个从零写起的版本控制系统，重新实现了真实 Git 的一整套核心行为：**commit**、**branch**、
**checkout**、**reset**、**log**，以及带冲突解决的三方 **merge**。

> 原项目基于 UCB CS61B 的课程作业，除必要的依赖库以外几乎没有初始代码和框架，纯手写完成并跑通测试，后通过 copilot 移植成为独立的项目

---

## 亮点

- **内容寻址存储（Content-addressed storage）。** 每个文件都以不可变的 *blob* 形式存一份，键是它的 SHA-1
  哈希；内容相同会自动去重。
- **提交是不可变快照。** 每个 commit 是一个序列化对象，记录一份 `文件名 -> blobID` 的映射、时间戳、提交
  信息和父节点指针。commit 的 ID 就是它整体内容的 SHA-1，因此历史天然防篡改、不可变。
- **合并提交与 DAG 历史。** 合并会生成带**两个父节点**的提交；历史按有向无环图遍历，而不是链表。
- **带冲突检测的三方合并。** 计算两个分支的*最近公共祖先（LCA）*，再按文件分别处理：保留当前版 /
  采用对方版 / 删除 / 冲突（用标准的 `<<<<<<< HEAD ======= >>>>>>>` 标记）。
- **自定义持久化。** 对象通过 Java 序列化存到磁盘的 `.gitlet/` 目录，对标真实 Git 的 `.git/` 布局
  （`objects/`、`refs/` 等）。
- **运行时零依赖。** 只要装了 JDK 就能构建、运行。

## 支持的命令

| 命令 | 说明 |
| --- | --- |
| `init` | 在当前目录创建新仓库 |
| `add <file>` | 把文件暂存，供下一次提交使用 |
| `commit <msg>` | 提交所有暂存的改动 |
| `rm <file>` | 取消跟踪某文件（暂存删除） |
| `log` / `global-log` | 展示提交历史 |
| `find <msg>` | 按提交信息查找 commit |
| `status` | 展示分支、暂存与已删除文件 |
| `checkout [branch / file / commit -- file]` | 切换分支或还原文件 |
| `branch <name>` / `rm-branch <name>` | 创建 / 删除分支 |
| `reset <commit>` | 把当前分支移到某个 commit |
| `merge <branch>` | 把某分支并入当前分支（快进 + 三方合并 + 冲突） |

每个命令都保持了和 Git 相同的**错误语义**（比如拒绝覆盖未跟踪文件、拒绝在存在未提交改动时合并、能识别
"already up to date" / 快进等情况）。
---

## 架构

```
src/main/java/gitlet/
├── Main.java          CLI 入口：解析参数并分发命令
├── Repository.java    顶层状态：分支、HEAD、暂存区；命令逻辑与合并算法
├── Commit.java        不可变提交：信息、时间戳、文件名->blob 映射、（最多两个）父节点
├── Blob.java          内容寻址的文件快照（文件字节的 SHA-1）
├── Utils.java         文件 IO、SHA-1 哈希、序列化工具
└── GitletException.java / DumpObj.java / Dumpable.java   （调试用）
```

核心思路（与 Git 类似）：**运行时对象从不持有指向 commit/blob 的 Java 指针**——它们彼此用 SHA-1 字符串相互引用。

---

## 构建与运行

需要 JDK（17+），再配合 [Maven](https://maven.apache.org) 会更方便（仓库里也带了 `./mvnw` 包装脚本，
没有全局 Maven 也能构建）。

```bash
# 构建 —— 生成可运行的 jar  target/gitlet.jar
./mvnw package          # （或：mvn package）

# 运行 —— 像用 git 一样，在任意目录下使用
cd /some/scratch/dir
java -jar /path/to/gitlet/target/gitlet.jar init
java -jar /path/to/gitlet/target/gitlet.jar add wug.txt
java -jar /path/to/gitlet/target/gitlet.jar commit "initial"
java -jar /path/to/gitlet/target/gitlet.jar log
```

## 运行测试

仓库自带一套端到端测试工具（`testing/tester.py`，需要 `python3`）。

```bash
make check            # 编译并运行全部集成测试
```

测试覆盖了完整的命令，包括合并失败的情形、快进合并、祖先合并以及冲突标记的输出。
