package gitlet;

import java.io.File;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static gitlet.Utils.*;

/** Represents a gitlet repository.
 *
 *  @author mthgp26
 */
public class Repository implements Serializable {
    /**
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */
    // Repo不需要维护一个Hashmap<String, String> commits保存所有的commit引用，因为完全可以依靠parent来追溯
    private TreeMap<String, String> branches; // filename(branch name) -> branch head 每个分支头(commit(ID))
    // 为什么用TreeMap完全是后面字典序的要求
    private String HEAD; // branch head的名字的引用

    /** Staged area. */
    private TreeMap<String, String> stagedAddtion; // filename -> blobID
    private HashSet<String> stagedRemoval; // filename

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    /** Blobs and Commits directory. */
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    public static final File BLOBS_DIR = join(OBJECTS_DIR, "blobs");
    public static final File COMMITS_DIR = join(OBJECTS_DIR, "commits");

    /** Repo file. */
    public static final File REFS_FILE = join(GITLET_DIR, "refs");

//    /** The Staging Area directory. */
//    public static final File STAGED_DIR = join(GITLET_DIR, "staged");
//    public static final File ADDITION_DIR = join(STAGED_DIR, "addition");
//    public static final File REMOVAL_DIR = join(STAGED_DIR, "removal");

    public Repository() {
        branches = new TreeMap<>();
        HEAD = null;
        stagedAddtion = new TreeMap<>();
        stagedRemoval = new HashSet<>();
    }

    /** 反序列化 */
    public static Repository load() {
        // [Failure Case] 使用的命令需要在一个已初始化的仓库，然而它并不存在
        if (!REFS_FILE.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }

        return Utils.readObject(REFS_FILE, Repository.class);
    }

    /** 序列化 */
    public void save() {
        Utils.writeObject(REFS_FILE, this);
    }

    public static void init() {
        // [Failure Case] 当前目录中已经存在一个 Gitlet 版本控制系统
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            System.exit(0);
        }

        // 创建.gitlet及其子目录
        GITLET_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        BLOBS_DIR.mkdir();
        COMMITS_DIR.mkdir();

        // 创建initCommit
        Commit initCommit = new Commit();
        initCommit.save();

        // 初始化并序列化repo
        Repository repo = new Repository();
        repo.HEAD = "master";
        repo.branches.put("master", initCommit.getID());
        Utils.writeObject(REFS_FILE, repo); // 也许可以替换成repo.save()?
    }

    public void add(String filename) {
        // [Failure Case] 文件不存在
        File filePath = Utils.join(CWD, filename);
        if (!filePath.exists()) {
            System.out.println("File does not exist.");
            System.exit(0);
        }

        byte[] contents = Utils.readContents(filePath);
        String blobID = Utils.sha1(contents);

        // 检查是否在removal中
        if (stagedRemoval.contains(filename)) {
            stagedRemoval.remove(filename); // 在removal中则先从removal中移除，否则接着看下一步
        }

        Commit currentCommit = getCurrentCommit();

        /** 这里把条件写紧凑点应该代码行数还可以压一压，另外嵌套也可以拆除掉，但是先通过测试再说 */
        // 看current commit是否追踪该文件
        if (currentCommit.isTrackingFile(filename)) { // 如果current commit正在追踪
            String currentCommitBlobID = currentCommit.getBlobID(filename);

            // 比较commit中的版本与当前的版本是否一样
            if (currentCommitBlobID.equals(blobID)) { // 如果一样
                // 看addition中是否有该文件，如果有则移除，不在则直接return
                if (stagedAddtion.containsKey(filename)) {
                    removeFromAddition(filename);
                }
                return;
            }
        }

        // 如果不追踪，直接加入addition
        addForAddition(filename, contents);
    }

    public void commit(String message) {
        // [Failure Case] 暂存区为空
        if (stagedAddtion.isEmpty() && stagedRemoval.isEmpty()) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }
        // 创建新的commit
        Commit parent = getCurrentCommit();
        Commit newCurrentCommit = new Commit(message, stagedAddtion, stagedRemoval, parent);
        String newCurrentCommitID = newCurrentCommit.getID();
        newCurrentCommit.save();

        // 更新repo中的引用
        branches.put(HEAD, newCurrentCommitID);
        clearStagedArea();
    }

    public void rm(String filename) {
        // [Failure Case] 文件既没有被暂存，也没有被头提交追踪
        /** 注意，文件不存在不是错误的情况，因为有可能已经被用户手动移除掉了 */
        Commit currentCommit = getCurrentCommit();
        if (!stagedAddtion.containsKey(filename)
                && !stagedRemoval.contains(filename)
                && !currentCommit.isTrackingFile(filename)) {
            System.out.println("No reason to remove the file.");
            System.exit(0);
        }

        // 看当前commit是否追踪
        if (currentCommit.isTrackingFile(filename)) { // 如果被追踪，则加入removal
            stagedRemoval.add(filename);

            // 再看是否在addition区中
            if (stagedAddtion.containsKey(filename)) { // 如果在则移除
                stagedAddtion.remove(filename);
            }

            // 从工作目录删除文件（如果还未被用户删除），注意这个只要被追踪就检查
            File filePath = Utils.join(CWD, filename);
            if (filePath.exists()) {
                Utils.restrictedDelete(filePath);
            }
            return;
        }

        // 如果没有被追踪，则看addition区有没有
        if (stagedAddtion.containsKey(filename)) { // 如果有则移除
            stagedAddtion.remove(filename);
        }
    }

    public void log() {
        Commit currentCommit = getCurrentCommit();
        Commit dummy = currentCommit;
        while (dummy.getFirstParentID() != null) {
            dummy.printLogEntry();
            System.out.println();
            dummy = Commit.load(dummy.getFirstParentID());
        }
        dummy.printLogEntry();
    }

    // [BUG] 当有分支被删除后仍然被打印
    // 误会误会
    public void globalLog() {
        List<String> commitIDs = Utils.plainFilenamesIn(COMMITS_DIR);
        
        if (commitIDs == null) {
            return;
        }

        Commit dummy;
        for (String commitID : commitIDs) {
            dummy = Commit.load(commitID);
            dummy.printLogEntry();
            System.out.println();
        }
    }

    // [BUG] 当所在分支被删除时仍然被find到
    // 好吧是我误会了，我这个find没问题
    public void find(String message) {
        List<String> commitIDs = Utils.plainFilenamesIn(COMMITS_DIR);

        if (commitIDs == null) {
            System.out.println("Found no commit with that message.");
            return;
        }

        Commit dummy;
        boolean existence = false;
        for (String commitID : commitIDs) {
            dummy = Commit.load(commitID);
            if (dummy.getMessage().equals(message)) {
                System.out.println(commitID);
                existence = true;
            }
        }

        if (!existence) {
            System.out.println("Found no commit with that message.");
        }
    }

    public void status() {
        // Branches
        System.out.println("=== Branches ===");
        for (Map.Entry<String, String> branch : branches.entrySet()) {
            if (branch.getKey().equals(HEAD)) {
                System.out.println("*" + branch.getKey());
            } else {
                System.out.println(branch.getKey());
            }
        }
        System.out.println();

        // Staged Files
        System.out.println("=== Staged Files ===");
        for (Map.Entry<String, String> stagedFile : stagedAddtion.entrySet()) {
            System.out.println(stagedFile.getKey());
        }
        System.out.println();

        // Removed Files
        System.out.println("=== Removed Files ===");
        for (String removedFilename : stagedRemoval.stream()
                .sorted().collect(Collectors.toList())) { // 奇怪的语法，为了字典序
            System.out.println(removedFilename);
        }
        System.out.println();

        /** Extra Credits. */
        // Modifications Not Staged For Commit
        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println();

        // Untracked Files
        System.out.println("=== Untracked Files ===");
        System.out.println();
    }

    public void checkout(
            int option, String branchName,
            String commitID, String filename) {
        switch (option) {
            /** java gitlet.Main checkout -- [文件名] */
            case 0: {
                Commit currentCommit = getCurrentCommit();
                // [Failure Case] 该文件在头提交中不存在
                if (!currentCommit.isTrackingFile(filename)) {
                    System.out.println("File does not exist in that commit.");
                    System.exit(0);
                }

                String blobID = currentCommit.getBlobID(filename);
                File filePath = Utils.join(CWD, filename);
                Blob blob = Blob.load(blobID);
                Utils.writeContents(filePath, blob.getContents());

                break;
            }
            /** java gitlet.Main checkout [提交 ID] -- [文件名] */
            case 1: {
                Commit commit = findCommitByPrefix(commitID);
                if (!commit.isTrackingFile(filename)) {
                    System.out.println("File does not exist in that commit.");
                    System.exit(0);
                }

                String blobID = commit.getBlobID(filename);
                File filePath = Utils.join(CWD, filename);
                Blob blob = Blob.load(blobID);
                Utils.writeContents(filePath, blob.getContents());

                break;
            }
            /** java gitlet.Main checkout [分支名] */
            case 2: {
                // [Failure Case] 不存在具有该名称的分支
                if (!branches.containsKey(branchName)) {
                    System.out.println("No such branch exists.");
                    System.exit(0);
                }

                // [Failure Case] 该分支就是当前分支
                if (HEAD.equals(branchName)) {
                    System.out.println("No need to checkout the current branch.");
                    System.exit(0);
                }

                Commit currentCommit = getCurrentCommit();
                // 找到给定branch的头提交
                Commit branchHeadCommit = Commit.load(branches.get(branchName));

                List<String> filesInCWD = Utils.plainFilenamesIn(CWD);
                // [Failure Case] 当前分支中的一个未跟踪文件将被覆盖
                for (String file : filesInCWD) {
                    if (!currentCommit.isTrackingFile(file)
                            && branchHeadCommit.isTrackingFile(file)) {
                        System.out.println("There is an untracked file in the way; " +
                                "delete it, or add and commit it first.");
                        System.exit(0);
                    }
                }

                // 检出 branch head commit 追踪的所有文件
                for (String file : branchHeadCommit.getTrackingFiles()) {
                    String blobID = branchHeadCommit.getBlobID(file);
                    File filePath = Utils.join(CWD, file);
                    Blob blob = Blob.load(blobID);
                    Utils.writeContents(filePath, blob.getContents());
                }

                // 2. 移除被当前 commit 追踪、但 branch commit 不追踪的文件
                for (String file : currentCommit.getTrackingFiles()) {
                    if (!branchHeadCommit.isTrackingFile(file)) {
                        File filePath = Utils.join(CWD, file);
                        if (filePath.exists()) {
                            Utils.restrictedDelete(filePath);
                        }
                    }
                }

                // 更新HEAD
                HEAD = branchName;

                // 清空暂存区
                clearStagedArea();
            }
            default: {
                return;
            }
        }
    }

    // [BUG] 分支切换失败
    // [Fixed] Main里参数检查写错了:)
    public void branch(String branchName) {
        // [Failure Case] A branch with that name already exists.
        if (branches.containsKey(branchName)) {
            System.out.println("A branch with that name already exists.");
            System.exit(0);
        }

        Commit currentCommit = getCurrentCommit();
        branches.put(branchName, currentCommit.getID());
    }

    // TODO: [BUG] 同branch
    // [Fixed] 同branch
    public void rmBranch(String branchName) {
        // [Failure Case] 不存在具有给定名称的分支
        if (!branches.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }

        // [Failure Case] 试图移除当前所在的分支
        if (HEAD.equals(branchName)) {
            System.out.println("Cannot remove the current branch.");
            System.exit(0);
        }

        branches.remove(branchName);
    }

    public void reset(String commitID) {
        // [Failure Case] 不存在具有给定 ID 的提交
        Commit givenCommit = findCommitByPrefix(commitID);
        Commit currentCommit = getCurrentCommit();

        List<String> filesInCWD = Utils.plainFilenamesIn(CWD);

        // [Failure Case] 当前分支中的一个未跟踪文件将被覆盖，同checkout
        for (String filename : filesInCWD) {
            if (!currentCommit.isTrackingFile(filename)
                    && givenCommit.isTrackingFile(filename)) {
                System.out.println("There is an untracked file in the way; " +
                        "delete it, or add and commit it first.");
                System.exit(0);
            }
        }

        // 1. 检出 given commit 追踪的所有文件
        for (String filename : givenCommit.getTrackingFiles()) {
            String blobID = givenCommit.getBlobID(filename);
            File filePath = Utils.join(CWD, filename);
            Blob blob = Blob.load(blobID);
            Utils.writeContents(filePath, blob.getContents());
        }

        // 2. 移除被当前 commit 追踪、但 given commit 不追踪的文件
        for (String filename : currentCommit.getTrackingFiles()) {
            if (!givenCommit.isTrackingFile(filename)) {
                File filePath = Utils.join(CWD, filename);
                if (filePath.exists()) {
                    Utils.restrictedDelete(filePath);
                }
            }
        }

        // 3. 将当前分支头移动到 given commit
        branches.put(HEAD, givenCommit.getID());

        // 4. 清空暂存区
        clearStagedArea();
    }

    public void merge(String branchName) {

        // [Failure Case] 存在暂存的添加或移除
        if (!stagedAddtion.isEmpty() || !stagedRemoval.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }

        // [Failure Case] 给定名称的分支不存在
        if (!branches.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }

        // [Failure Case] 试图合并一个分支与其自身
        if (branchName.equals(HEAD)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }

        // 找到 split point（两个分支头的最新共同祖先）
        Commit currentCommit = getCurrentCommit();
        Commit givenCommit = Commit.load(branches.get(branchName));
        Commit splitPoint = findSplitPoint(currentCommit, givenCommit);

        /** split point与给定branch是同一个commit，说明给定分支是当前分支的祖先，什么都不做
         *  split <-- Commit1 <-- Commit2 -- HEAD
         *       \-- Branch
         */
        if (splitPoint.getID().equals(branches.get(branchName))) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }

        /** split point是当前分支，说明当前分支是给定分支的祖先，效果等同于检出给定分支（fast-forward）
         *  split <-- Commit1 <-- Commit2 -- Branch
         *       \-- HEAD
         */
        if (splitPoint.getID().equals(branches.get(HEAD))) {
            // [Failure Case] 未跟踪文件将被覆盖（在任何输出/改动前检查）
            checkUntrackedOverwrite(currentCommit, givenCommit);
            System.out.println("Current branch fast-forwarded.");
            fastForward(currentCommit, givenCommit);
            return;
        }

        /**
         * 构建合并计划。动作编码：KEEP=保持现状，TAKE=采用给定分支版本，DELETE=删除，CONFLICT=冲突。
         * 依据每个文件在 split/current/given 三处的 blobID 判定（null 表示该文件被删除/不存在）。
         * 先用 blob 内容做分类，未跟踪文件检查通过后再真正改动文件与暂存区。
         */
        int KEEP = 0, TAKE = 1, DELETE = 2, CONFLICT = 3;
        Map<String, Integer> plan = new TreeMap<>();
        Set<String> names = new TreeSet<>();
        names.addAll(splitPoint.getTrackingFiles());
        names.addAll(currentCommit.getTrackingFiles());
        names.addAll(givenCommit.getTrackingFiles());

        for (String f : names) {
            String s = splitPoint.isTrackingFile(f) ? splitPoint.getBlobID(f) : null;
            String c = currentCommit.isTrackingFile(f) ? currentCommit.getBlobID(f) : null;
            String g = givenCommit.isTrackingFile(f) ? givenCommit.getBlobID(f) : null;

            if (Objects.equals(c, g)) {
                // 两分支结果一致（都删除/都没改/相同改动）→ 保持现状
                plan.put(f, KEEP);
            } else if (Objects.equals(c, s)) {
                // 当前分支自分割点未改 → 采用给定分支的变化
                if (g == null) {
                    plan.put(f, DELETE);   // 给定分支删除了它
                } else {
                    plan.put(f, TAKE);     // 检出给定分支版本并暂存
                }
            } else {
                // 当前分支自分割点已改
                if (Objects.equals(g, s)) {
                    plan.put(f, KEEP);     // 只有当前分支改过 → 保持
                } else {
                    plan.put(f, CONFLICT); // 两分支以不同方式改过 → 冲突
                }
            }
        }

        // [Failure Case] 未跟踪文件将被合并覆盖或删除：在任何改动前先检查
        List<String> filesInCWD = Utils.plainFilenamesIn(CWD);
        if (filesInCWD != null) {
            for (String f : filesInCWD) {
                if (!currentCommit.isTrackingFile(f) && plan.containsKey(f)) {
                    int a = plan.get(f);
                    if (a == TAKE || a == DELETE || a == CONFLICT) {
                        System.out.println("There is an untracked file in the way; " +
                                "delete it, or add and commit it first.");
                        System.exit(0);
                    }
                }
            }
        }

        // 执行合并计划
        boolean conflict = false;
        for (String f : plan.keySet()) {
            switch (plan.get(f)) {
                case 1: { // TAKE：检出给定分支的版本
                    byte[] contents = Blob.load(givenCommit.getBlobID(f)).getContents();
                    Utils.writeContents(Utils.join(CWD, f), contents);
                    addForAddition(f, contents);
                    break;
                }
                case 2: { // DELETE：从工作目录删除并暂存移除
                    stagedRemoval.add(f);
                    File fp = Utils.join(CWD, f);
                    if (fp.exists()) {
                        Utils.restrictedDelete(fp);
                    }
                    break;
                }
                case 3: { // CONFLICT：写入冲突标记并暂存
                    String cur = currentCommit.isTrackingFile(f)
                            ? new String(Blob.load(currentCommit.getBlobID(f)).getContents(),
                                    StandardCharsets.UTF_8) : "";
                    String giv = givenCommit.isTrackingFile(f)
                            ? new String(Blob.load(givenCommit.getBlobID(f)).getContents(),
                                    StandardCharsets.UTF_8) : "";
                    String combined = "<<<<<<< HEAD\n" + cur + "=======\n" + giv + ">>>>>>>\n";
                    byte[] bytes = combined.getBytes(StandardCharsets.UTF_8);
                    Utils.writeContents(Utils.join(CWD, f), bytes);
                    addForAddition(f, bytes);
                    conflict = true;
                    break;
                }
                default:
                    break; // KEEP：不动
            }
        }

        // [Failure Case] 合并生成的提交没有任何更改（让普通 commit 的错误通过）
        if (stagedAddtion.isEmpty() && stagedRemoval.isEmpty()) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }

        // 自动提交一个合并提交：第一父 = 当前 HEAD，第二父 = 给定分支头
        Commit mergeCommit = new Commit(
                "Merged " + branchName + " into " + HEAD + ".",
                stagedAddtion, stagedRemoval, currentCommit);
        mergeCommit.setSecondParentID(givenCommit.getID());
        mergeCommit.save();
        branches.put(HEAD, mergeCommit.getID());
        clearStagedArea();

        // 若遇到冲突，在提交之后于终端提示
        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /** [Failure Case] 未跟踪文件将被覆盖：若工作目录中有一个当前 commit 未追踪、
     *  但 target commit 将写入的文件，则报错退出。 */
    private void checkUntrackedOverwrite(Commit currentCommit, Commit targetCommit) {
        List<String> filesInCWD = Utils.plainFilenamesIn(CWD);
        if (filesInCWD == null) {
            return;
        }
        for (String f : filesInCWD) {
            if (!currentCommit.isTrackingFile(f) && targetCommit.isTrackingFile(f)) {
                System.out.println("There is an untracked file in the way; " +
                        "delete it, or add and commit it first.");
                System.exit(0);
            }
        }
    }

    /** 将 HEAD 分支 fast-forward 到 givenCommit 并同步工作目录，不生成合并提交。 */
    private void fastForward(Commit currentCommit, Commit givenCommit) {
        // 检出 given commit 追踪的所有文件
        for (String f : givenCommit.getTrackingFiles()) {
            byte[] contents = Blob.load(givenCommit.getBlobID(f)).getContents();
            Utils.writeContents(Utils.join(CWD, f), contents);
        }

        // 移除被当前 commit 追踪、但 given commit 不追踪的文件
        for (String f : currentCommit.getTrackingFiles()) {
            if (!givenCommit.isTrackingFile(f)) {
                File fp = Utils.join(CWD, f);
                if (fp.exists()) {
                    Utils.restrictedDelete(fp);
                }
            }
        }

        // 移动当前分支头并清空暂存区
        branches.put(HEAD, givenCommit.getID());
        clearStagedArea();
    }
    
//    /** 暂时看来这个方法没什么卵用 */
//    private boolean isRemoved(String filename) {
//        return stagedRemoval.contains(filename);
//    }

    private Commit getCurrentCommit() { // current commit在gitlet中等价于HEAD，因为不存在头指针分离
        // 先得到commit的ID
        String currentCommitID = branches.get(HEAD);
        Commit currentCommit = new Commit();
        File currentCommitFile = Utils.join(COMMITS_DIR, currentCommitID); // 找到对应文件
        currentCommit = Utils.readObject(currentCommitFile, Commit.class);
        return currentCommit;
    }

    private void addForAddition(String filename, byte[] contents) {
        Blob blob = new Blob(contents);
        // Utils.writeObject(BLOBS_DIR, blob);
        // 还是写个save吧，这里犯了个明显的错误是，blob应当在自己的文件夹里
        blob.save();
        stagedAddtion.put(filename, blob.getID());
    }

    private void removeFromAddition(String filename) { // 这么看这个方法有点脱裤子放屁，但是为了统一还是这么写吧
        stagedAddtion.remove(filename);
    }

    private void clearStagedArea() {
        stagedAddtion.clear();
        stagedRemoval.clear();
    }

    /** copilot给写的支持任意前后缀的匹配算法 */
    private Commit findCommitByPrefix(String prefix) {
        List<String> allIDs = plainFilenamesIn(COMMITS_DIR);

        /** 看俅不懂...
         *  总之就是先把List变成stream，然后跟c++一样（死去的STL记忆攻击了我）各种链式调用
         *  最后用collect把stream收集成List容器
         */
        List<String> matches = allIDs.stream()
                .filter(cid -> cid.startsWith(prefix))
                .collect(Collectors.toList());
        if (matches.size() != 1) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        return Commit.load(matches.get(0));
    }

    /** 收集给定commit的所有祖先，不区分深度，BFS遍历 */
    private Set<String> getAllAncestors(Commit commit) {
        Set<String> ancestors = new HashSet<>();
        Queue<Commit> queue = new ArrayDeque<>();
        queue.add(commit);

        // BFS
        while (!queue.isEmpty()) {
            Commit c = queue.poll(); // 出队
            if (ancestors.contains(c.getID())) {
                /**
                 * 如果已经包含，说明这个节点之前已经被访问过（可能是通过另一条路径到达的），
                 * 那么跳过本次循环，不再处理它的父节点
                 * 比如说两个分支merge之后，它们的共同祖先会从两个路径上能到达
                 */
                continue;
            }
            ancestors.add(c.getID()); // 遍历到c则加入祖先中

            // 接着检查其祖先
            if (c.getFirstParentID() != null) {
                Commit firstParent = Commit.load(c.getFirstParentID());
                queue.add(firstParent);
            }

            if (c.getSecondParentID() != null) {
                Commit secondPatent = Commit.load(c.getSecondParentID());
                queue.add(secondPatent);
            }
        }
        return ancestors;
    }

    private Commit findSplitPoint(Commit currentCommit, Commit givenCommit) {
        // 收集 current commit 的所有祖先（含其自身）
        Set<String> currAncestors = getAllAncestors(currentCommit);

        // 从 given commit 出发做真正的按层 BFS（dist 记录每个节点到 given 的最小距离），
        // 第一个命中的、同时也是 current 祖先的节点，就是离两个分支头最近的公共祖先（split point）。
        Queue<Commit> queue = new ArrayDeque<>();
        Map<String, Integer> dist = new HashMap<>();
        queue.add(givenCommit);
        dist.put(givenCommit.getID(), 0);

        while (!queue.isEmpty()) {
            Commit c = queue.poll();
            int d = dist.get(c.getID());

            // 命中公共祖先，按 BFS 层序即离 given 最近 → 直接返回
            if (currAncestors.contains(c.getID())) {
                return c;
            }

            if (c.getFirstParentID() != null && !dist.containsKey(c.getFirstParentID())) {
                dist.put(c.getFirstParentID(), d + 1);
                queue.add(Commit.load(c.getFirstParentID()));
            }

            if (c.getSecondParentID() != null && !dist.containsKey(c.getSecondParentID())) {
                dist.put(c.getSecondParentID(), d + 1);
                queue.add(Commit.load(c.getSecondParentID()));
            }
        }

        // 理论上总能找到（至少 init commit 是公共祖先）
        return null;
    }
}
