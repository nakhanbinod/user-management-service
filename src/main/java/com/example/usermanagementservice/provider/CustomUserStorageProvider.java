package com.example.usermanagementservice.provider;

import com.example.usermanagementservice.model.Users;
import com.example.usermanagementservice.util.DatabaseConnection;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CustomUserStorageProvider implements UserStorageProvider, UserLookupProvider, CredentialInputValidator, UserQueryProvider {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserStorageProvider.class);

    private KeycloakSession session;

    private ComponentModel model;


    public CustomUserStorageProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    @Override
    public void close() {
        logger.info("Close");
    }

    @Override
    public int getUsersCount(RealmModel realm) {
        logger.info("getUsersCount: realm={}", realm.getName());
        try (Connection c = DatabaseConnection.getConnection(this.model)) {
            Statement st = c.createStatement();
            st.execute("select count(*) from users");
            ResultSet rs = st.getResultSet();
            rs.next();

            return rs.getInt(1);
        } catch (SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(), ex);
        }
    }

    @Override
    public List<UserModel> getUsers(RealmModel realm) {
        return getUsers(realm, 0, 1000);
    }

    @Override
    public List<UserModel> getUsers(RealmModel realm, int firstResult, int maxResults) {
        logger.info("getUsers: realm={}", realm.getName());

        try (Connection c = DatabaseConnection.getConnection(this.model)) {
            PreparedStatement st = c.prepareStatement(
                    "select username, firstName,lastName, email, birthDate from users order by username limit ? offset ?");
            st.setInt(1, maxResults);
            st.setInt(2, firstResult);
            st.execute();
            ResultSet rs = st.getResultSet();
            List<UserModel> users = new ArrayList<>();
            while (rs.next()) {
                users.add(mapUser(realm, rs));
            }
            return users;
        } catch (SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(), ex);
        }
    }

    @Override
    public List<UserModel> searchForUser(String search, RealmModel realm) {
        return searchForUser(search, realm, 0, 1000);
    }

    @Override
    public List<UserModel> searchForUser(String search, RealmModel realm, int firstResult, int maxResults) {
        logger.info("searchForUser: realm={}", realm.getName());

        try (Connection c = DatabaseConnection.getConnection(this.model)) {
            PreparedStatement st = c.prepareStatement(
                    "select username, firstName,lastName, email, birthDate from users where username like ? order by username limit ? offset ?");
            st.setString(1, search);
            st.setInt(2, maxResults);
            st.setInt(3, firstResult);
            st.execute();
            ResultSet rs = st.getResultSet();
            List<UserModel> users = new ArrayList<>();
            while (rs.next()) {
                users.add(mapUser(realm, rs));
            }
            return users;
        } catch (SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(), ex);
        }
    }

    @Override
    public List<UserModel> searchForUser(Map<String, String> params, RealmModel realm) {
        return searchForUser(params, realm, 0, 1000);
    }

    @Override
    public List<UserModel> searchForUser(Map<String, String> params, RealmModel realm, int firstResult,
                                         int maxResults) {
        return getUsers(realm, firstResult, maxResults);
    }

    @Override
    public List<UserModel> getGroupMembers(RealmModel realm, GroupModel group) {
        return Collections.emptyList();
    }

    @Override
    public List<UserModel> getGroupMembers(RealmModel realm, GroupModel group, int firstResult, int maxResults) {
        return Collections.emptyList();
    }

    @Override
    public List<UserModel> searchForUserByUserAttribute(String attrName, String attrValue, RealmModel realm) {
        return Collections.emptyList();
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        logger.info("supportsCredentialType({})", credentialType);
        return PasswordCredentialModel.TYPE.endsWith(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        logger.info("isConfiguredFor(realm={},user={},credentialType={})", realm.getName(), user.getUsername(),
                credentialType);
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput credentialInput) {
        logger.info("isValid(realm={},user={},credentialInput.type={})", realm.getName(), user.getUsername(),
                credentialInput.getType());
        if (!this.supportsCredentialType(credentialInput.getType())) {
            return false;
        }
        StorageId sid = new StorageId(user.getId());
        String username = sid.getExternalId();

        try (Connection c = DatabaseConnection.getConnection(this.model)) {
            PreparedStatement st = c.prepareStatement("select password from users where username = ?");
            st.setString(1, username);
            st.execute();
            ResultSet rs = st.getResultSet();
            if (rs.next()) {
                String pwd = rs.getString(1);
                return pwd.equals(credentialInput.getChallengeResponse());
            } else {
                return false;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(), ex);
        }
    }

    @Override
    public UserModel getUserById(String id, RealmModel realm) {
        logger.info("getUserById({})", id);
        StorageId sid = new StorageId(id);
        return getUserByUsername(sid.getExternalId(), realm);
    }

    @Override
    public UserModel getUserByUsername(String username, RealmModel realm) {
        logger.info("getUserByUsername({})", username);
        try (Connection c = DatabaseConnection.getConnection(this.model)) {
            PreparedStatement st = c.prepareStatement(
                    "select username, firstName, lastName, email, birthDate from users where username = ?");
            st.setString(1, username);
            st.execute();
            ResultSet rs = st.getResultSet();
            if (rs.next()) {
                return mapUser(realm, rs);
            } else {
                return null;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(), ex);
        }
    }

    @Override
    public UserModel getUserByEmail(String email, RealmModel realm) {
        logger.info("getUserByEmail({})", email);
        try (Connection c = DatabaseConnection.getConnection(this.model)) {
            PreparedStatement st = c.prepareStatement(
                    "select username, firstName,lastName, email, birthDate from users where email = ?");
            st.setString(1, email);
            st.execute();
            ResultSet rs = st.getResultSet();
            if (rs.next()) {
                return mapUser(realm, rs);
            } else {
                return null;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(), ex);
        }
    }

    private UserModel mapUser(RealmModel realm, ResultSet rs) throws SQLException {
        return new Users.Builder(session, realm, model, rs.getString("username"))
                .email(rs.getString("email"))
                .firstName(rs.getString("firstName"))
                .lastName(rs.getString("lastName"))
                .birthDate(rs.getDate("birthDate"))
                .build();
    }
}
