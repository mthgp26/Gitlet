package gitlet;

import java.io.File;
import java.io.Serializable;

/** Represent a blob
 *
 *  @author mthgp26
 */
public class Blob implements Serializable {
    private byte[] contents;

    public Blob(byte[] contents) {
        this.contents = contents;
    }

    public String getID() {
        return Utils.sha1(contents);
    }

    public byte[] getContents() {
        return contents;
    }

    /** 反序列化 */
    public static Blob load(String blobID) {
        File blobFile = Utils.join(Repository.BLOBS_DIR, blobID);
        return Utils.readObject(blobFile, Blob.class);
    }

    // 看情况，如果有需要单独写一个save方法
    public void save() {
        String blobFilename = getID();
        File blobFile = Utils.join(Repository.BLOBS_DIR, blobFilename);
        Utils.writeObject(blobFile, this);
    }
}
