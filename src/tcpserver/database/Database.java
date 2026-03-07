package tcpserver.database;

import chat.models.GroupChat;
import chat.models.GroupMember;
import chat.models.Message;
import chat.models.User;
import chat.network.ChatDtos;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Database {
    private static final Logger logger = Logger.getLogger(Database.class.getName());
    private static final HikariDataSource dataSource;

    static {
        Dotenv dotenv = Dotenv.load();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://"
                + dotenv.get("DB_HOST") + ":"
                + dotenv.get("DB_PORT") + "/"
                + dotenv.get("DB_DATABASE"));
        config.setUsername(dotenv.get("DB_USER"));
        config.setPassword(dotenv.get("DB_PASSWORD"));

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(3000);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(600000);
        config.setPoolName("ChatAppPool");

        dataSource = new HikariDataSource(config);
        logger.info("[DATABASE] HikariCP pool initialized.");
    }

    public static void main(String[] args) {
        createTableUsers();
        createTableGroupChats();
        createTableGroupMembers();
        createTableUserLogs();
        createTableMessages();
        createTableOfflineQueue();
        System.out.println("[DATABASE] All tables created.");
    }

    public static void createTableUsers() {
        try (var connection = dataSource.getConnection();
             var stmt = connection.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS USERS(
                    id SERIAL PRIMARY KEY,
                    username VARCHAR(100) NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    salt VARCHAR(100) NOT NULL,
                    created_at BIGINT,
                    identity_key TEXT,
                    signed_pre_key TEXT,
                    signature TEXT
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create USERS table", e);
        }
    }

    public static void createTableUserLogs() {
        try (var connection = dataSource.getConnection();
             var stmt = connection.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS USER_LOGS(
                    id SERIAL PRIMARY KEY,
                    id_user INTEGER,
                    action_type VARCHAR(100),
                    log_timestamp BIGINT,
                    ip_address VARCHAR(50),
                    FOREIGN KEY(id_user) REFERENCES USERS(id)
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create USER_LOGS table", e);
        }
    }

    public static void createTableGroupChats() {
        try (var connection = dataSource.getConnection();
             var stmt = connection.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS GROUP_CHATS(
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(200) NOT NULL
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create GROUP_CHATS table", e);
        }
    }

    public static void createTableGroupMembers() {
        try (var connection = dataSource.getConnection();
             var stmt = connection.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS GROUP_MEMBERS(
                    id_group INTEGER,
                    id_user INTEGER,
                    PRIMARY KEY(id_group, id_user),
                    FOREIGN KEY(id_group) REFERENCES GROUP_CHATS(id) ON DELETE CASCADE,
                    FOREIGN KEY(id_user) REFERENCES USERS(id) ON DELETE CASCADE
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create GROUP_MEMBERS table", e);
        }
    }

    public static void createTableMessages() {
        try (var connection = dataSource.getConnection();
             var stmt = connection.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS MESSAGES(
                    id SERIAL PRIMARY KEY,
                    content BYTEA NOT NULL,
                    log_timestamp BIGINT NOT NULL,
                    id_sender INTEGER NOT NULL,
                    id_group INTEGER NOT NULL,
                    FOREIGN KEY(id_sender) REFERENCES USERS(id) ON DELETE CASCADE,
                    FOREIGN KEY(id_group) REFERENCES GROUP_CHATS(id) ON DELETE CASCADE
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create MESSAGES table", e);
        }
    }

    public static void createTableOfflineQueue() {
        try (var connection = dataSource.getConnection();
             var stmt = connection.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS OFFLINE_QUEUE(
                    id SERIAL PRIMARY KEY,
                    id_user INTEGER NOT NULL,
                    packet_content TEXT NOT NULL,
                    created_at BIGINT,
                    FOREIGN KEY(id_user) REFERENCES USERS(id) ON DELETE CASCADE
                );
            """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Failed to create OFFLINE_QUEUE table", e);
        }
    }

    public static void insertUser(String username, String passwordHash, String salt, long createdAt) {
        String query = "INSERT INTO users(username, password_hash, salt, created_at) VALUES (?, ?, ?, ?)";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.setLong(4, createdAt);
            ps.executeUpdate();

            logger.info("[DATABASE] User inserted: " + username);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting user: " + username, e);
        }
    }

    public static User selectUserByUsername(String usernameData) {
        String query = """
            SELECT id, username, password_hash, salt, created_at,
                   identity_key, signed_pre_key, signature
            FROM USERS WHERE username = ?
        """;
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setString(1, usernameData);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("salt"),
                        rs.getLong("created_at"),
                        rs.getString("identity_key"),
                        rs.getString("signed_pre_key"),
                        rs.getString("signature")
                );
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error selecting user: " + usernameData, e);
        }
        return null;
    }

    public static List<String> selectUsersAddConversation() {
        List<String> users = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             var stmt = connection.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT id, username FROM USERS");
            while (rs.next()) {
                users.add(rs.getInt("id") + "," + rs.getString("username"));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching users for conversation", e);
        }
        return users;
    }

    public static synchronized boolean updateUserKeys(int userId, String ik, String spk, String sig) {
        String query = "UPDATE users SET identity_key=?, signed_pre_key=?, signature=? WHERE id=?";
        try (var connection = dataSource.getConnection();
             var ps = connection.prepareStatement(query)) {

            ps.setString(1, ik);
            ps.setString(2, spk);
            ps.setString(3, sig);
            ps.setInt(4, userId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error updating keys for user " + userId, e);
            return false;
        }
    }

    public static synchronized ChatDtos.GetBundleResponseDto selectUserKeys(int targetUserId) {
        String query = "SELECT identity_key, signed_pre_key, signature FROM users WHERE id=?";
        try (var connection = dataSource.getConnection();
             var ps = connection.prepareStatement(query)) {

            ps.setInt(1, targetUserId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String ik = rs.getString("identity_key");
                String spk = rs.getString("signed_pre_key");
                String sig = rs.getString("signature");
                if (ik == null || spk == null) return null;
                return new ChatDtos.GetBundleResponseDto(targetUserId, ik, spk, sig);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching keys for user " + targetUserId, e);
        }
        return null;
    }

    public static void insertUserLog(int userId, String actionType, long timestamp, String ipAddress) {
        String query = "INSERT INTO USER_LOGS(id_user, action_type, log_timestamp, ip_address) VALUES(?, ?, ?, ?)";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, userId);
            ps.setString(2, actionType);
            ps.setLong(3, timestamp);
            ps.setString(4, ipAddress);
            ps.executeUpdate();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting user log", e);
        }
    }

    public static void insertGroupChat(String name) {
        String query = "INSERT INTO GROUP_CHATS(name) VALUES(?)";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setString(1, name);
            ps.executeUpdate();
            logger.info("[DATABASE] Group chat inserted: " + name);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting group chat", e);
        }
    }

    public static GroupChat selectGroupChatByName(String groupChatName) {
        String query = "SELECT * FROM GROUP_CHATS WHERE name=?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setString(1, groupChatName);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new GroupChat(rs.getInt("id"), rs.getString("name"));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching group chat by name: " + groupChatName, e);
        }
        return null;
    }

    public static List<GroupChat> selectGroupChatsByUserId(int userId) {
        List<GroupChat> chats = new ArrayList<>();
        String query = """
            SELECT GROUP_CHATS.id, GROUP_CHATS.name
            FROM GROUP_CHATS
            JOIN GROUP_MEMBERS ON GROUP_CHATS.id = GROUP_MEMBERS.id_group
            WHERE id_user=?
        """;
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                chats.add(new GroupChat(rs.getInt(1), rs.getString(2)));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching group chats for user " + userId, e);
        }
        return chats;
    }

    public static boolean updateGroupChatName(int chatId, String newName) {
        String query = "UPDATE GROUP_CHATS SET name=? WHERE id=?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setString(1, newName);
            ps.setInt(2, chatId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error updating group chat name", e);
            return false;
        }
    }

    public static boolean deleteGroupChatTransactional(int chatId) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (
                    var psMsg = connection.prepareStatement("DELETE FROM MESSAGES WHERE id_group=?");
                    var psMem = connection.prepareStatement("DELETE FROM GROUP_MEMBERS WHERE id_group=?");
                    var psChat = connection.prepareStatement("DELETE FROM GROUP_CHATS WHERE id=?")
            ) {
                psMsg.setInt(1, chatId); psMsg.executeUpdate();
                psMem.setInt(1, chatId); psMem.executeUpdate();
                psChat.setInt(1, chatId);
                int rows = psChat.executeUpdate();

                connection.commit();
                return rows > 0;

            } catch (SQLException e) {
                connection.rollback();
                logger.log(Level.SEVERE, "[DATABASE] Transaction failed. Rolled back chat deletion: " + chatId, e);
                return false;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error during transactional group delete", e);
            return false;
        }
    }

    public static void insertGroupMember(int groupId, int userId) {
        String query = "INSERT INTO GROUP_MEMBERS(id_group, id_user) VALUES(?, ?)";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
            logger.info("[DATABASE] User " + userId + " added to Group " + groupId);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting group member", e);
        }
    }

    public static List<GroupMember> selectGroupMembersByChatId(int groupId) {
        List<GroupMember> members = new ArrayList<>();
        String query = "SELECT id_group, id_user FROM GROUP_MEMBERS WHERE id_group=?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(new GroupMember(rs.getInt(1), rs.getInt(2)));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching group members for group " + groupId, e);
        }
        return members;
    }

    public static int insertMessageReturningId(byte[] content, long timestamp, int senderId, int groupId) {
        String query = "INSERT INTO MESSAGES(content, log_timestamp, id_sender, id_group) VALUES(?,?,?,?) RETURNING id";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setBytes(1, content);
            ps.setLong(2, timestamp);
            ps.setInt(3, senderId);
            ps.setInt(4, groupId);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting message", e);
        }
        return -1;
    }

    public static List<Message> selectMessagesByGroup(int groupId) {
        List<Message> messages = new ArrayList<>();
        String query = "SELECT * FROM MESSAGES WHERE id_group=?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                messages.add(new Message(
                        rs.getInt(1),
                        rs.getBytes(2),
                        rs.getLong(3),
                        rs.getInt(4),
                        rs.getInt(5)
                ));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error fetching messages for group " + groupId, e);
        }
        return messages;
    }

    public static boolean updateMessageById(int id, byte[] newContent) {
        String query = "UPDATE MESSAGES SET content=? WHERE id=?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setBytes(1, newContent);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error updating message ID: " + id, e);
            return false;
        }
    }

    public static boolean deleteMessageById(int id) {
        String query = "DELETE FROM MESSAGES WHERE id=?";
        try (var connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(query)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error deleting message ID: " + id, e);
            return false;
        }
    }

    public static void insertPendingPacket(int targetId, String jsonPacket) {
        String query = "INSERT INTO OFFLINE_QUEUE (id_user, packet_content, created_at) VALUES (?, ?, ?)";
        try (var connection = dataSource.getConnection();
             var ps = connection.prepareStatement(query)) {

            ps.setInt(1, targetId);
            ps.setString(2, jsonPacket);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error inserting pending packet for user " + targetId, e);
        }
    }

    public static List<String> getAndClearPendingPackets(int userId) {
        List<String> queue = new ArrayList<>();
        String selectQuery = "SELECT packet_content FROM OFFLINE_QUEUE WHERE id_user=? ORDER BY id ASC";
        String deleteQuery = "DELETE FROM OFFLINE_QUEUE WHERE id_user=?";

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try (var ps = connection.prepareStatement(selectQuery)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) queue.add(rs.getString("packet_content"));
                }
            }

            if (!queue.isEmpty()) {
                try (var del = connection.prepareStatement(deleteQuery)) {
                    del.setInt(1, userId);
                    del.executeUpdate();
                }
                connection.commit();
                logger.info("[OFFLINE] Delivered " + queue.size() + " packets to User " + userId);
            } else {
                connection.rollback();
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[DATABASE] Error clearing pending packets for user " + userId, e);
        }
        return queue;
    }
}

