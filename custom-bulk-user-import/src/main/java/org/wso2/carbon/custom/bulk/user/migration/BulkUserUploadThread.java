package org.wso2.carbon.custom.bulk.user.migration;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.custom.bulk.user.migration.internal.CustomUserAdministratorDataHolder;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;

import static org.wso2.carbon.custom.bulk.user.migration.Constants.*;

public class BulkUserUploadThread implements Callable<Boolean> {
    private static final Log log = LogFactory.getLog(BulkUserUploadThread.class);
    private Properties properties = new Properties();
    private String tenantDomain = "carbon.super";
    private int tenantId = -1234;
    private String userDomain = "primary";
    private UserStoreManager store;
    private File[] files;
    private String[] firstLine;
    private String outputDirectory;

    /**
     * This is the main method executed by the thread. It handles the entire bulk user upload process.
     * It performs the following steps:
     * 1. Checks prerequisites (configuration, tenant context, CSV files).
     * 2. Reads user data from CSV files.
     * 3. Provisions users into the user store.
     * 4. Logs the results (successful and failed users).
     *
     * @return true if the process completes successfully, false if any error occurs.
     */
    @Override
    public Boolean call() {
        if (!doCheckPrerequisites()) {
            return false;
        }

        long startTime = System.currentTimeMillis();
        List<String[]> successfulUsers = new ArrayList<>();
        List<String[]> failedUsers = new ArrayList<>();

        if (store != null) {
            Set<String[]> userSet = readUsersFromCSVFiles();
            if (userSet == null) {
                return false; // Error occurred while reading files
            }

            log.info(BULK_UPLOAD_LOG_PREFIX + "Starting user provisioning to the given user store...");
            provisionUsers(userSet, successfulUsers, failedUsers);

            PrivilegedCarbonContext.endTenantFlow();

            logTimeTaken("Reading from CSV files", startTime, System.currentTimeMillis());

            writeUsersToCSV(successfulUsers, "successful_users.csv");
            writeUsersToCSV(failedUsers, "failed_users.csv");
        }
        return true;
    }

    /**
     * Reads user data from CSV files and returns it as a set of String arrays.
     * Each array represents a user, and each element in the array represents a field (e.g., username, password, claims).
     *
     * Steps:
     * 1. Opens each CSV file.
     * 2. Reads the header line (first line) to determine the structure of the data.
     * 3. Reads and trims subsequent lines (user data).
     * 4. Adds the user data to a set to avoid duplicates.
     *
     * @return A set of user data arrays, or null if an error occurs while reading the files.
     */
    private Set<String[]> readUsersFromCSVFiles() {
        Set<String[]> userSet = new HashSet<>();
        for (File file : files) {
            log.info(BULK_UPLOAD_LOG_PREFIX + "Reading from file " + file.getAbsolutePath());

            InputStream targetStream = null;
            BufferedReader reader = null;
            CSVReader csvReader = null;

            try {
                targetStream = new FileInputStream(file);
                reader = new BufferedReader(new InputStreamReader(targetStream, StandardCharsets.UTF_8));
                csvReader = new CSVReader(reader);

                String[] line = csvReader.readNext();
                if (line != null) {
                    firstLine = Arrays.stream(line).map(String::trim).toArray(String[]::new);
                }

                while ((line = csvReader.readNext()) != null) {
                    if (line.length > 0) {
                        String[] trimmedLine = Arrays.stream(line).map(String::trim).toArray(String[]::new);
                        userSet.add(trimmedLine);
                    }
                }
            } catch (IOException e) {
                log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while reading from CSV files", e);
                return null;
            } finally {
                if (csvReader != null) {
                    try {
                        csvReader.close();
                    } catch (IOException e) {
                        log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while closing CSVReader", e);
                    }
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while closing BufferedReader", e);
                    }
                }
                if (targetStream != null) {
                    try {
                        targetStream.close();
                    } catch (IOException e) {
                        log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while closing FileInputStream", e);
                    }
                }
            }
        }
        return userSet;
    }

    private void provisionUsers(Set<String[]> userSet, List<String[]> successfulUsers, List<String[]> failedUsers) {
        for (String[] user : userSet) {
            if (user != null && user[0] != null && !user[0].isEmpty()) {
                Map<String, String> claims = new HashMap<>();
                for (int i = 2; i < firstLine.length; i++) { // Skip Username and Password
                    String claimURI = firstLine[i];
                    String value = user[i];
                    if (!claimURI.isEmpty() && !value.isEmpty()) {
                        claims.put(claimURI, value);
                    }
                }

                try {
                    ((AbstractUserStoreManager) store).addUserWithID(user[0], user[1], null, claims, null);
                    successfulUsers.add(user);
                } catch (UserStoreException e) {
                    log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while adding user with the username: " + user[0], e);
                    failedUsers.add(user);
                }
            }
        }
    }

    /**
     * Checks if all prerequisites for the bulk upload process are met.
     * This includes:
     * 1. Loading configuration properties from the config file.
     * 2. Setting up the tenant context.
     * 3. Finding and validating CSV files in the specified directory.
     * 4. Initializing the user store manager.
     *
     * @return true if all prerequisites are satisfied, false otherwise.
     */
    public boolean doCheckPrerequisites() {
        long startTime = System.currentTimeMillis();
        log.info(BULK_UPLOAD_LOG_PREFIX + "Started prerequisite check.");

        if (!loadProperties()) {
            return false;
        }

        tenantDomain = properties.getProperty(TENANT_DOMAIN);
        userDomain = properties.getProperty(USER_DOMAIN);
        outputDirectory = properties.getProperty(OUTPUT_DIRECTORY);
        String folderPath = properties.getProperty(FOLDER_PATH);

        tenantId = getTenantIdFromDomain(tenantDomain);
        if (tenantId == -2) {
            return false;
        }

        initializeTenantContext();

        files = getCSVFilesFromDirectory(folderPath);
        if (files == null || files.length == 0) {
            log.error(BULK_UPLOAD_LOG_PREFIX + "Prerequisites were not satisfied. No CSV file is found at " + folderPath);
            return false;
        }

        if (!initializeUserStoreManager()) {
            return false;
        }

        logTimeTaken("Prerequisite check", startTime, System.currentTimeMillis());
        return true;
    }

    private boolean loadProperties() {
        try (InputStream inputStream = new FileInputStream(CONFIG_FILE_PATH)) {
            properties.load(inputStream);
        } catch (IOException e) {
            log.error(BULK_UPLOAD_LOG_PREFIX + "Error while loading properties file. Task Aborted.", e);
            return false;
        }
        return true;
    }

    private void initializeTenantContext() {
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenantId);
    }

    private File[] getCSVFilesFromDirectory(String folderPath) {
        File dir = new File(folderPath);
        return dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".csv"));
    }

    private boolean initializeUserStoreManager() {
        try {
            log.info(BULK_UPLOAD_LOG_PREFIX + "Attempting to find user store manager for tenant: "
                    + tenantDomain + ", TenantId: " + tenantId + ", userstore: " + userDomain);
            store = getUserStoreManager(userDomain, tenantDomain);
            if (store == null) {
                log.error(BULK_UPLOAD_LOG_PREFIX + "User store manager not found for tenant: "
                        + tenantDomain + ", userstore: " + userDomain);
                return false;
            }

            log.info(BULK_UPLOAD_LOG_PREFIX + "User store manager successfully initialized for tenant: "
                    + tenantDomain + ", TenantId: " + tenantId + ", userstore: " + userDomain);
            return true;
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            log.error(BULK_UPLOAD_LOG_PREFIX + "Error while obtaining user store manager for tenant: "
                    + tenantDomain + ", userstore: " + userDomain, e);
            return false;
        }
    }


    /**
     * Retrieves the tenant ID from the tenant domain.
     *
     * @param tenantDomain The tenant domain (e.g., "carbon.super").
     * @return The tenant ID if found, or -2 if an error occurs.
     */
    private int getTenantIdFromDomain(String tenantDomain) {
        try {
            RealmService realm = CustomUserAdministratorDataHolder.getInstance().getRealmService();
            return realm.getTenantManager().getTenantId(tenantDomain);
        } catch (Throwable e) {
            log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while resolving tenant Id from tenant domain: " + tenantDomain, e);
            return -2;
        }
    }

    /**
     * Retrieves the user store manager for the given user domain and tenant domain.
     * If a secondary user store is specified, it waits for it to become available (up to 30 seconds).
     *
     * @param userDomain The user domain.
     * @param tenantDomain The tenant domain.
     * @return The UserStoreManager instance, or null if the user store is not found.
     * @throws org.wso2.carbon.user.api.UserStoreException If an error occurs while retrieving the user store manager.
     */
    private UserStoreManager getUserStoreManager(String userDomain, String tenantDomain)
            throws org.wso2.carbon.user.api.UserStoreException {

        RealmService realm = CustomUserAdministratorDataHolder.getInstance().getRealmService();
        if (StringUtils.isNotEmpty(userDomain)) {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() < startTime + 300000) {
                UserStoreManager store = ((AbstractUserStoreManager) realm.getTenantUserRealm(tenantId).getUserStoreManager())
                        .getSecondaryUserStoreManager(userDomain);
                if (store != null) {
                    return store;
                }
            }
            log.error(BULK_UPLOAD_LOG_PREFIX + "Secondary user store manager not found for userDomain: "
                    + userDomain + ", tenantDomain: " + tenantDomain + "Allocated time exceeded. Task aborted.");
            return null;
        } else {
            log.info(BULK_UPLOAD_LOG_PREFIX + "Retrieving primary user store manager for tenantDomain: " + tenantDomain);
            return realm.getTenantUserRealm(tenantId).getUserStoreManager();
        }
    }

    /**
     * Writes a list of users to a CSV file. This method is used to save both successful and failed users
     * to separate CSV files for logging and auditing purposes.
     *
     * The file is saved in the specified output directory with the given file name.
     *
     * @param users The list of users to write. Each user is represented as a String array.
     * @param fileName The name of the output CSV file (e.g., "successful_users.csv" or "failed_users.csv").
     */
    private void writeUsersToCSV(List<String[]> users, String fileName) {
        if (users.isEmpty()) {
            log.info(BULK_UPLOAD_LOG_PREFIX + "No users to write to " + fileName);
            return;
        }

        String outputDir = outputDirectory.endsWith(File.separator) ? outputDirectory : outputDirectory + File.separator;
        String filePath = outputDir + fileName;

        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(String.join(",", firstLine) + "\n");
            for (String[] user : users) {
                writer.write(String.join(",", user) + "\n");
            }
            log.info(BULK_UPLOAD_LOG_PREFIX + "Successfully wrote " + users.size() + " users to " + filePath);
        } catch (IOException e) {
            log.error(BULK_UPLOAD_LOG_PREFIX + "Error occurred while writing to " + filePath, e);
        }
    }

    private void logTimeTaken(String operation, long startTime, long endTime) {
        log.info(BULK_UPLOAD_LOG_PREFIX + "[TIME INDICATOR] Total time taken for " + operation +
                " (in milliseconds): " + (endTime - startTime));
    }
}