package tr.eaydgdu.email;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface DaoFolder {
    @Query("SELECT * FROM folder" +
            " WHERE account = :account" +
            " ORDER BY CASE WHEN folder.type = '" + EntityFolder.USER + "' THEN 1 ELSE 0 END")
    List<EntityFolder> getFolders(long account);

    @Query("SELECT * FROM folder" +
            " WHERE account = :account" +
            " AND synchronize = :synchronize" +
            " ORDER BY CASE WHEN folder.type = '" + EntityFolder.USER + "' THEN 1 ELSE 0 END")
    List<EntityFolder> getFolders(long account, boolean synchronize);

    @Query("SELECT * FROM folder" +
            " WHERE account = :account" +
            " AND type = '" + EntityFolder.USER + "'")
    List<EntityFolder> getUserFolders(long account);

    @Query("SELECT folder.*, account.name AS accountName" +
            ", COUNT(message.id) AS messages" +
            ", SUM(CASE WHEN message.content = 1 THEN 1 ELSE 0 END) AS content" +
            ", SUM(CASE WHEN message.ui_seen = 0 THEN 1 ELSE 0 END) AS unseen" +
            " FROM folder" +
            " LEFT JOIN account ON account.id = folder.account" +
            " LEFT JOIN message ON message.folder = folder.id AND NOT message.ui_hide" +
            " WHERE folder.account = :account OR folder.account IS NULL" +
            " GROUP BY folder.id")
    LiveData<List<TupleFolderEx>> liveFolders(long account);

    @Query("SELECT * FROM folder" +
            " WHERE (:account < 0 OR folder.account = :account)" +
            " AND type <> '" + EntityFolder.USER + "'")
    LiveData<List<EntityFolder>> liveSystemFolders(long account);

    @Query("SELECT folder.*, account.name AS accountName" +
            ", COUNT(message.id) AS messages" +
            ", SUM(CASE WHEN message.content = 1 THEN 1 ELSE 0 END) AS content" +
            ", SUM(CASE WHEN message.ui_seen = 0 THEN 1 ELSE 0 END) AS unseen" +
            " FROM folder" +
            " JOIN account ON account.id = folder.account" +
            " JOIN message ON message.folder = folder.id AND NOT message.ui_hide" +
            " WHERE account.`synchronize`" +
            " AND folder.unified" +
            " GROUP BY folder.id")
    LiveData<List<TupleFolderEx>> liveUnified();

    @Query("SELECT folder.* FROM folder WHERE folder.id = :id")
    LiveData<EntityFolder> liveFolder(long id);

    @Query("SELECT folder.*, account.name AS accountName" +
            ", COUNT(message.id) AS messages" +
            ", SUM(CASE WHEN message.content = 1 THEN 1 ELSE 0 END) AS content" +
            ", SUM(CASE WHEN message.ui_seen = 0 THEN 1 ELSE 0 END) AS unseen" +
            " FROM folder" +
            " LEFT JOIN account ON account.id = folder.account" +
            " LEFT JOIN message ON message.folder = folder.id AND NOT message.ui_hide" +
            " WHERE folder.id = :id")
    LiveData<TupleFolderEx> liveFolderEx(long id);

    @Query("SELECT * FROM folder WHERE id = :id")
    EntityFolder getFolder(Long id);

    @Query("SELECT * FROM folder WHERE account = :account AND name = :name")
    EntityFolder getFolderByName(Long account, String name);

    @Query("SELECT folder.* FROM folder" +
            " WHERE account = :account AND type = :type")
    EntityFolder getFolderByType(long account, String type);

    // For debug/crash info
    @Query("SELECT folder.* FROM folder" +
            " JOIN account ON account.id = folder.account" +
            " WHERE `primary` AND type = '" + EntityFolder.DRAFTS + "'")
    EntityFolder getPrimaryDrafts();

    @Query("SELECT folder.* FROM folder" +
            " JOIN account ON account.id = folder.account" +
            " WHERE `primary` AND type = '" + EntityFolder.ARCHIVE + "'")
    EntityFolder getPrimaryArchive();

    @Query("SELECT * FROM folder WHERE type = '" + EntityFolder.OUTBOX + "'")
    EntityFolder getOutbox();

    @Insert
    long insertFolder(EntityFolder folder);

    @Query("UPDATE folder SET state = :state WHERE id = :id")
    int setFolderState(long id, String state);

    @Query("UPDATE folder SET error = :error WHERE id = :id")
    int setFolderError(long id, String error);

    @Query("UPDATE folder SET type = :type WHERE id = :id")
    int setFolderType(long id, String type);

    @Query("UPDATE folder" +
            " SET type = '" + EntityFolder.USER + "'" +
            " WHERE account = :account")
    int setFoldersUser(long account);

    @Query("UPDATE folder" +
            " SET name = :name" +
            ", display = :display" +
            ", hide = :hide" +
            ", synchronize = :synchronize" +
            ", unified = :unified" +
            ", `after` = :after" +
            " WHERE id = :id")
    int setFolderProperties(long id, String name, String display, boolean hide, boolean synchronize, boolean unified, int after);

    @Query("UPDATE folder SET name = :name WHERE account = :account AND name = :old")
    int renameFolder(long account, String old, String name);

    @Query("DELETE FROM folder WHERE id = :id")
    void deleteFolder(long id);

    @Query("DELETE FROM folder WHERE account= :account AND name = :name")
    void deleteFolder(long account, String name);
}
