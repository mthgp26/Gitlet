package gitlet;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Locale;

import static gitlet.Utils.sha1;

/** Represents a gitlet commit object.
 *  does at a high level.
 *
 *  @author mthgp26
 */
public class Commit implements Serializable {
    /**
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** meta data. */
    private String timeStamp;
    private String message; // log message

    /** 时间戳格式，匹配 log 输出要求，例如 "Thu Nov 9 20:00:05 2017 -0800" */
    private static final String TIME_FORMAT = "EEE MMM dd HH:mm:ss yyyy Z";

    /** references. */
    private TreeMap<String, String> blobs; // filename -> blobID
    // 使用TreeMap是出于add（相对commit中的文件数量N）的对数时间要求
    private String parentID; //
    private String secondParentID;

    // (仅用于)创建init commit
    public Commit() {
        Date UNIX_EPOCH = new Date(0);
        SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT, Locale.US);
        timeStamp = sdf.format(UNIX_EPOCH);
        message = "initial commit";

        blobs = new TreeMap<>();
        parentID = null;
        secondParentID = null;
    }

    public Commit(String message, TreeMap<String, String> stagedAddition,
                  HashSet<String> stagedRemoval, Commit parentCommit) {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT, Locale.US);
        timeStamp = sdf.format(now);
        this.message = message;
        this.parentID = parentCommit.getID();
        this.secondParentID = null;

        // 从parent这里继承原来的Blob，需要深拷贝
        this.blobs = new TreeMap<>();
        for (Map.Entry<String, String> map : parentCommit.blobs.entrySet()) {
            this.blobs.put(map.getKey(), map.getValue());
        }

        for (Map.Entry<String, String> map : stagedAddition.entrySet()) { // 从addition中更新
            this.blobs.put(map.getKey(), map.getValue());
        }

        for (String filename : stagedRemoval) { // 从removal中更新
            this.blobs.remove(filename);
        }
    }

    public String getID() { // 求sha1 id
        StringBuilder blobStr = new StringBuilder();
        for (Map.Entry<String, String> entry : blobs.entrySet()) {
            blobStr.append(entry.getKey() + entry.getValue());
        }

        String parStr = (parentID == null) ? "" : parentID;
        String secondParStr = (secondParentID == null) ? "" : secondParentID;

        return sha1(timeStamp, message, blobStr.toString(), parStr, secondParStr);
    }

    public void save() {
        String commitFilename = getID();
        File commitFile = Utils.join(Repository.COMMITS_DIR, commitFilename);
        Utils.writeObject(commitFile, this);
    }

    /** 反序列化，不检查文件是否存在 */
    public static Commit load(String commitID) {
        // Commit commit = new Commit();
        File commitFile = Utils.join(Repository.COMMITS_DIR, commitID);
        return Utils.readObject(commitFile, Commit.class);
    }

    public String getMessage() {
        return message;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public String getFirstParentID() {
        return parentID;
    }

    public String getSecondParentID() {
        return secondParentID;
    }

    /** 供 merge 生成的合并提交设置第二父提交 */
    public void setSecondParentID(String secondParentID) {
        this.secondParentID = secondParentID;
    }

    public boolean isTrackingFile(String filename) {
        return blobs.containsKey(filename);
    }

    public String getBlobID(String filename) {
        return blobs.get(filename);
    }

    public void printLogEntry() {
        System.out.println("===");
        System.out.println("commit " + getID());
        if (secondParentID != null) {
            System.out.println("Merge: " + parentID.substring(0, 7)
                    + " " + secondParentID.substring(0, 7));
        }
        System.out.println("Date: " + timeStamp);
        System.out.println(getMessage());
    }

    public Set<String> getTrackingFiles() {
        return blobs.keySet();
    }
}
