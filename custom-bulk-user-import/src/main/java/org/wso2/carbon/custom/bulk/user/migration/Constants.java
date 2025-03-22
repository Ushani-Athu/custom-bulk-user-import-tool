package org.wso2.carbon.custom.bulk.user.migration;

public class Constants {

    public static final int DEFAULT_BULK_USER_UPLOAD_POOL_SIZE = 4;
    public static final String BULK_UPLOAD = "bulkupload";
    public static final String BULK_UPLOAD_LOG_PREFIX = "[CUSTOM BULK USER UPLOADER]";

    // Configuration file paths

    //properties file directory
    public static final String FOLDER_PATH_PROPERTIES = "./repository/conf/";
    // Name of the properties file
    public static final String CONFIG_FILE_NAME = "bulk.user.properties";
    // Full path to the properties file
    public static final String CONFIG_FILE_PATH = FOLDER_PATH_PROPERTIES + CONFIG_FILE_NAME;
    // CSV file directory
    public static final String FOLDER_PATH = "csvFilePath";
    //output directory in properties file
    public static final String OUTPUT_DIRECTORY = "outputDirectory";

    // Tenant and User Domain Configuration
    public static final String TENANT_DOMAIN = "tenantDomain";
    public static final String USER_DOMAIN = "userDomain";


}
